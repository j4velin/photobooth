package de.j4velin.photobooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * What a callback hell...
 */
public class CameraPreview extends Activity implements ITrigger, ICamera, IDisplay {

    private final static int REQUEST_CODE_CAMERA_PERMISSION = 1;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader imageReader;
    private TextureView cameraView;
    private ImageView imageView;
    private Surface surface;

    private final static boolean TRIGGER_ENABLED = true;
    private final static boolean CAMERA_ENABLED = BuildConfig.DEBUG;

    private final List<ICamera.CameraCallback> cameraCallbacks = new ArrayList<>(1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startService(new Intent(getApplicationContext(), SocketService.class));
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
            setupPreviewSurface();
            Main mainClass = (Main) getApplicationContext();
            if (TRIGGER_ENABLED)
                enableTrigger(mainClass);
            if (CAMERA_ENABLED)
                mainClass.addCamera(this);
            mainClass.addDisplay(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) {
            camera.close();
            camera = null;
        }
        disableTrigger();
        Main mainClass = (Main) getApplicationContext();
        mainClass.removeCamera(this);
        mainClass.removeDisplay(this);
        imageReader.close();
        cameraCallbacks.clear();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupPreviewSurface();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void setupPreviewSurface() {
        cameraView = findViewById(R.id.cameraview);
        imageView = findViewById(R.id.imageview);
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;

        Matrix matrix = new Matrix();
        matrix.setScale(-1, 1);
        matrix.postTranslate(width, 0);
        cameraView.setTransform(matrix);
        cameraView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (BuildConfig.DEBUG) Log.d(Main.TAG, "Surface created");
                CameraPreview.this.surface = new Surface(surface);
                setupCamera();
            }

            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                // Ignored, Camera does all the work for us
            }

            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (BuildConfig.DEBUG) Log.d(Main.TAG, "Surface destroyed");
                CameraPreview.this.surface = null;
                return true;
            }

            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                // Invoked every time there's a new Camera preview frame
            }
        });
    }

    private void setupImageReader(int width, int height) {
        imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                Image image = imageReader.acquireLatestImage();
                if (image != null) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length,
                            null);
                    image.close();
                    for (CameraCallback callback : cameraCallbacks) {
                        callback.imageReady(bitmap);
                    }
                } else if (BuildConfig.DEBUG) Log.e(Main.TAG,
                        "Capture failed: Did not receive any image");
            }
        }, null);
    }

    @SuppressLint("MissingPermission")
    private void setupCamera() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String frontFacingCamera = null;
            if (cm != null)
                for (String id : cm.getCameraIdList()) {
                    CameraCharacteristics cameraCharacteristics = cm.getCameraCharacteristics(id);
                    if (Objects.equals(cameraCharacteristics.get(
                            CameraCharacteristics.LENS_FACING),
                            CameraCharacteristics.LENS_FACING_FRONT)) {
                        frontFacingCamera = id;
                        if (BuildConfig.DEBUG)
                            android.util.Log.d(Main.TAG,
                                    "Front facing camera id=" + frontFacingCamera);
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
                        break;
                    }
                }

            if (frontFacingCamera == null) {
                if (BuildConfig.DEBUG)
                    android.util.Log.e(Main.TAG, "No front facing camera found!");
                finish();
                return;
            }
            cm.openCamera(frontFacingCamera, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cameraDevice) {
                    if (BuildConfig.DEBUG)
                        android.util.Log.i(Main.TAG, "Camera connected");
                    CameraPreview.this.camera = cameraDevice;
                    try {
                        camera.createCaptureSession(
                                Arrays.asList(surface, imageReader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(
                                            CameraCaptureSession cameraCaptureSession) {
                                        CameraPreview.this.session = cameraCaptureSession;
                                        if (BuildConfig.DEBUG)
                                            android.util.Log.i(Main.TAG,
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
                                        CameraPreview.this.session = null;
                                        if (BuildConfig.DEBUG)
                                            android.util.Log.e(Main.TAG,
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
                        android.util.Log.e(Main.TAG, "Camera connection lost");
                    CameraPreview.this.camera = null;
                }

                @Override
                public void onError(CameraDevice cameraDevice, int error) {
                    if (BuildConfig.DEBUG)
                        android.util.Log.e(Main.TAG,
                                "Error connecting to camera device. ErrorCode=" + error);
                }
            }, null);
        } catch (CameraAccessException cae) {
            if (BuildConfig.DEBUG)
                cae.printStackTrace();
        }
    }

    @Override
    public void enableTrigger(final TriggerCallback callback) {
        cameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                callback.takePhoto();
            }
        });
    }

    @Override
    public void disableTrigger() {
        cameraView.setClickable(false);
    }

    @Override
    public void takePhoto() {
        if (cameraIsReady()) {
            try {
                CaptureRequest.Builder builder = camera.createCaptureRequest(
                        CameraDevice.TEMPLATE_STILL_CAPTURE);
                builder.addTarget(imageReader.getSurface());
                CaptureRequest request = builder.build();
                session.capture(request, new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureFailed(CameraCaptureSession session,
                                                CaptureRequest request,
                                                CaptureFailure failure) {
                        if (BuildConfig.DEBUG) Log.e(Main.TAG, "Capture failed: " + failure);
                        super.onCaptureFailed(session, request, failure);
                    }
                }, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void addPhotoTakenCallback(CameraCallback callback) {
        cameraCallbacks.add(callback);
    }

    @Override
    public boolean cameraIsReady() {
        return camera != null && session != null;
    }

    @Override
    public void displayImage(Drawable image) {
        imageView.setImageDrawable(image);
        imageView.setVisibility(View.VISIBLE);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                imageView.setVisibility(View.GONE);
            }
        }, 5000);
    }
}
