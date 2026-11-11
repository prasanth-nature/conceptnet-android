package io.aridhiran.conceptnet

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

internal object FullGraphStore {
    private const val RELEASE_URL =
        "https://github.com/prasanth-nature/conceptnet-android/releases/download/conceptnet-en-ta-5.7.0/conceptnet-en-ta-5.7.0.sqlite.gz"

    private fun databaseFile(context: Context) =
        File(context.filesDir, "conceptnet-en-ta-5.7.0.sqlite")

    fun isInstalled(context: Context): Boolean =
        databaseFile(context).let { it.exists() && it.length() > 0L }

    suspend fun download(
        context: Context,
        onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val target = databaseFile(context)
        val compressed = File(context.cacheDir, "conceptnet-en-ta.sqlite.gz.part")
        val expanded = File(context.filesDir, "conceptnet-en-ta.sqlite.part")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(RELEASE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 120000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val code = connection.responseCode
            check(code in 200..299) { "Database download failed (HTTP $code)." }
            val total = connection.contentLengthLong.coerceAtLeast(-1L)
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(compressed).use { output ->
                    val buffer = ByteArray(1024 * 256)
                    var read: Int
                    var received = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        received += read
                        onProgress(received, total)
                    }
                    output.fd.sync()
                }
            }
            GZIPInputStream(BufferedInputStream(compressed.inputStream()), 1024 * 256).use { input ->
                FileOutputStream(expanded).use { output ->
                    val buffer = ByteArray(1024 * 256)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) output.write(buffer, 0, read)
                    output.fd.sync()
                }
            }
            // Open the database before replacing an existing installation.
            val checkDb = SQLiteDatabase.openDatabase(expanded.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val integrity = checkDb.rawQuery("PRAGMA quick_check", null).use {
                    if (it.moveToFirst()) it.getString(0) else "no result"
                }
                check(integrity == "ok") { "Downloaded database failed its integrity check." }
                val version = checkDb.rawQuery("SELECT value FROM build_info WHERE key='dataset'", null).use {
                    if (it.moveToFirst()) it.getString(0) else ""
                }
                check(version == "ConceptNet 5.7.0") { "Unexpected database version: $version" }
            } finally {
                checkDb.close()
            }
            if (target.exists()) target.delete()
            check(expanded.renameTo(target)) { "Could not install the downloaded database." }
        } finally {
            connection?.disconnect()
            compressed.delete()
            expanded.delete()
        }
    }

    fun lookup(context: Context, term: String, language: String): Concept? {
        val file = databaseFile(context)
        if (!file.exists()) return null
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val normalized = term.trim().lowercase()
            val matchingUris = mutableListOf<String>()
            db.rawQuery(
                "SELECT uri FROM nodes WHERE language=? AND normalized_label=? LIMIT 100",
                arrayOf(language, normalized)
            ).use { cursor ->
                while (cursor.moveToNext()) matchingUris.add(cursor.getString(0))
            }
            if (matchingUris.isEmpty()) return null

            val edges = mutableListOf<Edge>()
            val related = linkedSetOf<String>()
            var display = term.trim()
            for (uri in matchingUris) {
                db.rawQuery(
                    """SELECT e.relation_uri, s.label, s.uri, t.label, t.uri, e.weight
                       FROM edges e
                       JOIN nodes s ON s.uri=e.start_uri
                       JOIN nodes t ON t.uri=e.end_uri
                       WHERE e.start_uri=? OR e.end_uri=?
                       LIMIT 500""",
                    arrayOf(uri, uri)
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val relation = cursor.getString(0).substringAfterLast("/")
                        val startLabel = cursor.getString(1)
                        val startUri = cursor.getString(2)
                        val endLabel = cursor.getString(3)
                        val endUri = cursor.getString(4)
                        val weight = cursor.getDouble(5)
                        if (startUri == uri) display = startLabel
                        edges.add(Edge(relation, startLabel, endLabel, weight))
                        if (startUri != uri) related.add(startLabel)
                        if (endUri != uri) related.add(endLabel)
                    }
                }
            }
            if (edges.isEmpty()) return null
            return Concept(display, edges.distinctBy { "${it.relation}|${it.start}|${it.end}|${it.weight}" }, related.toList())
        } finally {
            db.close()
        }
    }
}
