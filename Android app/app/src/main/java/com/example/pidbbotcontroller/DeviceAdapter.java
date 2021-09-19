package com.example.pidbbotcontroller;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private List<BluetoothDevice> devices = new ArrayList<>();
    private Context mContext;
    private OnDeviceSelectedListener onSelectListener;

    public void setDevices(List<BluetoothDevice> devices) {
        this.devices = devices;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        mContext = parent.getContext();
        View view = LayoutInflater.from(mContext).inflate(R.layout.layout_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        holder.nameTxtvw.setText(device.getName());
        holder.addressTxtvw.setText(device.getAddress());
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public class DeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public TextView nameTxtvw;
        public TextView addressTxtvw;
        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTxtvw = itemView.findViewById(R.id.name_txtvw);
            addressTxtvw = itemView.findViewById(R.id.address_txtvw);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            onSelectListener.onSelected(devices.get(getAdapterPosition()));
        }
    }

    interface OnDeviceSelectedListener{
        void onSelected(BluetoothDevice device);
    }

    void setOnDeviceSelectedListener(OnDeviceSelectedListener listener){
        onSelectListener = listener;
    }

}
