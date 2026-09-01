package com.normo.morsetrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.normo.morsetrainer.ui.ChartScreen
import com.normo.morsetrainer.ui.KeyScreen
import com.normo.morsetrainer.ui.SendScreen
import com.normo.morsetrainer.ui.SettingsScreen
import com.normo.morsetrainer.ui.TrainerScreen
import com.normo.morsetrainer.ui.theme.MorseTrainerTheme

class MainActivity : ComponentActivity() {

    private val vm: MorseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MorseTrainerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MorseTrainerApp(vm)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Nothing should still be keying once we are off screen — least of all the torch.
        vm.stop()
        vm.outputs.release()
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    CHART("Card", Icons.Filled.GraphicEq),
    KEY("Key", Icons.Filled.TouchApp),
    TRAIN("Train", Icons.Filled.School),
    SEND("Send", Icons.Filled.Send),
    SETTINGS("Setup", Icons.Filled.Settings),
}

@Composable
private fun MorseTrainerApp(vm: MorseViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.CHART) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = {
                            // Leaving a screen should never leave the tone hanging.
                            if (tab != entry) vm.stop()
                            tab = entry
                        },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { insets ->
        val content = Modifier
            .fillMaxSize()
            .padding(insets)

        when (tab) {
            Tab.CHART -> ChartScreen(vm, content)
            Tab.KEY -> KeyScreen(vm, content)
            Tab.TRAIN -> TrainerScreen(vm, content)
            Tab.SEND -> SendScreen(vm, content)
            Tab.SETTINGS -> SettingsScreen(vm, content)
        }
    }
}
