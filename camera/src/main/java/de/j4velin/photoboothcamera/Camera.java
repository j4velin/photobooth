package de.j4velin.photoboothcamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public class Camera extends Activity {

    private final static int REQUEST_CODE_CAMERA_PERMISSION = 1;
    private final static int SOCKET_PORT = 5556;
    private final static String TAG = "photobooth.camera";
    final static String TAKE_PHOTO_COMMAND = "TAKE_PHOTO";

    private ImageReader imageReader;
    private DataOutputStream out;

    private CameraDevice camera;
    private CameraCaptureSession session;

    private TextureView cameraView;
    private Surface surface;

    private final Handler handler = new Handler();
    private final Object PHOTO_READY_LOCK = new Object();
    private final Thread sendImageThread = new Thread(new Runnable() {
        @Override
        public void run() {
            while (camera != null) {
                synchronized (PHOTO_READY_LOCK) {
                    try {
                        PHOTO_READY_LOCK.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    Image image = imageReader.acquireLatestImage();
                    if (image != null) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Image available");
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] result = new byte[buffer.remaining()];
                        buffer.get(result);
                        image.close();
                        try {
                            out.writeInt(result.length);
                            out.write(result);
                            out.flush();
                        } catch (IOException e) {
                            if (BuildConfig.DEBUG) Log.e(TAG,
                                    "Cant send result image: " + e.getMessage());
                        }
                    }
                } catch (Throwable t) {
                    if (BuildConfig.DEBUG) Log.e(TAG,
                            "Capture failed: " + t.getMessage());
                }
            }
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkSelfPermission(
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE_CAMERA_PERMISSION);
        } else {
            setupSurface();
        }
    }

    private void setupSurface() {
        cameraView = findViewById(R.id.cameraview);
        cameraView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width,
                                                  int height) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Surface created");
                Camera.this.surface = new Surface(surface);
                connect();
            }

            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width,
                                                    int height) {
                // Ignored, Camera does all the work for us
            }

            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Surface destroyed");
                Camera.this.surface = null;
                return true;
            }

            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                // Invoked every time there's a new Camera preview frame
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (imageReader != null)
            imageReader.close();
        if (camera != null) {
            camera.close();
            camera = null;
        }
        synchronized (PHOTO_READY_LOCK) {
            PHOTO_READY_LOCK.notifyAll();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupSurface();
        } else {
            finish();
        }
    }

    private void connect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WifiManager wm = (WifiManager) getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
                    DhcpInfo info = wm.getDhcpInfo();
                    InetAddress gateway = InetAddress.getByAddress(
                            BigInteger.valueOf(info.gateway).toByteArray());

                    gateway = InetAddress.getByName("192.168.178.37");

                    Socket socket = new Socket(gateway, SOCKET_PORT);
                    socket.setKeepAlive(true);
                    out = new DataOutputStream(socket.getOutputStream());
                    try {
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(socket.getInputStream()));
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            if (BuildConfig.DEBUG) Log.d(TAG,
                                    "Line read over socket: " + inputLine);
                            if (inputLine.equalsIgnoreCase(TAKE_PHOTO_COMMAND)) {
                                takePhoto();
                            } else if (BuildConfig.DEBUG) {
                                Log.w(TAG, "Ignoring unknown command: " + inputLine);
                            }
                        }
                    } catch (IOException e) {
                        if (BuildConfig.DEBUG) Log.e(TAG,
                                "Socket connection failed: " + e.getMessage());
                    }
                    if (BuildConfig.DEBUG) Log.i(TAG, "Socket connection closed");
                } catch (Throwable t) {
                    t.printStackTrace();
                    finish();
                }
            }
        }).start();
        setupCamera();
    }

    private void setupCamera() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String cameraId = null;
            if (cm != null)
                for (String id : cm.getCameraIdList()) {
                    CameraCharacteristics cameraCharacteristics = cm.getCameraCharacteristics(id);
                    if (Objects.equals(cameraCharacteristics.get(
                            CameraCharacteristics.LENS_FACING),
                            CameraCharacteristics.LENS_FACING_BACK)) {
                        cameraId = id;
                        if (BuildConfig.DEBUG) Log.d(TAG, "Camera id=" + cameraId);
                        StreamConfigurationMap map = cameraCharacteristics.get(
                                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                        int width = 500;
                        int height = 500;
                        if (map != null) {
                            for (Size s : map.getOutputSizes(ImageFormat.JPEG)) {
                                if (s.getWidth() >= width && s.getHeight() >= height) {
                                    width = s.getWidth();
                                    height = s.getHeight();
                                }
                            }
                        }
                        setupImageReader(width, height);
                        openCamera(cameraId);
                        break;
                    }
                }

            if (cameraId == null) {
                if (BuildConfig.DEBUG)
                    android.util.Log.e(TAG, "No camera found!");
                finish();
            }
        } catch (CameraAccessException cae) {
            if (BuildConfig.DEBUG)
                cae.printStackTrace();
        }
    }

    private void setupImageReader(int width, int height) {
        imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
        if (BuildConfig.DEBUG) Log.d(TAG,
                "setupImageReader width=" + width + ", height=" + height);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                synchronized (PHOTO_READY_LOCK) {
                    PHOTO_READY_LOCK.notifyAll();
                }
            }
        }, null);
    }

    private void takePhoto() {
        try {
            CaptureRequest.Builder builder = camera
                    .createCaptureRequest(
                            CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());
            builder.set(
                    CaptureRequest.JPEG_ORIENTATION,
                    90);
            CaptureRequest request = builder.build();
            session.capture(request, new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    if (BuildConfig.DEBUG) Log.e(TAG, "Capture onCaptureCompleted: ");
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                            CaptureFailure failure) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Capture failed: " + failure);
                    super.onCaptureFailed(session, request, failure);
                }
            }, handler);
        } catch (CameraAccessException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "CameraAccessException: " + e.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera(String cameraId) throws CameraAccessException {
        CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
        cm.openCamera(cameraId, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(final CameraDevice camera) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Camera connected");
                Camera.this.camera = camera;
                try {
                    camera.createCaptureSession(
                            Arrays.asList(surface, imageReader.getSurface()),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(
                                        final CameraCaptureSession session) {
                                    if (BuildConfig.DEBUG) Log.d(TAG,
                                            "Camera capture session configured");
                                    Camera.this.session = session;
                                    try {
                                        CaptureRequest.Builder builder = camera
                                                .createCaptureRequest(
                                                        CameraDevice.TEMPLATE_PREVIEW);
                                        builder.addTarget(surface);
                                        CaptureRequest request = builder.build();
                                        session.setRepeatingRequest(request, null, handler);
                                    } catch (CameraAccessException e) {
                                        if (BuildConfig.DEBUG)
                                            e.printStackTrace();
                                    }
                                    sendImageThread.start();
                                }

                                @Override
                                public void onConfigureFailed(
                                        CameraCaptureSession cameraCaptureSession) {
                                    if (BuildConfig.DEBUG)
                                        android.util.Log.e(TAG,
                                                "Camera capture session configuration failed!");
                                }
                            }, null);
                } catch (CameraAccessException e) {
                    if (BuildConfig.DEBUG)
                        e.printStackTrace();
                }
            }

            @Override
            public void onDisconnected(CameraDevice cameraDevice) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Camera connection lost");
            }

            @Override
            public void onError(CameraDevice cameraDevice, int error) {
                if (BuildConfig.DEBUG) Log.e(TAG,
                        "Error connecting to camera device. ErrorCode=" + error);
            }
        }, handler);
    }

}
