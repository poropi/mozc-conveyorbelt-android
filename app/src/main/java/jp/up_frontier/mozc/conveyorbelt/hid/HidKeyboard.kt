package jp.up_frontier.mozc.conveyorbelt.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HidStatus { IDLE, NEEDS_PERMISSION, UNSUPPORTED, STARTING, REGISTERED, CONNECTED }

data class HidState(
  val status: HidStatus = HidStatus.IDLE,
  val detail: String = "",
  val hostName: String? = null,
  val pairedHosts: List<BluetoothDevice> = emptyList(),
)

/** Presents this phone to a PC as a Bluetooth HID keyboard (BluetoothHidDevice, API 28+). */
@SuppressLint("MissingPermission") // checked in start()/hasPermission()
class HidKeyboard(private val context: Context) {
  private val _state = MutableStateFlow(HidState())
  val state: StateFlow<HidState> = _state.asStateFlow()

  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
  private var hid: BluetoothHidDevice? = null
  private var host: BluetoothDevice? = null

  fun hasPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

  fun start() {
    if (hid != null) return
    if (!hasPermission()) {
      _state.value = HidState(HidStatus.NEEDS_PERMISSION, "Bluetooth の権限が必要です")
      return
    }
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    if (adapter == null) {
      _state.value = HidState(HidStatus.UNSUPPORTED, "この端末には Bluetooth がありません")
      return
    }
    if (!adapter.isEnabled) {
      _state.value = HidState(HidStatus.UNSUPPORTED, "Bluetooth がオフです")
      return
    }
    _state.value = HidState(HidStatus.STARTING, "HID プロファイルに接続中")
    val requested = adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
      override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
        if (profile != BluetoothProfile.HID_DEVICE) return
        hid = proxy as BluetoothHidDevice
        register(proxy)
      }

      override fun onServiceDisconnected(profile: Int) {
        if (profile != BluetoothProfile.HID_DEVICE) return
        hid = null
        host = null
        _state.value = HidState(HidStatus.IDLE, "HID プロファイルが切断されました")
      }
    }, BluetoothProfile.HID_DEVICE)
    if (!requested) {
      _state.value = HidState(HidStatus.UNSUPPORTED, "この端末は Bluetooth HID デバイスに対応していません")
    }
  }

  private fun register(proxy: BluetoothHidDevice) {
    val sdp = BluetoothHidDeviceAppSdpSettings(
      "Gboard DIY Device",
      "Mozc Conveyorbelt",
      "Gboard DIY team",
      BluetoothHidDevice.SUBCLASS1_KEYBOARD,
      HID_REPORT_DESCRIPTOR,
    )
    val callback = object : BluetoothHidDevice.Callback() {
      override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
        _state.value = if (registered) {
          HidState(HidStatus.REGISTERED, "PC からの接続を待っています", pairedHosts = pairedHosts())
        } else {
          HidState(HidStatus.IDLE, "HID の登録が解除されました")
        }
      }

      override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
        if (state == BluetoothProfile.STATE_CONNECTED) {
          host = device
          prefs.edit().putString("lastHost", device.address).apply()
          _state.value = HidState(HidStatus.CONNECTED, "接続中", device.name ?: device.address, pairedHosts())
        } else if (state == BluetoothProfile.STATE_DISCONNECTED && device == host) {
          host = null
          _state.value = HidState(HidStatus.REGISTERED, "切断されました。PC からの接続を待っています", pairedHosts = pairedHosts())
        }
      }
    }
    val ok = proxy.registerApp(sdp, null, null, context.mainExecutor, callback)
    if (!ok) _state.value = HidState(HidStatus.UNSUPPORTED, "HID アプリの登録に失敗しました（他のアプリが使用中の可能性）")
  }

  /** Paired computers only: a keyboard can't type into the speakers and headsets this phone also knows. */
  private fun pairedHosts(): List<BluetoothDevice> =
    context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
      .filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER }

  /**
   * The paired PC to connect to without asking: the one used last time, or the only one there is.
   * Null when there is nothing to pick (never paired, or several and none used before).
   */
  fun preferredHost(): BluetoothDevice? {
    val hosts = pairedHosts()
    val last = prefs.getString("lastHost", null)
    return hosts.firstOrNull { it.address == last } ?: hosts.singleOrNull()
  }

  /** Connects to an already-paired PC. First-time pairing is done from the PC's Bluetooth settings. */
  fun connect(device: BluetoothDevice) {
    hid?.connect(device)
  }

  fun sendReport(report: ByteArray): Boolean {
    val target = host ?: return false
    return hid?.sendReport(target, 0, report) ?: false
  }

  fun stop() {
    hid?.let {
      it.unregisterApp()
      context.getSystemService(BluetoothManager::class.java)?.adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it)
    }
    hid = null
    host = null
    _state.value = HidState()
  }
}
