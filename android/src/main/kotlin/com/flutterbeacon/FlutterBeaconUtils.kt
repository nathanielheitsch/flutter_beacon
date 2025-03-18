package com.flutterbeacon

import android.util.Log
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.MonitorNotifier
import org.altbeacon.beacon.Region
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.Locale

object FlutterBeaconUtils {
    fun parseState(state: Int): String {
        return when (state) {
            MonitorNotifier.INSIDE -> "INSIDE"
            MonitorNotifier.OUTSIDE -> "OUTSIDE"
            else -> "UNKNOWN"
        }
    }

    fun beaconsToArray(beacons: List<Beacon>?): List<Map<String, Any>> {
        if (beacons == null) {
            return ArrayList()
        }
        val list = ArrayList<Map<String, Any>>()
        for (beacon in beacons) {
            val map = beaconToMap(beacon)
            list.add(map)
        }
        return list
    }

    private fun beaconToMap(beacon: Beacon): Map<String, Any> {
        val map = HashMap<String, Any>()

        map["proximityUUID"] = beacon.id1.toString().toUpperCase(Locale.US)
        map["major"] = beacon.id2.toInt()
        map["minor"] = beacon.id3.toInt()
        map["rssi"] = beacon.rssi
        map["txPower"] = beacon.txPower
        map["accuracy"] = String.format(Locale.US, "%.2f", beacon.distance)
        map["macAddress"] = beacon.bluetoothAddress

        return map
    }

    fun regionToMap(region: Region): Map<String, Any> {
        val map = HashMap<String, Any>()

        map["identifier"] = region.uniqueId
        region.id1?.let { map["proximityUUID"] = it.toString() }
        region.id2?.let { map["major"] = it.toInt() }
        region.id3?.let { map["minor"] = it.toInt() }

        return map
    }

    fun regionFromMap(map: Map<*, *>): Region? {
        return try {
            var identifier = ""
            val identifiers = ArrayList<Identifier>()

            val objectIdentifier = map["identifier"]
            if (objectIdentifier is String) {
                identifier = objectIdentifier
            }

            val proximityUUID = map["proximityUUID"]
            if (proximityUUID is String) {
                identifiers.add(Identifier.parse(proximityUUID))
            }

            val major = map["major"]
            if (major is Int) {
                identifiers.add(Identifier.fromInt(major))
            }

            val minor = map["minor"]
            if (minor is Int) {
                identifiers.add(Identifier.fromInt(minor))
            }

            Region(identifier, identifiers)
        } catch (e: IllegalArgumentException) {
            Log.e("REGION", "Error : $e")
            null
        }
    }

    fun beaconFromMap(map: Map<*, *>): Beacon {
        val builder = Beacon.Builder()

        val proximityUUID = map["proximityUUID"]
        if (proximityUUID is String) {
            builder.setId1(proximityUUID)
        }
        val major = map["major"]
        if (major is Int) {
            builder.setId2(major.toString())
        }
        val minor = map["minor"]
        if (minor is Int) {
            builder.setId3(minor.toString())
        }

        val txPower = map["txPower"]
        if (txPower is Int) {
            builder.setTxPower(txPower)
        } else {
            builder.setTxPower(-59)
        }

        builder.setDataFields(Collections.singletonList(0L))
        builder.setManufacturer(0x004c)

        return builder.build()
    }
}