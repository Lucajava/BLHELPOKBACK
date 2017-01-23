package com.dstech.android.blhelp;

/**
 * Created by Luca on 30/12/16.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class BootUpReceiver extends BroadcastReceiver{
String TAG="boooo";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED") && DeviceScanActivity.getDefaults(DeviceScanActivity.DEVICE_ADDRESS,context)!=null) {
            Intent i = new Intent(context, DeviceScanActivity.class);
            Log.d(TAG,"bootif");
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            Toast.makeText(context,"BOOT",Toast.LENGTH_SHORT).show();
        }


    }
}