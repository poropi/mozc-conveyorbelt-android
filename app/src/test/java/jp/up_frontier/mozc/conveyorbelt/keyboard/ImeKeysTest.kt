package jp.up_frontier.mozc.conveyorbelt.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeKeysTest {
  @Test fun macHasOneKeyPerMode() {
    assertEquals(0x90, ImeKeys.usageFor(HostOs.MAC, ImeMode.HIRAGANA))
    assertEquals(0x91, ImeKeys.usageFor(HostOs.MAC, ImeMode.ENGLISH))
  }

  @Test fun windowsAlwaysGetsTheZenkakuHankakuToggle() {
    assertEquals(0x35, ImeKeys.usageFor(HostOs.WINDOWS, ImeMode.HIRAGANA))
    assertEquals(0x35, ImeKeys.usageFor(HostOs.WINDOWS, ImeMode.ENGLISH))
  }

  @Test fun theModeKeysFitTheReportDescriptorRange() {
    // The key array's maximum is 0xff (see HidDescriptor); both must be reportable.
    for (usage in listOf(ImeKeys.KANA, ImeKeys.EISU, ImeKeys.ZENKAKU_HANKAKU)) assertEquals(true, usage in 0..0xff)
  }

  @Test fun oppositeFlips() {
    assertEquals(ImeMode.ENGLISH, ImeKeys.opposite(ImeMode.HIRAGANA))
    assertEquals(ImeMode.HIRAGANA, ImeKeys.opposite(ImeMode.ENGLISH))
  }
}
