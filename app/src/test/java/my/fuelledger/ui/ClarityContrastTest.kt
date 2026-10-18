package my.fuelledger.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ClarityContrastTest {
    private fun readable(label: String, foreground: Color, background: Color) {
        val first = foreground.luminance(); val second = background.luminance()
        val contrast = (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
        assertTrue("$label must meet normal-text contrast: $contrast", contrast >= 4.5f)
    }
    @Test fun primaryAndSecondaryTextRemainReadableInBothThemesAndTheNavyDashboard() {
        for ((name, colors) in listOf("Light" to LedgerLightColors, "Dark" to LedgerDarkColors)) {
            readable("$name background", colors.onBackground, colors.background)
            readable("$name card", colors.onSurface, colors.surfaceContainer)
            readable("$name muted label", colors.onSurfaceVariant, colors.surfaceContainer)
            readable("$name primary button", colors.onPrimary, colors.primary)
            readable("$name selected control", colors.onPrimaryContainer, colors.primaryContainer)
        }
        readable("Dashboard primary", DashboardColors.primary, DashboardColors.surface)
        readable("Dashboard secondary", DashboardColors.secondary, DashboardColors.surface)
    }
}
