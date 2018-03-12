package de.j4velin.photobooth;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import de.j4velin.photobooth.common.Config;
import de.j4velin.photobooth.common.Const;

/**
 * Server component for socket base triggers (for example a Raspberry Pi with a button)
 */
public class SocketTriggerService extends Service implements ITrigger {

    private ServerSocket serverSocket;

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        Main main = (Main) getApplication();
        main.addTrigger(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        disableTrigger();
        super.onDestroy();
    }

    @Override
    public void enableTrigger(final TriggerCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(Config.TRIGGER_SOCKET_PORT);
                    while (!serverSocket.isClosed()) {
                        Socket clientSocket = serverSocket.accept();
                        if (BuildConfig.DEBUG) Log.i(Main.TAG,
                                "Socket connection established to " + clientSocket);
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(clientSocket.getInputStream()));
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            if (inputLine.equalsIgnoreCase(Const.TAKE_PHOTO_COMMAND)) {
                                callback.takePhoto();
                            } else if (BuildConfig.DEBUG) {
                                Log.w(Main.TAG,
                                        "Ignoring unknown command: " + inputLine);
                            }
                        }
                        if (BuildConfig.DEBUG) Log.i(Main.TAG, "Socket connection closed");
                    }
                } catch (IOException e) {
                    if (BuildConfig.DEBUG)
                        e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void disableTrigger() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
        }
        stopSelf();
    }
}
