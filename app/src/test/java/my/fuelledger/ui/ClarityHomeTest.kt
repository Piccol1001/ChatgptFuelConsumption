package my.fuelledger.ui

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClarityHomeTest {
    @get:Rule val compose = createComposeRule()
    private fun render(dark: Boolean) {
        val (state, finance) = overviewSample()
        compose.setContent { LedgerTheme(dark) { Surface { OverviewDashboard(state, finance, {}, {}, {}, {}, {}, {}) } } }
        compose.onNodeWithTag("home-income-amount", true).assertTextEquals("RM 3,000.00")
        compose.onNodeWithTag("home-month-amount", true).assertExists()
        compose.onNodeWithText("Add Expense").assertHasClickAction()
        compose.onNodeWithText("Add Fuel").assertHasClickAction()
        val card = compose.onNodeWithTag("home-financial-card").fetchSemanticsNode().boundsInRoot
        assertEquals(16f, card.left, .5f)
        assertEquals(377f, card.right, .5f)
        val amount = compose.onNodeWithTag("home-today-amount", true).fetchSemanticsNode().boundsInRoot
        assertTrue(card.contains(amount.topLeft) && card.contains(amount.bottomRight))
        val dir = File("build/reports/clarity").apply { mkdirs() }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "home-${if (dark) "dark" else "light"}-393.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    @Test fun darkHomeUsesWrittenClarityHierarchy() = render(true)
    @Test fun lightHomeKeepsNavyFinancialCardAndActualIncome() = render(false)
}
