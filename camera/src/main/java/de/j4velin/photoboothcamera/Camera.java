package de.j4velin.photoboothcamera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.media.Image;
import android.media.ImageReader;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

import de.j4velin.photobooth.common.CameraUtil;
import de.j4velin.photobooth.common.Config;
import de.j4velin.photobooth.common.Const;

public class Camera extends Activity {

    private final static int REQUEST_CODE_CAMERA_PERMISSION = 1;
    private final static String TAG = "photobooth.camera";

    private volatile DataOutputStream out;
    private volatile boolean keepRunning = true;
    private volatile byte[] bytesToSend;

    private final CameraUtil cameraUtil = new CameraUtil();
    private final Object PHOTO_READY_LOCK = new Object();

    private TextureView cameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0f;
        getWindow().setAttributes(lp);
        setContentView(R.layout.camera);
        cameraView = findViewById(R.id.cameraview);
        cameraView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepRunning = true;
        if (checkSelfPermission(
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE_CAMERA_PERMISSION);
        } else {
            setup();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraUtil.shutdown();
        keepRunning = false;
        synchronized (PHOTO_READY_LOCK) {
            PHOTO_READY_LOCK.notifyAll();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setup();
        } else {
            finish();
        }
    }

    private void setup() {
        cameraUtil.setup(this, CameraCharacteristics.LENS_FACING_BACK,
                new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(final ImageReader imageReader) {
                        try {
                            Image image = imageReader.acquireLatestImage();
                            if (image != null) {
                                if (BuildConfig.DEBUG) Log.d(TAG, "Image available");
                                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                bytesToSend = new byte[buffer.remaining()];
                                buffer.get(bytesToSend);
                                image.close();
                                synchronized (PHOTO_READY_LOCK) {
                                    PHOTO_READY_LOCK.notifyAll();
                                }
                            }
                        } catch (Throwable t) {
                            if (BuildConfig.DEBUG) Log.e(TAG,
                                    "Capture failed: " + t.getMessage());
                        }
                    }
                }, cameraView);
        new Thread(new ImageSender()).start();
        new Thread(new WifiListener()).start();
    }


    private class WifiListener implements Runnable {
        @Override
        public void run() {
            try {
                while (keepRunning) {
                    WifiManager wm = (WifiManager) getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
                    DhcpInfo info = wm.getDhcpInfo();
                    InetAddress gateway = InetAddress.getByAddress(
                            BigInteger.valueOf(info.gateway).toByteArray());

                    // TODO: remove
                    gateway = InetAddress.getByName("192.168.178.37");

                    if (BuildConfig.DEBUG) Log.i(TAG,
                            "Socket connect to " + gateway.getHostAddress());
                    try {
                        Socket socket = new Socket(gateway, Config.CAMERA_SOCKET_PORT);
                        socket.setKeepAlive(true);
                        synchronized (PHOTO_READY_LOCK) {
                            out = new DataOutputStream(socket.getOutputStream());
                        }
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(socket.getInputStream()));
                        String inputLine;
                        while (keepRunning && (inputLine = in.readLine()) != null) {
                            if (BuildConfig.DEBUG) Log.d(TAG,
                                    "Line read over socket: " + inputLine);
                            if (inputLine.equalsIgnoreCase(Const.TAKE_PHOTO_COMMAND)) {
                                cameraUtil.takePhoto();
                            } else if (BuildConfig.DEBUG) {
                                Log.w(TAG, "Ignoring unknown command: " + inputLine);
                            }
                        }
                    } catch (ConnectException ce) {
                        if (BuildConfig.DEBUG) Log.e(TAG,
                                "Cant connect to socket: " + ce
                                        .getMessage() + ", retry in 5 sec");
                        try {
                            Thread.sleep(Config.SOCKET_CONNECT_RETRY_SLEEP);
                        } catch (InterruptedException ie) {
                            // ignore
                        }
                    } catch (IOException e) {
                        if (BuildConfig.DEBUG) Log.e(TAG,
                                "Socket connection failed: " + e.getMessage());
                    }
                }
                if (BuildConfig.DEBUG) Log.i(TAG, "connectThread exit");
            } catch (Throwable t) {
                if (BuildConfig.DEBUG) Log.e(TAG,
                        t.getClass().getSimpleName() + ": " + t.getMessage());
                t.printStackTrace();
                finish();
            }
        }
    }

    private class ImageSender implements Runnable {
        @Override
        public void run() {
            while (keepRunning) {
                synchronized (PHOTO_READY_LOCK) {
                    try {
                        PHOTO_READY_LOCK.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (bytesToSend != null) {
                        try {
                            out.writeInt(bytesToSend.length);
                            out.write(bytesToSend);
                            out.flush();
                            bytesToSend = null;
                        } catch (IOException e) {
                            if (BuildConfig.DEBUG) Log.e(TAG,
                                    "Cant send result image: " + e.getMessage());
                        }
                    }
                }
            }
            if (BuildConfig.DEBUG) Log.i(TAG, "senderThread exit");
        }
    }
}
