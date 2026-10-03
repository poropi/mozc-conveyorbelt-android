package jp.up_frontier.mozc.conveyorbelt.keyboard

import jp.up_frontier.mozc.conveyorbelt.belt.BeltModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportBuilderTest {
  private fun idx(label: String) = KEYMAP.indexOfFirst { it.label == label }.also { check(it >= 0) { "no key $label" } }

  private fun report(vararg labels: String) = ReportBuilder.build(labels.map(::idx))

  private fun bytes(vararg v: Int) = ByteArray(8) { if (it < v.size) v[it].toByte() else 0 }

  @Test fun keymapHas120EntriesWithFiveEmptySlots() {
    assertEquals(120, KEYMAP.size)
    assertEquals(5, KEYMAP.count { it.isNone })
  }

  @Test fun letterLabelsMatchTheirUsageAndShift() {
    for (c in 'a'..'z') {
      val lower = KEYMAP[idx(c.toString())]
      assertEquals("$c", 0x04 + (c - 'a'), lower.usage)
      assertFalse(lower.shift)
      val upper = KEYMAP[idx(c.uppercase())]
      assertEquals("${c.uppercase()}", 0x04 + (c - 'a'), upper.usage)
      assertTrue(upper.shift)
    }
  }

  @Test fun digitLabelsMatchTheirUsage() {
    for (c in '1'..'9') assertEquals("$c", 0x1e + (c - '1'), KEYMAP[idx(c.toString())].usage)
    assertEquals(0x27, KEYMAP[idx("0")].usage)
  }

  @Test fun plainKeyHasNoModifier() {
    assertArrayEquals(bytes(0x00, 0x00, 0x04), report("a"))
  }

  @Test fun uppercaseAddsLeftShift() {
    assertArrayEquals(bytes(0x02, 0x00, 0x04), report("A"))
  }

  @Test fun jisDoubleQuoteIsShiftTwo() {
    assertArrayEquals(bytes(0x02, 0x00, 0x1f), report("\""))
  }

  @Test fun aAndUppercaseAReportOneKeyWithShift() {
    assertArrayEquals(bytes(0x02, 0x00, 0x04), report("a", "A"))
  }

  @Test fun controlIsAModifierBitNotAKey() {
    assertArrayEquals(bytes(0x01, 0x00, 0x06), report("Control", "c"))
  }

  @Test fun fnTurnsDigitsIntoFunctionKeys() {
    assertArrayEquals(bytes(0x00, 0x00, 0x3a), report("Fn", "1"))
    assertArrayEquals(bytes(0x00, 0x00, 0x43), report("Fn", "0"))
  }

  @Test fun fnAloneReportsNothing() {
    assertArrayEquals(bytes(), report("Fn"))
  }

  @Test fun shiftedDigitUnderFnIsFunctionKeyWithShift() {
    // '!' is Shift+1, so Fn turns it into Shift+F1 exactly as the firmware does.
    assertArrayEquals(bytes(0x02, 0x00, 0x3a), report("Fn", "!"))
  }

  @Test fun moreThanSixKeysReportsRollover() {
    assertArrayEquals(bytes(0, 0, 1, 1, 1, 1, 1, 1), report("a", "b", "c", "d", "e", "f", "g"))
  }

  @Test fun noKeysIsAllZero() {
    assertArrayEquals(bytes(), ReportBuilder.build(emptySet()))
  }

  @Test fun beltHasFourLanesAndFirstRowIsBlank() {
    assertEquals(30, BeltModel.ROWS)
    assertTrue((0 until 4).all { BeltModel.key(0, it).isNone })
    assertEquals("CapsLock", BeltModel.key(1, 1).label)
  }

  @Test fun beltRowsMatchTheProductPhotoLanes() {
    // Photo: left lane reads "! @ # $ % ^ & * ( ) _ +" and the next lane "Q W E R T Y U I O P { }".
    val lane0 = (16..29 step 1).map { BeltModel.key(it, 0).label }
    assertEquals(listOf("Escape", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "_", "+", "~"), lane0)
    assertEquals(listOf("Shift", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "{", "}", "|"), (16..29).map { BeltModel.key(it, 1).label })
  }

  @Test fun beltWrapsAround() {
    assertEquals(BeltModel.key(0, 2), BeltModel.key(30, 2))
    assertEquals(BeltModel.key(29, 3), BeltModel.key(-1, 3))
  }
}
