package com.example.pidbbotcontroller;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Objects;


public class ControllerActivity extends AppCompatActivity implements BluetoothConnectionService.onReceiveListener {

    private static final String TAG = "ControllerActivity";
    public SeekBar.OnSeekBarChangeListener seekBarChangeListener;
    private BluetoothConnectionService serial ;
    private StringBuilder receivedMessage = new StringBuilder();
    private boolean start = true;
    private AlertDialog console_dialog;
    private TextView console_textvw;
    private StringBuilder console_logs = new StringBuilder();
    private boolean handShacked = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        Joystick joystick = findViewById(R.id.joystick);
        MySeekBar p_seekbar = findViewById(R.id.p_seekbar);
        MySeekBar i_seekbar = findViewById(R.id.i_seekbar);
        MySeekBar d_seekbar = findViewById(R.id.d_seekbar);
        final TextView p_TextView = findViewById(R.id.p_txtvw);
        final TextView i_TextView = findViewById(R.id.i_txtvw);
        final TextView d_TextView = findViewById(R.id.d_txtvw);


        //getting initial gains:
        SharedPreferences pr = PreferenceManager.getDefaultSharedPreferences(this);
        int kp = Integer.parseInt(pr.getString(SettingsActivity.KP_PREF,"0"));
        int ki = Integer.parseInt(pr.getString(SettingsActivity.KI_PREF,"0"));
        int kd = Integer.parseInt(pr.getString(SettingsActivity.KD_PREF,"0"));
        p_seekbar.setProgress(kp);
        p_TextView.setText("P: ".concat(String.valueOf(kp)));
        i_seekbar.setProgress(ki);
        i_TextView.setText("I: ".concat(String.valueOf(ki)));
        d_seekbar.setProgress(kd);
        d_TextView.setText("D: ".concat(String.valueOf(kd)));

        //========================================= FABS ===========================================

        FloatingActionButton start_fab = findViewById(R.id.start_button);
        start_fab.setOnClickListener(view -> {
            if (serial.isConnected()) serial.println("S");
            flipFAB(start_fab);
        });


        buildConsoleDialog();
        FloatingActionButton terminal_fab = findViewById(R.id.terminal_button);
        terminal_fab.setOnClickListener(view -> {
          console_dialog.show();
            if (serial != null && serial.isConnected())
                serial.write((byte)'X');
        });

        findViewById(R.id.back_button).setOnClickListener(view -> finish());

        //======================================= BT stuff =========================================

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter.isEnabled()) {
            serial  = BluetoothConnectionService.getInstance();
            serial.setOnReceiveListener(this);
        }
        else Toast.makeText(this, "Sadi9i ro7 connicti f bluetooth mbe3d arwah !", Toast.LENGTH_LONG).show();


        //==================================== PID seekbars ========================================

        seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                String progress = null;

                Log.d(TAG, "onProgressChanged: ");

                switch (seekBar.getId()){
                    case R.id.p_seekbar:
                        progress = String.format("P: %03d", i);
                        p_TextView.setText(progress);
                        break;
                    case R.id.i_seekbar:
                        progress = String.format("I: %03d", i);
                        i_TextView.setText(progress);
                        break;
                    case R.id.d_seekbar:
                        progress = String.format("D: %03d", i);
                        d_TextView.setText(progress);
                        break;
                }

                if (serial.isConnected()){ serial.println(progress);}
                else Toast.makeText(ControllerActivity.this, "Please connect first !", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        };

        p_seekbar.setOnSeekBarChangeListener(seekBarChangeListener);
        i_seekbar.setOnSeekBarChangeListener(seekBarChangeListener);
        d_seekbar.setOnSeekBarChangeListener(seekBarChangeListener);



        //================================== steer joystick ========================================

        joystick.setStraightOnly(true, Joystick.FLAG_XY);

        final TextView xTxtvw = findViewById(R.id.px_txtvw);
        final TextView yTxtvw = findViewById(R.id.py_txtvw);

        joystick.setOnMoveListener((px, py, angle) -> {
            xTxtvw.setText("Steer: ".concat(String.format("%.2f",px)));
            yTxtvw.setText("Move: ".concat(String.format("%.2f",py)));
            if (serial.isConnected()){

                if (px == 0 && py ==0){
                    serial.println("R:".concat(String.valueOf(0)));
                    serial.println("L:".concat(String.valueOf(0)));
                    serial.println("B:".concat(String.valueOf(0)));
                    serial.println("F:".concat(String.valueOf(0)));
                }
                else if (px < 0) serial.println("L:".concat(String.valueOf((int)-(px*255))));
                else if (px > 0) serial.println("R:".concat(String.valueOf((int)(px*255))));
                else if (py < 0) serial.println("B:".concat(String.valueOf((int)-(py*255))));
                else if (py > 0) serial.println("F:".concat(String.valueOf((int)(py*255))));

            }
        });


    }



    //################################## UTILS FUNCTIONS ###########################################

    private void flipFAB(FloatingActionButton fab) {
        if (start){
            fab.hide(new FloatingActionButton.OnVisibilityChangedListener() {
                @Override
                public void onHidden(FloatingActionButton fab) {
                    fab.show();
                    fab.setImageDrawable(getDrawable(R.drawable.ic_baseline_stop_24));
                    fab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ControllerActivity.this, R.color.stop_red)));
                }
            });
        }
        else{
            fab.hide(new FloatingActionButton.OnVisibilityChangedListener() {
                @Override
                public void onHidden(FloatingActionButton fab) {
                    fab.show();
                    fab.setImageDrawable(getDrawable(R.drawable.ic_baseline_play_arrow_24));
                    fab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ControllerActivity.this, R.color.start_green)));
                }
            });
        }
        start = !start;
    }


    private void buildConsoleDialog(){
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_console, null);
        console_textvw = view.findViewById(R.id.console_textview);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);
        console_dialog = builder.create();
        Objects.requireNonNull(console_dialog.getWindow()).setBackgroundDrawable(getDrawable(R.drawable.transparent));
        console_dialog.setOnDismissListener(dialogInterface -> {
            if (serial != null && serial.isConnected())
                serial.write((byte)'Y');
        });

    }



    //######################################### BT EVENTS ##########################################

    @Override
    public void onReceive(byte[] bytes, int numberOfBytes, String strMessage) {

        String message = "";

        for (int j = 0; j < numberOfBytes; j++) {
            if (bytes[j] == '\n'){
                message = receivedMessage.toString();
                receivedMessage = new StringBuilder();
                console_logs.append("> ".concat(message).concat("\n"));
                if (console_dialog.isShowing()) console_textvw.setText(message);
                Log.d(TAG, "onReceive: " + message);
            }
            else if (bytes[j] != (byte)13){
                receivedMessage.append((char) bytes[j]);
            }
        }



        if (message.equals("h") && !handShacked) {
            serial.write((byte) 'i');
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

            serial.println(ref_angle);
            serial.println(deadGapAngle);
            serial.println(vmax);
            serial.println(imax);
            serial.println(delay);
            serial.println(kp);
            serial.println(ki);
            serial.println(kd);
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serial != null) serial.unregisterOnReceiveListener(this);
    }
}