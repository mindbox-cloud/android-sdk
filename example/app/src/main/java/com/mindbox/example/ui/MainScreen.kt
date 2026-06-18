package com.mindbox.example.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindbox.example.R
import com.mindbox.example.ui.theme.SetStatusBarAppearance

/** Plain data carrier for everything the SDK Info card displays. */
data class SdkInfoState(
    val deviceUuid: String = "",
    val token: String = "",
    val tokenDate: String = "",
    val pushUrl: String = "",
    val pushPayload: String = "",
    val sdkVersion: String = "",
)

/** In-app options offered by the picker bottom sheet. A stable id so callers
 * don't have to compare localized display titles. */
enum class InAppOption(@StringRes val titleRes: Int) {
    WheelOfFortune(R.string.inapp_wheel_of_fortune),
    LuckFeed(R.string.inapp_luck_feed),
    ScratchCard(R.string.inapp_scratch_card),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: SdkInfoState,
    darkTheme: Boolean,
    onCopy: (label: String, value: String) -> Unit,
    onShowInApp: () -> Unit,
    onSendAsync: () -> Unit,
    onSendSync: () -> Unit,
    onOpenSecondActivity: () -> Unit,
    onOpenHistory: () -> Unit,
    showInAppSheet: Boolean,
    onDismissInAppSheet: () -> Unit,
    onPickInApp: (InAppOption) -> Unit,
) {
    SetStatusBarAppearance(darkIcons = darkTheme)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HeroHeader(sdkVersion = state.sdkVersion)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
        ) {
            SdkInfoCard(state = state, onCopy = onCopy)

            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.section_actions), Modifier.padding(start = 6.dp, bottom = 12.dp))

            ShowInAppButton(onClick = onShowInApp)
            Spacer(Modifier.height(12.dp))
            ActionsGroup(
                onSendAsync = onSendAsync,
                onSendSync = onSendSync,
                onOpenSecondActivity = onOpenSecondActivity,
                onOpenHistory = onOpenHistory,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showInAppSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onDismissInAppSheet,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            InAppPickerContent(onPick = onPickInApp)
        }
    }
}

@Composable
private fun HeroHeader(sdkVersion: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
            )
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 22.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.brand_name),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                )
                Text(
                    text = stringResource(R.string.hero_subtitle),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Box(
                Modifier
                    .background(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.version_format, sdkVersion),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.5f.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private const val SECRET_MASK = "••••••••••••••••••••"

/** How many leading characters of the device UUID stay visible on screen. */
private const val UUID_VISIBLE_PREFIX = 9

/**
 * Masks the device UUID for display: keeps the first [UUID_VISIBLE_PREFIX]
 * characters and replaces the remainder with "****". Per legal guidance the full
 * UUID must not be readable or copyable from the example app's main screen.
 */
private fun maskUuid(value: String): String =
    if (value.length <= UUID_VISIBLE_PREFIX) value else value.take(UUID_VISIBLE_PREFIX) + "****-****-****-************"

@Composable
private fun SdkInfoCard(state: SdkInfoState, onCopy: (String, String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(9.dp))
            SectionLabel(stringResource(R.string.section_sdk_info))
        }

        Spacer(Modifier.height(10.dp))
        UuidField(
            label = stringResource(R.string.label_device_uuid),
            value = state.deviceUuid,
        )

        Spacer(Modifier.height(4.dp))
        ServiceDataSection(state = state, onCopy = onCopy)
    }
}

/** Device UUID: label + masked value in a single-line monospace box. Per legal
 * guidance the full UUID must not be copyable or fully visible here, so there is
 * no copy button and only the first [UUID_VISIBLE_PREFIX] characters are shown. */
@Composable
private fun UuidField(label: String, value: String) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Text(
            text = if (value.isEmpty()) stringResource(R.string.value_placeholder) else maskUuid(value),
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
        )
    }
}

/** Collapsible "Service data" section: Token (masked + reveal), Token date,
 * Url from push, Payload. */
@Composable
private fun ServiceDataSection(state: SdkInfoState, onCopy: (String, String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "serviceChevron")

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { open = !open }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.section_service_data),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = stringResource(R.string.toggle_service_data),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation),
        )
    }

    AnimatedVisibility(visible = open) {
        Column {
            val tokenLabel = stringResource(R.string.label_token)
            TokenRow(
                label = tokenLabel,
                value = state.token,
                onCopy = { onCopy(tokenLabel, state.token) },
            )
            Spacer(Modifier.height(9.dp))
            InlineValueRow(label = stringResource(R.string.label_token_date), value = state.tokenDate)
            Spacer(Modifier.height(9.dp))
            FromPushRow(label = stringResource(R.string.label_url_from_push), value = state.pushUrl)
            Spacer(Modifier.height(9.dp))
            FromPushRow(label = stringResource(R.string.label_payload), value = state.pushPayload)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Token row: label + masked/revealed value + eye toggle + copy. */
@Composable
private fun TokenRow(label: String, value: String, onCopy: () -> Unit) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.width(78.dp),
        )
        Text(
            text = if (revealed) value.ifEmpty { stringResource(R.string.value_placeholder) } else SECRET_MASK,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.5f.sp,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconCircleButton(
            icon = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = stringResource(if (revealed) R.string.hide_token else R.string.reveal_token),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { revealed = !revealed },
        )
        IconCircleButton(
            icon = Icons.Filled.ContentCopy,
            contentDescription = stringResource(R.string.copy_content_description, label),
            tint = MaterialTheme.colorScheme.primary,
            onClick = onCopy,
        )
    }
}

@Composable
private fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(32.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun InlineValueRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(
            text = value.ifEmpty { stringResource(R.string.value_placeholder) },
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.5f.sp,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(11.dp),
                )
                .padding(horizontal = 11.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun FromPushRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        if (value.isEmpty()) {
            EmptyFromPushChip(stringResource(R.string.chip_filled_from_push))
        } else {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5f.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun ShowInAppButton(onClick: () -> Unit) {
    val source = rememberPressSource()
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressScale(source)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Campaign,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(23.dp),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            stringResource(R.string.action_show_inapp),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ActionsGroup(
    onSendAsync: () -> Unit,
    onSendSync: () -> Unit,
    onOpenSecondActivity: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val big = 22.dp
    val small = 7.dp
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ActionRow(
            icon = Icons.AutoMirrored.Filled.Send,
            label = stringResource(R.string.action_send_async),
            shape = RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small),
            onClick = onSendAsync,
        )
        ActionRow(
            icon = Icons.Filled.Sync,
            label = stringResource(R.string.action_send_sync),
            shape = RoundedCornerShape(small),
            onClick = onSendSync,
        )
        ActionRow(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            label = stringResource(R.string.action_open_second_activity),
            shape = RoundedCornerShape(small),
            onClick = onOpenSecondActivity,
        )
        ActionRow(
            icon = Icons.Filled.Notifications,
            label = stringResource(R.string.action_open_notification_history),
            shape = RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big),
            onClick = onOpenHistory,
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val source = rememberPressSource()
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(source)
            .background(color = MaterialTheme.colorScheme.secondaryContainer, shape = shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = 14.5f.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun InAppPickerContent(onPick: (InAppOption) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, bottom = 30.dp),
    ) {
        Text(
            text = stringResource(R.string.inapp_picker_title),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        )
        InAppOption.entries.forEach { option ->
            OutlinedButton(
                onClick = { onPick(option) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 13.dp),
            ) {
                Text(
                    stringResource(option.titleRes),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}
