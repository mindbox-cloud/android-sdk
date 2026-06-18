package com.mindbox.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindbox.example.R
import com.mindbox.example.ui.theme.SetStatusBarAppearance

@Composable
fun SecondActivityScreen(
    pushUrl: String,
    pushPayload: String,
    triggerUrl: String,
    darkTheme: Boolean,
    onBack: () -> Unit,
) {
    SetStatusBarAppearance(darkIcons = !darkTheme)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        // App bar
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.second_activity_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.6).sp,
            )
        }

        // Centered hero
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(46.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.second_activity_hero_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
            )
            Text(
                stringResource(R.string.second_activity_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
            )
            TriggerUrlHint(triggerUrl)
        }

        // Info card
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp)),
        ) {
            PushField(label = stringResource(R.string.label_url_from_push), value = pushUrl)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            PushField(label = stringResource(R.string.label_payload), value = pushPayload)
        }
    }
}

@Composable
private fun TriggerUrlHint(triggerUrl: String) {
    Column(
        Modifier.padding(top = 20.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.second_activity_trigger_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(999.dp))
                .padding(start = 11.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        ) {
            Icon(
                Icons.Filled.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                triggerUrl,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PushField(label: String, value: String) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        SectionLabel(label)
        Spacer(Modifier.height(8.dp))
        if (value.isEmpty()) {
            EmptyFromPushChip(stringResource(R.string.chip_open_from_push))
        } else {
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}
