package eu.ydiaeresis.trovalasonda

import android.net.Uri
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.compression.*
import kotlinx.serialization.json.*
import org.osmdroid.util.GeoPoint
import java.time.Instant

class Sondehub(private val sondeType:String, private val sondeId:String, private val lastSeen: Instant?) {
    suspend fun getTrack():List<GeoPoint> {
        var duration="3h"

        if (lastSeen!=null) {
            val delta = Instant.now().epochSecond - lastSeen.epochSecond
            if (delta<60) duration="1m"
            else if (delta<60*30) duration="30m"
            else if (delta<60*60) duration="1h"
        }
        val uri=Uri.parse(URI).buildUpon()
            .appendQueryParameter("duration",duration)
            .appendQueryParameter("serial",getSondehubId(sondeType,sondeId))
            .build()
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
        }
        catch (ex:Exception) {
            Log.i("MAURI",ex.toString())
        }
        return points
    }

    companion object {
        private const val URI = "https://api.v2.sondehub.org/sondes/telemetry"
        private fun getSondehubId(sondeType:String,sondeId:String):String =
            when (sondeType) {
                "M10","M20" -> sondeId.substring(0,3)+"-"+sondeId.substring(3,4)+"-"+sondeId.substring(4)
                else -> sondeId
            }
    }
}