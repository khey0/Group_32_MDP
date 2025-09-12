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
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.group_32_mdp.BluetoothService.LocalBinder

class MainActivity : AppCompatActivity(), GridMap.ObstacleInteractionListener, EditObstacleDialog.OnObstacleUpdatedListener {

    private var bluetoothService: BluetoothService? = null
    private var isBound = false
    private var statusText: TextView? = null  // Reference to statusText
    private var bluetoothBtn: Button? = null
    private var gridMap: GridMap? = null  // Reference to GridMap
    
    // Obstacle controls
    private var obstacleIcon: ImageView? = null
    private var editObstacleToggle: Switch? = null
    private var dragObstacleToggle: Switch? = null
    private var isObstaclePlacementActive: Boolean = false

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
        gridMap?.setObstacleInteractionListener(this)

        // Initialize statusText
        statusText = findViewById(R.id.statusText)

        bluetoothBtn = findViewById(R.id.bluetoothUIButton)

        // Initialize obstacle controls
        obstacleIcon = findViewById(R.id.obstacleIcon)
        editObstacleToggle = findViewById(R.id.editObstacleToggle)
        dragObstacleToggle = findViewById(R.id.dragObstacleToggle)

        // Start service (binding happens in onStart)
        val serviceIntent = Intent(this, BluetoothService::class.java)
        startService(serviceIntent)

        // Bluetooth Button
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
            } else {
                gridMap?.setEditMode(false)
                android.widget.Toast.makeText(this, "edit obstacle is off", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

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
            } else {
                gridMap?.setDragMode(false)
                android.widget.Toast.makeText(this, "drag obstacle is off", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
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

    
}
