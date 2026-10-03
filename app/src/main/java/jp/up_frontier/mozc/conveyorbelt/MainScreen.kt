package jp.up_frontier.mozc.conveyorbelt

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.up_frontier.mozc.conveyorbelt.belt.BeltKeyboard
import jp.up_frontier.mozc.conveyorbelt.hid.HidState
import jp.up_frontier.mozc.conveyorbelt.hid.HidStatus
import jp.up_frontier.mozc.conveyorbelt.keyboard.HostOs
import jp.up_frontier.mozc.conveyorbelt.keyboard.ImeMode
import jp.up_frontier.mozc.conveyorbelt.keyboard.ReportBuilder

private val FrameTop = Color(0xFF4DA3F5)
private val FrameBottom = Color(0xFF1B7FDB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: KeyboardViewModel = viewModel(), initialFlowSpeed: Float? = null) {
  val context = LocalContext.current
  val hidState by vm.hid.state.collectAsState()
  val pressed by vm.pressed.collectAsState()
  val report by vm.lastReport.collectAsState()
  val imeMode by vm.imeMode.collectAsState()
  val hostOs by vm.hostOs.collectAsState()
  val autoConnect by vm.autoConnect.collectAsState()
  var settingsOpen by rememberSaveable { mutableStateOf(false) }
  // The motor. 150 rpm through 20T pulleys and 22 mm sprockets tops out near 8 rows/s unloaded;
  // 3 rows/s is a starting point, not a measurement of the product.
  var flowOn by rememberSaveable { mutableStateOf(true) }
  var flowSpeed by rememberSaveable { mutableFloatStateOf(initialFlowSpeed ?: 3f) }
  var flowDown by rememberSaveable { mutableStateOf(true) }

  // start() reports NEEDS_PERMISSION again when the permission was denied.
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.hid.start() }
  fun startKeyboard() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !vm.hid.hasPermission()) {
      permission.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      vm.hid.start()
    }
  }

  // Start advertising as a keyboard right away; the controls stay out of the belt's way.
  LaunchedEffect(Unit) { if (vm.hid.hasPermission()) vm.hid.start() }

  // First time it is ready but not connected, open the how-to once so nobody has to guess.
  var guideShown by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(hidState.status) {
    if (hidState.status == HidStatus.REGISTERED && hidState.pairedHosts.isEmpty() && !guideShown) {
      guideShown = true
      settingsOpen = true
    }
  }

  Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
    Row(
      Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ModeButton(imeMode, onToggle = vm::toggleIme, onFlipLabel = vm::flipImeLabel)
      Spacer(Modifier.width(10.dp))
      // The pill takes what is left and ellipsizes, so a long PC name can't push the buttons off screen.
      Box(Modifier.weight(1f)) { StatusPill(hidState, onClick = { settingsOpen = true }) }
      TextButton(onClick = { flowOn = !flowOn }) { Text(if (flowOn) "止める" else "流す") }
      TextButton(onClick = { settingsOpen = true }) { Text("設定") }
    }

    // The product's blue body: a thick rounded frame around the belt. It always gets all the
    // space that is left, whatever the settings are doing.
    Box(
      Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp)
        .background(Brush.verticalGradient(listOf(FrameTop, FrameBottom)), RoundedCornerShape(30.dp))
        .padding(horizontal = 7.dp, vertical = 9.dp)
        .clip(RoundedCornerShape(18.dp)),
    ) {
      BeltKeyboard(
        pressed = pressed,
        onPress = vm::press,
        onRelease = vm::release,
        flowRowsPerSec = if (!flowOn) 0f else if (flowDown) flowSpeed else -flowSpeed,
      )
    }
  }

  if (settingsOpen) {
    ModalBottomSheet(
      onDismissRequest = { settingsOpen = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
      Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ConnectGuide(hidState, deviceName = phoneName(context))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = ::startKeyboard) { Text("キーボードを開始") }
          OutlinedButton(onClick = {
            context.startActivity(
              Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120),
            )
          }) { Text("検出可能にする") }
          OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Bluetooth 設定") }
        }
        if (hidState.pairedHosts.isNotEmpty() && hidState.status != HidStatus.CONNECTED) {
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (device in hidState.pairedHosts) {
              @Suppress("MissingPermission")
              OutlinedButton(onClick = { vm.hid.connect(device) }) { Text("接続: ${device.name ?: device.address}") }
            }
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(checked = autoConnect, onCheckedChange = vm::setAutoConnect)
          Spacer(Modifier.width(8.dp))
          Text("起動したら自動で PC に接続する", style = MaterialTheme.typography.bodyMedium)
        }
        Text("接続先の PC（ひらがな／英字ボタンが送るキーが変わります）", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(selected = hostOs == HostOs.MAC, onClick = { vm.setHostOs(HostOs.MAC) }, label = { Text("Mac（英数／かな）") })
          FilterChip(selected = hostOs == HostOs.WINDOWS, onClick = { vm.setHostOs(HostOs.WINDOWS) }, label = { Text("Windows（半角/全角）") })
        }
        Text(
          if (hostOs == HostOs.WINDOWS) "Windows は切り替えキーが 1 つなので、押すたびに入れ替わります。表示が PC とずれたら、「あ／A」を長押しすると表示だけ入れ替わります。"
          else "「あ」は かな、「A」は 英数 キーを送ります。",
          style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(checked = flowOn, onCheckedChange = { flowOn = it })
          Spacer(Modifier.width(8.dp))
          Text("自動で流す  %.1f 行/秒".format(flowSpeed), style = MaterialTheme.typography.bodyMedium)
          Spacer(Modifier.weight(1f))
          OutlinedButton(onClick = { flowDown = !flowDown }) { Text(if (flowDown) "↓ 手前へ" else "↑ 奥へ") }
        }
        Slider(value = flowSpeed, onValueChange = { flowSpeed = it }, valueRange = 0.5f..9f)
        Text(
          "report: ${ReportBuilder.toHex(report)}",
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** This phone's Bluetooth name, which is what the PC lists. */
private fun phoneName(context: android.content.Context): String =
  Settings.Global.getString(context.contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL

@Composable
private fun ConnectGuide(state: HidState, deviceName: String) {
  val steps = when (state.status) {
    HidStatus.CONNECTED -> listOf("接続できています。ベルトのキーをタップすると、${state.hostName} に入力されます。")
    HidStatus.REGISTERED -> listOf(
      "初めての PC の場合（ペアリング）",
      "1. 下の「検出可能にする」を押して、「許可」を選ぶ（2分間、PC から見えるようになります）",
      "2. PC の Bluetooth 設定で、「$deviceName」を探して接続する",
      "　Mac: システム設定 → Bluetooth → 「近くのデバイス」の「$deviceName」→ 接続",
      "　Windows: 設定 → Bluetooth とデバイス → デバイスを追加 → Bluetooth → 「$deviceName」",
      "3. この端末にも PC にも同じ数字が出たら、この端末で「ペア設定する」、PC で「接続」を押す",
      "",
      "以前ペアリングした PC の場合",
      "下の「接続: ○○」から、使う PC の名前を押します（見つからなければ、PC 側の Bluetooth をオンに）。",
    )
    HidStatus.NEEDS_PERMISSION -> listOf("下の「キーボードを開始」を押して、Bluetooth の許可を「許可」にしてください。")
    HidStatus.UNSUPPORTED -> listOf(state.detail, "Bluetooth がオンか確認してください。")
    else -> listOf("下の「キーボードを開始」を押してください。")
  }
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text("PC につなぐには", style = MaterialTheme.typography.titleSmall)
    for (line in steps) Text(line, style = MaterialTheme.typography.bodyMedium)
  }
}

/** Hiragana/English switch: shows the mode the app believes the PC is in; tap sends the switch key. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModeButton(mode: ImeMode, onToggle: () -> Unit, onFlipLabel: () -> Unit) {
  Box(
    Modifier
      .size(width = 60.dp, height = 40.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.primaryContainer)
      .combinedClickable(onClick = onToggle, onLongClick = onFlipLabel),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      if (mode == ImeMode.HIRAGANA) "あ" else "A",
      fontSize = 22.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
  }
}

@Composable
private fun StatusPill(state: HidState, onClick: () -> Unit) {
  val (dot, text) = when (state.status) {
    HidStatus.IDLE -> Color(0xFF9E9E9E) to "未開始"
    HidStatus.STARTING -> Color(0xFF9E9E9E) to "開始中"
    HidStatus.NEEDS_PERMISSION -> Color(0xFFE53935) to "権限が必要"
    HidStatus.UNSUPPORTED -> Color(0xFFE53935) to "利用できません"
    HidStatus.REGISTERED -> Color(0xFFFFB300) to "PCの接続待ち"
    HidStatus.CONNECTED -> Color(0xFF43A047) to (state.hostName ?: "接続中")
  }
  Row(
    Modifier
      .clip(RoundedCornerShape(50))
      .clickable(onClick = onClick)
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
    Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
