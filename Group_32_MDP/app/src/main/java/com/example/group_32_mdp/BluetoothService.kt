package com.example.group_32_mdp

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private var socket: BluetoothSocket? = null // represents connection to a remote Bluetooth device

    private var serverSocket: BluetoothServerSocket? = null
    private var `in`: InputStream? = null
    private var out: OutputStream? = null

    private var manualDisconnect = false //shows if we disconnect on our own
    private var remoteDisconnected = false


    private var lastDevice: BluetoothDevice? = null
    private var lastUUID: UUID? = null
    private val binder: IBinder =
        LocalBinder() // allows activities to bind to this service & call its public methods

    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val reconnectRunnable = object : Runnable {
        override fun run() {
            if (lastDevice != null && lastUUID != null) {
                Log.d("BluetoothService", "Auto-reconnect attempt…")
                connect(lastDevice!!, lastUUID) { success ->
                    if (!success) {
                        Log.d("BluetoothService", "Reconnect failed, retrying in 5s")
                        reconnectHandler.postDelayed(this, 500)
                    } else {
                        Log.d("BluetoothService", "Auto-reconnect succeeded")
                    }
                }
            }
        }
    }


    private fun startServer(uuid: UUID) {
        Thread {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("MyAppServer", uuid)
                while (true) {
                    val incomingSocket = serverSocket?.accept() // blocking call
                    if (incomingSocket != null) {
                        socket = incomingSocket
                        `in` = socket!!.inputStream
                        out = socket!!.outputStream
                        remoteDisconnected = false
                        sendStatusBroadcast("Connected")
                        listenForMessages()
                    }
                }
            } catch (e: IOException) {
                Log.d("BluetoothService", "Server socket error: ${e.message}")
            }
        }.start()
    }


    private val aclReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            if (device != null && device == lastDevice) {
                Log.d("BluetoothService", "ACL disconnected for ${device.address}")
                handleRemoteDisconnect()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(aclReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        })
        // Start listening for incoming connections
        startServer(BluetoothConstants.APP_UUID)
    }




    private fun handleRemoteDisconnect() {
        if (manualDisconnect) return   // don’t act if we disconnected ourselves
        remoteDisconnected = true      // mark that the remote ended the connection
        sendStatusBroadcast("Waiting for Bluetooth device to reconnect")
        // No auto-reconnect here – the server socket will wait for the remote device
    }




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
                manualDisconnect = false  // reset flag on attempt
                // Close old socket before making a new one (for reconnections)
                if (socket != null) {
                    try {
                        socket!!.close()
                    } catch (ignored: IOException) {
                    }
                    socket = null
                }
                socket = if (device.address == "AA:AA:AA:AA:AA:AA") {
                    // RPi special case → use reflection on port 1
                    val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    m.invoke(device, 1) as BluetoothSocket
                } else {
                    // Default case → use UUID-based socket (works with Android Module Tool, others)
                    device.createRfcommSocketToServiceRecord(uuid)
                }
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
            var bytes: Int = 0  // initialize here
            try {
                while (socket != null && socket!!.isConnected() && (`in`!!.read(buffer).also { bytes = it }) != -1) {
                    val msg = String(buffer, 0, bytes)
                    Log.d("BluetoothActivity", "Received: $msg")

                    // Broadcast message to activity
                    val intent = Intent("BLUETOOTH_MESSAGE")
                    intent.putExtra("message", msg)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                }
            } catch (e: IOException) {
                Log.d("BluetoothService", "Connection lost: ${e.message}")

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@BluetoothService, "Connection lost", Toast.LENGTH_SHORT).show()
                }

                // Clean up socket
                try { socket?.close() } catch (ignored: IOException) {}
                socket = null
                sendStatusBroadcast("Disconnected")

                if (!manualDisconnect && !remoteDisconnected) {
                    // Only auto-reconnect if not manual or remote disconnect
                    handleRemoteDisconnect() // this could be renamed to handleErrorDisconnect()
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
        manualDisconnect = true  // mark that this is user-initiated
        try {
            socket?.close()
        } catch (ignored: IOException) {}
        socket = null
        sendStatusBroadcast("Disconnected")
    }

    fun setManualDisconnect(boolean: Boolean){
        manualDisconnect = boolean;
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

        // Clean up socket
        if (socket != null) {
            try { socket!!.close() } catch (ignored: IOException) {}
            socket = null
        }

        // Unregister ACL receiver
        try { unregisterReceiver(aclReceiver) } catch (ignored: IllegalArgumentException) {}
    }

}
