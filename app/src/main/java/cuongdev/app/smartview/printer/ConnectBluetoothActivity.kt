package cuongdev.app.smartview.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import cuongdev.app.smartview.R
import cuongdev.app.smartview.printer.adapters.DevicesAdapter
import kotlinx.android.synthetic.main.activity_connect_printer.*

class ConnectBluetoothActivity : AppCompatActivity() {
    private val BLUETOOTH_PERMISSION = 76
    private val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN
    )

    companion object {
        val CONNECT_BLUETOOTH = 606
    }

    lateinit var deviceAdapter: DevicesAdapter
    lateinit var bluetoothAdapter: BluetoothAdapter

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, intent: Intent?) {
            if (intent!!.action!! == BluetoothDevice.ACTION_FOUND) {
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)!!
                deviceAdapter.addDevice(device)
            }
        }
    }

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, intent: Intent?) {
            if (intent!!.action!! == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val bluetoothState = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )

                if (bluetoothState == BluetoothAdapter.STATE_ON && deviceAdapter.itemCount == 0) {
                    findBluetooth()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect_printer)
        title = "Bluetooth"

        deviceAdapter = DevicesAdapter(this)

        recyclerDevices.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = deviceAdapter
        }

        if (hasAllPermissions()) {
            findBluetooth()
        } else {
            requestPermissions(PERMISSIONS, BLUETOOTH_PERMISSION)
        }
    }

    private fun hasAllPermissions(): Boolean {
        for (permission in PERMISSIONS)
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) return false
        return true
    }

    @SuppressLint("MissingPermission")
    private fun findDevices() {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled) {
            BluetoothAdapter.getDefaultAdapter().enable()
            safeRegisterReceiver(
                connectionReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            )
            return
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED)

        safeRegisterReceiver(broadcastReceiver, filter)
        bluetoothAdapter.startDiscovery()
    }

    private fun findBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevice = bluetoothAdapter.bondedDevices
        if (pairedDevice.isNotEmpty()) {
            for (device in pairedDevice) {
                deviceAdapter.addDevice(device)
                Log.d("TEST","device name ${device.name}")
            }
        }
        bluetoothAdapter.startDiscovery()
    }


    override fun onDestroy() {
        safeUnregisterReceiver(broadcastReceiver)
        safeUnregisterReceiver(connectionReceiver)
        super.onDestroy()
    }

    private fun safeRegisterReceiver(receiver: BroadcastReceiver, intentFilter: IntentFilter) {
        try {
            registerReceiver(receiver, intentFilter)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    private fun safeUnregisterReceiver(receiver: BroadcastReceiver) {
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_PERMISSION) {
            if (hasAllPermissions()) {
                findBluetooth()
            } else {
                finish()
            }
        }
    }
}
