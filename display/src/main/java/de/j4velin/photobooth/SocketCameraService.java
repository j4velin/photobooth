package de.j4velin.photobooth;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

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
 * Server component for all socket based cameras (for example an external Android device acting as camera)
 */
public class SocketCameraService extends Service implements ICamera {

    private final List<ExternalCameraDevice> cameras = new ArrayList<>(2);
    private final List<CameraCallback> cameraCallbacks = new ArrayList<>(2);
    private final Handler handler = new Handler(Looper.getMainLooper());
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
                        try {
                            final Socket clientSocket = serverSocket.accept();
                            clientSocket.setTcpNoDelay(true);
                            if (BuildConfig.DEBUG) Log.i(Main.TAG,
                                    "Socket connection established to " + clientSocket);
                            ExternalCameraDevice camera = new ExternalCameraDevice(clientSocket);
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                // ignore
                            }
                            // check if camera is still connected (might have been just a portscan)
                            try {
                                camera.out.write(Const.COMMAND_PING);
                                camera.out.newLine();
                                camera.out.flush();
                                camera.in.readUTF();
                                if (BuildConfig.DEBUG) Log.i(Main.TAG,
                                        "ping to " + clientSocket + " successful");
                            } catch (IOException e) {
                                if (BuildConfig.DEBUG) Log.i(Main.TAG,
                                        clientSocket + " was portscan -> ignore");
                                camera.close();
                                continue;
                            }
                            synchronized (cameras) {
                                cameras.add(camera);
                            }
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(SocketCameraService.this,
                                            "Camera connected " + clientSocket.getLocalAddress(),
                                            Toast.LENGTH_SHORT)
                                            .show();
                                }
                            });
                        } catch (Throwable t) {
                            if (BuildConfig.DEBUG) {
                                Log.e(Main.TAG,
                                        "Error accepting socket connection: " + t.getMessage());
                                t.printStackTrace();
                            }
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
                c.close();
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
                    out.write(Const.COMMAND_TAKE_PHOTO);
                    out.newLine();
                    out.flush();
                    int length = in.readInt();
                    byte[] message = new byte[length];
                    in.readFully(message, 0, message.length);
                    Bitmap image = BitmapFactory.decodeByteArray(message, 0, length);
                    for (CameraCallback cb : cameraCallbacks) {
                        cb.imageReady(image);
                    }
                } catch (final Exception e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(Main.TAG, "Can send take photo cmd: " + e.getClass()
                                .getSimpleName() + " - " + e.getMessage());
                    }
                    boolean errorOnLastCamera;
                    synchronized (cameras) {
                        cameras.remove(ExternalCameraDevice.this);
                        errorOnLastCamera = cameras.isEmpty();
                    }
                    if (errorOnLastCamera) {
                        for (CameraCallback cb : cameraCallbacks) {
                            cb.error();
                        }
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(SocketCameraService.this,
                                        "Camera disconnected: " + e.getMessage(),
                                        Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                    }
                    close();
                }
            }
        });

        private ExternalCameraDevice(final Socket socket) throws IOException {
            in = new DataInputStream(socket.getInputStream());
            out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream()));
        }

        @Override
        public void close() {
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
