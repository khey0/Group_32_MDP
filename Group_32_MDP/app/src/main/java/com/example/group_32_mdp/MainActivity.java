package com.example.group_32_mdp;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity {

    private BluetoothService bluetoothService;
    private boolean isBound = false;

    private TextView statusText;
    private EditText messageInput;
    private Button sendBtn, bluetoothBtn;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        // binds BluetoothService methods to MainActivity for us to call its methods
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            bluetoothService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    //listens for messages from remote device
    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("BLUETOOTH_MESSAGE".equals(intent.getAction())) {
                ScrollView msgScroll = findViewById(R.id.msgScroll);
                TextView msgTxt = findViewById(R.id.msgTxt);

                String msg = intent.getStringExtra("message");
                Log.d("MainActivity", "Received: " + msg);
                if (msg != null) {
                    msgTxt.append("\nReceived: " + msg);

                    // Scroll to the bottom automatically
                    msgScroll.post(() -> msgScroll.fullScroll(View.FOCUS_DOWN));
                }
            }
        }
    };

    // for updating bluetooth connection status
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("status");
            if (status != null) {
                if (status.equals("Connected")) {
                    statusText.setText(status);
                    statusText.setTextColor(Color.GREEN);
                } else {
                    statusText.setText(status);
                    statusText.setTextColor(Color.RED);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText); // to show connection status
        messageInput = findViewById(R.id.messageInput);
        sendBtn = findViewById(R.id.sendBtn);
        bluetoothBtn = findViewById(R.id.bluetoothUIButton);

        // Start and bind to service
        Intent serviceIntent = new Intent(this, BluetoothService.class);
        startService(serviceIntent); // starts BluetoothService and keeps it running in the bg(can't call its methods)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE); //android calls onServiceConnected & allows activity to call its methods

        // Register broadcast receivers
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(messageReceiver, new IntentFilter("BLUETOOTH_MESSAGE"));
        lbm.registerReceiver(statusReceiver, new IntentFilter("BLUETOOTH_STATUS"));

        // for sending messages
        sendBtn.setOnClickListener(v -> {
            if (isBound && bluetoothService != null) {
                String message = messageInput.getText().toString();
                if (!message.isEmpty()) {
                    bluetoothService.sendMessage(message);
                    messageInput.setText(""); // clear after sending
                }
            }
        });

        bluetoothBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BluetoothActivity.class);
            startActivity(intent);
        });
    }


    // to bind to current running bluetoothService
    @Override
    protected void onStart() {
        super.onStart();
        Intent serviceIntent = new Intent(this, BluetoothService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver);
    }


}
