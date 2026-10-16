package my.fuelledger.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import my.fuelledger.data.*
import my.fuelledger.domain.*

@Composable
private fun EntryIconOption(label: String, icon: ImageVector, selected: Boolean, enabled: Boolean,
    description: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, choose: () -> Unit) {
    val interaction = rememberAppLockInteraction()
    val haptic = LocalHapticFeedback.current
    val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        tween(if (LocalUiPreferences.current.reducedMotion) 0 else 150), label = "Expense selection")
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).selectable(selected, enabled = enabled, role = Role.RadioButton,
        onClick = { if (interaction()) { choose(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } })
        .semantics { contentDescription = description }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(48.dp).background(color, shape)
            .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else tint)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ExpenseCategoryRow(categories: List<Category>, selected: String, enabled: Boolean = true, preferred: List<String> = emptyList(), choose: (String) -> Unit) {
    val selectedItem = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { selectedItem.bringIntoView() }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val ordered = displayCategories(categories)
        ordered.sortedWith(compareBy<Category> { if (it.id == OTHER_CATEGORY) 1 else 0 }.thenBy { preferred.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }.thenBy { ordered.indexOf(it) }).forEach { category ->
            val label = when (category.id) { "food" -> "Food"; FUEL_CATEGORY -> "Fuel"; else -> category.name }
            EntryIconOption(label, categoryIcon(category.icon), selected == category.id, enabled, "Category ${category.name}",
                Modifier.width(76.dp).heightIn(min = 84.dp).then(if (selected == category.id) Modifier.bringIntoViewRequester(selectedItem) else Modifier), categoryTint(category)) { choose(category.id) }
        }
    }
}

@Composable
fun ExpensePaymentRow(payment: String, enabled: Boolean = true, choose: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        expensePaymentOptions.forEach { option ->
            val label = if (option == "E-wallet") "E-Wallet" else option
            val icon = when (option) { "Cash" -> Icons.Outlined.Payments; "Card" -> Icons.Outlined.CreditCard; else -> Icons.Outlined.Smartphone }
            EntryIconOption(label, icon, expensePaymentGroup(payment) == option, enabled, "Payment method $label", Modifier.weight(1f)) { choose(option) }
        }
    }
}

@Composable
fun ExpenseAmount(cents: Long) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Amount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Money.format(cents), Modifier.fillMaxWidth().testTag("expense-amount"), textAlign = TextAlign.Center,
            style = if (cents >= 100_000_000) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge)
    }
}

@Composable
fun CentsKeypad(cents: Long, enabled: Boolean = true, change: (Long) -> Unit, rejected: () -> Unit) {
    val interaction = rememberAppLockInteraction()
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("Clear", "0", "Backspace")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    TextButton(onClick = {
                        if (interaction()) {
                            focus.clearFocus()
                            val updated = when (key) { "Clear" -> 0L; "Backspace" -> backspaceSen(cents); else -> appendSenDigit(cents, key.toInt()) }
                            if (updated == null) rejected() else { change(updated); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        }
                    }, enabled = enabled, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("expense-key-$key")
                            .semantics { contentDescription = when (key) { "Clear" -> "Clear amount"; "Backspace" -> "Backspace amount"; else -> "Digit $key" } }) {
                        if (key == "Backspace") Icon(Icons.AutoMirrored.Outlined.Backspace, null)
                        else Text(key, style = if (key == "Clear") MaterialTheme.typography.labelLarge else MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}
