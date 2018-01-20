package de.j4velin.photobooth;

public interface ITrigger {

    /**
     * Enables the trigger
     *
     * @param callback the callback to be notified if the trigger has been triggered
     */
    void enableTrigger(final TriggerCallback callback);

    /**
     * Disables the trigger
     */
    void disableTrigger();

    interface TriggerCallback {

        /**
         * Called once a trigger has been triggered
         */
        void takePhoto();
    }
}
