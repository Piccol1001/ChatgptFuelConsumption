package my.fuelledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import my.fuelledger.LedgerState
import my.fuelledger.LedgerViewModel
import my.fuelledger.data.AppSettings

@Composable fun PersonalizationSettings(state: LedgerState, vm: LedgerViewModel, busy: Boolean) {
    val settings = state.settings
    fun save(value: AppSettings) { vm.saveSettings(value) {} }
    LedgerCard {
        Text("Gestures & feedback", style = MaterialTheme.typography.titleMedium)
        PreferenceSwitch("Main-screen swipes", settings.mainSwipeEnabled, !busy) { save(settings.copy(mainSwipeEnabled = it)) }
        Choice("Swipe right action", settings.swipeStart, listOf("Edit", "Duplicate", "Delete", "None").map { it to it }) { save(settings.copy(swipeStart = it)) }
        Choice("Swipe left action", settings.swipeEnd, listOf("Edit", "Duplicate", "Delete", "None").map { it to it }) { save(settings.copy(swipeEnd = it)) }
        PreferenceSwitch("Haptic feedback", settings.haptics, !busy) { save(settings.copy(haptics = it)) }
        PreferenceSwitch("Reduced animation", settings.reducedMotion, !busy) { save(settings.copy(reducedMotion = it)) }
    }
    Spacer(Modifier.height(16.dp))
    LedgerCard {
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        PreferenceSwitch("Hide monetary amounts", settings.privacyMode, !busy) { save(settings.copy(privacyMode = it)) }
        PreferenceSwitch("Hide widget amounts", settings.widgetPrivacy, !busy) { save(settings.copy(widgetPrivacy = it)) }
        Text("Recent-app previews and screen capture are protected by Android.", style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(16.dp))
    LedgerCard {
        Text("Dashboard arrangement", style = MaterialTheme.typography.titleMedium)
        val names = mapOf("plan" to "Monthly plan", "budget" to "Monthly budget", "actions" to "Quick actions", "upcoming" to "Upcoming payments")
        settings.dashboardOrder.forEachIndexed { index, key ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(names.getValue(key), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (key in listOf("budget", "upcoming")) Checkbox(key !in settings.dashboardHidden, { shown -> save(settings.copy(dashboardHidden = if (shown) settings.dashboardHidden - key else settings.dashboardHidden + key)) }, enabled = !busy)
                IconButton({ val order = settings.dashboardOrder.toMutableList(); java.util.Collections.swap(order, index, index - 1); save(settings.copy(dashboardOrder = order)) }, enabled = index > 0 && !busy) { Icon(Icons.Outlined.ArrowUpward, "Move ${names[key]} up") }
                IconButton({ val order = settings.dashboardOrder.toMutableList(); java.util.Collections.swap(order, index, index + 1); save(settings.copy(dashboardOrder = order)) }, enabled = index < 3 && !busy) { Icon(Icons.Outlined.ArrowDownward, "Move ${names[key]} down") }
            }
        }
        TextButton({ save(settings.copy(dashboardOrder = AppSettings().dashboardOrder, dashboardHidden = emptyList())) }, enabled = !busy) { Text("Restore default arrangement") }
    }
    Spacer(Modifier.height(16.dp))
    LocalDataSettings(state, vm, busy)
}
@Composable private fun PreferenceSwitch(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Switch(checked, change, enabled = enabled) }
}
