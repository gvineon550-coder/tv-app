package com.tv.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.Surface
import com.tv.player.ui.HomeScreen
import com.tv.player.ui.theme.TVTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TVTheme {
                Surface {
                    HomeScreen()
                }
            }
        }
    }
}
