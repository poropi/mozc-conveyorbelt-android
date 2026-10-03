package jp.up_frontier.mozc.conveyorbelt.keyboard

/** Builds the 8-byte HID boot keyboard report. Port of switches_poll() in firmware/switches.c. */
object ReportBuilder {
  const val REPORT_LEN = 8

  fun build(pressed: Collection<Int>, keymap: List<KeyDef> = KEYMAP): ByteArray {
    val report = ByteArray(REPORT_LEN)
    val keyMax = REPORT_LEN - 2
    val indices = pressed.toSortedSet()

    val fnActive = indices.any { keymap[it].isFn }
    var modifiers = 0
    var shiftNeeded = false
    var keyCount = 0

    for (idx in indices) {
      val entry = keymap[idx]
      if (entry.isNone || entry.isFn) continue
      if (entry.shift) shiftNeeded = true

      if (entry.usage in KEY_LCTRL..KEY_LAST_MODIFIER) {
        modifiers = modifiers or (1 shl (entry.usage - KEY_LCTRL))
        continue
      }

      var code = entry.usage
      if (fnActive) {
        if (code in KEY_1..KEY_9) {
          code = KEY_F1 + (code - KEY_1)
        } else if (code == KEY_0) {
          code = KEY_F10
        }
      }

      // 'a' and 'A' pressed together must not report the same usage twice.
      val duplicate = (0 until minOf(keyCount, keyMax)).any { (report[2 + it].toInt() and 0xff) == code }
      if (!duplicate) {
        if (keyCount < keyMax) report[2 + keyCount] = code.toByte()
        keyCount++
      }
    }

    if (shiftNeeded) modifiers = modifiers or MOD_LSHIFT
    if (keyCount > keyMax) {
      for (i in 0 until keyMax) report[2 + i] = KEY_ERROR_ROLLOVER.toByte()
    }
    report[0] = modifiers.toByte()
    return report
  }

  fun toHex(report: ByteArray): String = report.joinToString(" ") { "%02x".format(it.toInt() and 0xff) }
}
