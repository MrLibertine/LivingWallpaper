package com.livingwallpaper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.livingwallpaper.app.ui.LivingWallpaperApp
import com.livingwallpaper.app.ui.theme.LivingWallpaperTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LivingWallpaperTheme {
                LivingWallpaperApp()
            }
        }
    }
}
