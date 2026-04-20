package com.airplaytv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import com.airplaytv.databinding.ActivityMainBinding

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var statusReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStatusReceiver()
        updateNetworkStatus()
        startAirPlayService()
        setupButtons()
    }

    private fun setupButtons() {
        binding.btnToggleService.setOnClickListener {
            if (AirPlayService.isRunning) {
                stopAirPlayService()
            } else {
                startAirPlayService()
            }
        }

        binding.btnChangeDeviceName.setOnClickListener {
            showDeviceNameDialog()
        }
    }

    private fun startAirPlayService() {
        val intent = Intent(this, AirPlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopAirPlayService() {
        stopService(Intent(this, AirPlayService::class.java))
    }

    private fun setupStatusReceiver() {
        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    AirPlayService.ACTION_STATUS_UPDATE -> {
                        val status = intent.getStringExtra(AirPlayService.EXTRA_STATUS)
                            ?: AirPlayStatusState.IDLE
                        val clientName = intent.getStringExtra(AirPlayService.EXTRA_CLIENT_NAME)
                        updateUI(status, clientName)
                    }
                }
            }
        }

        val filter = IntentFilter(AirPlayService.ACTION_STATUS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }

    private fun updateUI(status: String, clientName: String?) {
        binding.statusIndicatorView.setStatus(status, clientName)
    }

    private fun updateNetworkStatus() {
        val ip = getDeviceIpAddress()
        val deviceName = AirPlayPreferences.getDeviceName(this)
        binding.deviceNameText.text = deviceName
        binding.ipAddressText.text = if (ip != null) ip else getString(R.string.no_network)
        binding.networkWarning.visibility =
            if (ip == null) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun getDeviceIpAddress(): String? {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null

        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return null

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        return if (ip == 0) null
        else String.format(
            "%d.%d.%d.%d",
            ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
        )
    }

    private fun showDeviceNameDialog() {
        DeviceNameDialog().show(supportFragmentManager, "device_name")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Evitar que el botón BACK cierre la app si el servicio está corriendo
        if (keyCode == KeyEvent.KEYCODE_BACK && AirPlayService.isRunning) {
            moveTaskToBack(true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        updateNetworkStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(statusReceiver)
    }
}
