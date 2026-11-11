package io.aridhiran.conceptnet

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Navy = Color(0xFF102B4E)
private val Blue = Color(0xFF1769D2)
private val Muted = Color(0xFF657184)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ConceptNetApp(this) }
    }
}

private data class Edge(val relation: String, val start: String, val end: String, val weight: Double)
private data class Concept(val term: String, val edges: List<Edge>, val related: List<String>)

private fun label(uri: String) = uri.substringAfterLast("/").replace('_', ' ').replaceFirstChar { it.uppercase() }

private suspend fun api(path: String): JSONObject = withContext(Dispatchers.IO) {
    var attempt = 0
    while (attempt < 3) {
        val connection = (URL("https://api.conceptnet.io$path").openConnection() as HttpURLConnection)
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/ld+json")
        var retry = false
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) return@withContext JSONObject(body)
            if (code == 429 || code == 502 || code == 503 || code == 504) {
                retry = attempt < 2
                if (!retry) {
                    val reason = when (code) {
                        429 -> "rate limit reached"
                        502, 503, 504 -> "ConceptNet service is temporarily unavailable"
                        else -> "request failed"
                    }
                    error("$reason (HTTP $code). Please try again in a little while.")
                }
            } else {
                error("ConceptNet API returned HTTP $code. Check the search term and try again.")
            }
        } finally {
            connection.disconnect()
        }
        if (retry) {
            delay(1000L shl attempt)
            attempt++
        }
    }
    error("ConceptNet service is temporarily unavailable. Please try again later.")
}

private suspend fun lookup(context: Context, term: String, lang: String): Concept {
    val clean = term.trim()
    val store = HybridStore(context)
    try {
        val slug = URLEncoder.encode(clean.replace(' ', '_'), "UTF-8").replace("+", "%20")
        val data = api("/c/$lang/$slug?limit=80&format=json")
        val edges = mutableListOf<Edge>()
        val related = linkedSetOf<String>()
        val array = data.optJSONArray("edges")
        if (array != null) for (i in 0 until array.length()) {
            val e = array.optJSONObject(i) ?: continue
            val relation = e.optJSONObject("rel")?.optString("label").orEmpty().ifBlank { "RelatedTo" }
            val startLabel = e.optJSONObject("start")?.optString("label").orEmpty()
            val endLabel = e.optJSONObject("end")?.optString("label").orEmpty()
            edges.add(Edge(relation, startLabel, endLabel, e.optDouble("weight", 1.0)))
            if (startLabel.isNotBlank() && !startLabel.equals(clean, true)) related.add(startLabel)
            if (endLabel.isNotBlank() && !endLabel.equals(clean, true)) related.add(endLabel)
        }
        if (edges.isNotEmpty()) {
            val result = Concept(clean, edges, related.toList())
            withContext(Dispatchers.IO) { store.put(lang, result) }
            return result
        }
    } catch (_: Exception) {
        // The live service is optional; continue with local data.
    }
    val cached = withContext(Dispatchers.IO) { store.get(lang, clean) }
    if (cached != null) return cached
    val bundled = withContext(Dispatchers.IO) { store.seed(lang, clean) }
    if (bundled != null) return bundled
    error(if (lang == "ta")
        "இணையத் தரவும் உள்ளகத் தரவும் கிடைக்கவில்லை. மரம், நீர், வீடு, புத்தகம், நாய் போன்ற சொல்லை முயற்சிக்கவும்."
    else
        "No online result or local cache. Try a bundled term such as tree, water, house, book or dog.")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConceptNetApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("conceptnet", Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("en") }
    var concept by remember { mutableStateOf<Concept?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(prefs.getStringSet("saved", emptySet())?.toSet() ?: emptySet()) }
    val scope = rememberCoroutineScope()

    fun search(value: String) {
        if (value.isBlank()) return
        query = value
        tab = 0
        loading = true
        error = null
        scope.launch {
            try { concept = lookup(context, value, lang) }
            catch (e: Exception) { error = e.message ?: "Search failed. Check your internet connection." }
            finally { loading = false }
        }
    }
    fun save(value: String) {
        saved = saved + value
        prefs.edit().putStringSet("saved", saved).apply()
    }
    fun remove(value: String) {
        saved = saved - value
        prefs.edit().putStringSet("saved", saved).apply()
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Color(0xFFF7F9FC), surface = Color.White)) {
        Scaffold(
            containerColor = Color(0xFFF7F9FC),
            bottomBar = {
                NavigationBar {
                    listOf("Home", "Graph", "Saved", "About").forEachIndexed { index, name ->
                        val icon = when(index) {
                            0 -> Icons.Default.Home
                            1 -> Icons.Default.AccountTree
                            2 -> Icons.Default.Bookmark
                            else -> Icons.Default.Info
                        }
                        NavigationBarItem(selected = tab == index, onClick = { tab = index },
                            icon = { Icon(icon, contentDescription = name) }, label = { Text(name) })
                    }
                }
            }
        ) { padding ->
            when (tab) {
                0 -> Home(query, { query = it }, lang, { lang = it }, loading, error, concept,
                    saved.contains(concept?.term), ::search,
                    { c -> if (saved.contains(c)) remove(c) else save(c) }, Modifier.padding(padding))
                1 -> GraphPage(concept, ::search, Modifier.padding(padding))
                2 -> SavedPage(saved, ::search, ::remove, Modifier.padding(padding))
                else -> AboutPage(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun Home(
    query: String, onQuery: (String) -> Unit, lang: String, onLang: (String) -> Unit,
    loading: Boolean, error: String?, concept: Concept?, isSaved: Boolean,
    search: (String) -> Unit, toggleSave: (String) -> Unit, modifier: Modifier
) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 24.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            GraphLogo()
            Spacer(Modifier.width(12.dp))
            Column {
                Text("ConceptNet", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text("Explore commonsense knowledge", color = Muted, fontSize = 12.sp)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search for a concept...") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Language", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = lang == "en", onClick = { onLang("en") }, label = { Text("English") })
            Spacer(Modifier.width(4.dp))
            FilterChip(selected = lang == "ta", onClick = { onLang("ta") }, label = { Text("தமிழ்") })
            Spacer(Modifier.weight(1f))
            Button(onClick = { search(query) }, enabled = query.isNotBlank() && !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (lang == "ta") "தேடு" else "Search")
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(if (lang == "ta") "மாதிரிச் சொற்கள்" else "Try a concept", fontWeight = FontWeight.SemiBold, color = Navy)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (lang == "ta") listOf("மரம்", "நீர்", "வீடு", "நாய்") else listOf("tree", "water", "book", "dog").forEach {
                SuggestionChip(onClick = { search(it) }, label = { Text(it) })
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
        if (concept == null && !loading) {
            Spacer(Modifier.height(28.dp))
            Text("Discover how ideas connect", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text("Search a word or phrase to explore semantic relationships and related concepts in the ConceptNet knowledge graph.",
                color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
        if (concept != null) {
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(concept.term, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f))
                TextButton(onClick = { toggleSave(concept.term) }) {
                    Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null)
                    Text(if (isSaved) "Saved" else "Save")
                }
            }
            Text("${concept.edges.size} relationships · ${concept.related.size} related concepts", color = Muted, fontSize = 12.sp)
            if (concept.related.isNotEmpty()) {
                Text("Related concepts", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(concept.related) { term ->
                        ListItem(
                            headlineContent = { Text(term) },
                            leadingContent = { Icon(Icons.Default.Hub, null, tint = Blue) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = Muted) },
                            modifier = Modifier.clickable { search(term) }
                        )
                        HorizontalDivider(color = Color(0xFFE9EDF3))
                    }
                }
            } else {
                Text("No related concepts returned.", color = Muted, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun GraphPage(concept: Concept?, search: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Knowledge graph", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Explore connected concepts", color = Muted)
        Spacer(Modifier.height(20.dp))
        if (concept == null) Text("Search for a concept on the Home tab first.", color = Muted)
        else {
            GraphPanel(concept.term, concept.related.take(10))
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                items(concept.related) { term ->
                    ListItem(headlineContent = { Text(term) },
                        leadingContent = { Icon(Icons.Default.Circle, null, tint = Blue) },
                        modifier = Modifier.clickable { search(term) })
                }
            }
        }
    }
}

@Composable
private fun GraphPanel(center: String, nodes: List<String>) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth().height(240.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension * .36f
                val points = nodes.mapIndexed { i, _ ->
                    val a = 2 * PI * i / maxOf(nodes.size, 1) - PI / 2
                    Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
                }
                points.forEach { drawLine(Color(0xFF9DB4D1), c, it, 3f) }
                points.forEach { drawCircle(Blue, 10f, it) }
                drawCircle(Navy, 18f, c)
            }
            Text(center, fontWeight = FontWeight.Bold, color = Navy, fontSize = 18.sp)
            Text("Connected concepts", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SavedPage(saved: Set<String>, search: (String) -> Unit, remove: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Saved concepts", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy)
        if (saved.isEmpty()) Text("Your saved concepts will appear here.", color = Muted, modifier = Modifier.padding(top = 12.dp))
        LazyColumn {
            items(saved.sorted()) { term ->
                ListItem(headlineContent = { Text(term) },
                    modifier = Modifier.clickable { search(term) },
                    trailingContent = { IconButton(onClick = { remove(term) }) { Icon(Icons.Default.DeleteOutline, null) } })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AboutPage(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("About ConceptNet", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy)
        Spacer(Modifier.height(12.dp))
        Text("ConceptNet is a multilingual commonsense knowledge graph. This app queries its public read-only API and presents concepts, semantic relationships, and related terms.")
        Spacer(Modifier.height(16.dp))
        Text("Data source", fontWeight = FontWeight.Bold)
        Text("ConceptNet 5 · https://api.conceptnet.io")
        Spacer(Modifier.height(12.dp))
        Text("Internet connection is required for live searches. Saved concept names are stored locally on this device.")
        Spacer(Modifier.height(12.dp))
        Text("ConceptNet data is licensed under CC BY-SA 4.0. See the official documentation for attribution requirements.")
    }
}

@Composable
private fun GraphLogo() {
    Canvas(Modifier.size(52.dp)) {
        val p = listOf(Offset(size.width*.18f,size.height*.25f),Offset(size.width*.72f,size.height*.12f),
            Offset(size.width*.82f,size.height*.75f),Offset(size.width*.30f,size.height*.82f),Offset(size.width*.48f,size.height*.48f))
        listOf(0 to 4,1 to 4,2 to 4,3 to 4,0 to 3,1 to 2).forEach { drawLine(Color(0xFF8DA4C2),p[it.first],p[it.second],3f) }
        p.forEachIndexed { i, pt -> drawCircle(if(i%2==0) Blue else Color(0xFF8295AC), if(i==4) 7f else 8f, pt) }
    }
}
