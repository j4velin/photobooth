package de.j4velin.photobooth;

import android.graphics.Bitmap;

/**
 * An interface for all objects, which can display an image
 */
public interface IDisplay {

    /**
     * Call to briefly display a new image
     *
     * @param image the new image to display
     */
    void displayImage(Bitmap image);

    /**
     * Call to show a "please wait" dialog
     */
    void showWait();

    /**
     * Shows a countdown dialog
     * <p>
     * The countdown time is set in {@link Main#COUNTDOWN_SECONDS}
     */
    void showCountdown();

    /**
     * An error occurred. Cancels any dialog
     */
    void error();
}
