package com.petr.toll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Duration

/**
 * The toll ladder: free up to half the limit, then typing, then grey, then holds. The scale runs to 125% of the
 * limit, and a marker shows today. Zones are labelled, so colour is never the only cue.
 */
@Composable
fun LadderMeter(paid: Duration, limit: Duration, modifier: Modifier = Modifier) {
    val p = LocalTollPalette.current
    val zones = listOf("Free" to 2f, "Typing" to 1f, "Grey" to 1f, "Holds" to 1f)
    val scale = 1.25f
    val position = (paid.toMillis().toFloat() / limit.toMillis().coerceAtLeast(1)).coerceIn(0f, scale) / scale
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            Row(Modifier.fillMaxSize().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                zones.forEachIndexed { i, (_, weight) ->
                    Box(
                        Modifier
                            .weight(weight)
                            .fillMaxSize()
                            .background(p.ladder[i], RoundedCornerShape(4.dp)),
                    )
                }
            }
            Box(
                Modifier
                    .offset(x = (maxWidth * position - 3.dp).coerceIn(0.dp, maxWidth - 6.dp))
                    .width(6.dp)
                    .fillMaxSize()
                    .background(p.ink, RoundedCornerShape(3.dp))
                    .border(2.dp, p.surface, RoundedCornerShape(3.dp)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            zones.forEach { (label, weight) ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.weight(weight))
            }
        }
    }
}
