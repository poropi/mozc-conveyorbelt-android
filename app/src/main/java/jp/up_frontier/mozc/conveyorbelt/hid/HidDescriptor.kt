package jp.up_frontier.mozc.conveyorbelt.hid

/**
 * Same layout as the firmware's HID_REPORT_MAP (8-byte boot keyboard report, no report ID),
 * except the key array's maximum is widened from 0x65 to 0xff: the JIS keymap uses usages
 * 0x87 (ro) and 0x89 (yen), which a 0x65 maximum would declare out of range.
 */
val HID_REPORT_DESCRIPTOR: ByteArray = intArrayOf(
  0x05, 0x01, // usage page (generic desktop)
  0x09, 0x06, // usage (keyboard)
  0xa1, 0x01, // collection (application)
  0x05, 0x07, //   usage page (key codes)
  0x19, 0xe0, //   usage minimum (left control)
  0x29, 0xe7, //   usage maximum (right gui)
  0x15, 0x00, //   logical minimum (0)
  0x25, 0x01, //   logical maximum (1)
  0x75, 0x01, //   report size (1)
  0x95, 0x08, //   report count (8)
  0x81, 0x02, //   input (modifier byte)
  0x95, 0x01, //   report count (1)
  0x75, 0x08, //   report size (8)
  0x81, 0x03, //   input (reserved byte)
  0x95, 0x06, //   report count (6)
  0x75, 0x08, //   report size (8)
  0x15, 0x00, //   logical minimum (0)
  0x26, 0xff, 0x00, //   logical maximum (255)
  0x05, 0x07, //   usage page (key codes)
  0x19, 0x00, //   usage minimum (0)
  0x2a, 0xff, 0x00, //   usage maximum (255)
  0x81, 0x00, //   input (key array)
  0xc0, // end collection
).map { it.toByte() }.toByteArray()
