package de.j4velin.photobooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.util.Arrays;
import java.util.Objects;

/**
 * What a callback hell...
 */
public class CameraPreview extends Activity {

    public final static String TAG = "photobooth";
    private final static int REQUEST_CODE_CAMERA_PERMISSION = 1;
    private CameraDevice camera;
    private Surface surface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkSelfPermission(
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE_CAMERA_PERMISSION);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            setupSurface();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) {
            camera.close();
            camera = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupSurface();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void setupSurface() {
        SurfaceView surfaceView = findViewById(R.id.cameraview);
        SurfaceHolder holder = surfaceView.getHolder();
        holder.setFixedSize(500, 500);
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                surface = surfaceHolder.getSurface();
                setupCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width,
                                       int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                surface = null;
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void setupCamera() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String frontFacingCamera = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cm.getCameraCharacteristics(id);
                if (Objects.equals(cameraCharacteristics.get(
                        CameraCharacteristics.LENS_FACING),
                        CameraCharacteristics.LENS_FACING_FRONT)) {
                    frontFacingCamera = id;
                    if (BuildConfig.DEBUG)
                        android.util.Log.d(TAG, "Front facing camera id=" + frontFacingCamera);
                    break;
                }
            }

            if (frontFacingCamera == null) {
                if (BuildConfig.DEBUG)
                    android.util.Log.e(TAG, "No front facing camera found!");
                finish();
                return;
            }
            cm.openCamera(frontFacingCamera, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cameraDevice) {
                    if (BuildConfig.DEBUG)
                        android.util.Log.i(TAG, "Camera connected");
                    CameraPreview.this.camera = cameraDevice;
                    try {
                        camera.createCaptureSession(Arrays.asList(surface),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(
                                            CameraCaptureSession cameraCaptureSession) {
                                        if (BuildConfig.DEBUG)
                                            android.util.Log.i(TAG,
                                                    "Camera capture session configured");
                                        try {
                                            CaptureRequest.Builder builder = camera
                                                    .createCaptureRequest(
                                                            CameraDevice.TEMPLATE_PREVIEW);
                                            builder.addTarget(surface);
                                            CaptureRequest request = builder.build();
                                            cameraCaptureSession.setRepeatingRequest(request, null
                                                    , null);
                                        } catch (CameraAccessException e) {
                                            if (BuildConfig.DEBUG)
                                                e.printStackTrace();
                                        }
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
                    if (BuildConfig.DEBUG)
                        android.util.Log.e(TAG, "Camera connection lost");
                    CameraPreview.this.camera = null;
                }

                @Override
                public void onError(CameraDevice cameraDevice, int error) {
                    if (BuildConfig.DEBUG)
                        android.util.Log.e(TAG,
                                "Error connecting to camera device. ErrorCode=" + error);
                }
            }, null);
        } catch (CameraAccessException cae) {
            if (BuildConfig.DEBUG)
                cae.printStackTrace();
        }
    }

}
