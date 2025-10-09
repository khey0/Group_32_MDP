package com.example.group_32_mdp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.group_32_mdp.BluetoothService.LocalBinder
import java.util.UUID
import java.util.function.Function
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class BluetoothActivity : AppCompatActivity() {
    private var bluetoothService: BluetoothService? = null
    private var isBound = false //check if bound to BluetoothService
    private var isConnecting = false
    private val isScanning = false // Track BLE scan state
    private var isReceiverRegistered = false // for scanning of bluetooth devices

    private var pairedListView: ListView? = null
    private var availableDevicesList: ListView? = null
    private var scanButton: Button? = null
    private var disconnectBtn: Button? = null
    private var adapter: BluetoothAdapter? = null
    private val availableDevices = ArrayList<BluetoothDevice>()
    private var availableDevicesAdapter: ArrayAdapter<String?>? = null
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocalBinder
            bluetoothService = binder.service
            isBound = true

            adapter = BluetoothAdapter.getDefaultAdapter()
            //check if there is bluetooth on the device
            if (adapter == null) {
                Toast.makeText(
                    this@BluetoothActivity,
                    "Bluetooth not supported",
                    Toast.LENGTH_SHORT
                ).show()
                val intent = Intent(this@BluetoothActivity, MainActivity::class.java)
                startActivity(intent)
            }


            // Enable buttons now that service is ready
            pairedListView!!.setEnabled(true)
            availableDevicesList!!.setEnabled(true)
            scanButton!!.setEnabled(true)
            disconnectBtn!!.setEnabled(true)

            //Toast.makeText(BluetoothActivity.this, "Bluetooth Service Connected", Toast.LENGTH_SHORT).show();
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)

        pairedListView = findViewById<ListView>(R.id.paired_devices)
        availableDevicesList = findViewById<ListView>(R.id.available_devices)
        scanButton = findViewById<Button>(R.id.scan_button)
        disconnectBtn = findViewById<Button>(R.id.disconnectBtn)
        pairedListView!!.setEnabled(false)
        availableDevicesList!!.setEnabled(false)
        scanButton!!.setEnabled(false)
        disconnectBtn!!.setEnabled(false)

        // Start and bind the service
        val serviceIntent = Intent(this, BluetoothService::class.java)
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)

        //request permissions required for bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf<String>(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ), 1
                )
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf<String>(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ), 1
                )
            }
        }

        scanButton?.setOnClickListener {
            if (!isBound) {
                Toast.makeText(this, "Bluetooth service not ready", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Show devices now that service is ready
            showPairedDevices()
            discoverAvailableDevices()
        }



        disconnectBtn!!.setOnClickListener(View.OnClickListener { v: View? ->
            if (isBound && bluetoothService != null) {
                bluetoothService!!.disconnect()
                Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
            }
        })

        // Listen for messages coming from BluetoothService (C9 feed)
        LocalBroadcastManager.getInstance(this).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val msg = intent.getStringExtra("message") ?: return
                handleIncomingLine(msg)
            }
        }, IntentFilter("BLUETOOTH_MESSAGE"))
    }

    private fun showPairedDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter != null) {
            val pairedDevices = adapter.getBondedDevices()
            val devices: Array<BluetoothDevice> = pairedDevices.toTypedArray()
            val deviceNames = pairedDevices.map { it.name ?: "Unknown Device" }.toTypedArray()

            pairedListView!!.setAdapter(object : ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                deviceNames
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(Color.WHITE)   // force white text
                    return view
                }
            })



            pairedListView?.setOnItemClickListener { parent, view, position, id ->
                val selectedDevice = devices[position]  // non-nullable

                if (!isBound || bluetoothService == null) {
                    Toast.makeText(this, "Bluetooth service not ready yet", Toast.LENGTH_SHORT).show()
                    return@setOnItemClickListener
                }

                if (isConnecting) {
                    Toast.makeText(this, "Already connecting to a device", Toast.LENGTH_SHORT).show()
                    return@setOnItemClickListener
                }

                isConnecting = true
                Toast.makeText(this, "Connecting to ${selectedDevice.name}", Toast.LENGTH_SHORT).show()

                adapter?.cancelDiscovery()
                bluetoothService?.disconnect()

                // Start connection asynchronously
                bluetoothService?.connect(selectedDevice, BluetoothConstants.APP_UUID, object : BluetoothService.ConnectionCallback {
                    override fun onResult(success: Boolean) {
                        runOnUiThread {
                            isConnecting = false
                            if (success) {
                                Toast.makeText(this@BluetoothActivity, "Connected to ${selectedDevice.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@BluetoothActivity, "Failed to connect", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                })
            }

        }
    }

    private fun discoverAvailableDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) return
        Log.d("discoverAvailableDevices", "Discovering available devices...")

        // Clear previous devices
        availableDevices.clear()
        availableDevicesAdapter = object : ArrayAdapter<String?>(
            this,
            android.R.layout.simple_list_item_1,
            ArrayList<String>()
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE) // force white text
                return view
            }
        }
        availableDevicesList!!.adapter = availableDevicesAdapter


        // Ensure location is enabled (required for discovery)
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!locationManager.isLocationEnabled()) {
                Toast.makeText(this, "Enable Location for discovery to work", Toast.LENGTH_LONG)
                    .show()
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                return
            }
        }


        // Register receiver
        if (!isReceiverRegistered) {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            registerReceiver(receiver, filter)
            isReceiverRegistered = true

            Log.d("ReceiverTest", "Receiver Registered!")
        }

        // Start discovery
        if (adapter.isDiscovering()) adapter.cancelDiscovery()
        val started = adapter.startDiscovery()
        Log.d("discoverAvailableDevices", "startDiscovery returned: " + started)
        Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show()

        // Click listener to connect or just show info
        availableDevicesList?.setOnItemClickListener { parent, view, position, id ->
            val device = availableDevices[position]
            val name = device.name ?: "Unknown Device"

            if (!isBound || bluetoothService == null) {
                Toast.makeText(this, "Bluetooth service not ready", Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }

            if (isConnecting) {
                Toast.makeText(this, "Already connecting to a device", Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }

            isConnecting = true
            adapter.cancelDiscovery() // stop scanning to avoid interference
            bluetoothService!!.disconnect()
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                // Already paired → connect directly
                bluetoothService!!.connect(
                    device,
                    BluetoothConstants.APP_UUID,
                    object : BluetoothService.ConnectionCallback {
                        override fun onResult(success: Boolean) {
                            runOnUiThread {
                                isConnecting = false
                                if (success) {
                                    Toast.makeText(this@BluetoothActivity, "Connected to $name", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@BluetoothActivity, "Failed to connect", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    })
            } else {
                // Not paired → initiate pairing
                Toast.makeText(this, "Pairing with " + name, Toast.LENGTH_SHORT).show()
                device.createBond()

                // Listen for bonding state changes in a BroadcastReceiver
                val bondReceiver: BroadcastReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent) {
                        val action = intent.getAction()
                        if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                            val state = intent.getIntExtra(
                                BluetoothDevice.EXTRA_BOND_STATE,
                                BluetoothDevice.ERROR
                            )
                            val prevState = intent.getIntExtra(
                                BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                                BluetoothDevice.ERROR
                            )
                            if (state == BluetoothDevice.BOND_BONDED) {
                                Toast.makeText(
                                    this@BluetoothActivity,
                                    "Paired with " + name,
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Connect after pairing
                                bluetoothService!!.connect(
                                    device,
                                    BluetoothConstants.APP_UUID,
                                    object : BluetoothService.ConnectionCallback {
                                        override fun onResult(success: Boolean) {
                                            runOnUiThread {
                                                isConnecting = false
                                                if (success) {
                                                    Toast.makeText(this@BluetoothActivity, "Connected to $name", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(this@BluetoothActivity, "Failed to connect", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                )
                                unregisterReceiver(this)
                            } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDING) {
                                Toast.makeText(
                                    this@BluetoothActivity,
                                    "Pairing failed with " + name,
                                    Toast.LENGTH_SHORT
                                ).show()
                                isConnecting = false
                                unregisterReceiver(this)
                            }
                        }
                    }
                }
                registerReceiver(
                    bondReceiver,
                    IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                )
            }
        }
    }

    // Helper to prevent duplicates
    private fun alreadyAdded(device: BluetoothDevice): Boolean {
        for (d in availableDevices) {
            if (d.getAddress() == device.getAddress()) return true
        }
        return false
    }

    // methods used during startDiscovery() to receive broadcasts from remote devices
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.getAction()
            // Test if receiver is triggered
            Log.d("ReceiverTest", "onReceive called! Action: " + action)
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device =
                    intent.getParcelableExtra<BluetoothDevice?>(BluetoothDevice.EXTRA_DEVICE)
                if (device != null && !alreadyAdded(device)) {
                    availableDevices.add(device)
                    val name = if (device.getName() != null) device.getName() else "Unknown Device"
                    availableDevicesAdapter!!.add(name + "\n" + device.getAddress())
                    availableDevicesAdapter!!.notifyDataSetChanged()
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                Toast.makeText(context, "Discovery finished", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ensureLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_LOCATION_PERMISSION
                )
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        var allGranted = true
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false
                break
            }
        }

        if (!allGranted) {
            Toast.makeText(
                this,
                "Permissions are required for Bluetooth scanning",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        }
    }



    override fun onResume() {
        super.onResume()

        adapter = BluetoothAdapter.getDefaultAdapter()
        // request user to enable bluetooth if disabled
        if (adapter != null && !adapter!!.isEnabled()) {
            val enableBt = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBt)
        }

        // Location Permission
        ensureLocationPermission()

        // Ensure Location is ON (required for scanning in Android 6.0+)
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // Android 9+
            if (!locationManager.isLocationEnabled()) {
                Toast.makeText(
                    this,
                    "Please enable Location for Bluetooth scanning",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isReceiverRegistered) {
            unregisterReceiver(receiver)
            isReceiverRegistered = false // ✅ Reset flag
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            unregisterReceiver(receiver)
            isReceiverRegistered = false // ✅ Reset flag
        }
    }

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1

    }

    // Parses lines like: TARGET,4,11
    private fun handleIncomingLine(line: String) {
        val text = line.trim()
        val parts = text.split(",").map { it.trim() }
        if (parts.isEmpty()) return

        when (parts[0].uppercase()) {
            "TARGET" -> {
                if (parts.size >= 3) {
                    val obstacleNo = parts[1].toIntOrNull()
                    val targetPart = parts[2].trim()
                    
                    if (obstacleNo != null) {
                        val targetId = if (targetPart.uppercase() == "NULL") {
                            ObstacleCatalog.NULL_TARGET_ID
                        } else {
                            targetPart.toIntOrNull()
                        }
                        
                        if (targetId != null) {
                            TargetAssignments.setTarget(obstacleNo, targetId)
                            Log.d("C9", "TARGET received -> obstacle=$obstacleNo targetId=$targetId")
                            // Notify UI components (e.g., GridMap) to refresh
                            val intent = Intent("C9_TARGET_UPDATED")
                            intent.putExtra("obstacle", obstacleNo)
                            intent.putExtra("targetId", targetId)
                            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                        }
                    }
                }
            }
        }
    }
}
