package de.j4velin.photobooth;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Main extends Application implements ITrigger.TriggerCallback, ICamera.CameraCallback {
    public final static String TAG = "photobooth";

    private final List<ICamera> cameras = new ArrayList<>(2);
    private final List<IDisplay> displays = new ArrayList<>(1);

    @Override
    public void onCreate() {
        super.onCreate();
        cameras.add(new GoProCamera(getApplicationContext()));
    }

    public void addCamera(final ICamera camera) {
        cameras.add(Objects.requireNonNull(camera, "camera must not be null"));
        camera.addPhotoTakenCallback(this);
    }

    public void removeCamera(final ICamera camera) {
        cameras.remove(camera);
    }

    public void addDisplay(final IDisplay display) {
        displays.add(Objects.requireNonNull(display, "camera must not be null"));
    }

    public void removeDisplay(final IDisplay display) {
        displays.remove(display);
    }

    @Override
    public void takePhoto() {
        if (BuildConfig.DEBUG) Log.d(Main.TAG, "Taking photo...");
        for (ICamera camera : cameras) {
            if (camera.cameraIsReady()) {
                camera.takePhoto();
            }
        }
    }

    @Override
    public void imageReady(Bitmap image) {
        if (BuildConfig.DEBUG) Log.d(Main.TAG, "Image ready");
        Drawable d = new BitmapDrawable(getResources(), image);
        for (IDisplay display : displays) {
            display.displayImage(d);
        }
    }
}
