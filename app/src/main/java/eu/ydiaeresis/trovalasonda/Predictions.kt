package eu.ydiaeresis.trovalasonda

import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

@Serializable
data class Metadata(val complete_datetime: String, val start_datetime: String)

@Serializable
data class TrajectoryPoint(
    val altitude: Double,
    val datetime: String,
    val latitude: Double,
    val longitude: Double)

@Serializable
data class Request(
    val ascent_rate: Double,
    val burst_altitude: Double,
    val dataset: String,
    val descent_rate: Double,
    val launch_altitude: Double,
    val launch_datetime: String,
    val launch_latitude: Double,
    val launch_longitude: Double,
    val profile: String,
    val version: Int)

@Serializable
data class Stage(val stage:String,val trajectory:Array<TrajectoryPoint>)

@Serializable
data class Response(val metadata:Metadata,val prediction:Array<Stage>,val request:Request)

class Tawhiri(private val time: Instant, private val lat:Double, private val lng:Double,
              private val alt:Double, private val burstAlt:Double=33000.0,
              private val ascRate:Double=5.0, private val descRate:Double=5.0) {
    private fun getUri() : Uri {
        return Uri.parse(URI).buildUpon()
            .appendQueryParameter("launch_latitude",lat.toString())
            .appendQueryParameter("launch_longitude",lng.toString())
            .appendQueryParameter("launch_altitude",alt.toString())
            .appendQueryParameter("ascent_rate",ascRate.toString())
            .appendQueryParameter("descent_rate",descRate.toString())
            .appendQueryParameter("burst_altitude",burstAlt.toString())
            .appendQueryParameter("launch_datetime",time.toString())
            .build()
    }

    suspend fun getPrediction():Array<Stage> {
        HttpClient(CIO).use {
            val response: HttpResponse = it.get(getUri().toString())
            val result:Response = Json.decodeFromString(Response.serializer(),response.bodyAsText())
            //Log.d("MAURI",result.request.toString())
            return result.prediction
        }
    }
    companion object {
        private const val URI="http://predict.cusf.co.uk/api/v1"
    }
}