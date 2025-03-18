package com.flutterbeacon

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.RemoteException
import androidx.core.app.ActivityCompat
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener

class FlutterBeaconPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler,
    RequestPermissionsResultListener, ActivityResultListener {

    private var flutterPluginBinding: FlutterPluginBinding? = null
    private var activityPluginBinding: ActivityPluginBinding? = null

    private var beaconScanner: FlutterBeaconScanner? = null
    private var beaconBroadcast: FlutterBeaconBroadcast? = null
    private var platform: FlutterPlatform? = null

    var beaconManager: BeaconManager? = null
    var flutterResult: MethodChannel.Result? = null
    private var flutterResultBluetooth: MethodChannel.Result? = null
    private var eventSinkLocationAuthorizationStatus: EventChannel.EventSink? = null

    private var channel: MethodChannel? = null
    private var eventChannel: EventChannel? = null
    private var eventChannelMonitoring: EventChannel? = null
    private var eventChannelBluetoothState: EventChannel? = null
    private var eventChannelAuthorizationStatus: EventChannel? = null

    companion object {
        private val iBeaconLayout = BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
        const val REQUEST_CODE_LOCATION = 1234
        const val REQUEST_CODE_BLUETOOTH = 5678
        const val REQUEST_CODE_BLUETOOTH_SCAN = 5679
    }

    override fun onAttachedToEngine(binding: FlutterPluginBinding) {
        this.flutterPluginBinding = binding
    }

    override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
        this.flutterPluginBinding = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activityPluginBinding = binding
        setupChannels(flutterPluginBinding!!.binaryMessenger, binding.activity)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        teardownChannels()
    }

    private fun setupChannels(messenger: BinaryMessenger, activity: Activity) {
        activityPluginBinding?.addActivityResultListener(this)
        activityPluginBinding?.addRequestPermissionsResultListener(this)

        beaconManager = BeaconManager.getInstanceForApplication(activity.applicationContext)
        if (!beaconManager!!.beaconParsers.contains(iBeaconLayout)) {
            beaconManager!!.beaconParsers.clear()
            beaconManager!!.beaconParsers.add(iBeaconLayout)
        }

        platform = FlutterPlatform(activity)
        beaconScanner = FlutterBeaconScanner(this, activity)
        beaconBroadcast = FlutterBeaconBroadcast(activity, iBeaconLayout)

        channel = MethodChannel(messenger, "flutter_beacon")
        channel!!.setMethodCallHandler(this)

        eventChannel = EventChannel(messenger, "flutter_beacon_event")
        eventChannel!!.setStreamHandler(beaconScanner!!.rangingStreamHandler)

        eventChannelMonitoring = EventChannel(messenger, "flutter_beacon_event_monitoring")
        eventChannelMonitoring!!.setStreamHandler(beaconScanner!!.monitoringStreamHandler)

        eventChannelBluetoothState = EventChannel(messenger, "flutter_bluetooth_state_changed")
        eventChannelBluetoothState!!.setStreamHandler(FlutterBluetoothStateReceiver(activity))

        eventChannelAuthorizationStatus = EventChannel(messenger, "flutter_authorization_status_changed")
        eventChannelAuthorizationStatus!!.setStreamHandler(locationAuthorizationStatusStreamHandler)
    }

    private fun teardownChannels() {
        activityPluginBinding?.removeActivityResultListener(this)
        activityPluginBinding?.removeRequestPermissionsResultListener(this)

        platform = null
        beaconBroadcast = null

        channel?.setMethodCallHandler(null)
        eventChannel?.setStreamHandler(null)
        eventChannelMonitoring?.setStreamHandler(null)
        eventChannelBluetoothState?.setStreamHandler(null)
        eventChannelAuthorizationStatus?.setStreamHandler(null)

        channel = null
        eventChannel = null
        eventChannelMonitoring = null
        eventChannelBluetoothState = null
        eventChannelAuthorizationStatus = null

        activityPluginBinding = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "initialize" -> {
                platform!!.requestBluetoothPermissions()
                if (beaconManager != null && !beaconManager!!.isBound(beaconScanner!!.beaconConsumer)) {
                    this.flutterResult = result
                    this.beaconManager!!.bind(beaconScanner!!.beaconConsumer)
                    return
                }
                result.success(true)
            }
            "initializeAndCheck" -> initializeAndCheck(result)
            "setScanPeriod" -> {
                val scanPeriod = call.argument<Int>("scanPeriod")!!
                this.beaconManager!!.foregroundScanPeriod = scanPeriod.toLong()
                try {
                    this.beaconManager!!.updateScanPeriods()
                    result.success(true)
                } catch (e: RemoteException) {
                    result.success(false)
                }
            }
            "setBetweenScanPeriod" -> {
                val betweenScanPeriod = call.argument<Int>("betweenScanPeriod")!!
                this.beaconManager!!.foregroundBetweenScanPeriod = betweenScanPeriod.toLong()
                try {
                    this.beaconManager!!.updateScanPeriods()
                    result.success(true)
                } catch (e: RemoteException) {
                    result.success(false)
                }
            }
            "setLocationAuthorizationTypeDefault" -> result.success(true)
            "authorizationStatus" -> result.success(if (platform!!.checkLocationServicesPermission()) "ALLOWED" else "NOT_DETERMINED")
            "checkLocationServicesIfEnabled" -> result.success(platform!!.checkLocationServicesIfEnabled())
            "bluetoothState" -> {
                try {
                    val flag = platform!!.checkBluetoothIfEnabled()
                    result.success(if (flag) "STATE_ON" else "STATE_OFF")
                } catch (ignored: RuntimeException) {
                    result.success("STATE_UNSUPPORTED")
                }
            }
            "requestAuthorization" -> {
                if (!platform!!.checkLocationServicesPermission()) {
                    this.flutterResult = result
                    platform!!.requestAuthorization()
                    return
                }
                eventSinkLocationAuthorizationStatus?.success("ALLOWED")
                result.success(true)
            }
            "openBluetoothSettings" -> {
                if (!platform!!.checkBluetoothIfEnabled()) {
                    this.flutterResultBluetooth = result
                    platform!!.openBluetoothSettings()
                    return
                }
                result.success(true)
            }
            "openLocationSettings" -> {
                platform!!.openLocationSettings()
                result.success(true)
            }
            "openApplicationSettings" -> result.notImplemented()
            "close" -> {
                if (beaconManager != null) {
                    beaconScanner!!.stopRanging()
                    beaconManager!!.removeAllRangeNotifiers()
                    beaconScanner!!.stopMonitoring()
                    beaconManager!!.removeAllMonitorNotifiers()
                    if (beaconManager!!.isBound(beaconScanner!!.beaconConsumer)) {
                        beaconManager!!.unbind(beaconScanner!!.beaconConsumer)
                    }
                }
                result.success(true)
            }
            "startBroadcast" -> beaconBroadcast!!.startBroadcast(call.arguments, result)
            "stopBroadcast" -> beaconBroadcast!!.stopBroadcast(result)
            "isBroadcasting" -> beaconBroadcast!!.isBroadcasting(result)
            "isBroadcastSupported" -> result.success(platform!!.isBroadcastSupported())
            else -> result.notImplemented()
        }
    }

    private fun initializeAndCheck(result: MethodChannel.Result?) {
        if (platform!!.checkLocationServicesPermission() && platform!!.checkBluetoothIfEnabled() && platform!!.checkLocationServicesIfEnabled() && platform!!.checkBluetoothPermissions()) {
            result?.success(true)
            return
        }

        flutterResult = result
        if (!platform!!.checkBluetoothIfEnabled()) {
            platform!!.openBluetoothSettings()
        }

        if (!platform!!.checkLocationServicesPermission()) {
            platform!!.requestAuthorization()
        }

        if (!platform!!.checkLocationServicesIfEnabled()) {
            platform!!.openLocationSettings()
        }

        if (platform!!.checkBluetoothPermissions()) {
            platform!!.requestBluetoothPermissions()
        }


        if (beaconManager != null && !beaconManager!!.isBound(beaconScanner!!.beaconConsumer)) {
            flutterResult = result
            beaconManager!!.bind(beaconScanner!!.beaconConsumer)
        }

        result?.success(true)
    }

    private val locationAuthorizationStatusStreamHandler = object : EventChannel.StreamHandler {
        override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
            eventSinkLocationAuthorizationStatus = events
        }

        override fun onCancel(arguments: Any?) {
            eventSinkLocationAuthorizationStatus = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        if (requestCode != REQUEST_CODE_LOCATION) {
            return false
        }

        var locationServiceAllowed = false
        if (permissions.isNotEmpty() && grantResults.isNotEmpty()) {
            val permission = permissions[0]
            if (!platform!!.shouldShowRequestPermissionRationale(permission)) {
                val grantResult = grantResults[0]
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    locationServiceAllowed = true
                }
                eventSinkLocationAuthorizationStatus?.success(if (locationServiceAllowed) "ALLOWED" else "DENIED")
            } else {
                eventSinkLocationAuthorizationStatus?.success("NOT_DETERMINED")
            }
        } else {
            eventSinkLocationAuthorizationStatus?.success("NOT_DETERMINED")
        }

        flutterResult?.let {
            if (locationServiceAllowed) {
                it.success(true)
            } else {
                it.error("Beacon", "location services not allowed", null)
            }
            flutterResult = null
        }

        return locationServiceAllowed
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?): Boolean {
        val bluetoothEnabled = requestCode == REQUEST_CODE_BLUETOOTH && resultCode == Activity.RESULT_OK

        if (bluetoothEnabled) {
            if (!platform!!.checkLocationServicesPermission()) {
                platform!!.requestAuthorization()
            } else {
                flutterResultBluetooth?.success(true)
                flutterResultBluetooth = null
                flutterResult?.success(true)
                flutterResult = null
            }
        } else {
            flutterResultBluetooth?.error("Beacon", "bluetooth disabled", null)
            flutterResultBluetooth = null
            flutterResult?.error("Beacon", "bluetooth disabled", null)
            flutterResult = null
        }

        return bluetoothEnabled
    }
}