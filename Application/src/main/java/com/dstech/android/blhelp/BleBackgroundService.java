package com.dstech.android.blhelp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;


/**
 * Created by basil on 30/12/2016.
 */

public class BleBackgroundService extends Service {

    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private final String TAG = "BleBackgroundService";
    private static final long SCAN_PERIOD = 10000;
    private final IBinder mBinder = new BleBackgroundBinder();
    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            Log.d(TAG,"onServiceConnected del tentativo di connession a LeService");
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                stopSelf();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
            (new Thread(new Runnable() {

                @Override
                public void run() {
                    while (!Thread.interrupted())
                        try {
                            Thread.sleep(1000);
                            new Runnable()
                            {
                                @Override
                                public void run() {
                                    if (mBluetoothLeService != null) {
                                        mBluetoothLeService.readCustomCharacteristic();
                                    }
                                }
                            };
                        } catch (InterruptedException e) {
                            // ooops
                        }
                }
            })).start();
            Log.d(TAG, "Tentativo nell'onServiceConnected dell'mServiceConnection");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BleBackgroundService");
        mHandler = new Handler();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            stopSelf();
        }

        Intent notificationIntent = new Intent(this, DeviceControlActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("BlE")
                .setContentText("Running")
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1337, notification);

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }

        Log.d(TAG, "Tentativo di far partire lo scan");
        if(!isAplicationOpen()){
            Log.d(TAG, "è partito lo scan");
            mLeScanCallback=resetLeScanCallBack();
            scanLeDevice(true);
        }
    }

    public boolean isAplicationOpen() {
        return DeviceScanActivity.getDefaults(DeviceScanActivity.APPLICATION_RUN, this)!=null? DeviceScanActivity.getDefaults(DeviceScanActivity.APPLICATION_RUN, this).equals("1"):true;

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);
        return START_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "OnUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "OnDestroy");
        super.onDestroy();
    }

    public void stopLeScan(){
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
    }

    public void setmLeScanCallback(BluetoothAdapter.LeScanCallback leScanCallback){
        this.mLeScanCallback=leScanCallback;
    }

    public boolean bluetoothAdapterEnable(){
        if(mBluetoothAdapter!=null){
            return mBluetoothAdapter.isEnabled();
        }else{
            return false;
        }
    }

    public void scanLeDevice(final boolean enable){
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    public void connectBluetoothLeService(){
        mDeviceAddress = DeviceScanActivity.getDefaults(DeviceScanActivity.DEVICE_ADDRESS, this);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        Log.d(TAG,"tentativo connession a LeService");
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    public void verificateDeviceAddress(String address){
        mDeviceAddress = DeviceScanActivity.getDefaults(DeviceScanActivity.DEVICE_ADDRESS, this);
        if(address.contains(mDeviceAddress)){
            Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
            Log.d(TAG,"tentativo connession a LeService");
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }
    }

    public BluetoothLeService getmBluetoothLeService() {
        return mBluetoothLeService;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class BleBackgroundBinder extends Binder {
        BleBackgroundService getService() {
            return BleBackgroundService.this;
        }
    }

    private BluetoothAdapter.LeScanCallback resetLeScanCallBack(){
        return new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                new Runnable() {
                    @Override
                    public void run() {
                        if (device.getName() != null && device.getName().contains("Consmart")) {
                            if(mDeviceAddress != null && device.getAddress().contains(mDeviceAddress)){
                                connectBluetoothLeService();
                            }
                        }
                    }
                }.run();
            }
        };
    }

    private boolean mConnected = false;
    private String data;

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                Log.d(TAG, "Tentativo nell'OnReceive dell'mGattUpdateReceiver");

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                clickButton(data);
            }
        }
    };
    private int counter = 0;
    private Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

    private void clickButton(final String data) {
        if (data.contains("01")) {


            if (counter == 0) {
                Log.v("BUTTON", "CLICKED");
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                r.play();
            }

            counter = 1;

        } else if (data.contains("00")) {
            counter = 0;
            Log.v("Button", "OFF");

        } else {
            Log.d("Altro messaggio", data);
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


}
