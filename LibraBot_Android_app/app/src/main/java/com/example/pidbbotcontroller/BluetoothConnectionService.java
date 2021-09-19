package com.example.pidbbotcontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothConnectionService {
    public static final String ACTION_CONNECTED = "ACTION_CONNECTED";
    public static final String ACTION_CONNECTION_STARTED = "ACTION_CONNECTION_STARTED";
    public static final String ACTION_DISCONNECTED = "ACTION_DISCONNECTED";
    public static final String ACTION_FAILED = "ACTION_FAILED";
    private static final String TAG = "BluetoothConnectionServ";
    private static final String appname = "LibraBOT";
    public static final UUID MY_UUID_INSECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static BluetoothAdapter bluetoothAdapter;
    private static BluetoothDevice bluetoothDevice;
    private static UUID deviceUUID;
    private static AcceptThread mInsecureAcceptThread;
    private static ConnectThread mConnectThread;
    private static ConnectedThread mConnectedThread;
    public static BluetoothConnectionService instance;
    private List<onReceiveListener> receiveListeners = new ArrayList<>();
    private List<OnConnectionChangeListener> onChangeListeners = new ArrayList<>();
    private OnConnectionAcceptedListener acceptedListener;
    private boolean isConnected = false;
    private BluetoothDevice remoteDevice = null;
    private WriteThread writeThread;
    private boolean busIsFree = true;

    public boolean isConnected() {
        return isConnected;
    }

    public BluetoothDevice getRemoteDevice() {
        return remoteDevice;
    }

    public static BluetoothConnectionService getInstance() {
        if (instance == null) {
            instance = new BluetoothConnectionService();
        }
        return instance;
    }

    private BluetoothConnectionService() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        init();
    }

    private synchronized void init() {
        //stop any thread trying to make a connection and restart acceptThread
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }
    }


    private class ConnectThread extends Thread {// connecting to other server(guest Mode)
        private BluetoothSocket mmSocket = null;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            bluetoothDevice = device;
            remoteDevice = device;
            deviceUUID = uuid;
        }

        @Override
        public void run() {

            try {
                mmSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(deviceUUID);
            } catch (IOException e) {
                e.printStackTrace();
                onChange(ACTION_FAILED);
                isConnected = false;
            }

            //stop discovery and connect
            bluetoothAdapter.cancelDiscovery();
            try {
                //this is a blocking call and it will only
                // return on a successful connection or an exception
                mmSocket.connect();
                mConnectedThread = new ConnectedThread(mmSocket);
                mConnectedThread.start();

                Log.d(TAG, "run: BT Connection successful");
                onChange(ACTION_CONNECTED);
                isConnected = true;

            } catch (IOException e) {
                Log.d(TAG, "run: BT Connection failed" + e.getMessage());
                try {
                    mmSocket.close();
                } catch (IOException ex) {
                    Log.d(TAG, "run: unable to close socket after connection failure "
                            + ex.getMessage());
                }
                onChange(ACTION_FAILED);
                isConnected = false;
            }

        }

        public void cancel() {
            try {
                mmSocket.close();
                onChange(ACTION_DISCONNECTED);
                isConnected = false;
            } catch (IOException ex) {
                Log.d(TAG, "run: unable to close socket "
                        + ex.getMessage());
            }
        }
    }

    private class AcceptThread extends Thread { //accepting connection request here (host Mode)

        private final BluetoothServerSocket mmServerSocket;

        private AcceptThread() {
            BluetoothServerSocket temp = null;
            try {
                //listen for connections to local BT server
                temp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appname, MY_UUID_INSECURE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmServerSocket = temp;
        }

        @Override
        public void run() {
            BluetoothSocket socket = null;

            try {//this is a blocking call and it will only
                // return on a successful connection or an exception
                Log.d(TAG, "run: RFCOM server socket start..... ");
                bluetoothAdapter.startDiscovery();
                Log.d(TAG, "run: " + bluetoothAdapter.getAddress());
                socket = mmServerSocket.accept();
                Log.d(TAG, "run: connected to a device");


            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "run: accept thread failed to serice");
            }

            if (socket != null) {
                acceptedListener.onAccepted(socket.getRemoteDevice());
                remoteDevice = socket.getRemoteDevice();
                onChange(ACTION_CONNECTED);
                isConnected = true;
                mConnectedThread = new ConnectedThread(socket);
                mConnectedThread.start();
            } else {
                onChange(ACTION_FAILED);
                isConnected = false;
            }

        }

        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread {//handel read and write

        private final BluetoothSocket mmSocket;
        private final InputStream mmInputStream;
        private final OutputStream mmOutputStream;
        private boolean receive = true;


        private ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tempIn = null;
            OutputStream tempOut = null;
            try {
                tempIn = mmSocket.getInputStream();
                tempOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmInputStream = tempIn;
            mmOutputStream = tempOut;
        }

        @Override
        public void run() {


            while (receive) { //keep listening for incoming bytes
                try {
                    int nbrBytes; //number of bytes returned from read();
                    int i = 0;
                    byte[] bytes = new byte[2048];//2kb buffer
                    nbrBytes = mmInputStream.read(bytes);
                    for (int j = 0; j < nbrBytes; j++) {
                        Log.d(TAG, "run: next char is: "+ (char)bytes[j] +" in bytes it is: "+ bytes[j] + "and nbr of bytes is: "+ nbrBytes);
                    }

                    Log.d(TAG, "run: recieve listener are: " + receiveListeners.size());

                    for (onReceiveListener listener : receiveListeners) {
                        try {
                            listener.onReceive(bytes, nbrBytes, "");
                        } catch (Exception ignored) {
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    disconnect();
                    Log.d(TAG, "run: connection lost!");
                    break;
                }
            }
        }

        public void write(byte[] bytes) {

            try {
                mmOutputStream.write(bytes);
                String text = new String(bytes, Charset.defaultCharset());
                Log.d(TAG, "write: " + text);

            } catch (IOException e) {
                Log.e(TAG, "write: error writing to outputStream " + e.getMessage());
            }
        }

        public void cancel() {
            receive = false;
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void startClient(BluetoothDevice device, UUID uuid) {

        onChange(ACTION_CONNECTION_STARTED);
        mConnectThread = new ConnectThread(device, uuid);
        mConnectThread.start();
    }

    public void disconnect() {
        if(mConnectedThread != null) mConnectedThread.cancel();
        if (mConnectThread != null )mConnectThread.cancel();
        mConnectedThread = null;
        onChange(ACTION_DISCONNECTED);
        isConnected = false;
    }

    public interface onReceiveListener {
        void  onReceive(byte[] bytes, int numberOfBytes, String strMessage);
    }

    public void setOnReceiveListener(onReceiveListener listener) {
        if (!receiveListeners.contains(listener)) receiveListeners.add(listener);
    }
    public void unregisterOnReceiveListener(onReceiveListener listener) {
        if (receiveListeners.contains(listener)) receiveListeners.remove(listener);
    }


    //overloading write() for general write/print functionality
    public void write(byte[] bytes) {
        mConnectedThread.write(bytes);
    }

    public void write(byte b) {
        byte[] bytes = {b};
        mConnectedThread.write(bytes);
    }

    public void print(String out) {
        byte[] bytes = out.getBytes();
        mConnectedThread.write(bytes);
    }

    public void println(String out) {
        byte[] bytes = out.concat("\n").getBytes();
        mConnectedThread.write(bytes);
    }

    //######################################################################
    public interface OnConnectionChangeListener {
        void onChange(String Action);
    }

    public void setOnConnectionChangeListener(OnConnectionChangeListener listener) {
        if(!onChangeListeners.contains(listener)) onChangeListeners.add(listener);
    }
    
    public void unregisterOnConnectionChangeListener(OnConnectionChangeListener listener) {
        if(onChangeListeners.contains(listener)) onChangeListeners.remove(listener);
    }
    
    

    private void onChange(String Action) {
        for (OnConnectionChangeListener l : onChangeListeners) {
            try {
                l.onChange(Action);
            } catch (Exception ignored) {
            }
        }
    }

    public interface OnConnectionAcceptedListener {
        void onAccepted(BluetoothDevice remoteDevice);
    }

    public void setOnConnectionAcceptedListener(OnConnectionAcceptedListener listener) {
        acceptedListener = listener;
    }

    private class WriteThread extends Thread{

        byte[] bytes;
        final OutputStream out;
        WriteThread(byte[] bytes, OutputStream out) {
            this.bytes = bytes;
            this.out = out;
        }
        @Override
        public void run() {
            try {
                    if (busIsFree) busIsFree = false;
                    else while(!busIsFree){
                        Log.d(TAG, "run: bus is not free waiting ....");
                    }
                    out.write(bytes);
                    String text = new String(bytes, Charset.defaultCharset());
                    Log.d(TAG, "write: " + text);
                    busIsFree = true;

            } catch (IOException e) {
                Log.e(TAG, "write: error writing to outputStream " + e.getMessage());
            }
        }
    }
}
