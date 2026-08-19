package com.mindbox.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import coil.compose.AsyncImage
import com.mindbox.example.R
import com.mindbox.example.ui.theme.SetStatusBarAppearance

@Composable
fun NotificationHistoryScreen(
    notifications: List<MindboxRemoteMessage>,
    darkTheme: Boolean,
    onBack: () -> Unit,
    onItemClick: (MindboxRemoteMessage) -> Unit,
) {
    SetStatusBarAppearance(darkIcons = !darkTheme)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.notification_history_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.6).sp,
            )
        }

        HintBanner(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )

        if (notifications.isEmpty()) {
            EmptyHistory()
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(notifications, key = { it.uniqueKey }) { item ->
                    NotificationCard(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: MindboxRemoteMessage, onClick: () -> Unit) {
    val source = rememberPressSource()
    Column(
        Modifier
            .fillMaxWidth()
            .pressScale(source)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
            Text(
                text = item.uniqueKey,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            item.title.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
            item.description.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.5f.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        item.imageUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
        }

        item.pushLink?.takeIf { it.isNotEmpty() }?.let { link ->
            Row(
                Modifier.padding(start = 18.dp, end = 18.dp, top = 13.dp, bottom = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = link,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun HintBanner(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.notification_history_hint_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.notification_history_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.notification_history_payload_example),
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.5f.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.NotificationsNone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.notifications_empty_title),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.notifications_empty_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
