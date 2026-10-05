package com.petr.toll.ui.earn

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petr.toll.session.PushupCounter
import com.petr.toll.session.TollRepository
import com.petr.toll.ui.BarrierStripe
import com.petr.toll.ui.LocalTollPalette
import com.petr.toll.ui.Overpass
import com.petr.toll.ui.TollTheme
import com.petr.toll.ui.short
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * Earn time with push-ups. The phone lies on the floor under Petr's face, and the proximity sensor counts each time
 * the face comes close and goes away again. 20 push-ups earn one task's worth of time.
 */
class PushupActivity : ComponentActivity(), SensorEventListener {
    private val repo by lazy { TollRepository.get(this) }
    private val counter = PushupCounter()
    private var sensorManager: SensorManager? = null
    private var proximity: Sensor? = null
    private var count by mutableIntStateOf(0)
    private var reported = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager = getSystemService(SensorManager::class.java)
        proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        setContent {
            TollTheme(dark = true) {
                val home by repo.home.collectAsState()
                val today = home.today
                val reward = home.settings.earnPerTask
                val earnLeft = today?.earnLeft ?: home.settings.earnCapPerDay
                PushupScreen(
                    count = count,
                    goal = GOAL,
                    reward = reward,
                    canEarn = earnLeft > Duration.ZERO && proximity != null,
                    noSensor = proximity == null,
                    onDone = {
                        if (!reported) {
                            reported = true
                            repo.earn("pushups")
                        }
                        finish()
                    },
                    onStop = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        proximity?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val sensor = proximity ?: return
        if (count >= GOAL) return
        if (counter.onReading(event.values[0], sensor.maximumRange, event.timestamp / 1_000_000)) count = counter.count
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val GOAL = 20
    }
}

@Composable
private fun PushupScreen(
    count: Int,
    goal: Int,
    reward: Duration,
    canEarn: Boolean,
    noSensor: Boolean,
    onDone: () -> Unit,
    onStop: () -> Unit,
) {
    val p = LocalTollPalette.current
    // At the daily cap the counter still runs, but nothing is earned or claimed.
    val done = canEarn && count >= goal
    LaunchedEffect(done) {
        if (done) {
            delay(1500)
            onDone()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .safeDrawingPadding(),
    ) {
        BarrierStripe(Modifier.fillMaxWidth().height(14.dp))
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("EARN TIME", style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.fillMaxWidth())
            Text(
                "$goal push-ups for +${reward.short()}",
                style = MaterialTheme.typography.headlineSmall,
                color = p.ink,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                when {
                    noSensor -> "This phone has no proximity sensor, so Toll can't count push-ups."
                    !canEarn -> "You've already earned the most for today. Push-ups won't add more until tomorrow."
                    else -> "Lay the phone on the floor, screen up, under your face. Each time your face comes " +
                        "close to the top of the phone and goes back up counts as one."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(p.line, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(
                        p.go,
                        -90f,
                        360f * count / goal,
                        false,
                        Offset(inset, inset),
                        arc,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (done) "+${reward.short()}" else "$count",
                        fontFamily = Overpass,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (done) 44.sp else 72.sp,
                        color = if (done) p.go else p.ink,
                    )
                    Text(
                        if (done) "earned" else "of $goal",
                        style = MaterialTheme.typography.bodyLarge,
                        color = p.muted,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (done) "Nice. Back to Toll." else "Keep the screen on. Toll stops counting at $goal.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.muted,
                textAlign = TextAlign.Center,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Text("Stop", style = MaterialTheme.typography.labelLarge, color = p.muted)
            }
        }
    }
}
