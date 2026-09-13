package com.example.knockflasher

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.knockflasher.ui.theme.KnockFlasherTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // State for detector settings
    private var sensitivity by mutableFloatStateOf(8f)
    private var flashColor by mutableStateOf(Color.White)
    private var isFlashing by mutableStateOf(false)
    private var isCooldown by mutableStateOf(false)
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.d("KnockDetector", "MainActivity Created")

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            KnockFlasherTheme {
                MainScreen(
                    isFlashing = isFlashing,
                    flashColor = flashColor,
                    sensitivity = sensitivity,
                    onSensitivityChange = { sensitivity = it },
                    onColorChange = { flashColor = it },
                    onResetFlash = { 
                        Log.d("KnockDetector", "Flash sequence finished")
                        isFlashing = false 
                        isCooldown = true
                        lifecycleScope.launch {
                            delay(1200) // Cooldown to let vibrations settle
                            isCooldown = false
                            Log.d("KnockDetector", "System re-armed")
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("KnockDetector", "onResume - Registering sensor")
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("KnockDetector", "onPause - Unregistering sensor")
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Initialize baseline on first event
        if (lastX == 0f && lastY == 0f && lastZ == 0f) {
            lastX = x
            lastY = y
            lastZ = z
            return
        }

        val deltaX = abs(x - lastX)
        val deltaY = abs(y - lastY)
        val deltaZ = abs(z - lastZ)
        val totalDelta = deltaX + deltaY + deltaZ
        
        // ALWAYS update the last seen values. 
        // This ensures that when cooldown ends, we compare against the most recent 
        // resting state, rather than a "trigger" state from 2 seconds ago.
        lastX = x
        lastY = y
        lastZ = z

        // Only trigger if not already busy
        if (isFlashing || isCooldown) return

        if (totalDelta > sensitivity) {
            Log.d("KnockDetector", "!!! TRIGGER !!! Delta: $totalDelta")
            isFlashing = true
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun MainScreen(
    isFlashing: Boolean,
    flashColor: Color,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    onColorChange: (Color) -> Unit,
    onResetFlash: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "Door Knock Detector",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    color = if (isFlashing) flashColor else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isFlashing) "!!!" else "READY",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                Text(
                    text = "Detector is active. Mount phone to the door to detect vibrations.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.weight(1f))

                // Settings Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sensitivity Threshold: ${sensitivity.toInt()}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = sensitivity,
                        onValueChange = onSensitivityChange,
                        valueRange = 1f..30f
                    )
                    Text(
                        text = "Lower = More Sensitive",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Flash Color Selection",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ColorButton(Color.White, flashColor == Color.White, onColorChange)
                        ColorButton(Color.Red, flashColor == Color.Red, onColorChange)
                        ColorButton(Color.Green, flashColor == Color.Green, onColorChange)
                        ColorButton(Color.Blue, flashColor == Color.Blue, onColorChange)
                        ColorButton(Color.Yellow, flashColor == Color.Yellow, onColorChange)
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (isFlashing) {
            FullScreenFlash(flashColor, onResetFlash)
        }
    }
}

@Composable
fun ColorButton(color: Color, isSelected: Boolean, onClick: (Color) -> Unit) {
    Surface(
        onClick = { onClick(color) },
        shape = CircleShape,
        color = color,
        border = if (isSelected) BorderStroke(2.dp, Color.Black) else null,
        modifier = Modifier.size(48.dp)
    ) {
        // Just a colored circle
    }
}

@Composable
fun FullScreenFlash(flashColor: Color, onFinished: () -> Unit) {
    var isColorVisible by remember { mutableStateOf(true) }

    // Use a unique key to ensure it runs every time the composable enters the composition
    LaunchedEffect(flashColor) {
        Log.d("KnockDetector", "FullScreenFlash animation started")
        repeat(8) { i ->
            isColorVisible = !isColorVisible
            Log.d("KnockDetector", "Flash toggle $i: visible=$isColorVisible")
            delay(150)
        }
        Log.d("KnockDetector", "FullScreenFlash animation finished, calling onFinished")
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isColorVisible) flashColor else Color.Black)
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    KnockFlasherTheme {
        MainScreen(
            isFlashing = false,
            flashColor = Color.White,
            sensitivity = 15f,
            onSensitivityChange = {},
            onColorChange = {},
            onResetFlash = {}
        )
    }
}