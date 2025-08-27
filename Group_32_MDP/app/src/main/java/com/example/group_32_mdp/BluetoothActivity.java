package com.example.group_32_mdp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Build;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import android.util.Log;
import java.util.List;

public class BluetoothActivity extends AppCompatActivity {

    private BluetoothService bluetoothService;
    private boolean isBound = false;//check if bound to BluetoothService
    private boolean isConnecting = false;
    private boolean isScanning = false; // Track BLE scan state
    private boolean isReceiverRegistered = false; // for scanning of bluetooth devices

    private ListView pairedListView,availableDevicesList;
    private Button scanButton, disconnectBtn;
    private BluetoothAdapter adapter;
    private ArrayList<BluetoothDevice> availableDevices = new ArrayList<>();
    private ArrayAdapter<String> availableDevicesAdapter;
    private static final int REQUEST_LOCATION_PERMISSION = 1;

    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            bluetoothService = binder.getService();
            isBound = true;

            adapter = BluetoothAdapter.getDefaultAdapter();
            //check if there is bluetooth on the device
            if (adapter == null) {
                Toast.makeText(BluetoothActivity.this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(BluetoothActivity.this, MainActivity.class);
                startActivity(intent);
            }


            // Enable buttons now that service is ready
            pairedListView.setEnabled(true);
            availableDevicesList.setEnabled(true);
            scanButton.setEnabled(true);
            disconnectBtn.setEnabled(true);

            //Toast.makeText(BluetoothActivity.this, "Bluetooth Service Connected", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        pairedListView = findViewById(R.id.paired_devices);
        availableDevicesList = findViewById(R.id.available_devices);
        scanButton = findViewById(R.id.scan_button);
        disconnectBtn = findViewById(R.id.disconnectBtn);
        pairedListView.setEnabled(false);
        availableDevicesList.setEnabled(false);
        scanButton.setEnabled(false);
        disconnectBtn.setEnabled(false);

        // Start and bind the service
        Intent serviceIntent = new Intent(this, BluetoothService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        //request permissions required for bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 1);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            }
        }

        scanButton.setOnClickListener(v -> {
            if (!isBound) {
                Toast.makeText(this, "Bluetooth service not ready", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show devices now that service is ready
            showPairedDevices();
            discoverAvailableDevices();
        });



        disconnectBtn.setOnClickListener(v -> {
            if (isBound && bluetoothService != null) {
                bluetoothService.disconnect();
                Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPairedDevices() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
            BluetoothDevice[] devices = pairedDevices.toArray(new BluetoothDevice[0]);
            String[] deviceNames = pairedDevices.stream().map(BluetoothDevice::getName).toArray(String[]::new);

            pairedListView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames));

            pairedListView.setOnItemClickListener((parent, view, position, id) -> {
                BluetoothDevice selectedDevice = devices[position];

                if (!isBound || bluetoothService == null) {
                    Toast.makeText(this, "Bluetooth service not ready yet", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (isConnecting) {
                    Toast.makeText(this, "Already connecting to a device", Toast.LENGTH_SHORT).show();
                    return;
                }

                isConnecting = true;
                Toast.makeText(this, "Connecting to " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();

                adapter.cancelDiscovery();
                bluetoothService.disconnect();

                // Start connection asynchronously
                bluetoothService.connect(selectedDevice, MY_UUID, success -> runOnUiThread(() -> {
                    isConnecting = false; // reset connection flag
                    if (success) {
                        Toast.makeText(this, "Connected to " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to connect", Toast.LENGTH_SHORT).show();
                    }
                }));
            });
        }
    }

    private void discoverAvailableDevices() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;
        Log.d("discoverAvailableDevices", "Discovering available devices...");

        // Clear previous devices
        availableDevices.clear();
        availableDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        availableDevicesList.setAdapter(availableDevicesAdapter);

        // Ensure location is enabled (required for discovery)
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!locationManager.isLocationEnabled()) {
                Toast.makeText(this, "Enable Location for discovery to work", Toast.LENGTH_LONG).show();
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                return;
            }
        }


        // Register receiver
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            registerReceiver(receiver, filter);
            isReceiverRegistered = true;

            Log.d("ReceiverTest", "Receiver Registered!");
        }

        // Start discovery
        if (adapter.isDiscovering()) adapter.cancelDiscovery();
        boolean started = adapter.startDiscovery();
        Log.d("discoverAvailableDevices", "startDiscovery returned: " + started);
        Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show();

        // Click listener to connect or just show info
        availableDevicesList.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = availableDevices.get(position);
            String name = device.getName() != null ? device.getName() : "Unknown Device";

            if (!isBound || bluetoothService == null) {
                Toast.makeText(this, "Bluetooth service not ready", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isConnecting) {
                Toast.makeText(this, "Already connecting to a device", Toast.LENGTH_SHORT).show();
                return;
            }

            isConnecting = true;
            adapter.cancelDiscovery(); // stop scanning to avoid interference
            bluetoothService.disconnect();

            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                // Already paired → connect directly
                bluetoothService.connect(device, MY_UUID, success -> runOnUiThread(() -> {
                    isConnecting = false;
                    if (success) {
                        Toast.makeText(this, "Connected to " + name, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to connect", Toast.LENGTH_SHORT).show();
                    }
                }));
            } else {
                // Not paired → initiate pairing
                Toast.makeText(this, "Pairing with " + name, Toast.LENGTH_SHORT).show();
                device.createBond();

                // Listen for bonding state changes in a BroadcastReceiver
                BroadcastReceiver bondReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                            int prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                            if (state == BluetoothDevice.BOND_BONDED) {
                                Toast.makeText(BluetoothActivity.this, "Paired with " + name, Toast.LENGTH_SHORT).show();
                                // Connect after pairing
                                bluetoothService.connect(device, MY_UUID, success -> runOnUiThread(() -> {
                                    isConnecting = false;
                                    if (success) {
                                        Toast.makeText(BluetoothActivity.this, "Connected to " + name, Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(BluetoothActivity.this, "Failed to connect", Toast.LENGTH_SHORT).show();
                                    }
                                }));
                                unregisterReceiver(this);
                            } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDING) {
                                Toast.makeText(BluetoothActivity.this, "Pairing failed with " + name, Toast.LENGTH_SHORT).show();
                                isConnecting = false;
                                unregisterReceiver(this);
                            }
                        }
                    }
                };
                registerReceiver(bondReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
            }
        });
    }

    // Helper to prevent duplicates
    private boolean alreadyAdded(BluetoothDevice device) {
        for (BluetoothDevice d : availableDevices) {
            if (d.getAddress().equals(device.getAddress())) return true;
        }
        return false;
    }

    // methods used during startDiscovery() to receive broadcasts from remote devices
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // Test if receiver is triggered
            Log.d("ReceiverTest", "onReceive called! Action: " + action);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && !alreadyAdded(device)) {
                    availableDevices.add(device);
                    String name = device.getName() != null ? device.getName() : "Unknown Device";
                    availableDevicesAdapter.add(name + "\n" + device.getAddress());
                    availableDevicesAdapter.notifyDataSetChanged();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Toast.makeText(context, "Discovery finished", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private void ensureLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_LOCATION_PERMISSION
                );
            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            Toast.makeText(this, "Permissions are required for Bluetooth scanning", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
        }
    }




    @Override
    protected void onResume() {
        super.onResume();

        adapter = BluetoothAdapter.getDefaultAdapter();
        // request user to enable bluetooth if disabled
        if (adapter != null && !adapter.isEnabled()) {
            Intent enableBt = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBt);
        }

        // Location Permission
        ensureLocationPermission();

        // Ensure Location is ON (required for scanning in Android 6.0+)
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // Android 9+
            if (!locationManager.isLocationEnabled()) {
                Toast.makeText(this, "Please enable Location for Bluetooth scanning", Toast.LENGTH_LONG).show();
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (isReceiverRegistered) {
            unregisterReceiver(receiver);
            isReceiverRegistered = false; // ✅ Reset flag
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isReceiverRegistered) {
            unregisterReceiver(receiver);
            isReceiverRegistered = false; // ✅ Reset flag
        }
    }
}
