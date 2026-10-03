package jp.up_frontier.mozc.conveyorbelt.keyboard

enum class HostOs { MAC, WINDOWS }

enum class ImeMode { HIRAGANA, ENGLISH }

/** Keys that switch a PC's Japanese IME between hiragana and English. */
object ImeKeys {
  /** JIS 「かな」 (HID LANG1): what a Mac sends for its Kana key. */
  const val KANA = 0x90

  /** JIS 「英数」 (HID LANG2): what a Mac sends for its Eisu key. */
  const val EISU = 0x91

  /** JIS 「半角/全角」 (HID Grave Accent on a JIS layout): Microsoft IME's default on/off toggle. */
  const val ZENKAKU_HANKAKU = 0x35

  /**
   * The key to press to reach [target]. A Mac has a key per mode, so the result is exact.
   * Windows' Microsoft IME only has the on/off toggle by default, so it always gets the toggle
   * and [target] is only what the app expects to happen.
   */
  fun usageFor(os: HostOs, target: ImeMode): Int = when (os) {
    HostOs.MAC -> if (target == ImeMode.HIRAGANA) KANA else EISU
    HostOs.WINDOWS -> ZENKAKU_HANKAKU
  }

  fun opposite(mode: ImeMode): ImeMode = if (mode == ImeMode.HIRAGANA) ImeMode.ENGLISH else ImeMode.HIRAGANA
}
