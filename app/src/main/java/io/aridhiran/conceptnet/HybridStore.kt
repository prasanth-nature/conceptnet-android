package io.aridhiran.conceptnet

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

internal class HybridStore(context: Context) : SQLiteOpenHelper(context, "conceptnet_hybrid.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cache (lang TEXT NOT NULL, term TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(lang, term))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun get(lang: String, term: String): Concept? {
        val c = readableDatabase.rawQuery("SELECT payload FROM cache WHERE lang=? AND lower(term)=lower(?)", arrayOf(lang, term))
        c.use {
            if (!it.moveToFirst()) return null
            val j = JSONObject(it.getString(0))
            val edges = mutableListOf<Edge>()
            val a = j.optJSONArray("edges") ?: JSONArray()
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                edges.add(Edge(e.optString("relation"), e.optString("start"), e.optString("end"), e.optDouble("weight", 1.0)))
            }
            val related = mutableListOf<String>()
            val r = j.optJSONArray("related") ?: JSONArray()
            for (i in 0 until r.length()) related.add(r.optString(i))
            return Concept(j.optString("term"), edges, related.distinct())
        }
    }

    fun put(lang: String, concept: Concept) {
        val j = JSONObject().put("term", concept.term)
            .put("edges", JSONArray().apply {
                concept.edges.forEach { e -> put(JSONObject().put("relation", e.relation).put("start", e.start).put("end", e.end).put("weight", e.weight)) }
            }).put("related", JSONArray(concept.related))
        val v = ContentValues().apply { put("lang", lang); put("term", concept.term); put("payload", j.toString()) }
        writableDatabase.insertWithOnConflict("cache", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun seed(lang: String, term: String): Concept? {
        val tamil = lang == "ta"
        val rows = listOf(
            arrayOf("tree","மரம்","IsA","plant","தாவரம்"),
            arrayOf("tree","மரம்","HasA","root","வேர்"),
            arrayOf("tree","மரம்","HasA","branch","கிளை"),
            arrayOf("tree","மரம்","UsedFor","shade","நிழல்"),
            arrayOf("water","நீர்","UsedFor","drinking","குடித்தல்"),
            arrayOf("water","நீர்","RelatedTo","river","ஆறு"),
            arrayOf("house","வீடு","HasA","room","அறை"),
            arrayOf("book","புத்தகம்","UsedFor","reading","வாசித்தல்"),
            arrayOf("book","புத்தகம்","RelatedTo","knowledge","அறிவு"),
            arrayOf("knowledge","அறிவு","RelatedTo","learning","கற்றல்"),
            arrayOf("dog","நாய்","IsA","animal","விலங்கு"),
            arrayOf("dog","நாய்","CapableOf","barking","குரைத்தல்"),
            arrayOf("flower","மலர்","PartOf","plant","தாவரம்"),
            arrayOf("sun","சூரியன்","AtLocation","sky","வானம்"),
            arrayOf("moon","நிலா","AtLocation","sky","வானம்"),
            arrayOf("food","உணவு","UsedFor","eating","உண்ணுதல்"),
            arrayOf("school","பள்ளி","UsedFor","learning","கற்றல்"),
            arrayOf("music","இசை","RelatedTo","sound","ஒலி"),
            arrayOf("science","அறிவியல்","RelatedTo","knowledge","அறிவு")
        )
        val found = rows.filter { (if (tamil) it[1] else it[0]).equals(term.trim(), ignoreCase = true) }
        if (found.isEmpty()) return null
        val edges = found.map { if (tamil) Edge(it[2], it[1], it[4], 1.0) else Edge(it[2], it[0], it[3], 1.0) }
        val related = found.map { if (tamil) it[4] else it[3] }.distinct()
        return Concept(if (tamil) found[0][1] else found[0][0], edges, related)
    }
}
