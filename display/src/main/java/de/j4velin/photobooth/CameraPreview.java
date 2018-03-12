package de.j4velin.photobooth;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraCharacteristics;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import de.j4velin.photobooth.common.CameraUtil;

public class CameraPreview extends Activity implements ITrigger, ICamera, IDisplay {

    private final static int REQUEST_CODE_CAMERA_PERMISSION = 1;
    private final static boolean TRIGGER_ENABLED = true;

    private final CameraUtil cameraUtil = new CameraUtil();

    private TextureView cameraView;
    private ImageView imageView;
    private View progressbar;
    private boolean camera_enabled = true;

    private final List<ICamera.CameraCallback> cameraCallbacks = new ArrayList<>(1);
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((Main) getApplication()).start();
        setContentView(R.layout.activity_main);
        progressbar = findViewById(R.id.progressBar);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ((Main) getApplication()).stop();
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
            setupCamera();
        }
        Main mainClass = (Main) getApplication();
        if (TRIGGER_ENABLED)
            mainClass.addTrigger(this);
        mainClass.addCamera(this);
        mainClass.addDisplay(this);
    }

    private void setupCamera() {
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
        cameraUtil.setup(this, CameraCharacteristics.LENS_FACING_FRONT,
                new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(final ImageReader imageReader) {
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
                }, cameraView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraUtil.shutdown();
        Main mainClass = (Main) getApplication();
        mainClass.removeCamera(this);
        mainClass.removeDisplay(this);
        mainClass.removeTrigger(this);
        cameraCallbacks.clear();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupCamera();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void enableTrigger(final TriggerCallback callback) {
        if (cameraView != null) {
            cameraView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    callback.takePhoto();
                }
            });
        } else if (BuildConfig.DEBUG) Log.e(Main.TAG,
                "Cant enable trigger: cameraView is still null");
    }

    @Override
    public void disableTrigger() {
        if (cameraView != null) {
            cameraView.setClickable(false);
        }
    }

    @Override
    public void takePhoto() {
        if (cameraIsReady()) {
            cameraUtil.takePhoto();
        }
    }

    @Override
    public void addPhotoTakenCallback(final CameraCallback callback) {
        cameraCallbacks.add(callback);
    }

    @Override
    public boolean cameraIsReady() {
        return camera_enabled && cameraUtil.isOperational();
    }

    @Override
    public void shutdownCamera(final Context context) {
        camera_enabled = false;
    }

    @Override
    public Type getCameraType() {
        return Type.Backup;
    }

    @Override
    public void displayImage(final Bitmap image) {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap rotated = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(),
                matrix, true);
        final Drawable drawable = new BitmapDrawable(getResources(), rotated);
        final long tag = System.currentTimeMillis(); // "reset" timer by assigning a new tag
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                imageView.setImageDrawable(drawable);
                imageView.setVisibility(View.VISIBLE);
                imageView.setTag(tag);
                progressbar.setVisibility(View.GONE);
            }
        });
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (imageView.getTag().equals(tag)) {
                    imageView.setVisibility(View.GONE);
                }
            }
        }, 5000);
    }

    @Override
    public void showWait() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressbar.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void abortShowWait() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressbar.setVisibility(View.GONE);
            }
        });
    }
}
