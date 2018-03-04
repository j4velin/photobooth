package de.j4velin.photobooth;

import android.content.Context;
import android.graphics.Bitmap;

public interface ICamera {

    /**
     * Trigger taking a new photo
     */
    void takePhoto();

    /**
     * Adds a new callback listener to be informed about new photos
     *
     * @param callback the callback to be notified once the photo is taken
     */
    void addPhotoTakenCallback(final CameraCallback callback);

    /**
     * @return true, if the camera is ready to take pictures
     */
    boolean cameraIsReady();

    /**
     * Stops this camera instance
     *
     * @param context the application context
     */
    void shutdownCamera(final Context context);

    interface CameraCallback {

        /**
         * Called when a new photo was taken
         *
         * @param image the new photo
         */
        void imageReady(final Bitmap image);
    }
}
