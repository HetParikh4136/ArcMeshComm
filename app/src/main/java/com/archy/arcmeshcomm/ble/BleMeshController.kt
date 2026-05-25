package com.archy.arcmeshcomm.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class BleReadiness(
    val supported: Boolean,
    val enabled: Boolean,
    val permissionsGranted: Boolean,
    val missingPermissions: List<String>
) {
    val ready: Boolean = supported && enabled && permissionsGranted
}

class BleMeshController(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    fun readiness(): BleReadiness {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        return BleReadiness(
            supported = adapter != null,
            enabled = adapter?.isEnabled == true,
            permissionsGranted = missing.isEmpty(),
            missingPermissions = missing
        )
    }

    companion object {
        fun requiredPermissions(): List<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                listOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }
    }
}
