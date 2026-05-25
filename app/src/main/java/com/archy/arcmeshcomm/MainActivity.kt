package com.archy.arcmeshcomm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.archy.arcmeshcomm.ui.ArcMeshApp
import com.archy.arcmeshcomm.ui.theme.ArcMeshCommTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArcMeshCommTheme(dynamicColor = false) {
                ArcMeshApp()
            }
        }
    }
}
