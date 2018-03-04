package de.j4velin.photobooth;

import android.graphics.Bitmap;

public interface IDisplay {

    /**
     * Call to briefly display a new image
     *
     * @param image the new image to display
     */
    void displayImage(Bitmap image);
}
