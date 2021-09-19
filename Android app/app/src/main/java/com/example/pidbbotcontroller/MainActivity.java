package com.example.pidbbotcontroller;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Objects;

import static com.example.pidbbotcontroller.SettingsActivity.REF_ANGLE_PREF;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private StringBuilder receivedMessage = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.controller_button).setOnClickListener(view -> startActivity(new Intent(MainActivity.this, ControllerActivity.class)));

        findViewById(R.id.connection_button).setOnClickListener(view -> startActivity(new Intent(MainActivity.this, ConnectionActivity.class)));

        findViewById(R.id.settings_button).setOnClickListener(view -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        findViewById(R.id.calibration_button).setOnClickListener(view -> {
            BluetoothConnectionService serial = BluetoothConnectionService.getInstance();
            if (serial.isConnected()) {
                AlertDialog dialog1 = buildDialog("CALIBRATE_ME", (dialogInterface, i) -> {
                    final AlertDialog dialog2 = buildDialog("Dismiss", null);

                    BluetoothConnectionService.onReceiveListener receiveListener = (bytes, numberOfBytes, strMessage) -> {
                        String message = "";
                        for (int j = 0; j < numberOfBytes; j++) {
                            if (bytes[j] == '\n') {
                                try {
                                    message = receivedMessage.toString();
                                    receivedMessage = new StringBuilder();
                                    String value = String.valueOf(Double.parseDouble(message));
                                    SharedPreferences pr = PreferenceManager.getDefaultSharedPreferences(this);
                                    pr.edit().putString(REF_ANGLE_PREF, value).apply();
                                    dialog2.setMessage("the reference angle is: ".concat(message));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else if (bytes[j] != (byte) 13) {
                                receivedMessage.append((char) bytes[j]);
                            }
                        }
                    };

                    dialog2.setMessage("Calibrating ...");
                    dialog2.show();
                    serial.write((byte) 'C');
                    serial.setOnReceiveListener(receiveListener);
                    dialog2.setOnDismissListener(dialogInterface1 -> serial.unregisterOnReceiveListener(receiveListener));

                });
                dialog1.setTitle("Before Calibration!");
                dialog1.setMessage("Please make sure that the robot is standing exactly right, then click on calibrateMe button.");
                dialog1.show();

            } else Toast.makeText(MainActivity.this, "Please connect to BT first! ",
                    Toast.LENGTH_SHORT).show();
        });
    }


    //========================================= UTILS ==============================================
    AlertDialog buildDialog(String button_text, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setPositiveButton(button_text, listener);
        AlertDialog dialog = builder.create();
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(getDrawable(R.drawable.card_bg));
        return dialog;
    }
}