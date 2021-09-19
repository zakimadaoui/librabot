package com.example.pidbbotcontroller;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static com.example.pidbbotcontroller.BluetoothConnectionService.ACTION_CONNECTED;
import static com.example.pidbbotcontroller.BluetoothConnectionService.ACTION_CONNECTION_STARTED;
import static com.example.pidbbotcontroller.BluetoothConnectionService.ACTION_DISCONNECTED;
import static com.example.pidbbotcontroller.BluetoothConnectionService.ACTION_FAILED;
import static com.example.pidbbotcontroller.BluetoothConnectionService.MY_UUID_INSECURE;

public class ConnectionActivity extends AppCompatActivity implements BluetoothConnectionService.OnConnectionChangeListener, BluetoothConnectionService.OnConnectionAcceptedListener, BluetoothConnectionService.onReceiveListener {
    BluetoothAdapter btAdapter;
    private List<BluetoothDevice> bondedDevices = new ArrayList<>();
    BluetoothConnectionService connectionService;
    private BluetoothDevice remoteDevice = null;
    public ProgressDialog mDialog;
    public TextView status_txtvw;
    private boolean connected = false;
    private static final String TAG = "ConnectionActivity";
    private Button connect_btn;
    private StringBuilder receivedMessage = new StringBuilder();
    private boolean serviceInstantiated = false;
    private boolean handShacked = false;
    private ConstraintLayout connection_layout;
    private RecyclerView bondedDevicesRecycler;
    private DeviceAdapter bondedDevicesAdapter;

    private void instantiateService(){
        if (!serviceInstantiated){
            connectionService = BluetoothConnectionService.getInstance();
            connectionService.setOnReceiveListener(this);
            connectionService.setOnConnectionChangeListener(ConnectionActivity.this);
            serviceInstantiated = true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);

        //request Bluetooth to be enabled
        enableBluetooth();

        connection_layout = findViewById(R.id.connection_layout);
        bondedDevicesRecycler = findViewById(R.id.devices_recycler);
        connect_btn = findViewById(R.id.connect_btn);
        status_txtvw = findViewById(R.id.connection_status_txtvw);
        mDialog = new ProgressDialog(this);

        findViewById(R.id.back_button).setOnClickListener(view -> finish());

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter.isEnabled()){
            instantiateService();
            if (connectionService.isConnected()){
                remoteDevice = connectionService.getRemoteDevice();
                connected = true;
                status_txtvw.setText("Connected to " + remoteDevice.getName());
                status_txtvw.setTextColor(Color.GREEN);
                connect_btn.setText("Disconnect form " + remoteDevice.getName());
            }
        }


        //get bonded devices && inflate recycler
//        bondedDevices.addAll(btAdapter.getBondedDevices());
        bondedDevicesAdapter = new DeviceAdapter();
//        bondedDevicesAdapter.setDevices(bondedDevices);
        bondedDevicesRecycler.setAdapter(bondedDevicesAdapter);
        bondedDevicesRecycler.setLayoutManager(new LinearLayoutManager(this));


        connect_btn.setOnClickListener(view -> {

            if (connected){
                connectionService.disconnect();
                connect_btn.setText("Connect to paired device");
                status_txtvw.setText("Disconnected");
                status_txtvw.setTextColor(Color.RED);
                connected = false;

            }else{

                if (btAdapter.isEnabled()) {
                    bondedDevicesRecycler.setVisibility(View.VISIBLE);
                    connection_layout.setVisibility(View.GONE);
                    bondedDevicesAdapter.setDevices(bondedDevices);
                    bondedDevicesAdapter.notifyDataSetChanged();

                    bondedDevicesAdapter.setOnDeviceSelectedListener(device -> {
                        mDialog.setMessage("Connecting ...");
                        mDialog.show();

                        remoteDevice = device;
                        instantiateService();
                        connectionService.startClient(device, MY_UUID_INSECURE);
                        bondedDevicesRecycler.setVisibility(View.GONE);
                        connection_layout.setVisibility(View.VISIBLE);

                    });
                } else {
                    enableBluetooth();
                }
            }

        });


        findViewById(R.id.recieve_connection_button).setOnClickListener(view -> {

            if (btAdapter.isEnabled()) {
                mDialog.setMessage("Waiting for connection ...");
                mDialog.show();
                instantiateService();
                connectionService.setOnConnectionAcceptedListener(ConnectionActivity.this);
            } else {
                enableBluetooth();
            }
        });

    }


    void enableBluetooth() {
        startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 27);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 27){
            bondedDevices.addAll(btAdapter.getBondedDevices());
            bondedDevicesAdapter.setDevices(bondedDevices);
            bondedDevicesAdapter.notifyDataSetChanged();

        }
    }

    @Override
    public void onBackPressed() {
        if (connection_layout.getVisibility() == View.VISIBLE) finish();
        else {
            bondedDevicesRecycler.setVisibility(View.GONE);
            connection_layout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onChange(String Action) {
        switch (Action) {

            case ACTION_CONNECTION_STARTED:
                break;

            case ACTION_CONNECTED:

                mDialog.setMessage("Connection successful !");
                connected = true;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    status_txtvw.setText("Connected to " + remoteDevice.getName());
                    status_txtvw.setTextColor(Color.GREEN);
                    connect_btn.setText("Disconnect form " + remoteDevice.getName());
                    mDialog.dismiss();
                }, 1000);

//                status_txtvw.setTextColor(Color.rgb(0, 223, 50));

                break;

            case ACTION_FAILED:
                mDialog.setMessage("Connection Failed !");
                new Handler(Looper.getMainLooper()).postDelayed(() -> mDialog.dismiss(), 1000);
                break;

            case ACTION_DISCONNECTED:
                handShacked = false;
        }
    }

    @Override
    public void onAccepted(BluetoothDevice remoteDevice) {
        this.remoteDevice = remoteDevice;
    }

    @Override
    public void onReceive(byte[] bytes, int numberOfBytes, String strMessage) {

        String message = "";
        for (int j = 0; j < numberOfBytes; j++) {
            //Log.d(TAG, "onReceive: next char is: "+ (char)bytes[j] +" in bytes it is: "+ bytes[j] + "and nbr of bytes is: "+ numberOfBytes);
            if (bytes[j] == '\n'){
                message = receivedMessage.toString();
                receivedMessage = new StringBuilder();
            }
            else if (bytes[j] != (byte)13){
                receivedMessage.append((char) bytes[j]);
            }
        }


        if (bytes[0] == 'h' && !handShacked) {
            connectionService.write((byte) 'i');
            handShacked = true;
        }

        if (message.equals("setmeup")){
            Log.d(TAG, "onReceive: message is equale to setmeup");

            SharedPreferences pr = PreferenceManager.getDefaultSharedPreferences(this);
            String ref_angle = String.valueOf((int)(Float.parseFloat(pr.getString(SettingsActivity.REF_ANGLE_PREF,"0.0"))*100));
            String deadGapAngle = String.valueOf((int)(Float.parseFloat(pr.getString(SettingsActivity.DEAD_GAP_PREF,"0.0"))*100));
            String vmax = pr.getString(SettingsActivity.VMAX_PREF,"0.0");
            String imax = pr.getString(SettingsActivity.IMAX_PREF,"0.0");
            String delay = pr.getString(SettingsActivity.DELAY_PREF,"0.0");
            String kp = pr.getString(SettingsActivity.KP_PREF,"0.0");
            String ki = pr.getString(SettingsActivity.KI_PREF,"0.0");
            String kd = pr.getString(SettingsActivity.KD_PREF,"0.0");

            connectionService.println(ref_angle);
            connectionService.println(deadGapAngle);
            connectionService.println(vmax);
            connectionService.println(imax);
            connectionService.println(delay);
            connectionService.println(kp);
            connectionService.println(ki);
            connectionService.println(kd);

        }
        if (!message.isEmpty())Log.d(TAG, "onReceive: " + message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectionService != null) connectionService.unregisterOnConnectionChangeListener(this);
        if (connectionService != null) connectionService.unregisterOnReceiveListener(this);
    }
}