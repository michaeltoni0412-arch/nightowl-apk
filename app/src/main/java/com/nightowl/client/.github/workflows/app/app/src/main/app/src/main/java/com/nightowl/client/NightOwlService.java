package com.nightowl.client;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.display.*;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import okhttp3.*;
import okio.ByteString;

public class NightOwlService extends Service {
    private static final String SERVER = "ws://192.168.1.169:8765";
    private static final String CHANNEL = "nightowl";
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private WebSocket socket;
    private OkHttpClient http;
    private int W, H, DPI;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotification());
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(m);
        W = m.widthPixels; H = m.heightPixels; DPI = m.densityDpi;
        int code = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");
        MediaProjectionManager pm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = pm.getMediaProjection(code, data);
        connectServer();
        return START_STICKY;
    }

    private void connectServer() {
        http = new OkHttpClient();
        socket = http.newWebSocket(new Request.Builder().url(SERVER).build(), new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response r) {
                ws.send("{\"role\":\"client\",\"device_id\":\"NightOwl\"}");
                startCapture();
            }
            @Override public void onMessage(WebSocket ws, String text) { }
            @Override public void onFailure(WebSocket ws, Throwable t, Response r) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> connectServer(), 3000);
            }
        });
    }

    private void startCapture() {
        reader = ImageReader.newInstance(W, H, android.graphics.PixelFormat.RGBA_8888, 2);
        display = projection.createVirtualDisplay("NightOwl", W, H, DPI,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);
        reader.setOnImageAvailableListener(r -> {
            Image img = r.acquireLatestImage();
            if (img != null) { sendFrame(img); img.close(); }
        }, null);
    }

    private void sendFrame(Image image) {
        try {
            ByteBuffer buf = image.getPlanes()[0].getBuffer();
            Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buf);
            Bitmap small = Bitmap.createScaledBitmap(bmp, W/2, H/2, true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            small.compress(Bitmap.CompressFormat.JPEG, 50, out);
            if (socket != null) socket.send(ByteString.of(out.toByteArray()));
            bmp.recycle(); small.recycle();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Notification buildNotification() {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "NightOwl", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        return new Notification.Builder(this, CHANNEL)
            .setContentTitle("NightOwl Active")
            .setContentText("Screen is being shared")
            .setSmallIcon(android.R.drawable.ic_menu_view).build();
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        super.onDestroy();
        if (display != null) display.release();
        if (projection != null) projection.stop();
        if (socket != null) socket.cancel();
    }
          }
