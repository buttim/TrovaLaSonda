package eu.ydiaeresis.trovalasonda

import android.Manifest
import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.LevelListDrawable
import android.location.Location
import android.location.LocationListener
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
//import androidx.core.view.WindowInsetsCompat
//import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import com.harrysoft.androidbluetoothserial.BluetoothManager
import com.harrysoft.androidbluetoothserial.BluetoothSerialDevice
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface
import eu.ydiaeresis.trovalasonda.databinding.ActivityFullscreenBinding
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.*
import org.osmdroid.tileprovider.tilesource.MapBoxTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import org.osmdroid.views.overlay.Polygon as Polygon1


@SuppressLint("SetTextI18n")
class FullscreenActivity : AppCompatActivity(), LocationListener, MapEventsReceiver {
    private lateinit var binding:ActivityFullscreenBinding
    private var bluetoothManager = BluetoothManager.getInstance()
    private var btSerialDevice: Disposable?=null
    private var sondeTypes: Array<String>? = null
    private val path = Polyline()
    private val sondePath = Polyline()
    private val trajectory=Polyline()
    private var mkSonde: Marker? = null
    private var mkTarget: Marker?=null
    private var mkBurst:Marker?=null
    private var lastPrediction:Instant?=null
    private val sondeDirection = Polyline()
    private var locationOverlay: MyLocationNewOverlay? = null
    private var accuracyOverlay= Polygon1()//AccuracyOverlay()
    private var expandedMenu = false
    private var currentLocation: Location? = null
    private var satelliteView = false
    private var btMacAddress: String? = null
    private var deviceInterface: SimpleBluetoothDeviceInterface? = null
    private var mute = false
    private var muteChanged = true
    private var sondeId: String? = null
    private var sondeType = -1
    private var heightDelta=0.0
    private var freq = 0.0
    private var height=0.0
    private var bk: Instant? = null
    private var timeLastSeen: Instant? = null
    private var timeLastMessage: Instant? = null
    private val sondeLevelListDrawable = LevelListDrawable()
    private val handler = Handler(Looper.getMainLooper())
    private var burst=false

    private var receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action!!) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    try {
                        val deviceName = device?.name
                        Log.i(TAG, "BT device found: $deviceName")
                        if (deviceInterface == null && deviceName != null && deviceName.startsWith(
                                MYSONDYGOPREFIX
                            )
                        ) {
                            val btAdapter=(applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
                            btAdapter.cancelDiscovery()
                            connectDevice(device.address)
                        }
                    }
                    catch(ex:SecurityException) {
                        Toast.makeText(applicationContext, "Failed Bluetooth discovery", Toast.LENGTH_LONG).apply {
                            setGravity(Gravity.CENTER_VERTICAL, 0, 70)
                            show()
                        }
                    }
                }
            }
        }
    }
    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data: Intent? = result.data
            var reset = false
            val cmds = mutableListOf<Pair<String, Any>>()
            val resetCmds = listOf(
                SettingsActivity.LCD, SettingsActivity.OLED_SDA,
                SettingsActivity.OLED_SCL, SettingsActivity.OLED_RST, SettingsActivity.LED_POUT,
                SettingsActivity.BUZ_PIN, SettingsActivity.BATTERY
            )
            if (data != null && data.extras != null) {
                for (k in data.extras?.keySet()!!) {
                    if (resetCmds.indexOf(k) >= 0) reset = true
                    val t = data.extras?.get(k)
                    if (k == SettingsActivity.MYCALL)
                        cmds.add(Pair<String, Any>("myCall", t as String))
                    else
                        cmds.add(Pair<String, Any>(k, t as Int))
                }
                if (reset) {
                    val alertDialog = AlertDialog.Builder(this).create()
                    alertDialog.setTitle("Alert")
                    alertDialog.setMessage("New settings require a restart, do you want to apply them anyway?")
                    alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK") { dialog, _ ->
                        dialog.dismiss()
                        sendCommands(cmds)
                        showProgress(true)
                    }
                    alertDialog.show()
                } else
                    sendCommands(cmds)
            }
        }

    private fun showProgress(show: Boolean) {
        binding.progress.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onLocationChanged(location: Location) {
        val point = GeoPoint(location)
        if (currentLocation == null)
            binding.map.controller?.setCenter(point)
        currentLocation = location
        path.addPoint(point)
        path.actualPoints.apply { if (size > 400) removeAt(0) }
        updateSondeDirection()

        accuracyOverlay.points = Polygon1.pointsAsCircle(
            GeoPoint(location.latitude,location.longitude),location.accuracy.toDouble())
        binding.map.invalidate()
    }

    override fun onProviderDisabled(provider: String) = Unit
    override fun onProviderEnabled(provider: String) = Unit

    @Deprecated("deprecated")
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

    private fun connect() {
        val btAdapter=(applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
        with (btAdapter) {
            if (!isEnabled) {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

                registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode != Activity.RESULT_OK) finish()
                }.launch(intent)
            }

            try {
                if (!isDiscovering && !startDiscovery())
                    Log.e(TAG, "Failed to start BT discovery")
                Unit
            }
            catch (ex:SecurityException) {
                Toast.makeText(applicationContext, "Cannot start Bluetooth discovery", Toast.LENGTH_LONG).apply {
                    setGravity(Gravity.CENTER_VERTICAL, 0, 70)
                    show()
                }
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (deviceInterface == null) connect()
        }, 2000)
    }

    @SuppressLint("CheckResult")
    private fun connectDevice(mac: String) {
        btSerialDevice=bluetoothManager.openSerialDevice(mac)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::onConnected) { error ->
                Log.e(TAG, error.toString())
                bluetoothManager.closeDevice(mac)
                /*Handler(Looper.getMainLooper()).postDelayed({
                    connect()
                }, 1000)*/
            }
    }

    private fun onConnected(connectedDevice: BluetoothSerialDevice) {
        Log.i(TAG, "------------------------CONNECTED " + connectedDevice.mac)
        if (btMacAddress != null) {
            bluetoothManager.closeDevice(connectedDevice.mac)
            return
        }
        btMacAddress = connectedDevice.mac
        deviceInterface = connectedDevice.toSimpleDeviceInterface()
        deviceInterface?.setListeners(this::onMessageReceived, this::onMessageSent, this::onError)

        val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_person_yellow)
        locationOverlay?.setDirectionArrow(bmp, bmp)
        muteChanged = false

        try {
            val btAdapter=(applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
            val name = btAdapter.getRemoteDevice(btMacAddress).name
            Toast.makeText(applicationContext, "Connected to $name", Toast.LENGTH_LONG).apply {
                setGravity(Gravity.CENTER_VERTICAL, 0, 70)
                show()
            }
        }
        catch (ex:SecurityException) {
            Toast.makeText(applicationContext, "Cannot connect !", Toast.LENGTH_LONG).apply {
                setGravity(Gravity.CENTER_VERTICAL, 0, 70)
                show()
            }
        }
    }

    private fun onDisconnected() {
        Log.i(TAG, "onDisconnected")
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_person_red)
        locationOverlay?.setDirectionArrow(bmp, bmp)
        sondeLevelListDrawable.level = 0
        muteChanged = true
        bluetoothManager.closeDevice(btMacAddress)
        btMacAddress = null
        deviceInterface = null
        showProgress(false)
        Toast.makeText(applicationContext, "Connection to TTGO lost", Toast.LENGTH_LONG).apply {
            setGravity(Gravity.CENTER_VERTICAL, 0, 0)
            show()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            connect()
        }, 1000)
        binding.batteryMeter.chargeLevel=null
    }

    private fun onError(error: Throwable) {
        Log.d(TAG, error.toString())
        onDisconnected()
    }

    private fun onMessageSent(message: String) {
        Log.i(TAG, "SENT: $message")
    }

    private fun sendCommand(cmd: String) {
        try {
            deviceInterface?.sendMessage("o{$cmd}o\r\n")
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            btSerialDevice?.dispose()
            btSerialDevice=null
            //connect()
        }
    }

    @Suppress("SameParameterValue")
    private fun sendCommand(cmd: String, value: Any) {
        sendCommand("$cmd=$value")
    }

    private fun sendCommands(commands: List<Pair<String, Any>>) {
        if (commands.isEmpty()) return
        val sb = StringBuilder()
        commands.forEach { cmd ->
            if (sb.isNotEmpty()) sb.append("/")
            sb.append("${cmd.first}=${cmd.second}")
        }
        sendCommand(sb.toString())
    }

    private fun process(msg: String) {
        timeLastMessage = Instant.now()
        val campi = msg.split("/")
        if (campi[campi.size - 1] != "o") {
            Log.e(TAG, "manca terminatore messaggio")
            return
        }
        when (campi[0]) {
            "0" -> if (campi.size == 9)
                mySondyGOStatus(
                        campi[1], campi[2].toDouble(), campi[3].toDouble(), campi[4].toInt(),
                        campi[5].toInt(), campi[6] == "1", campi[7]
                )
            else {
                Log.e(
                        TAG,
                        "numero campi errato in messaggio tipo 0 (${campi.size} invece di 9)"
                )
                return
            }
            "1" -> if (campi.size == 20) {
                mySondyGOSondePos(
                    campi[1], campi[2].toDouble(), campi[3], campi[4].toDouble(),
                    campi[5].toDouble(), campi[6].toDouble(), campi[7].toDouble(),
                    campi[8].toDouble(), campi[9].toInt(), campi[10].toInt(), campi[11] == "1",
                    campi[12].toInt(), campi[13].toInt(), campi[14] == "1", campi[18]
                )
                freqOffsetReceiver?.freqOffset(campi[10].toInt())
            }
            else {
                Log.e(
                        TAG,
                        "numero campi errato in messaggio tipo 1 (${campi.size} invece di 20)"
                )
                return
            }
            "2" -> if (campi.size == 11) {
                mySondyGOSonde(
                        campi[1], campi[2].toDouble(), campi[3], campi[4].toDouble(),
                        campi[5].toInt(), campi[6].toInt(), campi[7].toInt(), campi[8] == "1",
                        campi[9]
                )
                freqOffsetReceiver?.freqOffset(campi[6].toInt())
            }
            else {
                Log.e(
                        TAG,
                        "numero campi errato in messaggio tipo 2 (${campi.size} invece di 11)"
                )
                return
            }
            "3" -> if (campi.size == 23)
                mySondyGOSettings(
                        campi[1], campi[2].toDouble(), campi[3].toInt(), campi[4].toInt(),
                        campi[5].toInt(), campi[6].toInt(), campi[7].toInt(), campi[8].toInt(),
                        campi[9].toInt(), campi[10].toInt(), campi[11].toInt(), campi[12],
                        campi[13].toInt(), campi[14].toInt(), campi[15].toInt(), campi[16].toInt(),
                        campi[17].toInt(), campi[18].toInt(), campi[19].toInt(), campi[20].toInt(),
                        campi[21]
                )
            else {
                Log.e(
                        TAG,
                        "numero campi errato in messaggio tipo 3 (${campi.size} invece di 23)"
                )
                return
            }
            else -> Log.e(TAG, "Tipo messaggio sconosciuto")
        }
    }

    private fun onMessageReceived(message: String) {
        Log.i(TAG, "RECEIVED: $message")
        try {
            process(message)
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            btSerialDevice?.dispose()
            btSerialDevice=null
        }
    }

    private fun updateMute(mute: Boolean) {
        this.mute = mute
        binding.buzzer.setImageResource(if (mute) R.drawable.ic_buzzer_off else R.drawable.ic_buzzer_on)
        binding.buzzer.imageAlpha = 255
        muteChanged = false
    }

    private fun updateSondeLocation(id: String, lat: Double, lon: Double, alt: Double) {
        binding.lat.text = String.format(Locale.US, " %.5f", lat)
        binding.lon.text = String.format(Locale.US, " %.5f", lon)
        if (sondeId != id) {
            sondeId = id
            binding.id.text = id
            bk = null

            mkSonde?.setVisible(true)
            sondePath.actualPoints.clear()
            mkBurst?.setVisible(false)
            trajectory.actualPoints.clear()
            trajectory.isVisible=false
            mkTarget?.setVisible(false)

            if (currentLocation != null) {
                binding.map.zoomToBoundingBox(
                        BoundingBox.fromGeoPointsSafe(
                                mutableListOf(
                                        GeoPoint(
                                                lat,
                                                lon
                                        ), GeoPoint(currentLocation)
                                )
                        ).increaseByScale(1.9F),
                    false, 50
                )
                binding.map.invalidate()
            } else
                binding.map.controller?.setCenter(mkSonde?.position)

            playSound()
        }

        mkSonde?.position = GeoPoint(lat, lon)
        sondePath.addPoint(mkSonde?.position)
        sondeLevelListDrawable.level = 1

        if (currentLocation != null) {
            val d = GeoPoint(currentLocation).distanceToAsDouble(mkSonde?.position)
            if (d > 10000F) {
                binding.unit.text = "km"
                binding.distance.text = String.format("%.1f", d / 1000)
            } else {
                binding.unit.text = "m"
                binding.distance.text = String.format("%.1f", d)
            }

            updateSondeDirection()
        }
        timeLastSeen = Instant.now()
        if (lastPrediction==null || Instant.now().epochSecond-lastPrediction!!.epochSecond>60) {
            lastPrediction=Instant.now()
            predict(lat,lon,alt)
        }
    }

    private fun updateSondeDirection() {
        if (currentLocation == null || sondeId == null) return
        sondeDirection.apply {
            actualPoints.clear()
            addPoint(GeoPoint(currentLocation))
            addPoint(mkSonde?.position)
            isVisible = true
        }
    }

    private fun updateTypeAndFreq(type: String, freq: Double) {
        if (this.freq!=freq || sondeType < 1 || type != sondeTypes!![sondeType - 1]) {
            binding.type.text = "$type ${freq}MHz"
            sondeType = sondeTypes?.indexOf(type)!! + 1
        }
        this.freq = freq
    }

    private fun updateBk(bk: Int) {
        binding.bk.apply {
            visibility = View.VISIBLE
            text = String.format("BK %d:%02d:%02d", bk / 3600, (bk / 60) % 60, bk % 60)
        }
        this.bk = Instant.now().plusSeconds(bk.toLong())
    }

    private fun updateRSSI(rssi: Double) {
        binding.dbm.text = "-${rssi}dBm"
        binding.rssi.progress = (binding.rssi.max-rssi).toInt()
    }

    private fun updateBattery(v: Int) {
        binding.batteryMeter.chargeLevel=v
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mySondyGOSondePos(
            type: String, freq: Double, name: String, lat: Double, lon: Double,
            height: Double, vel: Double, sign: Double, bat: Int, afc: Int, bk: Boolean,
            bktime: Int, batv: Int, mute: Boolean, ver: String
    ) {
        updateMute(mute)
        updateTypeAndFreq(type, freq)
        if (lat != 0.0 || lon != 0.0)
            updateSondeLocation(name, lat, lon, height)

        if (height!=0.0 || vel!=0.0) {
            binding.height.text = "H: ${height}m"
            binding.direction.text = if (abs(this.height - height) < 2) "=" else if (this.height < height) "▲" else "▼"
            val newHeightDelta = height - this.height
            if (!burst && heightDelta > 0 && newHeightDelta < 0) {
                burst=true
                mkBurst?.apply {
                    position = GeoPoint(lat, lon)
                    setVisible(true)
                    subDescription = Instant.now().toString()
                }
                playSound(R.raw._541192__eminyildirim__balloon_explosion_pop)
            }
            heightDelta = newHeightDelta
            this.height = height
            binding.horizontalSpeed.text = "V: ${vel}km/h"
        }
        if (bk && bktime>0 && bktime != 8 * 3600 + 30 * 60) updateBk(bktime)
        updateRSSI(sign)
        updateBattery(bat)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mySondyGOStatus(
            type: String, freq: Double, sign: Double, bat: Int, batV: Int,
            mute: Boolean, ver: String
    ) {
        updateTypeAndFreq(type, freq)
        updateMute(mute)
        sondeLevelListDrawable.level = 0
        updateRSSI(sign)
        updateBattery(bat)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mySondyGOSonde(
            type: String, freq: Double, name: String, sign: Double, bat: Int, afc: Int,
            batV: Int, mute: Boolean, ver: String
    ) {
        updateMute(mute)
        updateTypeAndFreq(type, freq)
        binding.id.text = name
        sondeLevelListDrawable.level = 0
        updateRSSI(sign)
        updateBattery(bat)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun mySondyGOSettings(
            type: String, freq: Double, sda: Int, scl: Int, rst: Int, led: Int,
            RS41bw: Int, M20bw: Int, M10bw: Int, PILOTbw: Int, DFMbw: Int, call: String,
            offset: Int, bat: Int, batMin: Int, batMax: Int, batType: Int, lcd: Int,
            nam: Int, buz: Int, ver: String
    ) {
        showProgress(false)
        updateMute(mute)
        val intent = Intent(this, SettingsActivity::class.java)
        val extras = Bundle().apply {
            putInt("oled_sda", sda)
            putInt("oled_scl", scl)
            putInt("oled_rst", rst)
            putInt("led_pout", led)
            putInt("rs41.rxbw", RS41bw)
            putInt("m20.rxbw", M20bw)
            putInt("m10.rxbw", M10bw)
            putInt("pilot.rxbw", PILOTbw)
            putInt("dfm.rxbw", DFMbw)
            putString("myCall", call)
            putInt("freqofs", offset)
            putInt("battery", bat)
            putInt("vBatMin", batMin)
            putInt("vBatMax", batMax)
            putInt("vBatType", batType)
            putInt("lcd", lcd)
            putInt("aprsName", nam)
            putInt("buz_pin", buz)
            putString("ver", ver)
        }
        intent.putExtras(extras)
        resultLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun closeMenu() {
        binding.coords.visibility = View.GONE
        binding.menu.layoutParams?.height = binding.menu.getChildAt(0).layoutParams.height
        binding.menu.requestLayout()
        expandedMenu = false
    }

    private fun openMenu() {
        if (binding.lat.text!="")
            binding.coords.visibility = View.VISIBLE
        expandedMenu = true
        binding.menu.apply {
            layoutParams?.height = children.fold(0) { sum, el -> sum + if (el.visibility==View.VISIBLE) el.layoutParams.height else 0 }
            requestLayout()
        }
    }

    private fun ttgoNotConnectedWarning() {
        Toast.makeText(applicationContext, "TTGO not connected", Toast.LENGTH_LONG).apply {
            setGravity(Gravity.CENTER_VERTICAL, 0, 0)
            show()
        }
    }

    private fun toggleBuzzer() {
        if (deviceInterface == null)
            ttgoNotConnectedWarning()
        else {
            if (muteChanged) return
            mute = !mute
            sendCommand("mute", if (mute) 1 else 0)
            binding.buzzer.imageAlpha = 64
            muteChanged = true
        }
    }

    private fun playSound(id:Int=R.raw._573381__ammaro__ding) {
        MediaPlayer().apply {
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, a, b -> Log.e(TAG, "$a $b"); true }
            try {
                setDataSource(applicationContext,Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/raw/${id}"))
                prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            }
        }
    }

    private fun requestPermissionsIfNecessary(permissions: Array<String>) {
        val permissionsToRequest: ArrayList<String> = ArrayList()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is not granted
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.size > 0) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toArray(arrayOfNulls(0)),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }
    private var n: Int = 0

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sondeTypes = resources.getStringArray(R.array.sonde_types)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        /*WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }*/

        requestPermissionsIfNecessary(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ))

        binding=ActivityFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.buzzer.setOnClickListener { toggleBuzzer() }

        binding.lat.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("sonde latitude", binding.lat.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(applicationContext, "Latitude copied to clipboard", Toast.LENGTH_SHORT).apply {
                setGravity(Gravity.CENTER_VERTICAL, 0, 0)
                show()
            }
        }
        binding.lon.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("sonde longitude", binding.lon.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(applicationContext, "Longitude copied to clipboard", Toast.LENGTH_SHORT).apply {
                setGravity(Gravity.CENTER_VERTICAL, 0, 0)
                show()
            }
        }
        binding.sonde.setOnClickListener {
            if (deviceInterface == null) {
                ttgoNotConnectedWarning()
                return@setOnClickListener
            }
            val dlg = SondeTypeDialog()
            dlg.freq = freq
            dlg.type = sondeType
            dlg.dialogCloseListener = object : DialogCloseListener {
                override fun handleDialogClose() {
                    freq = dlg.freq
                    sondeType = dlg.type

                    sendCommands(
                            listOf<Pair<String, Any>>(
                                    Pair("f", freq),
                                    Pair("tipo", sondeType)
                            )
                    )
                }
            }

            dlg.show(supportFragmentManager, "")
        }

        binding.menu.apply {
            layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)

            onFocusChangeListener = View.OnFocusChangeListener { _, _ ->
                Log.i(TAG, "Lost focus")
                closeMenu()
            }
        }
        closeMenu()
        binding.menuCenter.setOnClickListener {
            if (currentLocation != null)
                binding.map.controller?.setCenter(GeoPoint(currentLocation))
            else
                Toast.makeText(applicationContext, "No current location (yet)", Toast.LENGTH_SHORT).apply {
                    setGravity(Gravity.CENTER_VERTICAL, 0, 0)
                    show()
                }
            //////////////////////////////////////////////////////////////////////////////////
            if(Debug.isDebuggerConnected()) {
                val msgs = arrayOf(
                        "1/RS41/402.800/T1840263/41.20888/5.82557/6060.9/93.1/127.5/53/0/1/28040/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20888/8.82567/6060.9/93.1/127.5/15/0/1/28039/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20888/8.82577/6040.9/93.1/127.5/99/0/1/28038/3643/0/0/0/0/2.30/o",
                        "1/RS41/402.800/T1840263/45.20898/8.82567/6030.9/93.1/127.5/1/0/1/28037/3643/0/0/0/0/2.30/o"
                )
                n++
                n %= msgs.size
                process(msgs[n])
            }
            //////////////////////////////////////////////////////////////////////////////////
            closeMenu()
        }
        binding.menuSettings.setOnClickListener {
            if (deviceInterface == null)
                ttgoNotConnectedWarning()
            else {
                sendCommand("?")
                showProgress(true)
            }
            closeMenu()
        }
        binding.menuLayer.setOnClickListener {
            satelliteView = !satelliteView
            if (satelliteView) {
                val mapbox = MapBoxTileSource()
                mapbox.retrieveAccessToken(this)
                mapbox.retrieveMapBoxMapId(this)
                TileSourceFactory.addTileSource(mapbox)
                binding.map.setTileSource(mapbox)
            } else
                binding.map.setTileSource(TileSourceFactory.MAPNIK)
            closeMenu()
        }
        binding.menuCenterSonde.setOnClickListener {
            if (sondeId != null)
                binding.map.controller?.setCenter(mkSonde?.position)
            else
                Toast.makeText(applicationContext, "No sonde to show", Toast.LENGTH_SHORT).apply {
                setGravity(Gravity.CENTER_VERTICAL, 0, 0)
                show()
            }
            closeMenu()
        }
        binding.menuMaps.setOnClickListener {
            if (sondeId != null)
                navigate(mkSonde?.position!!)
            else
                Toast.makeText(applicationContext, "No sonde to navigate to", Toast.LENGTH_SHORT).apply {
                    setGravity(Gravity.CENTER_VERTICAL, 0, 0)
                    show()
                }
            closeMenu()
        }
        binding.menuOpen.setOnClickListener {
            if (!expandedMenu)
                openMenu()
            else
                closeMenu()
        }

        Configuration.getInstance().userAgentValue = applicationContext.packageName
        binding.map.run {
            overlays?.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun longPressHelper(p: GeoPoint): Boolean {
                    closeMenu()
                    return false
                }

                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    closeMenu()
                    return false
                }
            }))

            addOnFirstLayoutListener { _: View, _: Int, _: Int, _: Int, _: Int ->
                isTilesScaledToDpi=true
                maxZoomLevel=20.0
                controller?.setZoom(15.0)
                zoomController?.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller?.setCenter(GeoPoint(45.5, 7.1))

                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)

                val dm: DisplayMetrics = applicationContext.resources.displayMetrics
                val scaleBar = ScaleBarOverlay(binding.map)
                scaleBar.setScaleBarOffset(dm.widthPixels / 2, 10)

                val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_person_red)
                locationOverlay = object : MyLocationNewOverlay(GpsMyLocationProvider(applicationContext), binding.map) {
                    override fun onLocationChanged(
                            location: Location,
                            source: IMyLocationProvider?
                    ) {
                        super.onLocationChanged(location, source)
                        onLocationChanged(location)
                    }
                }.apply {
                    setDirectionArrow(bmp, bmp)
                    enableMyLocation()
                    runOnFirstFix {
                        runOnUiThread {
                            binding.map.controller?.animateTo(GeoPoint(lastFix))
                        }
                    }
                }
                sondeLevelListDrawable.apply {
                    addLevel(0, 0, AppCompatResources.getDrawable(applicationContext, R.drawable.ic_sonde_red))
                    addLevel(1, 1, AppCompatResources.getDrawable(applicationContext, R.drawable.ic_sonde_green))
                    level = 0
                }
                mkSonde = Marker(binding.map).apply {
                    icon = sondeLevelListDrawable
                    position = GeoPoint(45.088144, 7.633692)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setVisible(false)
                    setOnMarkerClickListener { marker, _ -> if (sondeId != null) navigate(marker.position); true }
                }
                mkTarget=Marker(binding.map).apply {
                    icon = AppCompatResources.getDrawable(applicationContext, R.drawable.target)
                    position = GeoPoint(45.088144, 7.633692)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setVisible(false)
                    setOnMarkerClickListener { marker, _ -> navigate(marker.position); true }
                }
                mkBurst=Marker(binding.map).apply {
                    title="Burst"
                    icon = AppCompatResources.getDrawable(applicationContext, R.drawable.ic_burst)
                    position = GeoPoint(45.088144, 7.633692)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setVisible(false)
                }

                path.outlinePaint.color = Color.rgb(0, 0, 255)
                sondePath.outlinePaint.color = Color.rgb(255, 128, 0)
                sondeDirection.outlinePaint.color = Color.rgb(255, 0, 0)
                sondeDirection.isVisible = false
                trajectory.outlinePaint.color = Color.argb(128,255, 128, 0)
                trajectory.isVisible = false
                accuracyOverlay=Polygon1(binding.map).apply {
                    fillPaint.color = Color.argb(32,0,0,255)
                    outlinePaint.strokeWidth=2F
                    outlinePaint.color=Color.argb(128,0,0,255)
                }

                overlays?.addAll(
                    listOf(accuracyOverlay, path, sondePath, sondeDirection, scaleBar, mkSonde,
                        locationOverlay, trajectory, mkBurst, mkTarget,
                        MapEventsOverlay(this@FullscreenActivity))
                )

                //predict(45.0,7.0,1000.0)///////////////////////////////////////////////////////////////////////////
            }
        }
        handler.post(object : Runnable {
            override fun run() {
                if (timeLastSeen != null && timeLastSeen?.until(
                                Instant.now(),
                                ChronoUnit.SECONDS
                        )!! > 3L
                ) {
                    if (bk != null)
                        updateBk(Instant.now().until(bk, ChronoUnit.SECONDS).toInt())
                }
                if (timeLastMessage != null && timeLastMessage?.until(
                                Instant.now(),
                                ChronoUnit.SECONDS
                        )!! > 10L
                ) {
                    if (deviceInterface != null) {
                        bluetoothManager.closeDevice(btMacAddress)
                        onDisconnected()
                    }
                }
                handler.postDelayed(this, 1000)
            }
        })

        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        if (deviceInterface == null) connect()
    }

    private fun predict(lat:Double,lng:Double,alt:Double) {
        val scope: CoroutineScope=object : CoroutineScope {
            private var job: Job = Job()
            override val coroutineContext: CoroutineContext
                get() = Dispatchers.Main + job
        }
        scope.launch {
            try {
                //TODO: usare velocità verticale corrente in discesa
                val tawhiri = Tawhiri(Instant.now(), lat, lng, alt, if (burst) alt + 1 else 33000.0)
                mkTarget?.setVisible(false)
                trajectory.actualPoints.clear()
                trajectory.isVisible=false
                var lastPoint: GeoPoint? = null
                var lastTrajectoryPoint:TrajectoryPoint?=null
                tawhiri.getTrajectory().apply {
                    if (this[0].stage=="ascent")
                        mkBurst?.setVisible(false)
                    forEach {
                        if (it.stage == "descent" && !burst)
                            mkBurst?.apply {
                                position = lastPoint
                                mkBurst?.subDescription=lastTrajectoryPoint?.datetime
                                setVisible(true)
                            }
                        it.trajectory.forEach { point ->
                            lastTrajectoryPoint=point
                            lastPoint = GeoPoint(point.latitude, point.longitude)
                            trajectory.addPoint(lastPoint)
                        }
                    }
                }
                trajectory.isVisible = true
                mkTarget?.position = trajectory.actualPoints[trajectory.actualPoints.size - 1]
                mkTarget?.setVisible(true)
            }
            catch (e:Exception) {
                Log.e(TAG,e.toString())
                trajectory.isVisible = false
                mkTarget?.setVisible(false)
            }
        }
    }

    private fun navigate(position:GeoPoint) {
        val uri = Uri.parse("google.navigation:q=${position.latitude},${position.longitude}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onBackPressed() {
        if (expandedMenu)
            closeMenu()
        else
            AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("TrovaLaSonda")
                    .setMessage("Are you sure you want to exit?")
                    .setPositiveButton("Yes") { _, _ -> bluetoothManager.closeDevice(btMacAddress); finish() }
                    .setNegativeButton("No", null)
                    .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.i(TAG,"onSaveInstanceState")
        outState.putAll(bundleOf("expandedMenu" to expandedMenu,
            "satelliteView" to satelliteView,
            "currentLocation" to currentLocation,
            "sondeId" to sondeId,
            "mute" to mute,
            "muteChanged" to muteChanged,
            "btMacAddress" to btMacAddress,
            "sondeType" to sondeType,
            "heightDelta" to heightDelta,
            "height" to height,
            "freq" to freq,
            "bk" to bk,
            "timeLastSeen" to timeLastSeen,
            "timeLastMessage" to timeLastMessage,
            "height" to height,
            "lat" to binding.lat.text,
            "lon" to binding.lon.text,
            "distance" to binding.distance.text,
            "units" to binding.unit.text,
            "horizontalSpeed" to binding.horizontalSpeed.text,
            "direction" to binding.direction.text
        ))
    }

    override fun onRestoreInstanceState(savedInstanceState:Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.i(TAG,"onRestoreInstanceState")
        with(savedInstanceState) {
            expandedMenu = get("expandedMenu") as Boolean
            satelliteView = get("satelliteView") as Boolean
            mute = get("mute") as Boolean
            muteChanged = get("muteChange") as Boolean
            btMacAddress = get("btMacAddress") as String
            sondeType = get("sondeType") as Int
            heightDelta = get("heightDelta") as Double
            height = get("height") as Double
            freq = get("freq") as Double
            bk = get("bk") as Instant
            timeLastSeen = get("timeLastSeen") as Instant
            timeLastMessage = get("timeLastMessage") as Instant
            currentLocation = get("currentLocation") as Location
            sondeId = get("deviceInterface") as String
            height = get("height") as Double
            binding.lat.text=get("lat") as String
            binding.lon.text=get("lon") as String
            binding.distance.text=get("distance") as String
            binding.unit.text=get("units") as String
            binding.horizontalSpeed.text=get("horizontalSpeed") as String
            binding.direction.text=get("direction") as String
        }
        binding.id.text=sondeId
        binding.height.text=height.toString()
    }


    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
        InfoWindow.closeAllInfoWindowsOn(binding.map)
        closeMenu()
        return false
    }

    override fun longPressHelper(p: GeoPoint?): Boolean {
        InfoWindow.closeAllInfoWindowsOn(binding.map)
        closeMenu()
        return false
    }

    companion object {
        private const val TAG = "MAURI"
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 1
        private const val MYSONDYGOPREFIX = "MySondyGO-"
        private var freqOffsetReceiver: FreqOffsetReceiver?=null
        fun registerFreqOffsetReceiver(r: FreqOffsetReceiver) {
            freqOffsetReceiver =r
        }

        fun unregisterFreqOffsetReceiver() {
            freqOffsetReceiver =null
        }
    }}
