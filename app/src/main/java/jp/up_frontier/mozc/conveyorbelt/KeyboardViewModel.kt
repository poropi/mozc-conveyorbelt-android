package jp.up_frontier.mozc.conveyorbelt

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.up_frontier.mozc.conveyorbelt.hid.HidKeyboard
import jp.up_frontier.mozc.conveyorbelt.hid.HidStatus
import jp.up_frontier.mozc.conveyorbelt.keyboard.HostOs
import jp.up_frontier.mozc.conveyorbelt.keyboard.ImeKeys
import jp.up_frontier.mozc.conveyorbelt.keyboard.ImeMode
import jp.up_frontier.mozc.conveyorbelt.keyboard.ReportBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class KeyboardViewModel(app: Application) : AndroidViewModel(app) {
  val hid = HidKeyboard(app)
  private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

  private val _hostOs = MutableStateFlow(if (prefs.getString("hostOs", null) == HostOs.WINDOWS.name) HostOs.WINDOWS else HostOs.MAC)
  val hostOs: StateFlow<HostOs> = _hostOs.asStateFlow()

  /** What the app believes the PC's IME is in. A PC can change without telling us. */
  private val _imeMode = MutableStateFlow(ImeMode.ENGLISH)
  val imeMode: StateFlow<ImeMode> = _imeMode.asStateFlow()

  private val _autoConnect = MutableStateFlow(prefs.getBoolean("autoConnect", true))
  val autoConnect: StateFlow<Boolean> = _autoConnect.asStateFlow()

  fun setAutoConnect(on: Boolean) {
    prefs.edit().putBoolean("autoConnect", on).apply()
    _autoConnect.value = on
  }

  init {
    // As soon as the phone is ready as a keyboard, go and connect to the PC it used before.
    // The PC may still be waking up, so try a few times and then leave it to the user.
    viewModelScope.launch {
      hid.state.map { it.status }.distinctUntilChanged().collectLatest { status ->
        if (status != HidStatus.REGISTERED) return@collectLatest
        repeat(AUTO_CONNECT_TRIES) {
          if (!_autoConnect.value) return@collectLatest
          hid.preferredHost()?.let { hid.connect(it) } ?: return@collectLatest
          delay(AUTO_CONNECT_INTERVAL_MS)
        }
      }
    }
  }

  fun setHostOs(os: HostOs) {
    prefs.edit().putString("hostOs", os.name).apply()
    _hostOs.value = os
  }

  /** Switches the PC's IME and remembers where it should now be. */
  fun toggleIme() {
    val target = ImeKeys.opposite(_imeMode.value)
    tapKey(ImeKeys.usageFor(_hostOs.value, target))
    _imeMode.value = target
  }

  /** Fixes the label when the PC's IME was changed some other way (no key is sent). */
  fun flipImeLabel() {
    _imeMode.value = ImeKeys.opposite(_imeMode.value)
  }

  private fun tapKey(usage: Int) {
    viewModelScope.launch {
      val down = ByteArray(ReportBuilder.REPORT_LEN).also { it[2] = usage.toByte() }
      _lastReport.value = down
      hid.sendReport(down)
      delay(40)
      hid.sendReport(ByteArray(ReportBuilder.REPORT_LEN))
      // Put back whatever the belt's own keys are holding.
      sentReport = ByteArray(ReportBuilder.REPORT_LEN)
      update(_pressed.value)
    }
  }

  private val _pressed = MutableStateFlow<Set<Int>>(emptySet())
  val pressed: StateFlow<Set<Int>> = _pressed.asStateFlow()

  private val _lastReport = MutableStateFlow(ByteArray(ReportBuilder.REPORT_LEN))
  val lastReport: StateFlow<ByteArray> = _lastReport.asStateFlow()

  private var sentReport = ByteArray(ReportBuilder.REPORT_LEN)

  fun press(keyIndex: Int) = update(_pressed.value + keyIndex)

  fun release(keyIndex: Int) = update(_pressed.value - keyIndex)

  private fun update(next: Set<Int>) {
    _pressed.value = next
    val report = ReportBuilder.build(next)
    // Like the firmware, only send when the report changed.
    if (!report.contentEquals(sentReport)) {
      sentReport = report
      _lastReport.value = report
      hid.sendReport(report)
    }
  }

  private companion object {
    const val AUTO_CONNECT_TRIES = 5
    const val AUTO_CONNECT_INTERVAL_MS = 6_000L
  }

  override fun onCleared() {
    hid.stop()
  }
}
