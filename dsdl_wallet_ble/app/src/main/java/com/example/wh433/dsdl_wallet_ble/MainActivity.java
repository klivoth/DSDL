package com.example.wh433.dsdl_wallet_ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.Button;
import android.widget.ListView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static android.content.ContentValues.TAG;

/**
 * Main activity of our wallet app.
 * Contains buttons to start/stop scanning nearby devices via BLE,
 * and display list of discovered devices.
 * Click on the entry of the list for connecting to the device.
 *
 * The scanning part is modified from: https://github.com/bignerdranch/android-bluetooth-testbed
 * The connection part is modified from: https://github.com/googlesamples/android-BluetoothChat
 */
public class MainActivity extends AppCompatActivity {

    // Members and constants for BLE scanning
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
    private static final long SCAN_PERIOD = 3000;

    // Members for displaying device list
    private ListView deviceListView;
    private ArrayList<String> deviceList = new ArrayList<>();
    private ArrayAdapter listAdapter;

    // connection
    private ConnectThread connectThread;
    private static ConnectedThread connectedThread;
    private int connectionState = STATE_NONE;
    private static final UUID UUID_INSECURE =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final int STATE_NONE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    // Messaging
    private ListView messageListView;
    private ArrayList<String> messageList = new ArrayList<>();
    private ArrayAdapter messageListAdapter;
    private EditText outputMessage;
    private View mainView, messageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainView = getLayoutInflater().inflate(R.layout.activity_main, null);
        messageView = getLayoutInflater().inflate(R.layout.activity_messaging, null);
        setContentView(R.layout.activity_main);

        // toolbar
        setSupportActionBar(findViewById(R.id.toolbar_main));
        getSupportActionBar().setTitle("Connect to wallet");

        // login screen
        if (PasswordActivity.checkPasswordExist(getApplicationContext())) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        }

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

        // list of scanned devices
        deviceListView = findViewById(R.id.deviceListView);
        listAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList);
        deviceListView.setAdapter(listAdapter);
        deviceListView.setOnItemClickListener((parent, view, position, id) ->
                connectDevice(parent.getItemAtPosition(position).toString()));
    }

    private void switchMode(int mode) {
        if (mode == 0) {
            setContentView(mainView);
            setSupportActionBar(findViewById(R.id.toolbar_main));
            getSupportActionBar().setTitle("Connect to wallet");
            Button startScanBtn = findViewById(R.id.startScanBtn);
            startScanBtn.setOnClickListener(v -> startScan());
        }
        else {
            setContentView(messageView);
            setSupportActionBar(findViewById(R.id.toolbar_message));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Send Message");
            messageListAdapter = new ArrayAdapter(this, R.layout.message,
                    messageList);
            messageListView = findViewById(R.id.message_list);
            messageListView.setAdapter(messageListAdapter);
            outputMessage = findViewById(R.id.output_message);
            Button sendButton = findViewById(R.id.send_button);
            sendButton.setOnClickListener(v -> {
                byte[] buffer = Arrays.copyOf(outputMessage.getText().toString().getBytes(),
                        outputMessage.length());
                write(buffer);
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        if (PasswordActivity.checkPasswordExist(getApplicationContext())) {
            MenuItem item = menu.findItem(R.id.set_password);
            item.setTitle("Change password");
        }
        else {
            MenuItem item = menu.findItem(R.id.remove_password);
            item.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent = new Intent(this, PasswordActivity.class);
        Bundle b = new Bundle();
        int mode = PasswordActivity.SET_PASSWORD;
        switch (item.getItemId()) {
            case R.id.set_password:
                if (PasswordActivity.checkPasswordExist(getApplicationContext()))
                   mode = PasswordActivity.CHANGE_PASSWORD;
                break;
            case R.id.remove_password:
                if (!PasswordActivity.checkPasswordExist(getApplicationContext()))
                    return true;
                mode = PasswordActivity.REMOVE_PASSWORD;
                break;
            case android.R.id.home:
                stopConnection("");
            default:
                return super.onOptionsItemSelected(item);
        }
        b.putInt("key", mode);
        intent.putExtras(b);
        startActivity(intent);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        invalidateOptionsMenu();

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
        deviceList.addAll(scanResults.keySet());
        listAdapter.notifyDataSetChanged();
    }

    public synchronized void connectDevice(String device) {
        Toast.makeText(getApplicationContext(), "Connecting to: " + device,
                Toast.LENGTH_LONG).show();
        stopScan();
        if (connectionState == STATE_CONNECTING && connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectionState == STATE_CONNECTED && connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        connectThread = new ConnectThread(scanResults.get(device));
        connectThread.start();
        switchMode(1);
    }

    public synchronized void connected(BluetoothSocket socket) {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
    }

    public void write(byte[] out) {
        ConnectedThread thread;
        synchronized (this) {
            if (connectionState != STATE_CONNECTED)
                return;
            thread = connectedThread;
        }
        if (out.length > 0)
            thread.write(out);
    }

    private void stopConnection(String errorMessage) {
        connectionState = STATE_NONE;
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        Log.e(TAG, errorMessage);
        new AsyncSwitchView().execute(0);
    }

    private void connectionFailed() {
        stopConnection("Connection failed, return to main screen");
    }

    private void connectionLost() {
        stopConnection("Connection lost, return to main screen");
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

    private class ConnectThread extends Thread {
        private BluetoothSocket socket;

        private ConnectThread(BluetoothDevice device) {
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(UUID_INSECURE);
            } catch (IOException e) {
                Log.e(TAG, "Socket create failed", e);
                return;
            }
            connectionState = STATE_CONNECTING;
        }

        public void run() {
            try {
                socket.connect();
            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException closeExc) {
                    Log.e(TAG, "Cannot close socket", closeExc);
                }
                connectionFailed();
                return;
            }

            // reset connectThread and start connectedThread
            synchronized (this) {
                connectThread = null;
            }
            connected(socket);
        }

        private void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close connectThread", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private BluetoothSocket socket;
        private InputStream input;
        private OutputStream output;

        private ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            try {
                input = socket.getInputStream();
                output = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Cannot get input/output streams", e);
                return;
            }
            connectionState = STATE_CONNECTED;
        }

        public void run() {
            byte[] buffer = new byte[2048];
            while (connectionState == STATE_CONNECTED) {
                try {
                    input.read(buffer);
                    new AsyncMessageLoader().execute(new String(buffer, "ASCII"));
                } catch (IOException e) {
                    Log.e(TAG, "Disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        private void write(byte[] buffer) {
            try {
                output.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Error when writing", e);
            }
        }

        private void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close socket", e);
            }
        }
    }

    private class AsyncMessageLoader extends AsyncTask<String, Void, String> {
        @Override
        protected void onPostExecute(String message) {
            super.onPostExecute(message);
            messageListAdapter.add(message);
            messageListAdapter.notifyDataSetChanged();
        }

        @Override
        protected String doInBackground(String... message) {
            return message[0];
        }
    }

    private class AsyncSwitchView extends AsyncTask<Integer, Void, Integer> {
        @Override
        protected void onPostExecute(Integer mode) {
            super.onPostExecute(mode);
            switchMode(mode);
        }

        @Override
        protected Integer doInBackground(Integer... mode) {
            return mode[0];
        }
    }
}

