package my.fuelledger.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import my.fuelledger.data.defaultCategories
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ExpenseEntryControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun exercise(name: String, fontScale: Float = 1f) {
        var dark by mutableStateOf(false)
        compose.setContent {
            var amount by remember { mutableLongStateOf(0) }
            var category by remember { mutableStateOf("food") }
            var payment by remember { mutableStateOf("Cash") }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme(colorScheme = if (dark) LedgerDarkColors else LedgerLightColors, typography = LedgerTypography) {
                    EditorShell("Add expense", false, null, {}, {}, saveEnabled = amount > 0, compact = true) {
                        ExpenseAmount(amount)
                        ExpenseCategoryRow(defaultCategories, category) { category = it }
                        ExpensePaymentRow(payment) { payment = it }
                        CentsKeypad(amount, change = { amount = it }, rejected = { fail("Unexpected keypad overflow") })
                    }
                }
            }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        "0012345".forEach { compose.onNodeWithTag("expense-key-$it").performScrollTo().performClick() }
        compose.onNodeWithTag("expense-amount").performScrollTo().assertTextEquals("RM 123.45")
        compose.onNodeWithTag("expense-key-Backspace").performScrollTo().performClick()
        compose.onNodeWithTag("expense-amount").performScrollTo().assertTextEquals("RM 12.34")
        compose.onNodeWithContentDescription("Payment method Card").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Category Others").performScrollTo().performClick().assertIsSelected()
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            compose.onNodeWithContentDescription("Payment method Card").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Save").assertIsEnabled().assertIsDisplayed()
            compose.onNodeWithTag("expense-amount").performScrollTo().assertTextEquals("RM 12.34")
            val directory = File("app/build/reports/ui-screenshots").let { if (File("src/main/AndroidManifest.xml").exists()) File("build/reports/ui-screenshots") else it }
            directory.mkdirs()
            val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
            File(directory, "expense-$name-${if (theme) "dark" else "light"}.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        compose.onNodeWithTag("expense-key-Clear").performScrollTo().performClick()
        compose.onNodeWithTag("expense-amount").performScrollTo().assertTextEquals("RM 0.00")
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test @Config(qualifiers = "w320dp-h568dp-mdpi") fun smallPhoneBothThemes() = exercise("small")
    @Test @Config(qualifiers = "w600dp-h960dp-mdpi") fun widePhoneBothThemes() = exercise("wide")
    @Test fun accessibilityFontScaleBothThemes() = exercise("large-font", 1.6f)
}
