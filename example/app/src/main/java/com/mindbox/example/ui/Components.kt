package com.mindbox.example.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * M3 Expressive press feedback: a quick squish on press and a springy overshoot
 * back to rest on release. Mirrors the `press` handler in the source design.
 *
 * Pass the same [interactionSource] you give to `clickable` so the scale tracks
 * the real press state.
 */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.945f else 1f,
        animationSpec = spring(
            dampingRatio = if (pressed) Spring.DampingRatioNoBouncy else 0.45f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressScale",
    )
    return this.scale(scale)
}

@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/** Uppercase, wide-tracked section label ("SDK Info", "Actions"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}

/**
 * Empty-state chip shown when Url/Payload from push are not filled yet —
 * a pill with the `ads_click` icon and a hint to open from a push.
 */
@Composable
fun EmptyFromPushChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(999.dp),
            )
            .padding(start = 8.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.AdsClick,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
