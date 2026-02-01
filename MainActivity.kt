package com.example.toitoitour
//////////////////////
import java.util.concurrent.TimeUnit
import android.util.Log
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toitoitour.ui.theme.ToiToiTourTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

// ✅ Places
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient

// =======================
// === MODELS START ===
// =======================
// ----------------- PODACI ------------------

data class Stop(
    val id: Int,
    val title: String, // ulica + broj (ili cijela adresa)
    val cityLine: String, // "ZIP Grad" (ako postoji)
    val location: LatLng? = null,
    val source: String = "PDF" // "PDF" ili "GOOGLE"
)

val demoStops: List<Stop> = emptyList()

// =======================
// === MODELS END ===
// =======================

// =======================
// === ACTIVITY START ===
// =======================
// ----------------- ACTIVITY ------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToiToiTourTheme {
                ToiToiTourApp()
            }
        }
    }
}

// =======================
// === ACTIVITY END ===
// =======================

// =======================
// === APP_ROOT START ===
// =======================
// ----------------- APP ROOT ------------------
@Composable
fun ToiToiTourApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stops by remember { mutableStateOf(demoStops) }

    // ✅ Snackbar (poruke vozaču)
    val snackbarHostState = remember { SnackbarHostState() }

    // ✅ Places init + client
    var placesClient by remember { mutableStateOf<PlacesClient?>(null) }
    LaunchedEffect(Unit) {
        try {
            if (!Places.isInitialized()) {
                Places.initialize(context.applicationContext, context.getString(R.string.google_maps_key))
            }
            placesClient = Places.createClient(context.applicationContext)
        } catch (e: Exception) {
            Log.e("ToiToiTour", "Places init error: ${e.message}", e)
            snackbarHostState.showSnackbar("Google Places nije inicijaliziran. Provjeri API key i Places API.")
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bytes = readPdfBytes(it, context)
            if (bytes != null) {
                uploadPdfToServer(
                    context = context,
                    pdfBytes = bytes,
                    onStopsLoaded = { loaded -> stops = loaded },
                    scope = scope
                )
            }
        }
    }

    ToiToiTourScreen(
        stops = stops,
        onOpenPdf = { launcher.launch("application/pdf") },
        snackbarHostState = snackbarHostState,
        placesClient = placesClient,
        onAddStop = { newStop ->
            stops = stops + newStop.copy(id = (stops.maxOfOrNull { it.id } ?: 0) + 1)
        },
        scope = scope
    )
}

// =======================
// === APP_ROOT END ===
// =======================

// =======================
// === JSON_HELPERS START ===
// =======================
// ----------------- JSON HELPERS ------------------
fun extractStopsFromJson(json: String): List<Stop> {
    val s = json.trim()
    if (s.isBlank()) return emptyList()

    return try {
        if (s.startsWith("[")) {
            val arr = JSONArray(s)
            parseStopsFromArray(arr)
        } else {
            val obj = JSONObject(s)
            val possibleKeys = listOf("items", "addresses", "stops", "data", "result")
            for (k in possibleKeys) {
                if (obj.has(k) && obj.get(k) is JSONArray) {
                    return parseStopsFromArray(obj.getJSONArray(k))
                }
            }
            emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseStopsFromArray(arr: JSONArray): List<Stop> {
    val out = ArrayList<Stop>()

    for (i in 0 until arr.length()) {
        val el = arr.get(i)

        when (el) {
            is JSONObject -> {
                val street = el.optString("street", "").trim()
                val zip = el.optString("zip", "").trim()
                val city = el.optString("city", "").trim()

                val addressFallback =
                    el.optString("address", "").trim()
                        .ifBlank { el.optString("full", "").trim() }
                        .ifBlank { el.optString("text", "").trim() }
                        .ifBlank { el.optString("line", "").trim() }
                        .ifBlank { el.optString("value", "").trim() }

                if (street.isNotBlank() && zip.isNotBlank() && city.isNotBlank()) {
                    out.add(
                        Stop(
                            id = out.size + 1,
                            title = street,
                            cityLine = "$zip $city",
                            location = null,
                            source = "PDF"
                        )
                    )
                } else if (addressFallback.isNotBlank()) {
                    out.add(
                        Stop(
                            id = out.size + 1,
                            title = addressFallback,
                            cityLine = "",
                            location = null,
                            source = "PDF"
                        )
                    )
                }
            }

            is String -> {
                val addr = el.trim()
                if (addr.isNotBlank()) {
                    out.add(
                        Stop(
                            id = out.size + 1,
                            title = addr,
                            cityLine = "",
                            location = null,
                            source = "PDF"
                        )
                    )
                }
            }
        }
    }

    return out
}

// =======================
// === JSON_HELPERS END ===
// =======================

// =======================
// === GEOCODE START ===
// =======================
// ----------------- GEOCODE (fallback) ------------------
suspend fun geocodeStops(
    context: android.content.Context,
    stops: List<Stop>
): List<Stop> =
    withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.getDefault())

        stops.map { stop ->
            if (stop.location != null) return@map stop

            try {
                val query = buildString {
                    append(stop.title)
                    if (stop.cityLine.isNotBlank()) {
                        append(", ")
                        append(stop.cityLine)
                    }
                    append(", Germany")
                }
                val results = geocoder.getFromLocationName(query, 1)
                val r = results?.firstOrNull()
                if (r != null) {
                    stop.copy(location = LatLng(r.latitude, r.longitude))
                } else stop
            } catch (_: Exception) {
                stop
            }
        }
    }

// =======================
// === GEOCODE END ===
// =======================

// =======================
// === NETWORK_PDF_IO START ===
// =======================
// ----------------- PDF HELPERS ------------------
fun readPdfBytes(uri: Uri, context: android.content.Context): ByteArray? =
    try {
        Log.d("ToiToiTour", "PDF URI: $uri")
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        Log.d("ToiToiTour", "PDF bytes read: ${bytes?.size ?: -1}")
        bytes
    } catch (e: Exception) {
        Log.e("ToiToiTour", "readPdfBytes ERROR: ${e.message}", e)
        null
    }

fun uploadPdfToServer(
    context: android.content.Context,
    pdfBytes: ByteArray,
    onStopsLoaded: (List<Stop>) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    val tempFile = File.createTempFile("upload", ".pdf", context.cacheDir)
    tempFile.writeBytes(pdfBytes)

    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            "file",
            "upload.pdf",
            tempFile.asRequestBody("application/pdf".toMediaType())
        )
        .build()

    val request = Request.Builder()
        .url("http://192.168.178.161:8080/extract") // ne brisi ip pc
        .post(requestBody)
        .build()

    Log.d("ToiToiTour", "UPLOAD -> ${request.url}")

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: java.io.IOException) {
            tempFile.delete()
            Log.e("ToiToiTour", "UPLOAD FAILED: ${e.message}", e)
        }

        override fun onResponse(call: Call, response: Response) {
            tempFile.delete()

            val body = response.body?.string().orEmpty()
            Log.d("ToiToiTour", "UPLOAD RESPONSE: code=${response.code} chars=${body.length}")

            if (!response.isSuccessful) {
                Log.e("ToiToiTour", "UPLOAD NOT OK: code=${response.code} body=${body.take(200)}")
                return
            }
            if (body.isBlank()) {
                Log.e("ToiToiTour", "UPLOAD EMPTY BODY")
                return
            }

            val baseStops = extractStopsFromJson(body)

            Handler(Looper.getMainLooper()).post {
                onStopsLoaded(baseStops)
            }

            scope.launch {
                val withCoords = geocodeStops(context, baseStops)
                onStopsLoaded(withCoords)
            }
        }
    })
}

// =======================
// === NETWORK_PDF_IO END ===
// =======================

// =======================
// === PLACES_HELPERS START ===
// =======================
// ----------------- PLACES HELPERS ------------------
private fun hasHouseNumber(address: String): Boolean {
    return Regex("\\b\\d+\\b").containsMatchIn(address)
}

private suspend fun findPredictions(
    client: PlacesClient,
    query: String
): List<AutocompletePrediction> = suspendCancellableCoroutine { cont ->
    val req = FindAutocompletePredictionsRequest.builder()
        .setQuery(query)
        .build()

    client.findAutocompletePredictions(req)
        .addOnSuccessListener { resp -> cont.resume(resp.autocompletePredictions) }
        .addOnFailureListener { e -> cont.resumeWithException(e) }
}

private suspend fun fetchPlace(
    client: PlacesClient,
    placeId: String
): Place = suspendCancellableCoroutine { cont ->
    val fields = listOf(Place.Field.LAT_LNG, Place.Field.ADDRESS, Place.Field.NAME)
    val req = FetchPlaceRequest.builder(placeId, fields).build()

    client.fetchPlace(req)
        .addOnSuccessListener { resp -> cont.resume(resp.place) }
        .addOnFailureListener { e -> cont.resumeWithException(e) }
}

// =======================
// === PLACES_HELPERS END ===
// =======================

// =======================
// === NAV_HELPER START ===
// =======================
// ----------------- NAV HELPER ------------------
private fun openGoogleNavigation(context: android.content.Context, stop: Stop) {
    val q = buildString {
        append(stop.title)
        if (stop.cityLine.isNotBlank()) {
            append(", ")
            append(stop.cityLine)
        }
    }

    val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(q))
    val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    context.startActivity(mapIntent)
}

// =======================
// === NAV_HELPER END ===
// =======================

// =======================
// === UI START ===
// =======================
// ----------------- UI ------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToiToiTourScreen(
    stops: List<Stop>,
    onOpenPdf: () -> Unit,
    snackbarHostState: SnackbarHostState,
    placesClient: PlacesClient?,
    onAddStop: (Stop) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current

    var selectedStop by remember { mutableStateOf<Stop?>(null) }

    // ✅ double click marker logika
    var lastMarkerId by remember { mutableStateOf<Int?>(null) }
    var lastMarkerClickMs by remember { mutableStateOf(0L) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(47.7, 11.7), 11f)
    }

    // ✅ 3 stanja: Hidden -> PartiallyExpanded (peek) -> Expanded
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = false // ✅ omogući "skroz zatvoreno"
    )
    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )

    var searchText by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    // =======================
    // === BOTTOM_SHEET_SCAFFOLD START ===
    // =======================
    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = 72.dp,
        sheetDragHandle = { BottomSheetDefaults.DragHandle() },
        snackbarHost = { SnackbarHost(snackbarHostState) },

        // =======================
        // === SHEET_CONTENT START ===
        // =======================
        sheetContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // =======================
                    // === SEARCH_BLOCK START ===
                    // =======================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { v ->
                                searchText = v
                                if (v.trim().length < 3) {
                                    predictions = emptyList()
                                    return@OutlinedTextField
                                }

                                val c = placesClient ?: return@OutlinedTextField

                                scope.launch {
                                    isSearching = true
                                    try {
                                        predictions = findPredictions(c, v.trim())
                                    } catch (e: Exception) {
                                        Log.e("ToiToiTour", "Places predictions error: ${e.message}", e)
                                        snackbarHostState.showSnackbar("Google search error: ${e.message ?: "unknown"}")
                                    } finally {
                                        isSearching = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Traži adresu (Google)") },
                            singleLine = true
                        )

                        if (isSearching) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        if (predictions.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    predictions.take(6).forEach { p ->
                                        val primary = p.getPrimaryText(null).toString()
                                        val secondary = p.getSecondaryText(null).toString()

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val c = placesClient ?: return@clickable
                                                    scope.launch {
                                                        try {
                                                            val place = fetchPlace(c, p.placeId)
                                                            val latLng = place.latLng
                                                            val addr = (place.address ?: primary).trim()

                                                            if (latLng == null || addr.isBlank()) {
                                                                snackbarHostState.showSnackbar("Adresa nije pronađena (Google). Označi ručno.")
                                                                return@launch
                                                            }

                                                            if (!hasHouseNumber(addr)) {
                                                                snackbarHostState.showSnackbar("Nema kućnog broja: koristi se lokacija ulice. Provjeri ručno.")
                                                            }

                                                            onAddStop(
                                                                Stop(
                                                                    id = 0,
                                                                    title = addr,
                                                                    cityLine = "",
                                                                    location = LatLng(latLng.latitude, latLng.longitude),
                                                                    source = "GOOGLE"
                                                                )
                                                            )

                                                            searchText = ""
                                                            predictions = emptyList()

                                                            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                                                LatLng(latLng.latitude, latLng.longitude),
                                                                14f
                                                            )
                                                        } catch (e: Exception) {
                                                            Log.e("ToiToiTour", "FetchPlace error: ${e.message}", e)
                                                            snackbarHostState.showSnackbar("Ne mogu dohvatiti točku: ${e.message ?: "unknown"}")
                                                        }
                                                    }
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Text(primary, fontWeight = FontWeight.Bold)
                                            if (secondary.isNotBlank()) Text(secondary, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Divider()
                                    }
                                }
                            }
                        }
                    }
                    // =======================
                    // === SEARCH_BLOCK END ===
                    // =======================

                    // =======================
                    // === SHEET_LIST_BLOCK START ===
                    // =======================
                    StopList(
                        stops = stops,
                        onStopNavigate = { s -> openGoogleNavigation(context, s) }
                    )
                    // =======================
                    // === SHEET_LIST_BLOCK END ===
                    // =======================
                }
            }
        }
        // =======================
        // === SHEET_CONTENT END ===
        // =======================
    ) { padding ->

        // =======================
        // === MAP_CONTENT START ===
        // =======================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Button(
                onClick = onOpenPdf,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text("PDF öffnen")
            }

            SelectedStopBar(
                stop = selectedStop,
                onNavigate = { s -> openGoogleNavigation(context, s) },
                onClear = { selectedStop = null }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ToiToiMap(
                    stops = stops,
                    cameraPositionState = cameraPositionState,
                    onMarkerClick = { stop, markerState ->
                        val now = System.currentTimeMillis()
                        val isDouble = (lastMarkerId == stop.id && (now - lastMarkerClickMs) < 650)

                        lastMarkerId = stop.id
                        lastMarkerClickMs = now

                        selectedStop = stop
                        markerState.showInfoWindow()

                        if (isDouble) {
                            scope.launch { sheetState.bottomSheetState.expand() }
                        }
                    }
                )
            }
        }
        // =======================
        // === MAP_CONTENT END ===
        // =======================
    }

// ✅ OVO JE BITNO: zatvori ToiToiTourScreen prije komponenti!
}

// =======================
// === COMPONENT: SelectedStopBar START ===
// =======================
@Composable
fun SelectedStopBar(
    stop: Stop?,
    onNavigate: (Stop) -> Unit,
    onClear: () -> Unit
) {
    if (stop == null) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stop.title, fontWeight = FontWeight.Bold)
                if (stop.cityLine.isNotBlank()) {
                    Text(stop.cityLine)
                }
            }

            Spacer(Modifier.width(8.dp))

            Button(onClick = { onNavigate(stop) }) {
                Text("Navigiraj")
            }

            Spacer(Modifier.width(8.dp))

            TextButton(onClick = onClear) {
                Text("X")
            }
        }
    }
}

// =======================
// === COMPONENT: SelectedStopBar END ===
// =======================

// =======================
// === COMPONENT: ToiToiMap START ===
// =======================
@Composable
fun ToiToiMap(
    stops: List<Stop>,
    cameraPositionState: CameraPositionState,
    onMarkerClick: (Stop, MarkerState) -> Unit
) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,      // ❌ makne +/-
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false         // ❌ makne one 2 ikone (directions + maps)
        )
    ) {
        stops.forEach { stop ->
            val loc = stop.location ?: return@forEach
            val markerState = remember(stop.id, loc) { MarkerState(position = loc) }

            Marker(
                state = markerState,
                title = stop.title,
                snippet = stop.cityLine,
                onClick = {
                    onMarkerClick(stop, markerState)
                    true // mi kontroliramo klik (da ne otvara default gluposti)
                }
            )
        }
    }
}
// =======================
// === COMPONENT: ToiToiMap END ===
// =======================

// =======================
// === COMPONENT: StopList START ===idem 
// =======================
@Composable
fun StopList(
    stops: List<Stop>,
    onStopNavigate: (Stop) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 450.dp)
            .padding(16.dp)
    ) {
        itemsIndexed(stops) { index, stop ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStopNavigate(stop) } // ✅ klik na listu = odmah navigacija
                    .padding(8.dp)
            ) {
                Text("${index + 1}. ${stop.title}", fontWeight = FontWeight.Bold)
                if (stop.cityLine.isNotBlank()) Text(stop.cityLine)
                Text(stop.source, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
// =======================
// === COMPONENT: StopList END ===
// =======================