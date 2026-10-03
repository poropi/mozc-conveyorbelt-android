package jp.up_frontier.mozc.conveyorbelt.keyboard

// HID usage IDs (Keyboard/Keypad page 0x07) used by the firmware's keymap.h.
const val KEY_NONE = 0x00
const val KEY_ERROR_ROLLOVER = 0x01
const val KEY_FN = 0xff
const val KEY_LCTRL = 0xe0
const val KEY_LAST_MODIFIER = 0xe7
const val KEY_1 = 0x1e
const val KEY_9 = 0x26
const val KEY_0 = 0x27
const val KEY_F1 = 0x3a
const val KEY_F10 = 0x43
const val MOD_LSHIFT = 1 shl 1

/** One physical key: what its cap shows, its HID usage, and whether it needs Shift. */
data class KeyDef(val label: String, val usage: Int, val shift: Boolean) {
  val isNone: Boolean get() = usage == KEY_NONE
  val isFn: Boolean get() = usage == KEY_FN

  /** Text shown on the key cap. */
  val caption: String
    get() = when (label) {
      "None" -> ""
      "Left" -> "←"
      "Right" -> "→"
      "Up" -> "↑"
      "Down" -> "↓"
      "Escape" -> "esc"
      "Backspace" -> "bksp"
      "CapsLock" -> "caps"
      "PageUp" -> "pgup"
      "PageDown" -> "pgdn"
      "Insert" -> "ins"
      "Delete" -> "del"
      "GUI(GOOG)" -> "G"
      "Control" -> "ctrl"
      else -> if (label.length > 1) label.lowercase() else label
    }
}
