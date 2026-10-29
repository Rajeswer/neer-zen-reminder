package com.zen.neer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsScreen(
    settings: ZenSettings,
    sipsToday: Int,
    onResetToday: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ZenTopBar(
            title = "Settings",
            navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
            navigationLabel = "Back",
            onNavigate = onBack
        )
        Column(Modifier.padding(horizontal = Space.screenInset, vertical = Space.s8)) {
            ZenSurface(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.s4)) {
                SettingRow(title = "Daily goal", body = "Mindful sips to aim for each day") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            stateDescription = "${settings.dailyGoal} sips"
                        }
                    ) {
                        IconButton(
                            onClick = { settings.updateGoal(settings.dailyGoal - 1) },
                            enabled = settings.dailyGoal > GOAL_RANGE.first
                        ) { Icon(Icons.Outlined.Remove, contentDescription = "Decrease daily goal", tint = Zen.textPrimary) }
                        Text(
                            "${settings.dailyGoal}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Zen.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 28.dp).clearAndSetSemantics { }
                        )
                        IconButton(
                            onClick = { settings.updateGoal(settings.dailyGoal + 1) },
                            enabled = settings.dailyGoal < GOAL_RANGE.last
                        ) { Icon(Icons.Outlined.Add, contentDescription = "Increase daily goal", tint = Zen.textPrimary) }
                    }
                }
                ZenDivider(Modifier.padding(horizontal = Space.s16))
                ToggleRow("Sound", "A soft chime when a timer finishes", settings.sound) { settings.updateSound(it) }
                ZenDivider(Modifier.padding(horizontal = Space.s16))
                ToggleRow("Haptics", "Gentle vibration at meaningful moments", settings.haptics) { settings.updateHaptics(it) }
            }

            Spacer(Modifier.height(Space.s24))
            ZenSurface(Modifier.fillMaxWidth()) {
                Text("Today's stones", style = MaterialTheme.typography.titleMedium, color = Zen.textPrimary)
                Text(
                    "$sipsToday mindful sips recorded today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zen.textSecondary
                )
                Spacer(Modifier.height(Space.s12))
                TextButton(
                    onClick = { confirmReset = true },
                    enabled = sipsToday > 0,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        "Reset today's count",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (sipsToday > 0) Zen.error else Zen.textDisabled
                    )
                }
            }

            Spacer(Modifier.height(Space.s24))
            Text("Neer Zen 2.1", style = MaterialTheme.typography.titleMedium, color = Zen.textPrimary)
            Text(
                "Works fully offline. Typefaces: Source Serif 4 and Inter, under the SIL Open Font License.",
                style = MaterialTheme.typography.bodySmall,
                color = Zen.textSecondary,
                modifier = Modifier.padding(top = Space.s4, bottom = Space.s32)
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Zen.surface,
            title = { Text("Reset today's count?", style = MaterialTheme.typography.headlineSmall, color = Zen.textPrimary) },
            text = {
                Text(
                    "Today's mindful sips go back to zero. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Zen.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onResetToday()
                }) { Text("Reset", color = Zen.error, style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Keep", color = Zen.textPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

@Composable
private fun SettingRow(title: String, body: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.s16, vertical = Space.s8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Zen.textPrimary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Zen.textSecondary)
        }
        Spacer(Modifier.width(Space.s12))
        trailing()
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = Space.s16, vertical = Space.s8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Zen.textPrimary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Zen.textSecondary)
        }
        Spacer(Modifier.width(Space.s12))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Zen.actionPrimaryContent,
                checkedTrackColor = Zen.actionPrimary,
                uncheckedThumbColor = Zen.iconSecondary,
                uncheckedTrackColor = Zen.surfaceSecondary,
                uncheckedBorderColor = Zen.iconSecondary
            ),
            modifier = Modifier.size(width = 52.dp, height = 32.dp)
        )
    }
}
