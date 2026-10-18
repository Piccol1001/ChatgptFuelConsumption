package my.fuelledger.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared phone/tablet dimensions. Font sizes remain scalable Material typography. */
internal object LedgerSpacing {
    val screen = 16.dp
    val section = 16.dp
    val card = 16.dp
    val cardRadius = 20.dp
    val heroRadius = 24.dp
    val touchTarget = 48.dp
    val contentMaxWidth = 840.dp
}

/** The dashboard deliberately uses a navy surface in both themes. */
internal object DashboardColors {
    val surface = Color(0xFF14283D)
    val primary = Color(0xFFF3F6FA)
    val secondary = Color(0xFFADBED0)
    val divider = Color(0xFF35495B)
}
