/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dstech.android.blhelp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = "DeviceControlActivty";

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String RESET = "com.dstech.android.blhelp.reset";

    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private boolean mConnected = false;
    private String data;
    private BleBackgroundService bleBackgroundService;

    private boolean stopReader = false;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.

            bleBackgroundService = ((BleBackgroundService.BleBackgroundBinder)service).getService();
            Log.d(TAG, "onServiceConnected del tentativo di connessione con BleService");
            if (bleBackgroundService.getmBluetoothLeService() != null) {
                final boolean result = bleBackgroundService.getmBluetoothLeService().connect(mDeviceAddress);
                //bleBackgroundService.connectBluetoothLeService();
                Log.d(TAG, "Connect request result=" + result);
            }

            // Tell the user about this for our demo.
            Toast.makeText(DeviceControlActivity.this, R.string.local_service_connected,
                    Toast.LENGTH_SHORT).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            bleBackgroundService = null;
            Toast.makeText(DeviceControlActivity.this, R.string.local_service_disconnected, Toast.LENGTH_SHORT).show();
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                invalidateOptionsMenu();
                Log.d(TAG, "Tentativo nell'OnReceive dell'mGattUpdateReceiver");

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
                clearUI();

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                displayData(data);
                clickButton(data);
            }
        }
    };

    private void clearUI() {
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.button_control);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(R.string.app_name);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        Intent bleBackgroundServiceIntent = new Intent(this, BleBackgroundService.class);
        bindService(bleBackgroundServiceIntent, mConnection, BIND_AUTO_CREATE);

        (new Thread(new Runnable() {

            @Override
            public void run() {
                while (!Thread.interrupted())
                    try {
                        Thread.sleep(1000);
                        runOnUiThread(new Runnable() // start actions in UI thread
                        {
                            @Override
                            public void run() {
                                if (bleBackgroundService.getmBluetoothLeService() != null && !stopReader) {
                                    bleBackgroundService.getmBluetoothLeService().readCustomCharacteristic();
                                }
                            }
                        });
                    } catch (InterruptedException e) {
                        // ooops
                    }
            }
        })).start();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                if(bleBackgroundService!=null && bleBackgroundService.getmBluetoothLeService()!=null){
                    bleBackgroundService.getmBluetoothLeService().connect(mDeviceAddress);
                }
                return true;
            case R.id.menu_disconnect:
                if(bleBackgroundService!=null && bleBackgroundService.getmBluetoothLeService()!=null){
                    bleBackgroundService.getmBluetoothLeService().disconnect();
                }
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_reset:
                onClickReset();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void onClickWriteSound(View v) {
        if (bleBackgroundService.getmBluetoothLeService() != null) {
            stopReader=true;
            bleBackgroundService.getmBluetoothLeService().writeCustomCharacteristic(0x02); //FIXME
            bleBackgroundService.getmBluetoothLeService().writeCustomCharacteristic(0x02);
            bleBackgroundService.getmBluetoothLeService().readCustomCharacteristic();
        }
    }

    public void onClickWriteLight(View v) {
        if (bleBackgroundService.getmBluetoothLeService() != null) {
            stopReader=true;
            bleBackgroundService.getmBluetoothLeService().writeCustomCharacteristic(0x01); //FIXME
            bleBackgroundService.getmBluetoothLeService().writeCustomCharacteristic(0x01);
            bleBackgroundService.getmBluetoothLeService().readCustomCharacteristic();
        }
    }

    public void onClickRead(View v) {
        if (bleBackgroundService.getmBluetoothLeService() != null) {
            bleBackgroundService.getmBluetoothLeService().readCustomCharacteristic(); //FIXME
        }
    }

    private boolean timerInCorso = false;
    private CountDownTimer timer = resetCounTimer(5000);
    private int counter = 0;
    private Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

    private CountDownTimer resetCounTimer(long millisInFuture) {
        timerInCorso = false;
        return new CountDownTimer(millisInFuture, 500) {
            @Override
            public void onTick(long millisUntilFinished) {
                //  ((TextView)findViewById(R.id.txt_timer)).setText("seconds remaining: " + millisUntilFinished / 1000);
                timerInCorso = true;
            }

            public void onFinish() {

                ((TextView) findViewById(R.id.txt_timer2)).setText("HAI PREMUTO 5 SECONDI");

                Intent intent = new Intent();
                intent.setAction("com.cleverdroid.driver.component.Button.1");
                sendBroadcast(intent);

                timerInCorso = false;
            }
        };
    }

    private void clickButton(final String data) {
        if (data.contains("01")) {

            ((TextView) findViewById(R.id.txt_2)).setText("HAI PREMUTO IL PULSANTE");
            if (counter == 0) {
                Log.v("BUTTON", "CLICKED");
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                r.play();
            }

            if (!timerInCorso) {
                timer.start();
            }
            counter = 1;

        } else if (data.contains("00")) {
            counter = 0;
            ((TextView) findViewById(R.id.txt_2)).setText("SPENTO");
            if (timerInCorso) {
                timer.cancel();
                timer = resetCounTimer(5000);
            }
        } else {
            Log.d("Altro messaggio", data);
        }

    }

    public void onClickReset() {
        DeviceScanActivity.setDefaults(DeviceScanActivity.DEVICE_ADDRESS, null, this);
        onBackPressed();
    }
}
