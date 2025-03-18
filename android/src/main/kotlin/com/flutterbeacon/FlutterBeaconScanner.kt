package com.flutterbeacon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconConsumer
import org.altbeacon.beacon.MonitorNotifier
import org.altbeacon.beacon.RangeNotifier
import org.altbeacon.beacon.Region
import io.flutter.plugin.common.EventChannel
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.HashMap

class FlutterBeaconScanner(private val plugin: FlutterBeaconPlugin, activity: Activity) {
    private val activity: WeakReference<Activity> = WeakReference(activity)
    private val handler: Handler = Handler(Looper.getMainLooper())

    private var eventSinkRanging: EventChannel.EventSink? = null
    private var eventSinkMonitoring: EventChannel.EventSink? = null
    private var regionRanging: MutableList<Region>? = null
    private var regionMonitoring: MutableList<Region>? = null

    val rangingStreamHandler = object : EventChannel.StreamHandler {
        override fun onListen(arguments: Any?, eventSink: EventChannel.EventSink?) {
            Log.d("RANGING", "Start ranging = $arguments")
            startRanging(arguments, eventSink)
        }

        override fun onCancel(arguments: Any?) {
            Log.d("RANGING", "Stop ranging = $arguments")
            stopRanging()
        }
    }

    private fun startRanging(arguments: Any?, eventSink: EventChannel.EventSink?) {
        if (arguments is List<*>) {
            if (regionRanging == null) {
                regionRanging = ArrayList()
            } else {
                regionRanging!!.clear()
            }
            for (obj in arguments) {
                if (obj is Map<*, *>) {
                    val region = FlutterBeaconUtils.regionFromMap(obj)
                    if (region != null) {
                        regionRanging!!.add(region)
                    }
                }
            }
        } else {
            eventSink?.error("Beacon", "invalid region for ranging", null)
            return
        }
        eventSinkRanging = eventSink
        if (plugin.beaconManager != null && !plugin.beaconManager!!.isBound(beaconConsumer)) {
            plugin.beaconManager!!.bind(beaconConsumer)
        } else {
            startRanging()
        }
    }

    fun startRanging() {
        if (regionRanging.isNullOrEmpty()) {
            Log.e("RANGING", "Region ranging is null or empty. Ranging not started.")
            return
        }

        try {
            plugin.beaconManager?.apply {
                removeAllRangeNotifiers()
                addRangeNotifier(rangeNotifier)
                for (region in regionRanging!!) {
                    startRangingBeaconsInRegion(region)
                }
            }
        } catch (e: RemoteException) {
            eventSinkRanging?.error("Beacon", e.localizedMessage, null)
        }
    }

    fun stopRanging() {
        if (!regionRanging.isNullOrEmpty()) {
            try {
                for (region in regionRanging!!) {
                    plugin.beaconManager?.stopRangingBeaconsInRegion(region)
                }
                plugin.beaconManager?.removeRangeNotifier(rangeNotifier)
            } catch (ignored: RemoteException) {
            }
        }
        eventSinkRanging = null
    }

    private val rangeNotifier = RangeNotifier { beacons, region ->
        eventSinkRanging?.let {
            val map = HashMap<String, Any>()
            map["region"] = FlutterBeaconUtils.regionToMap(region)
            map["beacons"] = FlutterBeaconUtils.beaconsToArray(ArrayList(beacons))
            handler.post {
                eventSinkRanging?.success(map)
            }
        }
    }

    val monitoringStreamHandler = object : EventChannel.StreamHandler {
        override fun onListen(arguments: Any?, eventSink: EventChannel.EventSink?) {
            startMonitoring(arguments, eventSink)
        }

        override fun onCancel(arguments: Any?) {
            stopMonitoring()
        }
    }

    private fun startMonitoring(arguments: Any?, eventSink: EventChannel.EventSink?) {
        Log.d(TAG, "START MONITORING=$arguments")
        if (arguments is List<*>) {
            if (regionMonitoring == null) {
                regionMonitoring = ArrayList()
            } else {
                regionMonitoring!!.clear()
            }
            for (obj in arguments) {
                if (obj is Map<*, *>) {
                    val region = FlutterBeaconUtils.regionFromMap(obj)
                    if (region != null) {
                        regionMonitoring!!.add(region)
                    }
                }
            }
        } else {
            eventSink?.error("Beacon", "invalid region for monitoring", null)
            return
        }
        eventSinkMonitoring = eventSink
        if (plugin.beaconManager != null && !plugin.beaconManager!!.isBound(beaconConsumer)) {
            plugin.beaconManager!!.bind(beaconConsumer)
        } else {
            startMonitoring()
        }
    }

    fun startMonitoring() {
        if (regionMonitoring.isNullOrEmpty()) {
            Log.e("MONITORING", "Region monitoring is null or empty. Monitoring not started.")
            return
        }

        try {
            plugin.beaconManager?.apply {
                removeAllMonitorNotifiers()
                addMonitorNotifier(monitorNotifier)
                for (region in regionMonitoring!!) {
                    startMonitoringBeaconsInRegion(region)
                }
            }
        } catch (e: RemoteException) {
            eventSinkMonitoring?.error("Beacon", e.localizedMessage, null)
        }
    }

    fun stopMonitoring() {
        if (!regionMonitoring.isNullOrEmpty()) {
            try {
                for (region in regionMonitoring!!) {
                    plugin.beaconManager?.stopMonitoringBeaconsInRegion(region)
                }
                plugin.beaconManager?.removeMonitorNotifier(monitorNotifier)
            } catch (ignored: RemoteException) {
            }
        }
        eventSinkMonitoring = null
    }

    private val monitorNotifier = object : MonitorNotifier {
        override fun didEnterRegion(region: Region) {
            eventSinkMonitoring?.let {
                val map = HashMap<String, Any>()
                map["event"] = "didEnterRegion"
                map["region"] = FlutterBeaconUtils.regionToMap(region)
                handler.post {
                    eventSinkMonitoring?.success(map)
                }
            }
        }

        override fun didExitRegion(region: Region) {
            eventSinkMonitoring?.let {
                val map = HashMap<String, Any>()
                map["event"] = "didExitRegion"
                map["region"] = FlutterBeaconUtils.regionToMap(region)
                handler.post {
                    eventSinkMonitoring?.success(map)
                }
            }
        }

        override fun didDetermineStateForRegion(state: Int, region: Region) {
            eventSinkMonitoring?.let {
                val map = HashMap<String, Any>()
                map["event"] = "didDetermineStateForRegion"
                map["state"] = FlutterBeaconUtils.parseState(state)
                map["region"] = FlutterBeaconUtils.regionToMap(region)
                handler.post {
                    eventSinkMonitoring?.success(map)
                }
            }
        }
    }

    val beaconConsumer = object : BeaconConsumer {
        override fun onBeaconServiceConnect() {
            plugin.flutterResult?.let {
                it.success(true)
                plugin.flutterResult = null
            } ?: run {
                startRanging()
                startMonitoring()
            }
        }

        override fun getApplicationContext(): Context {
            return activity.applicationContext
        }

        override fun unbindService(serviceConnection: ServiceConnection) {
            activity.unbindService(serviceConnection)
        }

        override fun bindService(intent: Intent, serviceConnection: ServiceConnection, flags: Int): Boolean {
            return activity.bindService(intent, serviceConnection, flags)
        }
    }

    companion object {
        private const val TAG = "FlutterBeaconScanner"
    }
}