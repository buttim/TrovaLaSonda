package eu.ydiaeresis.trovalasonda

import android.net.Uri
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

sealed class DownloadStatus {
    data object Success : DownloadStatus()
    data object NoContentLength : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
    data class Progress(val progress: Int): DownloadStatus()
}

@Serializable
data class VersionDB(val db:Map<String,VersionInfo>)
@Serializable
data class VersionInfo(val version:String, val info:String?=null, val file:String?=null)

class FirmwareUpdater {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun getVersion():VersionDB? {
        val uri=Uri.parse(BASE_URI+JSON)
        Log.i(FullscreenActivity.TAG,uri.toString())
        try {
            HttpClient(CIO).use {
                val response:HttpResponse=it.get(uri.toString())
                return json.decodeFromString(VersionDB.serializer(),response.bodyAsText())
            }
        }
        catch (ex:Exception) {
            Log.e(FullscreenActivity.TAG,"Eccezione in getVersion: $ex")
            return null
        }
    }

    fun getUpdate(name:String,file:File,chunkSize:Int):Flow<DownloadStatus> {
        return flow {
            val uri=Uri.parse(BASE_URI+name)
            Log.i(FullscreenActivity.TAG,"Downloading firmware from $uri")
            try {
                HttpClient(OkHttp).use {it ->
                    var bytesRead=0
                    val response:HttpResponse=it.get(uri.toString())
                    val length=response.contentLength() ?: 0
                    if (length==0L) emit(DownloadStatus.NoContentLength)
                    file.outputStream().use {
                        do {
                            val buff=response.readBytes(chunkSize.coerceAtMost((length-bytesRead).toInt()))
                            bytesRead+=buff.size
                            it.write(buff)
                            if (length>0) emit(DownloadStatus.Progress((100*bytesRead/length).toInt()))
                        } while (bytesRead<length)
                    }
                    emit(DownloadStatus.Success)
                }
            }
            catch (ex:java.lang.Exception) {
                emit(DownloadStatus.Error(ex.message?:"Unknown error"))
            }
        }
    }

    fun update(receiver:Receiver,file:File):Flow<DownloadStatus> {
        //TODO: timeout!
        return flow {
            val chunkSize=receiver.getOtaChunkSize()
            try {
                val length=file.length().toInt()
                receiver.startOTA(length)
                val buff=ByteArray(chunkSize)
                var bytesRead=0
                file.inputStream().use {
                    do {
                        val n=it.read(buff)
                        bytesRead+=n
                        receiver.otaChunk(buff.take(n).toByteArray())
                        emit(DownloadStatus.Progress(100*bytesRead/length))
                    } while (bytesRead<length)
                    emit(DownloadStatus.Success)
                }
            }
            catch (_:CancellationException) {
                receiver.stopOTA()
            }
            catch (ex:Exception) {
                Log.e(FullscreenActivity.TAG,"Eccezione in update: $ex")
                try {
                    emit(DownloadStatus.Error(ex.toString()))
                }
                catch (_:IllegalStateException) {}
            }
        }
    }

    companion object {
        const val BASE_URI="https://www.ydiaeresis.eu/public/"
        const val JSON="TrovaLaSondaFw.json"
    }
}