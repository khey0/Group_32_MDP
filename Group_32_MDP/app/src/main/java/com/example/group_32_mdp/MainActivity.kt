package com.example.group_32_mdp

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.group_32_mdp.BluetoothService.LocalBinder

class MainActivity : AppCompatActivity() {

    private var bluetoothService: BluetoothService? = null
    private var isBound = false
    private var statusText: TextView? = null  // Reference to statusText
    private var bluetoothBtn: Button? = null
    private var gridMap: GridMap? = null  // Reference to GridMap

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        // Binds BluetoothService methods to MainActivity for us to call its methods
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocalBinder
            bluetoothService = binder.service
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    // Listens for messages from the remote device
    private val statusReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val status = intent.getStringExtra("status")
            if (status != null) {
                statusText!!.text = status
                statusText!!.setTextColor(if (status == "Connected") Color.GREEN else Color.RED)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize GridMap view
        gridMap = findViewById(R.id.gridMap)

        // Initialize statusText
        statusText = findViewById(R.id.statusText)

        bluetoothBtn = findViewById(R.id.bluetoothUIButton)

        // Start service (binding happens in onStart)
        val serviceIntent = Intent(this, BluetoothService::class.java)
        startService(serviceIntent)

        // Bluetooth Button
        bluetoothBtn!!.setOnClickListener(View.OnClickListener { v: View? ->
            val intent = Intent(this@MainActivity, BluetoothActivity::class.java)
            startActivity(intent)
        })
    }

    // to bind to current running bluetoothService
    override fun onStart() {
        super.onStart()
        val serviceIntent = Intent(this, BluetoothService::class.java)
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
        LocalBroadcastManager.getInstance(this).registerReceiver(
            statusReceiver,
            IntentFilter("BLUETOOTH_STATUS")
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
    }
}
