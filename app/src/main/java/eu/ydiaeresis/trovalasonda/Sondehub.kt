package eu.ydiaeresis.trovalasonda

import android.content.Context
import android.net.Uri
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
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import org.json.JSONException
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.time.Instant

@Serializable
data class Site @OptIn(ExperimentalSerializationApi::class) constructor(
    val station:String,
    @JsonNames("station_name") val stationName:String,
    @JsonNames("burst_altitude") val burstAltitude:Float?=null,
    @JsonNames("ascent_rate") val ascentRate:Float?=null,
    @JsonNames("descent_rate") val descentRate:Float?=null
)

suspend fun sites():Map<String,Site>? {
    try{
        HttpClient(CIO){
            install(ContentEncoding) {
                gzip()
            }
        }.use {
            val response=it.get { url(Sondehub.URI+"sites")}
            return when (response.status) {
                HttpStatusCode.OK -> Json{
                    ignoreUnknownKeys = true
                }.decodeFromString(MapSerializer(String.serializer(),Site.serializer()),response.bodyAsText())
                else -> null
            }
        }
    }
    catch (ex:Exception) {
        Log.e(FullscreenActivity.TAG,"Eccezione in sites(): $ex")
        return null
    }
}

suspend fun stationFromSerial(sondeType:String,serial:String):String? {
    val id=Sondehub.getSondehubId(sondeType,serial)
    try {
        HttpClient(CIO) {
            install(ContentEncoding) {
                gzip()
            }
        }.use {
            val response=it.get {url(Sondehub.URI+"predictions/reverse?vehicles="+id)}
            //Log.i(FullscreenActivity.TAG,"RESPONSE: (${response.status}) ${response.body<ByteArray>()}")
            return when (response.status) {
                HttpStatusCode.OK -> {
                    val json=JSONObject(response.bodyAsText())
                    try {
                        json.getJSONObject(serial).getString("launch_site")
                    } catch (_:JSONException) {
                        null
                    }
                }

                else -> null
            }
        }
    }
    catch (ex:Exception) {
        Log.e(FullscreenActivity.TAG,"Eccezione in stationFromSerial: $ex")
        return null
    }
}

suspend fun recovered(
    context:Context,
    user:String,
    serial:String,
    lat:Double,
    lon:Double,
    alt:Double,
    description:String,
):String? {
    val data=buildJsonObject {
        put("serial",serial)
        put("recovered",true)
        put("recovered_by",user)
        put("description",description)
        put("lat",lat)
        put("lon",lon)
        put("alt",alt)
    }

    Log.i(FullscreenActivity.TAG,"JSON: $data")

    try {
        HttpClient(CIO).use {
            val response=it.put {
                setBody(data.toString())
                contentType(ContentType.Application.Json)
                url(Sondehub.URI+"recovered")
            }
            Log.i(FullscreenActivity.TAG,"RESPONSE: (${response.status}) ${response.bodyAsText()}")
            return when (response.status) {
                HttpStatusCode.OK -> null
                HttpStatusCode.BadRequest -> {
                    try {
                        val json=JSONObject(response.bodyAsText())
                        json.getString("message")
                    } catch (ex:Exception) {
                        context.getString(R.string.unknown_error,response.bodyAsText())
                    }
                }

                else -> context.getString(R.string.error_sending_report_status,response.status)
            }
        }
    } catch (ex:Exception) {
        Log.i(FullscreenActivity.TAG,ex.toString())
        return context.getString(R.string.failed_to_send_report,ex)
    }
}

class Sondehub(
    private val sondeType:String,
    private val sondeId:String,
    private val lastSeen:Instant?,
) {
    suspend fun getTrack():List<GeoPoint> {
        var duration="3h"

        if (lastSeen!=null) {
            val delta=Instant.now().epochSecond-lastSeen.epochSecond
            if (delta<60) duration="1m"
            else if (delta<60*30) duration="30m"
            else if (delta<60*60) duration="1h"
        }
        val uri=
            Uri.parse(URI+"sondes/telemetry").buildUpon().appendQueryParameter("duration",duration)
                .appendQueryParameter("serial",getSondehubId(sondeType,sondeId)).build()
        val points=mutableListOf<GeoPoint>()

        try {
            HttpClient(CIO).config {
                ContentEncoding {gzip()}
            }.use {
                val response:HttpResponse=it.get(uri.toString())
                //Log.d(FullscreenActivity.TAG, response.bodyAsText())
                val json=Json.parseToJsonElement(response.bodyAsText())
                val map=json.jsonObject.toMap()

                if (!map.values.isEmpty()) for (x in map.values.first().jsonObject.values) x.jsonObject.apply {
                    val t=
                        Instant.parse(get("time_received")?.jsonPrimitive?.content)//get("time_received").toString().trimStart('"').trimEnd('"'))
                    if (t>lastSeen) points.add(GeoPoint(get("lat")?.jsonPrimitive?.double!!,
                        get("lon")?.jsonPrimitive?.double!!,
                        get("alt")?.jsonPrimitive?.double!!))
                }
            }
        } catch (ex:Exception) {
            Log.i(FullscreenActivity.TAG,ex.toString())
        }
        return points
    }

    companion object {
        const val URI="https://api.v2.sondehub.org/"
        fun getSondehubId(sondeType:String,sondeId:String):String=when (sondeType) {
            "M10","M20" -> sondeId.substring(0,3)+"-"+sondeId.substring(3,
                4)+"-"+sondeId.substring(4)
            else -> sondeId
        }
    }
}