package com.example.grad_project2

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.SupplicantState
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.grad_project2.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding
    private val LOCATION_PERMISSION_REQUEST = 100
    private lateinit var wifiStateReceiver: BroadcastReceiver
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializeReceiver()
        checkAndRequestPermissions()

    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            val connManager = this.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            if (networkInfo?.isConnected == true) {
                val wifiManager =
                    this.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val connectionInfo = wifiManager.connectionInfo
                Log.d("Connection Info:",connectionInfo.toString())
                if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.ssid)) {
                    Log.d("zbam","zbam")
                    val wifiCheckBox = binding.wifiCheckBox
                    wifiCheckBox.isChecked = true
                    wifiCheckBox.text = connectionInfo.ssid.substring(0,5)
                }

            }
            else{
                val wifiCheckBox = findViewById<CheckBox>(R.id.wifiCheckBox)
                wifiCheckBox.isChecked = false
                wifiCheckBox.text = "No Connection!"
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initializeWifiDetails()
        } else {
            Toast.makeText(this, "Permission denied. Cannot access Wi-Fi details.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeWifiDetails() {
        getWifiInfo { wifiInfo ->
            val wifiCheckBox = findViewById<CheckBox>(R.id.wifiCheckBox)
            if (wifiInfo != null && wifiInfo.supplicantState == SupplicantState.COMPLETED) {
                val ssid = wifiInfo.ssid.replace("\"", "")  // Removing extra quotes around SSID
                wifiCheckBox.isChecked = true
                wifiCheckBox.text = ssid
                Toast.makeText(this, "Connected to $ssid", Toast.LENGTH_SHORT).show()
            } else {
                wifiCheckBox.isChecked = false
                wifiCheckBox.text = "No Connection"
                Toast.makeText(this, "No Wi-Fi connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Method to get Wi-Fi information using ConnectivityManager
    private fun getWifiInfo(callback: (WifiInfo?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29+
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: Network? = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiInfo = networkCapabilities.transportInfo as? WifiInfo
                callback(wifiInfo)
            } else {
                callback(null)
            }
        } else {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            callback(wifiManager.connectionInfo)
        }
    }
    private fun initializeReceiver() {
        wifiStateReceiver = object : BroadcastReceiver() {
            @SuppressLint("SetTextI18n")
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                    val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        // Connected to Wi-Fi
                        Toast.makeText(context, "Connected to Wi-Fi", Toast.LENGTH_SHORT).show()
                        Log.d("Arda","Arda")
                        checkAndRequestPermissions()


                    } else {
                        // Disconnected from Wi-Fi
                        //Toast.makeText(context, "Disconnected from Wi-Fi", Toast.LENGTH_SHORT).show()
                        binding.wifiCheckBox.isChecked = false
                        binding.wifiCheckBox.text ="No Connection"
                    }
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        registerReceiver(wifiStateReceiver, intentFilter)
        //initializeWifiDetails()

    }
    override fun onStop() {
        super.onStop()
        unregisterReceiver(wifiStateReceiver)
    }
    override fun onPause(){
        super.onPause()
        Log.d("bum","bum")
        //unregisterReceiver(wifiStateReceiver)
    }
}
