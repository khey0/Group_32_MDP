package com.example.group_32_mdp;

import android.app.Service;
import android.bluetooth.BluetoothAdapter; //(query for paired devices/create BluetoothServerSocket/instantiate BluetoothDevice)
import android.bluetooth.BluetoothDevice; //to request a connection/information about a device with a remote device thru BluetoothSocket
import android.bluetooth.BluetoothSocket; //connection point for exchanging data using InputStream and OutputStream
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;

import android.widget.Toast;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;



public class BluetoothService extends Service {
    private BluetoothSocket socket; // represents connection to a remote Bluetooth device
    private InputStream in;
    private OutputStream out;

    private BluetoothDevice lastDevice;
    private UUID lastUUID;
    private final IBinder binder = new LocalBinder(); // allows activities to bind to this service & call its public methods

    // provides reference to this service when an activity binds(activity can call its methods)
    public class LocalBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    //used to call onServiceConnected in the activity(returns LocalBinder)
    @Override
    public IBinder onBind(Intent intent) {
        Log.d("BluetoothService", "onBind called!");
        return binder;
    }

    //for service to tell activity whether connecting to device succeeded/failed asynchronously
    public interface ConnectionCallback {
        void onResult(boolean success);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Service gets recreated automatically if killed
    }

    /// connect to a remote device and runs on a separate thread
    /// 1.create RfcommSocket to device using the UUID
    /// 2. connect the socket
    /// 3. get input/output streams for reading/writing messages
    /// 4. returns true is successful
    /// 5. start listenForMessages to receive data asynchronously///
    public void connect(BluetoothDevice device, UUID uuid, ConnectionCallback callback) {
        lastDevice = device;
        lastUUID = uuid;
        new Thread(() -> {
            try {
                // Close old socket before making a new one (for reconnections)
                if (socket != null) {
                    try { socket.close(); } catch (IOException ignored) {}
                    socket = null;
                }
                // First attempt: normal UUID-based connection
                socket = device.createRfcommSocketToServiceRecord(uuid);
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                socket.connect();
            } catch (IOException e) {
                // If normal connect fails, try fallback
                try {
                    socket = createFallbackSocket(device);
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    socket.connect();
                } catch (Exception ex) {
                    // Both attempts failed
                    callback.onResult(false);
                    return;
                }
            }

            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
                callback.onResult(true);
                sendStatusBroadcast("Connected");
                listenForMessages();
            } catch (IOException e) {
                callback.onResult(false);
                sendStatusBroadcast("Disconnected");
            }
        }).start();
    }

    //Actively listens for messages from connected device
    // also in charge of reconnection if connection is lost
    private void listenForMessages() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            try {// continously runs while there is a connection and stream has not ended
                while (socket != null && socket.isConnected() && (bytes = in.read(buffer)) != -1) {
                    String msg = new String(buffer, 0, bytes); //converts bytes to string

                    Log.d("BluetoothActivity", "Received: " + msg);
                    // Broadcast message to activity
                    Intent intent = new Intent("BLUETOOTH_MESSAGE");
                    intent.putExtra("message", msg); //msg from remote device
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                }
            } catch (IOException e) { // to handle connection loss
                Log.d("BluetoothService", "Connection lost: " + e.getMessage());
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    Toast.makeText(BluetoothService.this, "Connection lost", Toast.LENGTH_SHORT).show();
                });

                if (socket != null) {
                    try { socket.close(); } catch (IOException ignored) {}
                }
                socket = null;
                sendStatusBroadcast("Disconnected");

                // Attempt reconnect automatically
                if (lastDevice != null && lastUUID != null) {
                    Log.d("BluetoothService", "Attempting automatic reconnect...");
                    connect(lastDevice, lastUUID, success -> {
                        if (success) Log.d("BluetoothService", "Reconnected automatically");
                        else {
                            Log.d("BluetoothService", "Reconnect failed");
                            sendStatusBroadcast("Reconnect failed");
                        }
                    });
                }

            }
        }).start();
    }

    public void sendMessage(String message) {
        if (socket != null && socket.isConnected()) {
            new Thread(() -> {
                try { out.write(message.getBytes()); } catch (IOException ignored) {}
            }).start();
        }
    }

    public void disconnect() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        socket = null;
        sendStatusBroadcast("Disconnected");
    }

    private BluetoothSocket createFallbackSocket(BluetoothDevice device) throws Exception {
        return (BluetoothSocket) device.getClass()
                .getMethod("createRfcommSocket", int.class)
                .invoke(device, 1);
    }

    // for sending bluetooth connection status
    private void sendStatusBroadcast(String status) {
        Intent intent = new Intent("BLUETOOTH_STATUS");
        intent.putExtra("status", status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
            socket = null;
        }
    }
}
