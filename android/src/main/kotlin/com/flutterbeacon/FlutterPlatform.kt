package com.flutterbeacon

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.flutterbeacon.FlutterBeaconPlugin.Companion.REQUEST_CODE_BLUETOOTH_SCAN
import org.altbeacon.beacon.BeaconTransmitter
import java.lang.ref.WeakReference

class FlutterPlatform(activity: Activity) {
    private val activityWeakReference: WeakReference<Activity> = WeakReference(activity)

    private val activity: Activity?
        get() = activityWeakReference.get()

    fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        activity?.startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    fun openBluetoothSettings() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activity?.startActivityForResult(intent, FlutterBeaconPlugin.REQUEST_CODE_BLUETOOTH)
    }

    fun requestAuthorization() {
        ActivityCompat.requestPermissions(activity!!, arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ), FlutterBeaconPlugin.REQUEST_CODE_LOCATION)
    }

    fun checkBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(activity!!, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(activity!!, arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ), FlutterBeaconPlugin.REQUEST_CODE_BLUETOOTH_SCAN)
        }
    }

    fun checkLocationServicesPermission(): Boolean {
        return ContextCompat.checkSelfPermission(activity!!, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun checkLocationServicesIfEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val locationManager = activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
            locationManager?.isLocationEnabled == true
        } else {
            val mode = Settings.Secure.getInt(activity?.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    @SuppressLint("MissingPermission")
    fun checkBluetoothIfEnabled(): Boolean {
        val bluetoothManager = activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
            ?: throw RuntimeException("No bluetooth service")
        val adapter = bluetoothManager.adapter
        return adapter?.isEnabled == true
    }

    fun isBroadcastSupported(): Boolean {
        return BeaconTransmitter.checkTransmissionSupported(activity) == 0
    }

    fun shouldShowRequestPermissionRationale(permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity!!, permission)
    }
}