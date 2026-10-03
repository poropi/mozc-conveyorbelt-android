package jp.up_frontier.mozc.conveyorbelt.belt

import jp.up_frontier.mozc.conveyorbelt.keyboard.KEYMAP
import jp.up_frontier.mozc.conveyorbelt.keyboard.KeyDef

/**
 * The belt is a loop of ROWS rows with LANES keys each. The 120 keymap entries are read
 * 4 at a time (one half of a shift-register byte = one belt row), row 0 first.
 * The row/lane assignment is inferred from the product photo and is not confirmed by the
 * (still unpublished) board data.
 */
object BeltModel {
  const val LANES = 4
  const val ROWS = 30

  init {
    check(KEYMAP.size == ROWS * LANES)
  }

  fun keyIndex(row: Int, lane: Int): Int = Math.floorMod(row, ROWS) * LANES + lane

  fun key(row: Int, lane: Int): KeyDef = KEYMAP[keyIndex(row, lane)]
}
