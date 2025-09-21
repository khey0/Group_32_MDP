package com.example.group_32_mdp

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.Switch
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.appcompat.app.AlertDialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.group_32_mdp.BluetoothService.LocalBinder

class MainActivity : AppCompatActivity(), GridMap.ObstacleInteractionListener, EditObstacleDialog.OnObstacleUpdatedListener {

    private var bluetoothService: BluetoothService? = null
    private var isBound = false
    private var statusText: TextView? = null
    private var robotStatusText: TextView? = null
    private var coordinatesStatusText: TextView? = null
    private var directionStatusText: TextView? = null
    private var messageInput: EditText? = null
    private var sendBtn: Button? = null

    private var bluetoothBtn: Button? = null
    private var gridMap: GridMap? = null  // Reference to GridMap

    //Task buttons and timer variables
    private var task1Button: Button? = null
    private var task2Button: Button? = null
    private lateinit var task1Chronometer: Chronometer
    private lateinit var task2Chronometer: Chronometer
    private var task1Running = false
    private var task2Running = false


    // Obstacle controls
    private var obstacleIcon: ImageView? = null
    private var editObstacleToggle: Switch? = null
    private var dragObstacleToggle: Switch? = null
    private var isObstaclePlacementActive: Boolean = false
    private var sendObstacleInfoButton: Button? = null
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
                        val parts = msg.split(",") // split message by comma
                        if (parts.size == 4) {
                            try {
                                val map = gridMap ?: return
                                val maxX = map.getColCount() - 2
                                val maxY = map.getRowCount() - 1
                                var x = parts[1].trim().toInt()
                                var y = parts[2].trim().toInt()
                                val direction = Direction.fromLetter(parts[3].trim())

                                if (x < 0) x = 0
                                if (x > maxX) x = maxX
                                if (y < 1) y = 1
                                if (y > maxY) y = maxY

                                val startCar = Car(x = x, y = y, direction = direction)
                                GridData.setCar(startCar)

                                gridMap?.setCarBitmap(
                                    BitmapFactory.decodeResource(resources, R.drawable.f1_car)
                                )
                                gridMap?.invalidate()

                                Log.d("GridMap", "Car updated to: x=$x, y=$y, dir=$direction")
                            } catch (e: Exception) {
                                // Catch NumberFormatException or any unexpected parsing error
                                Log.w("GridMap", "Invalid ROBOT message: $msg", e)
                            }
                        } else {
                            Log.w("GridMap", "Unexpected ROBOT format: $msg")
                        }
                    }
                    
                    // Handle TARGET message: "TARGET, <Obstacle Number>, <Target ID>"
                    if (msg.startsWith("TARGET")) {
                        val parts = msg.split(",")
                        if (parts.size == 3) {
                            try {
                                val obstacleNumber = parts[1].trim().toInt()
                                val targetId = parts[2].trim().toInt()
                                
                                // Set target ID for the obstacle
                                GridData.setTargetIdForObstacle(obstacleNumber, targetId)
                                
                                // Refresh the grid display
                                gridMap?.invalidate()
                                
                                Log.d("MainActivity", "Set target ID $targetId for obstacle $obstacleNumber")
                            } catch (e: Exception) {
                                Log.w("MainActivity", "Invalid TARGET message: $msg", e)
                            }
                        } else {
                            Log.w("MainActivity", "Unexpected TARGET format: $msg")
                        }
                    }



                }
            }
        }
    }

    // for updating bluetooth connection status
    private var reconnectDialog: AlertDialog? = null  // store reference so we can dismiss
    private val statusReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val status = intent.getStringExtra("status") ?: return

            when (status) {
                "Connected" -> {
                    reconnectDialog?.dismiss()
                    statusText?.text = status
                    statusText?.setTextColor(Color.GREEN)
                }
                "Waiting for Bluetooth device to reconnect" -> {
                    // Show blocking dialog
                    if (reconnectDialog == null || reconnectDialog?.isShowing == false) {
                        reconnectDialog = AlertDialog.Builder(this@MainActivity)
                            .setTitle("Bluetooth Disconnected")
                            .setMessage("Waiting for Bluetooth device to reconnect...")
                            .setCancelable(false) // blocks user interaction
                            .create()
                        reconnectDialog?.show()
                    }
                    statusText?.text = status
                    statusText?.setTextColor(Color.YELLOW)
                }
                "Disconnected" -> {
                    reconnectDialog?.dismiss()
                    statusText?.text = status
                    statusText?.setTextColor(Color.RED)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkBluetoothPermissions()

        // Initialize GridMap view
        gridMap = findViewById(R.id.gridMap)
        gridMap?.setObstacleInteractionListener(this)

        // Initialize statusText
        statusText = findViewById(R.id.statusText)
        robotStatusText = findViewById(R.id.robotStatus)
        coordinatesStatusText = findViewById(R.id.coordinatesStatus)
        directionStatusText = findViewById(R.id.directionStatus)
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

        // Initialize obstacle controls
        obstacleIcon = findViewById(R.id.obstacleIcon)
        editObstacleToggle = findViewById(R.id.editObstacleToggle)
        dragObstacleToggle = findViewById(R.id.dragObstacleToggle)
        sendObstacleInfoButton = findViewById(R.id.sendObstacleInfoButton)

        //Initialize Task buttons and timers
        task1Button = findViewById(R.id.task1Button)
        task2Button = findViewById(R.id.task2Button)
        task1Chronometer = findViewById(R.id.task1Chronometer)
        task2Chronometer = findViewById(R.id.task2Chronometer)



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

        // Obstacle icon click listener (toggle placement mode)
        obstacleIcon?.setOnClickListener {
            if (!isObstaclePlacementActive) {
                // Turn ON placement mode
                gridMap?.setObstacleMode(true)
                isObstaclePlacementActive = true
                obstacleIcon?.alpha = 0.5f
                android.widget.Toast.makeText(this, "Please plot obstacles", android.widget.Toast.LENGTH_SHORT).show()
                // turn off other modes
                editObstacleToggle?.isChecked = false
                dragObstacleToggle?.isChecked = false

                // Turn OFF car placement if active
                if (gridMap?.isPlacingCar() == true) {
                    gridMap?.disableCarPlacement()
                }
                setStartButton?.alpha = 1.0f

            } else {
                // Turn OFF placement mode
                gridMap?.setObstacleMode(false)
                isObstaclePlacementActive = false
                obstacleIcon?.alpha = 1.0f
            }
        }

        // Edit obstacle switch
        editObstacleToggle?.setOnCheckedChangeListener { _, isChecked ->
            // Leaving placement mode if switching to edit
            if (isChecked) {
                isObstaclePlacementActive = false
                obstacleIcon?.alpha = 1.0f
                gridMap?.setEditMode(true)
                // toggle off drag
                if (dragObstacleToggle?.isChecked == true) dragObstacleToggle?.isChecked = false
                android.widget.Toast.makeText(this, "edit obstacle is on", android.widget.Toast.LENGTH_SHORT).show()

                // Turn OFF car placement if active
                if (gridMap?.isPlacingCar() == true) {
                    gridMap?.disableCarPlacement()
                }
                setStartButton?.alpha = 1.0f

            } else {
                gridMap?.setEditMode(false)
                android.widget.Toast.makeText(this, "edit obstacle is off", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        gridMap?.onCarPlacedListener = {
            runOnUiThread {
                setStartButton?.alpha = 1.0f
            }
        }
        gridMap?.onCarUpdated = { x, y, direction ->
            runOnUiThread {
                coordinatesStatusText!!.text = "Coordinates: ($x, $y)"
                directionStatusText!!.text = "Direction: $direction"
            }
        }


        setStartButton?.setOnClickListener {
            val currentlyPlacing = gridMap?.isPlacingCar() == true
            if (currentlyPlacing) {
                gridMap?.disableCarPlacement()
            } else {
                gridMap?.enableCarPlacement()
                // turn off other modes
                editObstacleToggle?.isChecked = false
                dragObstacleToggle?.isChecked = false
                isObstaclePlacementActive = false
                obstacleIcon?.alpha = 1.0f
            }

            val nowPlacing = gridMap?.isPlacingCar() == true
            setStartButton?.alpha = if (nowPlacing) 0.5f else 1.0f
        }


        setupDirectionButton(fButton, Car::moveForward, "f")
        setupDirectionButton(bButton, Car::moveBackward, "b")
        setupDirectionButton(flButton, Car::moveForwardLeft, "fl")
        setupDirectionButton(blButton, Car::moveBackwardLeft, "bl")
        setupDirectionButton(frButton, Car::moveForwardRight, "fr")
        setupDirectionButton(brButton, Car::moveBackwardRight, "br")


        // Drag obstacle switch
        dragObstacleToggle?.setOnCheckedChangeListener { _, isChecked ->
            // Leaving placement mode if switching to drag
            if (isChecked) {
                isObstaclePlacementActive = false
                obstacleIcon?.alpha = 1.0f
                gridMap?.setDragMode(true)
                // toggle off edit
                if (editObstacleToggle?.isChecked == true) editObstacleToggle?.isChecked = false
                android.widget.Toast.makeText(this, "drag obstacle is on", android.widget.Toast.LENGTH_SHORT).show()

                // Turn OFF car placement if active
                if (gridMap?.isPlacingCar() == true) {
                    gridMap?.disableCarPlacement()
                }
                setStartButton?.alpha = 1.0f

            } else {
                gridMap?.setDragMode(false)
                android.widget.Toast.makeText(this, "drag obstacle is off", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        sendObstacleInfoButton?.setOnClickListener {
            if (isBound && bluetoothService != null) {
                // Get formatted obstacle string
                val obstacleData = GridData.getObstaclesFormattedString()

                // Send via your Bluetooth service
                bluetoothService!!.sendMessage(obstacleData)
            }
        }

        // Task 1 timer button
        task1Button?.setOnClickListener {
            if (!task1Running) {
                task1Chronometer.base = SystemClock.elapsedRealtime() // reset to 0
                task1Chronometer.start()
                task1Running = true
                task1Button!!.text = "Stop Task 1"
                robotStatusText?.text = "Task 1 Stopped"
            } else {
                task1Chronometer.stop()
                task1Running = false
                task1Button!!.text = "Start Task 1"
                robotStatusText?.text = "Task 1 Started"
            }
        }

        // Task 2 button
        task2Button?.setOnClickListener {
            if (!task2Running) {
                task2Chronometer.base = SystemClock.elapsedRealtime() // reset to 0
                task2Chronometer.start()
                task2Running = true
                task2Button!!.text = "Stop Task 2"
                robotStatusText?.text = "Task 2 Stopped"
            } else {
                task2Chronometer.stop()
                task2Running = false
                task2Button!!.text = "Start Task 2"
                robotStatusText?.text = "Task 2 Started"
            }
        }
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

    // Obstacle interaction callbacks
    override fun onObstacleCreated(obstacle: Obstacle) {
        // Send obstacle data via Bluetooth
        sendObstacleData(obstacle)
    }

    override fun onObstacleMoved(obstacle: Obstacle) {
        // Send updated obstacle data via Bluetooth
        sendObstacleData(obstacle)
    }

    override fun onObstacleEditRequested(obstacle: Obstacle) {
        // Show edit dialog
        val dialog = EditObstacleDialog.newInstance(obstacle)
        dialog.show(supportFragmentManager, "EditObstacleDialog")
    }

    override fun onObstacleUpdated(obstacle: Obstacle) {
        // Update the obstacle in the grid
        gridMap?.updateObstacle(obstacle)
        // Send updated data via Bluetooth
        sendObstacleData(obstacle)
    }

    private fun sendObstacleData(obstacle: Obstacle) {
        if (isBound && bluetoothService != null) {
            val message = "OBSTACLE,${obstacle.id},${obstacle.x},${obstacle.y},${obstacle.direction.name}"
            bluetoothService?.sendMessage(message)
        }
    }

    fun updateRobotStatus(status: String) {
        runOnUiThread {
            robotStatusText?.text = "Status: $status"
        }
    }

    fun updateCoordinatesStatus(x: Int, y: Int) {
        runOnUiThread {
            coordinatesStatusText?.text = "Coordinates: ($x, $y)"
        }
    }

    fun updateDirectionStatus(direction: String) {
        runOnUiThread {
            directionStatusText?.text = "Direction: $direction"
        }
    }

    private val REQUESTBLUETOOTHPERMISSIONS = 100

    private fun checkBluetoothPermissions() {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT),
                REQUESTBLUETOOTHPERMISSIONS
            )
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUESTBLUETOOTHPERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }



}
