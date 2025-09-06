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
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.group_32_mdp.BluetoothService.LocalBinder

class MainActivity : AppCompatActivity() {

    private var bluetoothService: BluetoothService? = null
    private var isBound = false

    private var statusText: TextView? = null
    private var messageInput: EditText? = null
    private var sendBtn: Button? = null
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

    //listens for messages from remote device
    private val messageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if ("BLUETOOTH_MESSAGE" == intent.getAction()) {
                val msgScroll = findViewById<ScrollView>(R.id.msgScroll)
                val msgTxt = findViewById<TextView>(R.id.msgTxt)

                val msg = intent.getStringExtra("message")
                Log.d("MainActivity", "Received: " + msg)
                if (msg != null) {
                    msgTxt.append("\nReceived: " + msg)

                    // Scroll to the bottom automatically
                    msgScroll.post(Runnable { msgScroll.fullScroll(View.FOCUS_DOWN) })
                }
            }
        }
    }

    // for updating bluetooth connection status
    private val statusReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val status = intent.getStringExtra("status")
            if (status != null) {
                if (status == "Connected") {
                    statusText!!.setText(status)
                    statusText!!.setTextColor(Color.GREEN)
                } else {
                    statusText!!.setText(status)
                    statusText!!.setTextColor(Color.RED)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize GridMap view
        gridMap = findViewById(R.id.gridMap)

        // Initialize Bluetooth layout buttons
        statusText = findViewById<TextView>(R.id.statusText) // to show connection status
        messageInput = findViewById<EditText>(R.id.messageInput)
        sendBtn = findViewById<Button>(R.id.sendBtn)
        bluetoothBtn = findViewById<Button>(R.id.bluetoothUIButton)

        // Start service (binding happens in onStart)
        val serviceIntent = Intent(this, BluetoothService::class.java)
        startService(serviceIntent) // starts BluetoothService and keeps it running in the bg(can't call its methods)
        bindService(
            serviceIntent,
            serviceConnection,
            BIND_AUTO_CREATE
        ) //android calls onServiceConnected & allows activity to call its methods

        // Register broadcast receivers
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.registerReceiver(messageReceiver, IntentFilter("BLUETOOTH_MESSAGE"))
        lbm.registerReceiver(statusReceiver, IntentFilter("BLUETOOTH_STATUS"))

        // Set up listener for button to send messages
        sendBtn!!.setOnClickListener(View.OnClickListener { v: View? ->
            if (isBound && bluetoothService != null) {
                val message = messageInput!!.getText().toString()
                if (!message.isEmpty()) {
                    bluetoothService!!.sendMessage(message)
                    messageInput!!.setText("") // clear after sending
                }
            }
        })

        // Set up listener for Bluetooth Button
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
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
    }
}
