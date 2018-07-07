package de.j4velin.photobooth.common;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;
import java.util.Objects;

/**
 * Utility class to simplify handling the camera2 API
 */
public class CameraUtil {

    private final static String TAG = "photobooth.camerautil";

    private ImageReader imageReader;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private final Handler handler = new Handler();

    /**
     * Call to shutdown the camera and all associated view and buffers
     */
    public void shutdown() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Camera shutdown");
        if (imageReader != null) {
            imageReader.close();
        }
        if (session != null) {
            session.close();
            session = null;
        }
        if (camera != null) {
            camera.close();
            camera = null;
        }
    }

    /**
     * True, if setup  is complete
     *
     * @return true, if camera setup is complete
     */
    public boolean isOperational() {
        return imageReader != null && camera != null && session != null;
    }

    /**
     * Call to setup the camera
     *
     * @param a          the calling activity
     * @param lens       the camera lens to use, must be one of
     *                   {@link CameraCharacteristics#LENS_FACING_BACK},
     *                   {@link CameraCharacteristics#LENS_FACING_FRONT}
     * @param callback   the image available callback
     * @param cameraView the preview view
     */
    public void setup(final Activity a, final int lens,
                      final ImageReader.OnImageAvailableListener callback,
                      final TextureView cameraView) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Camera setup #1");
        if (cameraView.getSurfaceTexture() != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Surface already created");
            setupCamera(a, lens, callback, new Surface(cameraView.getSurfaceTexture()));
        } else {
            cameraView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width,
                                                      int height) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Surface created");
                    setupCamera(a, lens, callback, new Surface(surface));
                }

                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width,
                                                        int height) {
                }

                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Surface destroyed");
                    shutdown();
                    return true;
                }

                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            });
        }
    }

    private void setupCamera(Activity a, int lens, ImageReader.OnImageAvailableListener callback,
                             Surface surface) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Camera setup #2");
        try {
            CameraManager cm = (CameraManager) a.getSystemService(Context.CAMERA_SERVICE);
            String cameraId = null;
            if (cm != null)
                for (String id : cm.getCameraIdList()) {
                    CameraCharacteristics cameraCharacteristics = cm.getCameraCharacteristics(id);
                    if (Objects.equals(cameraCharacteristics.get(
                            CameraCharacteristics.LENS_FACING), lens)) {
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
                        imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
                        imageReader.setOnImageAvailableListener(callback, handler);
                        openCamera(cm, cameraId, surface);
                        break;
                    }
                }

            if (cameraId == null) {
                if (BuildConfig.DEBUG)
                    android.util.Log.e(TAG, "No camera found!");
                shutdown();
                a.finish();
            }
        } catch (CameraAccessException cae) {
            if (BuildConfig.DEBUG)
                cae.printStackTrace();
            shutdown();
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera(final CameraManager cm, final String cameraId,
                            final Surface surface) throws
            CameraAccessException {
        if (BuildConfig.DEBUG) Log.d(TAG, "Camera setup #3 (openCamera)");
        cm.openCamera(cameraId, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(final CameraDevice camera) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Camera setup #4 (cameraOpened)");
                CameraUtil.this.camera = camera;
                try {
                    camera.createCaptureSession(
                            Arrays.asList(surface, imageReader.getSurface()),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(
                                        final CameraCaptureSession session) {
                                    if (BuildConfig.DEBUG) Log.d(TAG,
                                            "Camera setup #5 (captureSessionConfigured)");
                                    CameraUtil.this.session = session;
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
                                        shutdown();
                                    }
                                }

                                @Override
                                public void onConfigureFailed(
                                        CameraCaptureSession cameraCaptureSession) {
                                    if (BuildConfig.DEBUG)
                                        android.util.Log.e(TAG,
                                                "Camera capture session configuration failed!");
                                    shutdown();
                                }
                            }, null);
                } catch (CameraAccessException e) {
                    if (BuildConfig.DEBUG)
                        e.printStackTrace();
                    shutdown();
                }
            }

            @Override
            public void onDisconnected(CameraDevice cameraDevice) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Camera connection lost");
                shutdown();
            }

            @Override
            public void onError(CameraDevice cameraDevice, int error) {
                if (BuildConfig.DEBUG) Log.e(TAG,
                        "Error connecting to camera device. ErrorCode=" + error);
                shutdown();
            }
        }, handler);
    }

    /**
     * Call to take a photo
     */
    public void takePhoto() {
        try {
            CaptureRequest.Builder builder = camera
                    .createCaptureRequest(
                            CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());
            CaptureRequest request = builder.build();
            session.capture(request, new CameraCaptureSession.CaptureCallback() {
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
}
