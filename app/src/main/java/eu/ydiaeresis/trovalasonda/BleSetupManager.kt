package eu.ydiaeresis.trovalasonda

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.bluetooth.BluetoothStatusCodes
import androidx.annotation.RequiresPermission
import android.os.Build
import eu.ydiaeresis.trovalasonda.FullscreenActivity.Companion.TAG

data class BleTask(
    val serviceUuid: UUID,
    val characteristicUuid: UUID,
    val requiresNotification: Boolean = false,
    val requiresInitialRead: Boolean = false,
    val onCharacteristicFound: ((BluetoothGattCharacteristic) -> Unit)? = null,
    val onDataReceived: ((UUID, ByteArray) -> Unit)? = null
)
class BleSetupManager(
    private val gatt: BluetoothGatt,
    private val taskSequence: List<BleTask>
) {
    private val bleMutex = Mutex()
    private var pendingReadUuid: UUID? = null
    private var notificationContinuation: CancellableContinuation<Boolean>? = null
    private var readContinuation: CancellableContinuation<ByteArray?>? = null

    private var mtuContinuation: CancellableContinuation<Boolean>? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun requestMtuUpgrade(targetMtu: Int = 512): Boolean {
        return suspendCancellableCoroutine { continuation ->
            mtuContinuation = continuation

            // Request the larger MTU window from Android OS
            val success = gatt.requestMtu(targetMtu)
            if (!success) {
                mtuContinuation = null
                continuation.resume(false) // System rejected local request instantly
            }

            continuation.invokeOnCancellation { mtuContinuation = null }
        }
    }

    // --- MUST ADD THIS HANDLER ---
    // Connect this to your central BluetoothGattCallback override: onMtuChanged
    fun handleMtuChangedResult(mtu: Int, status: Int) {
        Log.d(TAG, "MTU changed to: $mtu, Status: $status")
        mtuContinuation?.resume(status == BluetoothGatt.GATT_SUCCESS)
        mtuContinuation = null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun executeSetupSequence() = withContext(Dispatchers.Main) {
        bleMutex.withLock {
            try {
                Log.d(TAG, "Requesting MTU upgrade...")
                val mtuSuccess = requestMtuUpgrade(512)
                Log.d(TAG, "MTU Upgrade Completed. Success: $mtuSuccess")
                delay(200)

                for (task in taskSequence) {
                    val service = gatt.getService(task.serviceUuid) ?: continue
                    val characteristic = service.getCharacteristic(task.characteristicUuid) ?: continue

                    task.onCharacteristicFound?.invoke(characteristic)

                    if (task.requiresInitialRead) {
                        val data = readCharacteristic(characteristic)
                        if (data != null) {
                            Log.d(TAG, "Read success for ${task.characteristicUuid}")
                            task.onDataReceived?.invoke(characteristic.uuid, data)

                        } else {
                            Log.e(TAG, "Read failed for ${task.characteristicUuid}")
                        }
                        delay(100) // Pacing delay to keep Android BLE stack happy
                    }

                    // Handle Notification Task
                    if (task.requiresNotification) {
                        val success = enableNotification(characteristic)
                        if (!success) throw Exception("Failed notification setup for ${task.characteristicUuid}")
                        delay(100)
                    }
                }
                Log.d(TAG, "All characteristics processed sequentially!")
            } catch (e: Exception) {
                Log.e(TAG, "Sequence aborted prematurely: ${e.message}")
            }
        }
    }

    // --- SUSPENDING BRIDGES ---

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun readCharacteristic(characteristic: BluetoothGattCharacteristic): ByteArray? {
        return suspendCancellableCoroutine { continuation ->
            pendingReadUuid = characteristic.uuid
            readContinuation = continuation

            val success = gatt.readCharacteristic(characteristic)
            if (!success) {
                pendingReadUuid = null
                readContinuation = null
                continuation.resume(null)
            }
            continuation.invokeOnCancellation {
                pendingReadUuid = null
                readContinuation = null
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun enableNotification(characteristic: BluetoothGattCharacteristic): Boolean {
        return suspendCancellableCoroutine { continuation ->
            notificationContinuation = continuation

            // 1. Tell Android's local stack to expect notifications
            val localSuccess = gatt.setCharacteristicNotification(characteristic, true)
            if (!localSuccess) {
                notificationContinuation = null
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }

            // 2. Grab the CCCD Descriptor
            val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            val cccdDescriptor = characteristic.getDescriptor(cccdUuid)

            if (cccdDescriptor != null) {
                // 3. Handle different Android API versions safely
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13 (API 33) and newer
                    val resultCode = gatt.writeDescriptor(
                        cccdDescriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                    if (resultCode != BluetoothStatusCodes.SUCCESS) {
                        notificationContinuation = null
                        continuation.resume(false)
                    }
                } else {
                    // Android 12 (API 31) and older fallback
                    @Suppress("DEPRECATION")
                    cccdDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    val writeSuccess = gatt.writeDescriptor(cccdDescriptor)
                    if (!writeSuccess) {
                        notificationContinuation = null
                        continuation.resume(false)
                    }
                }
            } else {
                notificationContinuation = null
                continuation.resume(false)
            }

            continuation.invokeOnCancellation { notificationContinuation = null }
        }
    }

    fun handleDescriptorWriteResult(status: Int) {
        notificationContinuation?.resume(status == BluetoothGatt.GATT_SUCCESS)
        notificationContinuation = null
    }

    fun handleCharacteristicReadResult(characteristic: BluetoothGattCharacteristic, status: Int) {
        if (characteristic.uuid == pendingReadUuid) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readContinuation?.resume(characteristic.value)
            } else {
                readContinuation?.resume(null)
            }
            readContinuation = null
            pendingReadUuid = null
        }
    }
}
