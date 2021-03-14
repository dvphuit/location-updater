package app.locationupadater.printer.adapters

import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import app.locationupadater.R
import app.locationupadater.printer.models.BluetoothConnection
import app.locationupadater.printer.utils.toast
import kotlinx.android.synthetic.main.item_bluetoothdevice.view.*

class DevicesAdapter(val context: Context) : RecyclerView.Adapter<DevicesAdapter.BDeviceHolder>() {

    private var list = mutableListOf<BluetoothDevice>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BDeviceHolder {
        val inflater = LayoutInflater.from(context)
        return BDeviceHolder(
            inflater,
            parent
        )
    }

    fun addDevice(device: BluetoothDevice) {
        this.list.add(device)
        notifyItemInserted(list.size)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: BDeviceHolder, position: Int) {
        holder.bind(list[position])
    }

    class BDeviceHolder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.item_bluetoothdevice, parent, false)) {

        fun bind(device: BluetoothDevice) {
            itemView.name.text =
                if (device.name == null || device.name == "null" || device.name.isEmpty())
                    "-"
                else
                    device.name

            itemView.address.text = device.address

            itemView.setOnClickListener {
                it.toast("Connecting...")
                BluetoothConnection.connectDevice(
                    device,
                    object : BluetoothConnection.ConnectionListener {
                        override fun onConnected() {
                            it.toast("Connected")
                            val data = Intent()
                            data.putExtra("device_name", device.name);
                            (it.context as AppCompatActivity).setResult(RESULT_OK, data)
                            (it.context as AppCompatActivity).finish()
                        }

                        override fun onConnectionError() {
                            it.toast("Connection error")
                        }
                    })
            }
        }
    }
}