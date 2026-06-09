package eu.ydiaeresis.trovalasonda

//TODO: sostituire java.time.Instant con kotlin.time.Instant ?
import android.content.Context
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.compression.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import kotlinx.datetime.LocalDateTime
import io.ktor.client.plugins.UserAgent
import kotlinx.serialization.DeserializationStrategy
import java.time.Instant
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SondeData(
    val serial: String,
    @JsonNames("launch_site") val launchSite: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Site(
    val station: String,
    @JsonNames("station_name") val stationName: String,
    @JsonNames("burst_altitude") val burstAltitude: Float? = null,
    @JsonNames("ascent_rate") val ascentRate: Float? = null,
    @JsonNames("descent_rate") val descentRate: Float? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class ChasePosition(
    @JsonNames("software_version") val softwareVersion: String = "",
    @JsonNames("uploader_callsign") val uploaderCallsign: String,
    @JsonNames("uploader_position") val uploaderPosition: Array<Double>,
    @JsonNames("uploader_antenna") val uploaderAntenna: String = "",
    val mobile: Boolean = false,
    @JsonNames("user-agent") val userAgent: String = "",
    @JsonNames("uploader_alt") val uploaderAlt: Float = 0F,
    @JsonNames("uploader_position_elk") val uploaderPositionElk: String = "",
    val ts: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChasePosition

        if (mobile != other.mobile) return false
        if (uploaderAlt != other.uploaderAlt) return false
        if (softwareVersion != other.softwareVersion) return false
        if (uploaderCallsign != other.uploaderCallsign) return false
        if (!uploaderPosition.contentEquals(other.uploaderPosition)) return false
        if (uploaderAntenna != other.uploaderAntenna) return false
        if (userAgent != other.userAgent) return false
        if (uploaderPositionElk != other.uploaderPositionElk) return false
        if (ts != other.ts) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mobile.hashCode()
        result = 31 * result + uploaderAlt.hashCode()
        result = 31 * result + softwareVersion.hashCode()
        result = 31 * result + uploaderCallsign.hashCode()
        result = 31 * result + uploaderPosition.contentHashCode()
        result = 31 * result + uploaderAntenna.hashCode()
        result = 31 * result + userAgent.hashCode()
        result = 31 * result + uploaderPositionElk.hashCode()
        result = 31 * result + ts.hashCode()
        return result
    }
}


@OptIn(ExperimentalSerializationApi::class)
@Serializable
@Suppress("PropertyName")
data class Sonde(
    val serial: String,
    val type: String,
    val frequency: Float? = null,
    val tx_frequency: Float? = null,
    val lat: Double,
    val lon: Double,
    val alt: Double
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Frame(
    val frame:Int,
    val lat:Double,
    val lon:Double,
    val alt:Double
) {}

@OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
@Serializable
//@Suppress("PropertyName")
data class RecoveredSonde(
    val serial: String,
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val recovered: Boolean = false,
    var planned: Boolean = false,
    @JsonNames("recovered_by") var recoveredBy: String = "",
    var description: String = "",
    var datetime: LocalDateTime,
    var position: Array<Float>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecoveredSonde

        if (lat != other.lat) return false
        if (lon != other.lon) return false
        if (alt != other.alt) return false
        if (recovered != other.recovered) return false
        if (planned != other.planned) return false
        if (serial != other.serial) return false
        if (recoveredBy != other.recoveredBy) return false
        if (description != other.description) return false
        if (datetime != other.datetime) return false
        if (!position.contentEquals(other.position)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lat.hashCode()
        result = 31 * result + lon.hashCode()
        result = 31 * result + alt.hashCode()
        result = 31 * result + recovered.hashCode()
        result = 31 * result + planned.hashCode()
        result = 31 * result + serial.hashCode()
        result = 31 * result + recoveredBy.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + datetime.hashCode()
        result = 31 * result + position.contentHashCode()
        return result
    }
}

@OptIn(ExperimentalSerializationApi::class,ExperimentalTime::class)
abstract class Sondehub {
    companion object {
        const val URI = "https://api.v2.sondehub.org/"
        private const val USER_AGENT = BuildConfig.APPLICATION_ID + " " + BuildConfig.VERSION_NAME
        private val json1 = Json {
            ignoreUnknownKeys = true
        }

        suspend fun getTrack(sondeType: String,sondeId:String,lastSeen:Instant?,): List<GeoPoint> {
            val points = mutableListOf<GeoPoint>()
            val ls=lastSeen?.toKotlinInstant()
            var duration = "3h"
            if (lastSeen != null) {
                val delta = Instant.now().epochSecond - lastSeen.epochSecond
                if (delta < 60) duration = "1m"
                else if (delta < 60 * 30) duration = "30m"
                else if (delta < 60 * 60) duration = "1h"
            }
            val res=callAPI("sondes/telemetry",
                MapSerializer(String.serializer(),MapSerializer(kotlin.time.Instant.serializer(),Frame.serializer())),
                mapOf("duration" to duration,"serial" to getSondehubId(sondeType, sondeId)))
            //if (res==null || res.size==0) return points
            res?.get(sondeId)?.filter { ls==null || it.key>ls}?.forEach {
                points.add(GeoPoint(it.value.lat,it.value.lon,it.value.alt))
            }
            return points
        }

        /*suspend fun getTrack(sondeType: String,sondeId:String,lastSeen:Instant?,): List<GeoPoint> {
            var duration = "3h"

            if (lastSeen != null) {
                val delta = Instant.now().epochSecond - lastSeen.epochSecond
                if (delta < 60) duration = "1m"
                else if (delta < 60 * 30) duration = "30m"
                else if (delta < 60 * 60) duration = "1h"
            }
            val uri =
                (URI + "sondes/telemetry").toUri().buildUpon()
                    .appendQueryParameter("duration", duration)
                    .appendQueryParameter("serial", getSondehubId(sondeType, sondeId)).build()
            val points = mutableListOf<GeoPoint>()

            try {
                HttpClient(CIO) {
                    install(UserAgent) {
                        agent = USER_AGENT
                    }
                }.config {
                    ContentEncoding { gzip() }
                }.use {
                    val response: HttpResponse = it.get(uri.toString())
                    //Log.d(FullscreenActivity.TAG, response.bodyAsText())
                    val json = Json.parseToJsonElement(response.bodyAsText())
                    val map = json.jsonObject.toMap()

                    if (!map.values.isEmpty()) for (x in map.values.first().jsonObject.values) x.jsonObject.apply {
                        val t =
                            Instant.parse(get("time_received")?.jsonPrimitive?.content)//get("time_received").toString().trimStart('"').trimEnd('"'))
                        if (t > lastSeen) points.add(
                            GeoPoint(
                                get("lat")?.jsonPrimitive?.double!!,
                                get("lon")?.jsonPrimitive?.double!!,
                                get("alt")?.jsonPrimitive?.double!!
                            )
                        )
                    }
                }
            } catch (ex: Exception) {
                Log.i(FullscreenActivity.TAG, ex.toString())
            }
            return points
        }*/

        fun getSondehubId(sondeType: String, sondeId: String): String = when (sondeType) {
            "M10", "M20" -> sondeId.take(3) + "-" + sondeId[3] + "-" + sondeId.substring(4)
            else -> sondeId
        }

        suspend fun recovered(
            context: Context,
            user: String,
            serial: String,
            lat: Double,
            lon: Double,
            alt: Double,
            description: String,
        ): String? {
            val data = buildJsonObject {
                put("serial", serial)
                put("recovered", true)
                put("recovered_by", user)
                put("description", description)
                put("lat", lat)
                put("lon", lon)
                put("alt", alt)
            }

            Log.i(FullscreenActivity.TAG, "JSON: $data")

            try {
                HttpClient(CIO) {
                    install(UserAgent) {
                        agent = USER_AGENT
                    }
                }.use {
                    val response = it.put {
                        setBody(data.toString())
                        contentType(ContentType.Application.Json)
                        url(URI + "recovered")
                    }
                    Log.i(
                        FullscreenActivity.TAG,
                        "RESPONSE: (${response.status}) ${response.bodyAsText()}"
                    )
                    return when (response.status) {
                        HttpStatusCode.OK -> null
                        HttpStatusCode.BadRequest -> {
                            try {
                                val json = JSONObject(response.bodyAsText())
                                json.getString("message")
                            } catch (_: Exception) {
                                context.getString(R.string.unknown_error, response.bodyAsText())
                            }
                        }

                        else -> context.getString(
                            R.string.error_sending_report_status,
                            response.status
                        )
                    }
                }
            } catch (ex: Exception) {
                Log.i(FullscreenActivity.TAG, ex.toString())
                return context.getString(R.string.failed_to_send_report, ex)
            }
        }

        suspend fun stationFromSerial(sondeType: String, serial: String): String? {
            val id = getSondehubId(sondeType, serial)
            val res = callAPI(
                "predictions/reverse",
                MapSerializer(String.serializer(), SondeData.serializer()),
                mapOf("vehicles" to id)
            )
            return res?.get(serial)?.launchSite
        }
        /*suspend fun stationFromSerial(sondeType: String, serial: String): String? {
            val id = getSondehubId(sondeType, serial)
            try {
                HttpClient(CIO) {
                    install(ContentEncoding) {
                        gzip()
                    }
                    install(UserAgent) {
                        agent = USER_AGENT
                    }
                }.use {
                    val response = it.get { url(URI + "predictions/reverse?vehicles=" + id) }
                    //Log.i(FullscreenActivity.TAG,"RESPONSE: (${response.status}) ${response.body<ByteArray>()}")
                    return when (response.status) {
                        HttpStatusCode.OK -> {
                            val json = JSONObject(response.bodyAsText())
                            try {
                                json.getJSONObject(serial).getString("launch_site")
                            } catch (_: JSONException) {
                                null
                            }
                        }

                        else -> null
                    }
                }
            } catch (ex: Exception) {
                Log.e(FullscreenActivity.TAG, "Exception in stationFromSerial: $ex")
                return null
            }
        }*/

        suspend fun getChaseCar(lat: Double, lng: Double, dist: Double): String? {
            val chaseCars = callAPI(
                "listeners/telemetry",
                MapSerializer(
                    String.serializer(),
                    MapSerializer(String.serializer(), ChasePosition.serializer())
                ),
                mapOf("duration" to "3h")
            )
            if (chaseCars == null) return null
            var chaseCar: String? = null
            var minDistance: Double? = null
            val point = GeoPoint(lat, lng)
            chaseCars.forEach { (name, positions: Map<String, ChasePosition>) ->
                val lastPosition =
                    positions.filter { it.value.mobile }.maxByOrNull { it.key }
                if (lastPosition != null) {
                    val p = GeoPoint(
                        lastPosition.value.uploaderPosition[0],
                        lastPosition.value.uploaderPosition[1]
                    )
                    val d = point.distanceToAsDouble(p)
                    if (d < dist && (minDistance === null || d < minDistance)) {
                        minDistance = d
                        chaseCar = name
                    }
                }
            }
            Log.i(FullscreenActivity.TAG, "getChaseCar -> $chaseCar")
            return chaseCar
        }
        /*suspend fun getChaseCar(lat: Double, lng: Double, dist: Double): String? {
            try {
                HttpClient(CIO) {
                    install(ContentEncoding) {
                        gzip()
                    }
                    install(UserAgent) {
                        agent = USER_AGENT
                    }
                }.use { client ->
                    val response = client.get { url(URI + "listeners/telemetry?duration=3h") }
                    return if (response.status == HttpStatusCode.OK) {
                        val ser = MapSerializer(String.serializer(), ChasePosition.serializer())
                        val chaseCars = json1.decodeFromString(
                            MapSerializer(String.serializer(), ser),
                            response.bodyAsText()
                        )
                        var chaseCar: String? = null
                        var minDistance: Double? = null
                        val point = GeoPoint(lat, lng)
                        chaseCars.forEach { (name, positions: Map<String, ChasePosition>) ->
                            val lastPosition =
                                positions.filter { it.value.mobile }.maxByOrNull { it.key }
                            if (lastPosition != null) {
                                val p = GeoPoint(
                                    lastPosition.value.uploaderPosition[0],
                                    lastPosition.value.uploaderPosition[1]
                                )
                                val d = point.distanceToAsDouble(p)
                                if (d < dist && (minDistance === null || d < minDistance)) {
                                    minDistance = d
                                    chaseCar = name
                                }
                            }
                        }
                        Log.i(FullscreenActivity.TAG, "getChaseCar -> $chaseCar")
                        chaseCar
                    } else null
                }
            } catch (ex: Exception) {
                Log.e(FullscreenActivity.TAG, "Exception in getChaseCars(): $ex")
                return null
            }
        }*/

        suspend fun sites(): Map<String, Site>? {
            return callAPI(
                "sites", MapSerializer(
                    String.serializer(),
                    Site.serializer()
                )
            )
        }
        /*suspend fun sites(): Map<String, Site>? {
            try {
                HttpClient(CIO) {
                    install(ContentEncoding) {
                        gzip()
                    }
                    install(UserAgent) {
                        agent = USER_AGENT
                    }
                }.use {
                    val response = it.get { url(URI + "sites") }
                    return when (response.status) {
                        HttpStatusCode.OK -> json1.decodeFromString(
                            MapSerializer(
                                String.serializer(),
                                Site.serializer()
                            ), response.bodyAsText()
                        )

                        else -> null
                    }
                }
            } catch (ex: Exception) {
                Log.e(FullscreenActivity.TAG, "Exception in sites(): $ex")
                return null
            }
        }*/

        suspend fun <T> callAPI(
            api: String,
            ser: DeserializationStrategy<T>,
            params: Map<String, Any>? = null
        ): T? {
            try {
                HttpClient(CIO) {
                    install(ContentEncoding) {
                        gzip()
                    }
                    install(UserAgent) {
                        agent = USER_AGENT
                    }
                }.use {
                    val response = it.get(URI + api) {
                        url {
                            params?.forEach { parameters.append(it.key, it.value.toString()) }
                        }
                    }
                    return when (response.status) {
                        HttpStatusCode.OK -> {
                            Log.d(FullscreenActivity.TAG, response.bodyAsText())
                            json1.decodeFromString(
                                ser, response.bodyAsText()
                            )
                        }

                        else -> null
                    }
                }
            } catch (ex: Exception) {
                Log.e(FullscreenActivity.TAG, "Exception in callAPI($api): $ex")
                return null
            }
        }

        suspend fun getRecovered(serial: String): RecoveredSonde? {
            val res = callAPI(
                "recovered",
                ListSerializer(RecoveredSonde.serializer()),
                mapOf("serial" to serial)
            )
            return res?.firstOrNull()
        }

        //find most likely sonde type and frequency from current position
        suspend fun getNearbySonde(lat: Double, lng: Double): Sonde? {
            val maxDistance = 200000
            val maxSeconds = 72000
            val sondes = callAPI(
                "sondes",
                MapSerializer(String.serializer(), Sonde.serializer()),
                mapOf(
                    "lat" to lat,
                    "lon" to lng,
                    "maxDistance" to maxDistance,
                    "last" to maxSeconds
                )
            )
            if (sondes == null) return null
            var minDistance: Double? = null
            val point = GeoPoint(lat, lng)
            var sonde: Sonde? = null
            sondes.forEach { entry ->
                val p = GeoPoint(entry.value.lat, entry.value.lon)
                val d = point.distanceToAsDouble(p)
                if (minDistance === null || d < minDistance) {
                    minDistance = d
                    sonde = entry.value
                }
            }
            if (sonde != null)
                Log.i(
                    FullscreenActivity.TAG,
                    "getNearbySonde -> ${sonde.serial} ${sonde.type} ${sonde.frequency} ${sonde.tx_frequency}"
                )
            else
                Log.d(FullscreenActivity.TAG, "getNearbySonde -> null")
            return sonde
        }

        /*suspend fun getNearbySonde(lat: Double, lng: Double): Sonde? {
            val maxDistance = 200000
            val maxSeconds = 72000
            val url = URI + "sondes?lat=$lat&lon=$lng&distance=$maxDistance&last=$maxSeconds"
            try {
                HttpClient(CIO) {
                    install(ContentEncoding) {
                        gzip()
                    }
                    install(UserAgent) {
                        agent = USER_AGENT
                    }
                }.use {
                    val response = it.get { url(url) }
                    return if (response.status == HttpStatusCode.OK) {
                        val point = GeoPoint(lat, lng)
                        val sondes: Map<String, Sonde> = json1.decodeFromString(
                            MapSerializer(String.serializer(), Sonde.serializer()),
                            response.bodyAsText()
                        )
                        var minDistance: Double? = null
                        var sonde: Sonde? = null
                        sondes.forEach { entry ->
                            val p = GeoPoint(entry.value.lat, entry.value.lon)
                            val d = point.distanceToAsDouble(p)
                            if (minDistance === null || d < minDistance) {
                                minDistance = d
                                sonde = entry.value
                            }
                        }
                        if (sonde != null)
                            Log.i(
                                FullscreenActivity.TAG,
                                "getNearbySonde -> ${sonde.serial} ${sonde.type} ${sonde.frequency} ${sonde.tx_frequency}"
                            )
                        else
                            Log.i(FullscreenActivity.TAG, "getNearbySonde -> null")
                        sonde
                    } else null
                }
            } catch (ex: Exception) {
                Log.e(FullscreenActivity.TAG, "Exception in getNearbySonde(): $ex")
                return null
            }
        }*/
    }
}