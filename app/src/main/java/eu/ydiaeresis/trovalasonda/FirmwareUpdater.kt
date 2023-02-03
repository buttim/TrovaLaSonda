package eu.ydiaeresis.trovalasonda

import android.net.Uri
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

sealed class DownloadStatus {
    object Success : DownloadStatus()
    object NoContentLength : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
    data class Progress(val progress: Int): DownloadStatus()
}

@Serializable
data class VersionInfo(val version:String, val info:String?=null)

class FirmwareUpdater {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun getVersion():VersionInfo? {
        val uri=Uri.parse(BASE_URI+JSON)
        Log.i("MAURI",uri.toString())
        try {
            HttpClient(CIO).use {
                val response:HttpResponse=it.get(uri.toString())
                return json.decodeFromString(VersionInfo.serializer(),response.bodyAsText())
            }
        }
        catch (ex:Exception) {
            Log.i("MAURI",ex.toString())
            return null
        }
    }

    suspend fun getUpdate(file:File):Flow<DownloadStatus> {
        return flow {
            val uri=Uri.parse(BASE_URI+FIRMWARE)
            try {
                HttpClient(CIO).use {it ->
                    var bytesRead=0
                    val response:HttpResponse=it.get(uri.toString())
                    val length=response.contentLength() ?: 0
                    if (length==0L) emit(DownloadStatus.NoContentLength)
                    file.outputStream().use {
                        do {
                            val buff=response.readBytes(CHUNK_SIZE.coerceAtMost((length-bytesRead).toInt()))
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

    suspend fun update(fullscreenActivity:FullscreenActivity,mutex:Mutex,file:File):Flow<DownloadStatus> {
        return flow {
            try {
                if (mutex.isLocked) mutex.unlock()
                val length=file.length().toInt()
                fullscreenActivity.sendOTA(length)
                val buff=ByteArray(CHUNK_SIZE)
                var bytesRead=0
                file.inputStream().use {
                    do {
                        val n=it.read(buff)
                        bytesRead+=n
                        mutex.lock()
                        fullscreenActivity.sendBytes(buff.take(n).toByteArray())
                        emit(DownloadStatus.Progress(100*bytesRead/length))
                    } while (bytesRead<length)
                    emit(DownloadStatus.Success)
                }
            } catch (ex:Exception) {
                Log.i("MAURI",ex.toString())
                emit(DownloadStatus.Error(ex.toString()))
            }
        }
    }

    companion object {
        const val CHUNK_SIZE=4096
        const val BASE_URI="https://www.ydiaeresis.eu/public/"//"http://buttim.asuscomm.com/"//
        const val JSON="rdzTrovaLaSonda.json"
        const val FIRMWARE="rdzTrovaLaSonda.ino.bin"
    }
}