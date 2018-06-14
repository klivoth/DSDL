package com.example.wh433.dsdl_wallet_ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.Button;
import android.widget.ListView;
import android.widget.AdapterView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.ContentValues.TAG;


public class MainActivity extends AppCompatActivity {
    private ListView deviceListView;
    private ArrayList<String> deviceList = new ArrayList<>();
    private ArrayAdapter listAdapter;

    private LocationManager locManager;
    private BluetoothAdapter BTAdapter;
    private HashMap<String, BluetoothDevice> scanResults;
    private ScanCallback scanCallback;
    private BluetoothLeScanner bleScanner;
    private Handler scanHandler;

    private boolean scanning = false;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ENABLE_LOCATION_PERMISSION = 2;
    private static final int REQUEST_ENABLE_LOCATION = 3;
    private static final long SCAN_PERIOD = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // login screen
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);

        // BLE
        locManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        final BluetoothManager bluetoothManager =
                (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        BTAdapter = bluetoothManager.getAdapter();
        if (locManager == null || BTAdapter == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Not compatible")
                    .setMessage("Cannot access Bluetooth or location services")
                    .setPositiveButton("Exit", (dialog, which) -> System.exit(0))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }

        Button startScanBtn = findViewById(R.id.startScanBtn);
        startScanBtn.setOnClickListener(v -> startScan());
        Button stopScanBtn = findViewById(R.id.stopScanBtn);
        stopScanBtn.setOnClickListener(v -> stopScan());

        // list of scanned devices
        deviceListView = findViewById(R.id.deviceListView);
        listAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList);
        deviceListView.setAdapter(listAdapter);
        deviceListView.setOnItemClickListener((parent, view, position, id) ->
                Toast.makeText(getApplicationContext(),
                "Clicked device address: " + parent.getItemAtPosition(position).toString(),
                Toast.LENGTH_LONG)
                .show());
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            new AlertDialog.Builder(this)
                    .setTitle("Not compatible")
                    .setMessage("Your phone does not support Bluetooth Low Energy (BLE)")
                    .setPositiveButton("Exit", (dialog, which) -> System.exit(0))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_ENABLE_BT || requestCode == REQUEST_ENABLE_LOCATION_PERMISSION) {
            String title = "";
            String message = "An error occurred while attempting to ";
            switch (resultCode) {
                case REQUEST_ENABLE_BT:
                    title = "Cannot enable Bluetooth";
                    message += "enable Bluetooth";
                    break;
                case REQUEST_ENABLE_LOCATION_PERMISSION:
                    title = "Cannot obtain location permission";
                    message += "obtain location permission";
                    break;
            }
            if (resultCode == RESULT_CANCELED) {
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("Exit", (dialog, which) -> System.exit(0))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            }
        }
    }

    private void startScan() {
        if (!hasPermissions() || scanning) {
            return;
        }

        deviceList.clear();

        List<ScanFilter> filterList = new ArrayList<>();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanResults = new HashMap<>();
        scanCallback = new BtleScanCallback(scanResults);

        bleScanner = BTAdapter.getBluetoothLeScanner();
        bleScanner.startScan(filterList, settings, scanCallback);
        scanHandler = new Handler();
        scanHandler.postDelayed(this::stopScan, SCAN_PERIOD);
        scanning = true;


    }

    private boolean hasPermissions() {
        if (!BTAdapter.isEnabled()) {
            requestBluetooth();
            return false;
        }
        if (!hasLocationPermissions()) {
            requestLocationPermissions();
            return false;
        }
        if (!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            requestLocation();
            return false;
        }
        return true;
    }

    private void requestBluetooth() {
        Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
    }

    private void requestLocation() {
        Intent enableLocation = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivityForResult(enableLocation, REQUEST_ENABLE_LOCATION);
    }

    private boolean hasLocationPermissions() {
        return (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED);
    }

    private void requestLocationPermissions() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_ENABLE_LOCATION_PERMISSION);
    }

    private void stopScan() {
        if (scanning && BTAdapter != null && BTAdapter.isEnabled() && bleScanner != null) {
            bleScanner.stopScan(scanCallback);
            scanComplete();
        }

        scanCallback = null;
        scanning = false;
        scanHandler = null;
    }

    private void scanComplete() {
        if (scanResults.isEmpty()) {
            return;
        }
        for (String deviceAddress : scanResults.keySet()) {
                deviceList.add(deviceAddress);
        }
        listAdapter.notifyDataSetChanged();
    }

    private class BtleScanCallback extends ScanCallback {
        private Map<String, BluetoothDevice> scanResults;

        BtleScanCallback(Map<String, BluetoothDevice> scanResults) {
            this.scanResults = scanResults;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errCode) {
            Log.e(TAG, "BLE Scan failed with code " + errCode);
        }


        private void addScanResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceAddress = device.getAddress();
            scanResults.put(deviceAddress, device);
        }
    }
}

