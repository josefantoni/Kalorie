package antoni.kalorie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import antoni.kalorie.application.KalorieApp

class MainActivity : ComponentActivity() {

    // MARK: - Functions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KalorieApp()
        }
    }
}
