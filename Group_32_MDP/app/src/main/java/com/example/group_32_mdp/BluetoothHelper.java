package com.example.group_32_mdp;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

// ----------- Handles All Bluetooth Logic ----------- \\
public class BluetoothHelper {

    // Reference to the device's Bluetooth adapter (the hardware interface)
    private BluetoothAdapter bluetoothAdapter;
    private BroadcastReceiver receiver;
    private List<BluetoothDevice> foundDevices = new ArrayList<>();

    // Constructor: runs automatically when you create a BluetoothHelper object
    public BluetoothHelper() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Enables Bluetooth on the device if it's not already enabled.
     * This will show a system dialog asking the user to turn on Bluetooth.
     *
     * @param activity The Activity that calls this method (needed to show the dialog).
     */
    public void enableBluetooth(Activity activity) {
        if (bluetoothAdapter == null) {
            Toast.makeText(activity, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }

        // Android 12+ requires BLUETOOTH_CONNECT permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(activity, android.Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }

        // Request to enable Bluetooth if it is disabled
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, 1);
        }
    }

    /**
     * Start scanning for nearby Bluetooth devices.
     * @param activity The Activity that registers the receiver.
     */
    public void startScan(Activity activity) {
        // Clear previous results
        foundDevices.clear();

        // Check Bluetooth permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(activity, android.Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.BLUETOOTH_SCAN}, 2);
            return;
        }

        // Register BroadcastReceiver to receive found devices
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    foundDevices.add(device);
                    Toast.makeText(context, "Found: " + device.getName(), Toast.LENGTH_SHORT).show();
                }
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        activity.registerReceiver(receiver, filter);

        // Start discovery
        bluetoothAdapter.startDiscovery();
        Toast.makeText(activity, "Scanning for devices...", Toast.LENGTH_SHORT).show();
    }

    /**
     * Stop scanning and unregister the BroadcastReceiver.
     * @param activity The Activity that registered the receiver.
     */
    public void stopScan(Activity activity) {
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        if (receiver != null) {
            activity.unregisterReceiver(receiver);
        }
    }

    /**
     * Return list of devices found in the last scan.
     */
    public List<BluetoothDevice> getFoundDevices() {
        return foundDevices;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

}
