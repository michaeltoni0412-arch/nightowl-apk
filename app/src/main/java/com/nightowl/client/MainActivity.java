package com.nightowl.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int SCREEN_CAPTURE_CODE = 100;
    private MediaProjectionManager projManager;

    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                runOnUiThread(() -> showAccessDialog());
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                stopService(new Intent(context, NightOwlService.class));
                Toast.makeText(context, "NightOwl: Session ended.", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);
    }

    private void showAccessDialog() {
        new AlertDialog.Builder(this)
            .setTitle("NightOwl")
            .setMessage("A device wants to view and control this screen.\n\nAllow access?")
            .setPositiveButton("Allow", (d, w) -> requestScreenPermission())
            .setNegativeButton("Deny", (d, w) -> Toast.makeText(this, "Access denied.", Toast.LENGTH_SHORT).show())
            .setCancelable(false)
            .show();
    }

    private void requestScreenPermission() {
        startActivityForResult(projManager.createScreenCaptureIntent(), SCREEN_CAPTURE_CODE);
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        if (req == SCREEN_CAPTURE_CODE && result == RESULT_OK) {
            Intent i = new Intent(this, NightOwlService.class);
            i.putExtra("resultCode", result);
            i.putExtra("data", data);
            startForegroundService(i);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
    }
  }
