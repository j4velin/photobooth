package de.j4velin.photobooth;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main extends Application implements ITrigger.TriggerCallback, ICamera.CameraCallback {
    public final static String TAG = "photobooth";
    public final static int COUNTDOWN_SECONDS = 3;
    private final static int WATCHDOG_TIMEOUT_MS = 10000;

    private final List<ICamera> cameras = new ArrayList<>(2);
    private final List<IDisplay> displays = new ArrayList<>(1);
    private final List<ITrigger> triggers = new ArrayList<>(2);

    private final Comparator<ICamera> cameraComparator = new ICamera.CameraComparator();

    private final static AtomicBoolean STARTED = new AtomicBoolean(false);
    private final static AtomicBoolean TAKING_PHOTO = new AtomicBoolean(false);

    private final static AtomicBoolean TRIGGER_CONNECTED = new AtomicBoolean(false);
    private final static AtomicBoolean EXT_CAMERA_CONNECTED = new AtomicBoolean(false);

    private final static Object PHOTO_READY_LOCK = new Object();

    private ExecutorService photoTaker;
    private ExecutorService watchdog;

    public void start() {
        if (!STARTED.getAndSet(true)) {
            if (BuildConfig.DEBUG) Log.i(Main.TAG, "starting");
            if (BuildConfig.FLAVOR.equalsIgnoreCase("gopro")) {
                addCamera(new GoProCamera(getApplicationContext()));
            } else {
                startService(new Intent(getApplicationContext(), SocketCameraService.class));
            }
            startService(new Intent(getApplicationContext(), SocketTriggerService.class));
            photoTaker = Executors.newSingleThreadExecutor();
            watchdog = Executors.newSingleThreadExecutor();
        }
    }

    public void stop() {
        if (STARTED.getAndSet(false)) {
            TAKING_PHOTO.set(false);
            if (BuildConfig.DEBUG) Log.i(Main.TAG, "stopping");
            for (ITrigger trigger : triggers) {
                trigger.disableTrigger();
            }
            for (ICamera camera : cameras) {
                camera.shutdownCamera(this);
            }
            triggers.clear();
            cameras.clear();
            displays.clear();
            photoTaker.shutdown();
            watchdog.shutdown();
        }
    }

    public void addCamera(final ICamera camera) {
        cameras.add(Objects.requireNonNull(camera, "camera must not be null"));
        Collections.sort(cameras, cameraComparator);
        camera.addPhotoTakenCallback(this);
    }

    public void removeCamera(final ICamera camera) {
        cameras.remove(Objects.requireNonNull(camera, "camera must not be null"));
    }

    public void addDisplay(final IDisplay display) {
        displays.add(Objects.requireNonNull(display, "display must not be null"));
        updateConnectionState();
    }

    public void removeDisplay(final IDisplay display) {
        displays.remove(Objects.requireNonNull(display, "display must not be null"));
    }

    public void addTrigger(final ITrigger trigger) {
        triggers.add(Objects.requireNonNull(trigger, "trigger must not be null"));
        trigger.enableTrigger(this);
    }

    public void removeTrigger(final ITrigger trigger) {
        triggers.remove(Objects.requireNonNull(trigger, "trigger must not be null"));
        trigger.disableTrigger();
    }

    @Override
    public void takePhoto() {
        if (TAKING_PHOTO.getAndSet(true)) {
            if (BuildConfig.DEBUG) Log.w(Main.TAG,
                    "Already taking a photo -> skip this trigger");
        } else {
            if (BuildConfig.DEBUG) Log.d(Main.TAG, "Taking photo...");
            photoTaker.execute(takePhoto);
        }
    }

    void updateExtCameraConnectionState(boolean connected) {
        EXT_CAMERA_CONNECTED.set(connected);
        updateConnectionState();
    }

    void updateTriggerConnectionState(boolean connected) {
        TRIGGER_CONNECTED.set(connected);
        updateConnectionState();
    }

    private void updateConnectionState() {
        for (IDisplay display : displays) {
            display.updateStatus(TRIGGER_CONNECTED.get(), EXT_CAMERA_CONNECTED.get());
        }
    }

    @Override
    public void imageReady(final Bitmap image) {
        if (BuildConfig.DEBUG) Log.d(Main.TAG, "Image ready");
        TAKING_PHOTO.set(false);
        synchronized (PHOTO_READY_LOCK) {
            PHOTO_READY_LOCK.notifyAll();
        }
        for (IDisplay display : displays) {
            display.displayImage(image);
        }
    }

    @Override
    public void error() {
        if (BuildConfig.DEBUG) Log.e(Main.TAG, "error while taking photo");
        if (TAKING_PHOTO.getAndSet(false)) {
            for (IDisplay display : displays) {
                display.error();
            }
        }
    }

    private final Runnable watchdogTimer = new Runnable() {
        @Override
        public void run() {
            synchronized (PHOTO_READY_LOCK) {
                try {
                    PHOTO_READY_LOCK.wait(WATCHDOG_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            if (TAKING_PHOTO.get()) {
                if (BuildConfig.DEBUG) Log.e(Main.TAG, "watchdog timeout");
                error();
            }
        }
    };

    private final Runnable takePhoto = new Runnable() {
        @Override
        public void run() {
            try {
                for (ICamera camera : cameras) {
                    camera.preparePhoto();
                }
                for (IDisplay display : displays) {
                    display.showCountdown();
                }
                try {
                    Thread.sleep(COUNTDOWN_SECONDS * 1000);
                } catch (InterruptedException e) {
                    // ignore
                }
                for (IDisplay display : displays) {
                    display.showWait();
                }
                boolean mainCameraReady = false;
                watchdog.execute(watchdogTimer);
                for (ICamera camera : cameras) {
                    if (mainCameraReady && camera.getCameraType() == ICamera.Type.Backup) {
                        break;
                    }
                    if (camera.cameraIsReady()) {
                        camera.takePhoto();
                        if (camera.getCameraType() == ICamera.Type.Main) {
                            mainCameraReady = true;
                        }
                    }
                }
            } catch (RejectedExecutionException | ConcurrentModificationException e) {
                // might be thrown when shutting down while trying to take a photo
                // -> can be ignored as we're shutting down anyway
            }
        }
    };
}
