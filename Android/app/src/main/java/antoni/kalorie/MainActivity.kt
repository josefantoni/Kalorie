package antoni.kalorie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import antoni.kalorie.scaffold.ScaffoldProbeConfigurator

class MainActivity : ComponentActivity() {

    // MARK: - Functions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScaffoldProbeConfigurator().createView()
        }
    }
}
