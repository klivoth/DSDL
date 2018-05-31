package com.example.wh433.dsdl_wallet_ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.ContentValues.TAG;


public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter BTAdapter;
    private HashMap<String, BluetoothDevice> scanResults;
    private ScanCallback scanCallback;
    private boolean scanning = false;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ENABLE_LOCATION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);

        final BluetoothManager bluetoothManager =
                (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        BTAdapter = bluetoothManager.getAdapter();
        if (BTAdapter == null) { // exit if device does not support bluetooth
            new AlertDialog.Builder(this)
                    .setTitle("Not compatible")
                    .setMessage("Your phone does not support Bluetooth")
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            System.exit(0);
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            new AlertDialog.Builder(this)
                    .setTitle("Not compatible")
                    .setMessage("Your phone does not support Bluetooth Low Energy (BLE)")
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            System.exit(0);
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_CANCELED) {
                new AlertDialog.Builder(this)
                        .setTitle("Cannot enable Bluetooth")
                        .setMessage("An error occurred while attempting to enable Bluetooth")
                        .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                System.exit(0);
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            }
        }
    }

    private void startScan() {
        if (!hasPermissions() || scanning) {
            return;
        }

        List<ScanFilter> filterList = new ArrayList<>();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();
        scanResults = new HashMap<>();
        scanCallback = new BtleScanCallback(scanResults);
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
        return true;
    }

    private void requestBluetooth() {
        Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
        Toast.makeText(getApplicationContext(), "Bluetooth enabled",
                Toast.LENGTH_LONG).show();
    }

    private boolean hasLocationPermissions() {
        return (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED);
    }

    private void requestLocationPermissions() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_ENABLE_LOCATION);
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

