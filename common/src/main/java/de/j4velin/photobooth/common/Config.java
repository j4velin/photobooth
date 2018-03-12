package de.j4velin.photobooth.common;

public abstract class Config {
    /**
     * Port of the trigger server
     */
    public final static int TRIGGER_SOCKET_PORT = 5555;

    /**
     * Port of the camera server
     */
    public final static int CAMERA_SOCKET_PORT = 5556;

    /**
     * Time in ms between socket connect retries
     */
    public final static int SOCKET_CONNECT_RETRY_SLEEP = 5000;
}
