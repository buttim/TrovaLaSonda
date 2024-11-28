@file:Suppress("PropertyName")

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
private data class Metadata(val complete_datetime: String, val start_datetime: String)

@Serializable
data class TrajectoryPoint(
    val altitude: Double,
    val datetime: String,
    val latitude: Double,
    val longitude: Double)

@Serializable
private data class Request(
    val ascent_rate: Double,
    val burst_altitude: Double,
    val dataset: String,
    val descent_rate: Double,
    val launch_altitude: Double,
    val launch_datetime: String,
    val launch_latitude: Double,
    val launch_longitude: Double,
    val profile: String,
    val version: Int,
    val format:String="json")

@Serializable
data class Stage(val stage:String,val trajectory:Array<TrajectoryPoint>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Stage

        if (stage != other.stage) return false
        return trajectory.contentEquals(other.trajectory)
    }

    override fun hashCode(): Int {
        var result = stage.hashCode()
        result = 31 * result + trajectory.contentHashCode()
        return result
    }
}

@Serializable
private data class Response(val metadata:Metadata,val prediction:Array<Stage>,val request:Request) {
    //TODO: aggiungere campo warnings
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Response

        if (metadata != other.metadata) return false
        if (!prediction.contentEquals(other.prediction)) return false
        return request==other.request
    }

    override fun hashCode(): Int {
        var result = metadata.hashCode()
        result = 31 * result + prediction.contentHashCode()
        result = 31 * result + request.hashCode()
        return result
    }
}

private val json = Json { ignoreUnknownKeys = true }

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
            //Log.d(FullscreenActivity.TAG,response.bodyAsText())
            val result:Response = json.decodeFromString(Response.serializer(),response.bodyAsText())
            //Log.d(FullscreenActivity.TAG,result.request.toString())
            return result.prediction
        }
    }
    companion object {
        private const val URI="https://api.v2.sondehub.org/tawhiri"
    }
}