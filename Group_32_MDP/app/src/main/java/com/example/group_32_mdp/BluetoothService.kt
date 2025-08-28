package com.example.group_32_mdp

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

//(query for paired devices/create BluetoothServerSocket/instantiate BluetoothDevice)
//to request a connection/information about a device with a remote device thru BluetoothSocket
//connection point for exchanging data using InputStream and OutputStream
class BluetoothService : Service() {
    private var socket: BluetoothSocket? =
        null // represents connection to a remote Bluetooth device
    private var `in`: InputStream? = null
    private var out: OutputStream? = null

    private var lastDevice: BluetoothDevice? = null
    private var lastUUID: UUID? = null
    private val binder: IBinder =
        LocalBinder() // allows activities to bind to this service & call its public methods

    // provides reference to this service when an activity binds(activity can call its methods)
    inner class LocalBinder : Binder() {
        val service: BluetoothService
            get() = this@BluetoothService
    }

    //used to call onServiceConnected in the activity(returns LocalBinder)
    override fun onBind(intent: Intent?): IBinder {
        Log.d("BluetoothService", "onBind called!")
        return binder
    }

    //for service to tell activity whether connecting to device succeeded/failed asynchronously
    fun interface ConnectionCallback {
        fun onResult(success: Boolean)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Service gets recreated automatically if killed
    }

    /** connect to a remote device and runs on a separate thread
     * 1.create RfcommSocket to device using the UUID
     * 2. connect the socket
     * 3. get input/output streams for reading/writing messages
     * 4. returns true is successful
     * 5. start listenForMessages to receive data asynchronously/// */
    fun connect(device: BluetoothDevice, uuid: UUID?, callback: ConnectionCallback) {
        lastDevice = device
        lastUUID = uuid
        Thread(Runnable {
            try {
                // Close old socket before making a new one (for reconnections)
                if (socket != null) {
                    try {
                        socket!!.close()
                    } catch (ignored: IOException) {
                    }
                    socket = null
                }
                // First attempt: normal UUID-based connection
                socket = device.createRfcommSocketToServiceRecord(uuid)
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                socket!!.connect()
            } catch (e: IOException) {
                // If normal connect fails, try fallback
                try {
                    socket = createFallbackSocket(device)
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    socket!!.connect()
                } catch (ex: Exception) {
                    // Both attempts failed
                    callback.onResult(false)
                    return@Runnable
                }
            }
            try {
                `in` = socket!!.getInputStream()
                out = socket!!.getOutputStream()
                callback.onResult(true)
                sendStatusBroadcast("Connected")
                listenForMessages()
            } catch (e: IOException) {
                callback.onResult(false)
                sendStatusBroadcast("Disconnected")
            }
        }).start()
    }

    //Actively listens for messages from connected device
    // also in charge of reconnection if connection is lost
    private fun listenForMessages() {
        Thread(Runnable {
            val buffer = ByteArray(1024)
            var bytes = 0
            try { // continously runs while there is a connection and stream has not ended
                while (socket != null && socket!!.isConnected() && (`in`!!.read(buffer)
                        .also { bytes = it }) != -1
                ) {
                    val msg = String(buffer, 0, bytes) //converts bytes to string

                    Log.d("BluetoothActivity", "Received: " + msg)
                    // Broadcast message to activity
                    val intent = Intent("BLUETOOTH_MESSAGE")
                    intent.putExtra("message", msg) //msg from remote device
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                }
            } catch (e: IOException) { // to handle connection loss
                Log.d("BluetoothService", "Connection lost: " + e.message)
                Handler(Looper.getMainLooper()).post(Runnable {
                    Toast.makeText(this@BluetoothService, "Connection lost", Toast.LENGTH_SHORT)
                        .show()
                })

                if (socket != null) {
                    try {
                        socket!!.close()
                    } catch (ignored: IOException) {
                    }
                }
                socket = null
                sendStatusBroadcast("Disconnected")

                // Attempt reconnect automatically
                if (lastDevice != null && lastUUID != null) {
                    Log.d("BluetoothService", "Attempting automatic reconnect...")
                    connect(lastDevice!!, lastUUID) { success ->
                        if (success) Log.d("BluetoothService", "Reconnected automatically")
                        else {
                            Log.d("BluetoothService", "Reconnect failed")
                            sendStatusBroadcast("Reconnect failed")
                        }
                    }

                }
            }
        }).start()
    }

    fun sendMessage(message: String) {
        if (socket != null && socket!!.isConnected()) {
            Thread(Runnable {
                try {
                    out!!.write(message.toByteArray())
                } catch (ignored: IOException) {
                }
            }).start()
        }
    }

    fun disconnect() {
        try {
            if (socket != null) socket!!.close()
        } catch (ignored: IOException) {
        }
        socket = null
        sendStatusBroadcast("Disconnected")
    }

    @Throws(Exception::class)
    private fun createFallbackSocket(device: BluetoothDevice): BluetoothSocket? {
        return device.javaClass
            .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            .invoke(device, 1) as BluetoothSocket?
    }

    // for sending bluetooth connection status
    private fun sendStatusBroadcast(status: String?) {
        val intent = Intent("BLUETOOTH_STATUS")
        intent.putExtra("status", status)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    override fun onDestroy() {
        super.onDestroy()
        if (socket != null) {
            try {
                socket!!.close()
            } catch (ignored: IOException) {
            }
            socket = null
        }
    }
}
