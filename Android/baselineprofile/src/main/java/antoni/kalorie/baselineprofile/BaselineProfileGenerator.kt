package antoni.kalorie.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    // MARK: - Properties

    @get:Rule
    val rule = BaselineProfileRule()

    private val addFood = Pattern.compile("Add food|Přidat jídlo")

    // MARK: - Functions

    @Test
    fun generate() = rule.collect(packageName = "antoni.kalorie") {
        pressHome()
        startActivityAndWait()

        repeat(3) {
            val addFoodButton = device.wait(Until.findObject(By.desc(addFood)), TIMEOUT_MILLIS)
                ?: device.wait(Until.findObject(By.text(addFood)), TIMEOUT_MILLIS)
                ?: error(
                    "Add food button not found, app shows: " +
                        device.findObjects(By.pkg("antoni.kalorie"))
                            .flatMap { listOfNotNull(it.text, it.contentDescription) }
                            .filter { it.isNotBlank() },
                )
            addFoodButton.click()
            device.waitForIdle()
            repeat(MAX_BACK_PRESSES) {
                if (device.hasObject(By.desc(addFood)) || device.hasObject(By.text(addFood))) return@repeat
                device.pressBack()
                device.waitForIdle()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000L
        const val MAX_BACK_PRESSES = 3
    }
}
