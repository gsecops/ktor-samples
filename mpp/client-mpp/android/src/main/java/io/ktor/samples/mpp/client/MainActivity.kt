package io.ktor.samples.mpp.client

import android.os.*
import android.support.v7.app.*
import android.widget.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val api = ApplicationApi()

        api.about {
            launch(UI) {
                findViewById<TextView>(R.id.about).text = it
            }
        }

        setContentView(R.layout.activity_main)
    }
}
