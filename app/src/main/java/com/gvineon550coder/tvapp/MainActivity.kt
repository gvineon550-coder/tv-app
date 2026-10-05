package com.gvineon550coder.tvapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.tv.material3.Surface
import com.gvineon550coder.tvapp.ui.HomeScreen
import com.gvineon550coder.tvapp.ui.theme.TVTheme
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
