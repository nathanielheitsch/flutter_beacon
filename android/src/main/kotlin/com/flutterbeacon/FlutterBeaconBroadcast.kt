package com.flutterbeacon

import android.Manifest
import android.app.Activity
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.BeaconTransmitter
import io.flutter.plugin.common.MethodChannel

class FlutterBeaconBroadcast(private val activity: Activity, iBeaconLayout: BeaconParser) {
    private val beaconTransmitter: BeaconTransmitter = BeaconTransmitter(activity, iBeaconLayout)

    fun isBroadcasting(result: MethodChannel.Result) {
        result.success(beaconTransmitter.isStarted)
    }

    fun stopBroadcast( result: MethodChannel.Result) {
        beaconTransmitter.stopAdvertising()
        result.success(true)
    }

    fun startBroadcast(arguments: Any?,  result: MethodChannel.Result) {
        if (arguments !is Map<*, *>) {
            result.error("Broadcast", "Invalid parameter", null)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                result.error("Broadcast", "BLUETOOTH_ADVERTISE permission not granted", null)
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE), REQUEST_CODE_BLUETOOTH_ADVERTISE)
                return
            }
        }

        val beacon = FlutterBeaconUtils.beaconFromMap(arguments)


            val advertisingMode = arguments["advertisingMode"]
            if (advertisingMode is Int) {
                beaconTransmitter.advertiseMode = advertisingMode
            }
            val advertisingTxPowerLevel = arguments["advertisingTxPowerLevel"]
            if (advertisingTxPowerLevel is Int) {
                beaconTransmitter.advertiseTxPowerLevel = advertisingTxPowerLevel
            }
            beaconTransmitter.startAdvertising(beacon, object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    Log.d(TAG, "Start broadcasting = $beacon")
                    result.success(true)
                }

                override fun onStartFailure(errorCode: Int) {
                    val error = when (errorCode) {
                        ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                        ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                        ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                        else -> "FEATURE_UNSUPPORTED"
                    }
                    Log.e(TAG, error)
                    result.error("Broadcast", error, null)
                }
            })
    }

    companion object {
        private const val TAG = "FlutterBeaconBroadcast"
        private const val REQUEST_CODE_BLUETOOTH_ADVERTISE = 1001
    }
}