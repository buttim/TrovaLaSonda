package eu.ydiaeresis.trovalasonda

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import org.json.JSONException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Radiosonde(
    val number: String,
    @JsonNames("alternative_number") val alternativeNumber: String? = null,
    @JsonNames("start_place") val startPlace: String
)

@OptIn(ExperimentalSerializationApi::class, kotlin.time.ExperimentalTime::class)
@Serializable
data class PlannedTaking(
    val radiosonde: Radiosonde,
    val sender: String,
    val message: String,
    //@Serializable(with = RadiosondeInstantSerializer::class)
    @JsonNames("valid_from") val validFrom: Instant,
    //@Serializable(with = RadiosondeInstantSerializer::class)
    @JsonNames("valid_to") val validTo: Instant
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class PlannedTakingsAnswer(val status: String, val results: Array<PlannedTaking>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlannedTakingsAnswer

        if (status != other.status) return false
        if (!results.contentEquals(other.results)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + results.contentHashCode()
        return result
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class RecoveredAnswer(val status: String, val results: Array<RecoveredData>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecoveredAnswer

        if (status != other.status) return false
        if (!results.contentEquals(other.results)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + results.contentHashCode()
        return result
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class RecoveredData(
    @JsonNames("log_info") val logInfo: Array<LogInfo>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecoveredData

        if (!logInfo.contentEquals(other.logInfo)) return false

        return true
    }

    override fun hashCode(): Int {
        return logInfo.contentHashCode()
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Coordinates(val latitude:Double,val longitude:Double) {
}

@OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
@Serializable
data class LogInfo(
    val status: String? = null,
    val finder: String? = null,
    @JsonNames("added_by") val addedBy: String? = null,
    @JsonNames("log_added") val logAdded: Instant? = null,
    val comment: String? = null,
    @JsonNames("found_coordinates") val foundCoordinates: Coordinates? = null
) {
}

class Radiosondy {
    companion object {
        private const val URI = "https://radiosondy.info/api/v1/"
        const val USER_AGENT = BuildConfig.APPLICATION_ID + " " + BuildConfig.VERSION_NAME

        private val json1 = Json {
            ignoreUnknownKeys = true
        }

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

        suspend fun getRecovered(serial: String):Array<LogInfo>? {
            //TODO: convertire serial to APRS id
            val res = callAPI(
                "radiosondes",
                RecoveredAnswer.serializer(),
                mapOf("type" to "sonde", "sondenumber" to serial))
            return res?.results?.firstOrNull()?.logInfo
        }

        suspend fun getPlannedTakings(sondeId: String): PlannedTaking? {
            try {
                HttpClient(CIO) {
                    install(UserAgent) {
                        agent = USER_AGENT
                    }
                }.use {
                    val response =
                        it.get(URI + "planned-takings")
                    /*Log.d(
                        FullscreenActivity.TAG,
                        "planned-takings: (${response.status}) ${response.bodyAsText()}"
                    )*/
                    return when (response.status) {
                        HttpStatusCode.OK -> {
                            try {
                                val json1 = Json {
                                    ignoreUnknownKeys = true
                                }
                                val answer =
                                    json1.decodeFromString<PlannedTakingsAnswer>(response.bodyAsText())
                                val result = answer.results.firstOrNull { result ->
                                    sondeId == result.radiosonde.number || sondeId == result.radiosonde.alternativeNumber
                                }
                                Log.d(
                                    FullscreenActivity.TAG,
                                    "planned-takings: sender=${result?.sender}, message=${result?.message}"
                                )
                                result
                            } catch (ex: JSONException) {
                                Log.w(
                                    FullscreenActivity.TAG,
                                    "Radiosondy JSON error ${ex.message}"
                                )
                                null
                            }
                        }

                        else -> {
                            Log.w(
                                FullscreenActivity.TAG,
                                "Radiosondy error status ${response.status}"
                            )
                            null
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e(FullscreenActivity.TAG, "Exception in getPlannedTakings: $ex")
                return null
            }
        }
    }
}
