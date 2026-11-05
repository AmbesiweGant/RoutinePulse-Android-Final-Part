package com.routinepulse.app

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.routinepulse.app.ui.AppRoot

// AppCompatActivity (not plain ComponentActivity) so the in-app language switch works on every Android version.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Navy title bars always sit under the status bar, so keep its icons light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val container = (application as RoutinePulseApp).container
        setContent { AppRoot(container) }
    }
}
