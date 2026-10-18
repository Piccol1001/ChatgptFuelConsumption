package my.fuelledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import my.fuelledger.LedgerState
import my.fuelledger.LedgerViewModel

/** The Home eye owns amount masking; widget visibility has a separate necessary control. */
@Composable fun PersonalizationSettings(state: LedgerState, vm: LedgerViewModel, busy: Boolean) {
    LedgerCard {
        Text("Privacy & Security", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Hide widget amounts", Modifier.weight(1f))
            Switch(state.settings.widgetPrivacy, { value -> vm.updateSettings({ it.copy(widgetPrivacy = value) }) }, enabled = !busy)
        }
        Text("Screenshots are allowed. This reduces protection against screen capture and authorized recording tools.", style = MaterialTheme.typography.bodySmall)
    }
}
