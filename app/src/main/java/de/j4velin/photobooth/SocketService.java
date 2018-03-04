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

public class SocketService extends Service implements ITrigger {

    private final static int SOCKET_PORT = 5555;
    private final static String TAKE_PHOTO_COMMAND = "TAKE_PHOTO";
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
                    serverSocket = new ServerSocket(SOCKET_PORT);
                    while (!serverSocket.isClosed()) {
                        Socket clientSocket = serverSocket.accept();
                        if (BuildConfig.DEBUG) Log.i(Main.TAG,
                                "Socket connection established to " + clientSocket);
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(clientSocket.getInputStream()));
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            if (inputLine.equalsIgnoreCase(TAKE_PHOTO_COMMAND)) {
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
