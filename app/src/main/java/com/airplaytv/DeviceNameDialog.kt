package com.airplaytv

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class DeviceNameDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val editText = EditText(context).apply {
            setText(AirPlayPreferences.getDeviceName(context))
            selectAll()
            hint = "Ej: Mi Xiaomi TV"
            setSingleLine(true)
            setPadding(48, 24, 48, 24)
        }

        return AlertDialog.Builder(context, R.style.Theme_AirPlayTV_Dialog)
            .setTitle(R.string.dialog_device_name_title)
            .setView(editText)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    AirPlayPreferences.setDeviceName(context, newName)
                    // Notificar a la Activity para que actualice la UI y reinicie el servicio
                    (activity as? MainActivity)?.let {
                        it.binding.deviceNameText.text = newName
                        // Reiniciar el servicio con el nuevo nombre
                        it.stopService(android.content.Intent(context, AirPlayService::class.java))
                        it.startForegroundService(android.content.Intent(context, AirPlayService::class.java))
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
    }
}
