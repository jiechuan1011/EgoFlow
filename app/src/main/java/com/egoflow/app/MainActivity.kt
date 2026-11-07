package com.egoflow.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.egoflow.app.navigation.AppNavigation
import com.egoflow.app.ui.theme.EgoFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            Log.w("EgoFlow", "enableEdgeToEdge failed", e)
        }

        try {
            setContent {
                EgoFlowTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        AppNavigation(navController = navController)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("EgoFlow", "Composition failed", e)
            setContent {
                Surface(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.material3.Text("App failed to start: ${e.message}")
                }
            }
        }
    }
}
