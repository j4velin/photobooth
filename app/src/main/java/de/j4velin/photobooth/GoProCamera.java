package de.j4velin.photobooth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// TODO: test if that actually works as expected...
public class GoProCamera implements ICamera {

    private final static String GOPRO_IP = "10.5.5.9";
    private final static Request TAKE_PHOTO_REQUEST = new Request.Builder().url(
            "http://" + GOPRO_IP + "/shutter?p=0").build();

    private final OkHttpClient client = new OkHttpClient();
    private final List<CameraCallback> cameraCallbacks = new ArrayList<>(1);

    private boolean ready = false;

    GoProCamera(final Context context) {
        checkNetworkState(context);
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                checkNetworkState(context);
            }
        }, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
    }

    private void checkNetworkState(final Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(
                Context.WIFI_SERVICE);
        ready = wm != null && Formatter.formatIpAddress(wm.getDhcpInfo().serverAddress).contains(
                GOPRO_IP);
        if (BuildConfig.DEBUG) Log.d(Main.TAG, "GoPro ready: " + ready);
    }

    @Override
    public void takePhoto() {
        client.newCall(TAKE_PHOTO_REQUEST).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (BuildConfig.DEBUG) e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                ResponseBody body = response.body();
                if (body != null) {
                    String result = body.string();
                    if (BuildConfig.DEBUG) Log.i(Main.TAG, "GoPro result: " + result);

                    URLConnection connection = new URL(result).openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    InputStream input = connection.getInputStream();
                    Bitmap image = BitmapFactory.decodeStream(input);
                    for (CameraCallback callback : cameraCallbacks) {
                        callback.imageReady(image);
                    }
                }
            }
        });
    }

    @Override
    public void addPhotoTakenCallback(CameraCallback callback) {
        cameraCallbacks.add(callback);
    }

    @Override
    public boolean cameraIsReady() {
        return ready;
    }
}
