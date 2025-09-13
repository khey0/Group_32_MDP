package com.example.group_32_mdp

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
    // car buttons and variables
    private var setStartButton: Button? = null
    private var flButton: ImageButton? = null
    private var fButton: ImageButton? = null
    private var frButton: ImageButton? = null
    private var blButton: ImageButton? = null
    private var bButton: ImageButton? = null
    private var brButton: ImageButton? = null

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

                    if (msg.startsWith("ROBOT")) {
                        val parts = msg.split(",")
                        if (parts.size == 4) {
                            val x = parts[1].trim().toInt()
                            val y = parts[2].trim().toInt()
                            val direction = Direction.fromLetter(parts[3].trim())

                            // flip Y because GridMap’s logical origin is bottom-left
                            val flippedY = (gridMap?.rowCount ?: 20) - 1 - y   // or just 20 if fixed

                            val startCar = Car(x = x, y = flippedY, direction = direction)
                            GridData.setCar(startCar)

                            // use the GridMap instance to update the bitmap and redraw
                            gridMap?.setCarBitmap(
                                BitmapFactory.decodeResource(resources, R.drawable.f1_car)
                            )
                            gridMap?.invalidate()

                            Log.d("GridMap", "Car updated to: x=$x, y=$flippedY, dir=$direction")
                        }
                    }

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
        //Initialize Car Buttons
        setStartButton = findViewById<Button>(R.id.setStartButton)
        flButton = findViewById<ImageButton>(R.id.flButton)
        fButton = findViewById<ImageButton>(R.id.fButton)
        frButton = findViewById<ImageButton>(R.id.frButton)
        blButton = findViewById<ImageButton>(R.id.blButton)
        bButton = findViewById<ImageButton>(R.id.bButton)
        brButton = findViewById<ImageButton>(R.id.brButton)

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

        setStartButton?.setOnClickListener {
            gridMap?.enableCarPlacement()
        }
        setupDirectionButton(fButton, Car::moveForward, "f")
        setupDirectionButton(bButton, Car::moveBackward, "b")
        setupDirectionButton(flButton, Car::moveForwardLeft, "fl")
        setupDirectionButton(blButton, Car::moveBackwardLeft, "bl")
        setupDirectionButton(frButton, Car::moveForwardRight, "fr")
        setupDirectionButton(brButton, Car::moveBackwardRight, "br")
    }

    private fun setupDirectionButton(
        button: ImageButton?,
        carAction: Car.() -> Unit,
        btMessage: String? = null   // optional: pass null if no Bluetooth
    ) {
        button?.setOnClickListener {
            val car = GridData.getCar()
            if (car != null) {
                // Move the car locally
                car.carAction()
                gridMap?.invalidate()

                // Send Bluetooth message if connected and provided
                if (btMessage != null && isBound && bluetoothService != null) {
                    bluetoothService!!.sendMessage(btMessage)
                }
            } else {
                Toast.makeText(this, "Place the car first!", Toast.LENGTH_SHORT).show()
            }
        }
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
