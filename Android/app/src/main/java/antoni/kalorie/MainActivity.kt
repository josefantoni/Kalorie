package antoni.kalorie

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import antoni.kalorie.application.KalorieApp
import antoni.kalorie.core.extensions.withDynamicTypeLimit

class MainActivity : ComponentActivity() {

    // MARK: - Functions

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withDynamicTypeLimit())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KalorieApp()
        }
    }
}
