package com.example.group_32_mdp;

import android.bluetooth.*;
import android.content.*;
import android.os.*;
import android.widget.*;

import androidx.appcompat.app.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.*;

// ----------- Handles Bluetooth UI ----------- \\

public class BluetoothActivity extends AppCompatActivity {

    private BluetoothHelper btHelper;
    private Button scanBtn;
    private ActivityResultLauncher<Intent> enableBluetoothLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        btHelper = new BluetoothHelper();
        scanBtn = findViewById(R.id.bluetoothConnectButton);

        // Initialize the launcher
        enableBluetoothLauncher =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (btHelper.isBluetoothEnabled()) {
                        btHelper.startScan(this);
                        scanBtn.setText(getString(R.string.scanning));
                    } else {
                        Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show();
                    }
                });

        // Set up the button click
        scanBtn.setOnClickListener(v -> {
            if (!btHelper.isBluetoothEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                enableBluetoothLauncher.launch(enableBtIntent);
            } else {
                btHelper.startScan(this);
                scanBtn.setText(getString(R.string.scanning));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        btHelper.stopScan(this); // clean up receiver
    }

}
