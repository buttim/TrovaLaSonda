package eu.ydiaeresis.trovalasonda

import android.net.Uri
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.compression.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.*
import org.osmdroid.util.GeoPoint
import java.time.Instant


suspend fun recovered(user:String,serial:String,lat:Double,lon:Double,alt:Double,description:String) {
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
                url(/*"http://192.168.1.39:3000/"*/Sondehub.URI+"recovered")
            }
            Log.i(FullscreenActivity.TAG,"RESPONSE: ${response.bodyAsText()}")
        }
    } catch (ex:Exception) {
        Log.i(FullscreenActivity.TAG,ex.toString())
    }
}

class Sondehub(private val sondeType:String,
               private val sondeId:String,
               private val lastSeen:Instant?) {
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
                //Log.d("MAURI", response.bodyAsText())
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
            Log.i("MAURI",ex.toString())
        }
        return points
    }

    companion object {
        const val URI="https://api.v2.sondehub.org/"
        private fun getSondehubId(sondeType:String,sondeId:String):String=when (sondeType) {
            "M10","M20" -> sondeId.substring(0,3)+"-"+sondeId.substring(3,
                4)+"-"+sondeId.substring(4)

            else -> sondeId
        }
    }
}