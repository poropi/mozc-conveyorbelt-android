package jp.up_frontier.mozc.conveyorbelt

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import jp.up_frontier.mozc.conveyorbelt.theme.MozcConveyorbeltTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Demo/test tooling, debuggable builds only: `am start ... --ef flow 6.0` fixes the belt speed.
    val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val demoFlow = if (debuggable && intent.hasExtra("flow")) intent.getFloatExtra("flow", 3f) else null
    setContent {
      MozcConveyorbeltTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainScreen(initialFlowSpeed = demoFlow) }
      }
    }
  }
}
