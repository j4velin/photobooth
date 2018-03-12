package de.j4velin.photobooth;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import de.j4velin.photobooth.common.Config;
import de.j4velin.photobooth.common.Const;

/**
 * Server component for all socket base camera (for example an external Android device)
 */
public class SocketCameraService extends Service implements ICamera {

    private final List<ExternalCameraDevice> cameras = new ArrayList<>(1);
    private final List<CameraCallback> cameraCallbacks = new ArrayList<>(1);
    private ServerSocket serverSocket;

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        Main main = (Main) getApplication();
        main.addCamera(this);
        start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        shutdownCamera(this);
        super.onDestroy();
    }

    private void start() {
        if (BuildConfig.DEBUG) Log.i(Main.TAG,
                "Starting camera socket on port " + Config.CAMERA_SOCKET_PORT);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(Config.CAMERA_SOCKET_PORT);
                    while (!serverSocket.isClosed()) {
                        Socket clientSocket = serverSocket.accept();
                        if (BuildConfig.DEBUG) Log.i(Main.TAG,
                                "Socket connection established to " + clientSocket);
                        synchronized (cameras) {
                            cameras.add(new ExternalCameraDevice(clientSocket));
                        }
                    }
                } catch (IOException e) {
                    if (BuildConfig.DEBUG)
                        e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void takePhoto() {
        synchronized (cameras) {
            for (ExternalCameraDevice c : cameras) {
                c.takePhotoThread.start();
            }
        }
    }

    @Override
    public void addPhotoTakenCallback(final CameraCallback callback) {
        cameraCallbacks.add(callback);
    }

    @Override
    public boolean cameraIsReady() {
        return !cameras.isEmpty();
    }

    @Override
    public void shutdownCamera(final Context context) {
        if (BuildConfig.DEBUG) Log.i(Main.TAG,
                "Shutdown SocketCameraService");
        synchronized (cameras) {
            for (ExternalCameraDevice c : cameras) {
                try {
                    c.close();
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) e.printStackTrace();
                }
            }
            cameras.clear();
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
        }
        stopSelf();
    }

    @Override
    public Type getCameraType() {
        return Type.Main;
    }

    private class ExternalCameraDevice implements Closeable {
        private final DataInputStream in;
        private final BufferedWriter out;
        private final Thread takePhotoThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (BuildConfig.DEBUG) Log.d(Main.TAG,
                        "Sending TAKE_PHOTO command over socket connection");
                try {
                    out.write(Const.TAKE_PHOTO_COMMAND);
                    out.newLine();
                    out.flush();
                    int length = in.readInt();
                    byte[] message = new byte[length];
                    in.readFully(message, 0, message.length);
                    Bitmap image = BitmapFactory.decodeByteArray(message, 0, length);
                    for (CameraCallback cb : cameraCallbacks) {
                        cb.imageReady(image);
                    }
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(Main.TAG, "Can send take photo cmd: " + e.getClass()
                                .getSimpleName() + " - " + e.getMessage());
                    }
                    for (CameraCallback cb : cameraCallbacks) {
                        cb.error();
                    }
                    synchronized (cameras) {
                        cameras.remove(ExternalCameraDevice.this);
                    }
                    try {
                        close();
                    } catch (IOException e1) {
                    }
                }
            }
        });

        private ExternalCameraDevice(final Socket socket) throws IOException {
            in = new DataInputStream(socket.getInputStream());
            out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream()));
        }

        @Override
        public void close() throws IOException {
            if (BuildConfig.DEBUG) {
                Log.d(Main.TAG, "Closing socket connection");
            }
            try {
                this.in.close();
            } catch (Exception e) {
                if (BuildConfig.DEBUG) e.printStackTrace();
            }
            try {
                this.out.close();
            } catch (Exception e) {
                if (BuildConfig.DEBUG) e.printStackTrace();
            }
        }
    }
}
