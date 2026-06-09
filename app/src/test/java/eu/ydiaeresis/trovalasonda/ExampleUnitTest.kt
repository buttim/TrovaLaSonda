package eu.ydiaeresis.trovalasonda

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class ExampleUnitTest {
    //@Test
    fun testRadiosondyPlannedTakings() = runBlocking {
        println("--- OUTPUT START ------------------------------------------------")
        val res=Radiosondy.getPlannedTakings("X0524047")
        println("sender=\"${res?.sender}\", message=\"${res?.message}\", validTo=\"${res?.validTo}\"")
        println("--- OUTPUT END ------------------------------------------------")
    }
    //@Test
    fun testSondehubPlannedTakings() = runBlocking {
        println("--- OUTPUT START ------------------------------------------------")
        val res=Sondehub.getRecovered("X5030266")
        println("${res?.serial}, ${res?.description}, ${res?.position}")
        println("--- OUTPUT END ------------------------------------------------")
    }
    //@Test fun testSondehubSites() = runBlocking { println("--- OUTPUT START ------------------------------------------------") val res=Sondehub.sites() println(res) println("--- OUTPUT END ------------------------------------------------") }
    //@Test
    fun testSondehubStationFromSerial() = runBlocking {
        println("--- OUTPUT START ------------------------------------------------")
        val res=Sondehub.stationFromSerial("RS41","X3832122")
        println(res)
        println("--- OUTPUT END ------------------------------------------------")
    }
    //@Test
    fun testSondehubGetChaseCar() = runBlocking {
        println("--- OUTPUT START ------------------------------------------------")
        val res=Sondehub.getChaseCar(45.5,10.2,100000.0)
        println(res)
        println("--- OUTPUT END ------------------------------------------------")
    }
    //@Test
    fun testSondehubGetNearbySonde() = runBlocking {
        println("--- OUTPUT START ------------------------------------------------")
        val res=Sondehub.getNearbySonde(45.0,6.0)
        println(res)
        println("--- OUTPUT END ------------------------------------------------")
    }
    //@Test
    fun testSondehubGetTrack2() = runBlocking {
        println("--- OUTPUT START ------------------------------------------------")
        val res=Sondehub.getTrack("RS41","X1413706",(Clock.System.now()-5.minutes).toJavaInstant())
        println(res)
        println("--- OUTPUT END ------------------------------------------------")
    }
    //@Test
    fun testRadiosondyGetRecovered() = runBlocking {
        println("--- OUTPUT START ------------------------------------------------")
        val res=Radiosondy.getRecovered("X1423154")
        res?.forEach {
            println("status: ${it.status}, finder:${it.finder}, added by:${it.addedBy}, log added:${it.logAdded}, comment:${it.comment},[${it.foundCoordinates?.latitude}, ${it.foundCoordinates?.longitude}]")
        }
        println("--- OUTPUT END ------------------------------------------------")
    }
}
