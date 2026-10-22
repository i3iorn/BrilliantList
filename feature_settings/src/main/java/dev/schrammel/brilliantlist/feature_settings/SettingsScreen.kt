package dev.schrammel.brilliantlist.feature_settings

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import dev.schrammel.brilliantlist.core_model.CrisisCatalog
import dev.schrammel.brilliantlist.core_model.CrisisRegion
import dev.schrammel.brilliantlist.core_model.RotationMode
import dev.schrammel.brilliantlist.core_model.SortMode

/**
 * The settings.
 *
 * The screen is scrollable, so every control stays reachable when the font
 * is large or the screen is small. The labels use the free width of their
 * row, so a switch is never pushed off the screen.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Local slider state: writing to DataStore on every drag step floods disk I/O
    // and races with concurrent edits. Commit only when the drag settles.
    var sliderValue by remember(settings.rotationIntervalMinutes) {
        mutableStateOf(settings.rotationIntervalMinutes.toFloat())
    }

    var showPassphraseDialog by remember { mutableStateOf(false) }
    var exportMode by remember { mutableStateOf(true) }
    var passphrase by remember { mutableStateOf("") }
    var passphraseConfirmation by remember { mutableStateOf("") }
    var pendingPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var showEraseDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val secret = pendingPassphrase
        pendingPassphrase = null
        if (uri != null && secret != null) {
            viewModel.exportJournal(uri, secret)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val secret = pendingPassphrase
        pendingPassphrase = null
        if (uri != null && secret != null) {
            viewModel.importJournal(uri, secret)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(stringResource(R.string.settings_section_journal))

            Text(
                text = stringResource(R.string.settings_entry_order),
                style = MaterialTheme.typography.bodyLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.sortMode == SortMode.MANUAL,
                    onClick = { viewModel.onSortModeChange(SortMode.MANUAL) },
                    label = { Text(stringResource(R.string.settings_order_manual)) }
                )
                FilterChip(
                    selected = settings.sortMode == SortMode.NEWEST_FIRST,
                    onClick = { viewModel.onSortModeChange(SortMode.NEWEST_FIRST) },
                    label = { Text(stringResource(R.string.settings_order_newest)) }
                )
            }

            SectionHeader(stringResource(R.string.settings_section_widget))

            Text(
                text = stringResource(R.string.settings_widget_interval),
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = sliderValue,
                // The interval snaps to full quarters of an hour, so the
                // value is something a person can read.
                onValueChange = {
                    sliderValue = ((it / INTERVAL_STEP_MINUTES).roundToInt() * INTERVAL_STEP_MINUTES)
                        .toFloat()
                },
                onValueChangeFinished = {
                    viewModel.onWidgetIntervalChange(sliderValue.toInt())
                },
                valueRange = 15f..1440f
            )
            Text(
                text = intervalLabel(sliderValue.toInt()),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.settings_rotation_mode),
                style = MaterialTheme.typography.bodyLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.rotationMode == RotationMode.RANDOM,
                    onClick = { viewModel.onRotationModeChange(RotationMode.RANDOM) },
                    label = { Text(stringResource(R.string.settings_rotation_random)) }
                )
                FilterChip(
                    selected = settings.rotationMode == RotationMode.SEQUENTIAL,
                    onClick = { viewModel.onRotationModeChange(RotationMode.SEQUENTIAL) },
                    label = { Text(stringResource(R.string.settings_rotation_sequential)) }
                )
            }

            SwitchRow(
                title = stringResource(R.string.settings_widget_auto_update),
                checked = settings.widgetAutoUpdate,
                onCheckedChange = viewModel::onAutoUpdateChange
            )

            SwitchRow(
                title = stringResource(R.string.settings_widget_show_content),
                checked = settings.widgetShowContent,
                onCheckedChange = viewModel::onWidgetShowContentChange
            )

            SectionHeader(stringResource(R.string.settings_section_privacy))

            val deviceSecure = remember { context.hasDeviceLock() }
            SwitchRow(
                title = stringResource(R.string.settings_app_lock),
                summary = if (deviceSecure) {
                    stringResource(R.string.settings_app_lock_summary)
                } else {
                    stringResource(R.string.settings_app_lock_unavailable)
                },
                checked = settings.appLockEnabled,
                enabled = deviceSecure,
                onCheckedChange = viewModel::onAppLockChange
            )

            ActionRow(
                title = stringResource(R.string.settings_export),
                summary = stringResource(R.string.settings_export_summary),
                onClick = {
                    exportMode = true
                    showPassphraseDialog = true
                }
            )

            ActionRow(
                title = stringResource(R.string.settings_import),
                summary = stringResource(R.string.settings_import_summary),
                onClick = {
                    exportMode = false
                    showPassphraseDialog = true
                }
            )

            ActionRow(
                title = stringResource(R.string.settings_erase),
                summary = stringResource(R.string.settings_erase_summary),
                destructive = true,
                onClick = { showEraseDialog = true }
            )

            Text(
                text = stringResource(R.string.settings_stored_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionHeader(stringResource(R.string.settings_section_support))

            RegionPicker(
                region = CrisisCatalog.normalize(settings.crisisRegion),
                onRegionChange = viewModel::onRegionChange
            )
        }
    }

    if (showPassphraseDialog) {
        PassphraseDialog(
            title = stringResource(
                if (exportMode) {
                    R.string.settings_export_passphrase_title
                } else {
                    R.string.settings_import_passphrase_title
                }
            ),
            text = stringResource(R.string.settings_export_passphrase_text),
            passphrase = passphrase,
            confirmation = passphraseConfirmation,
            onPassphraseChange = { passphrase = it },
            onConfirmationChange = { passphraseConfirmation = it },
            onDismiss = {
                showPassphraseDialog = false
                passphrase = ""
                passphraseConfirmation = ""
            },
            onConfirm = {
                val secret = passphrase.toCharArray()
                pendingPassphrase = secret
                showPassphraseDialog = false
                passphrase = ""
                passphraseConfirmation = ""
                if (exportMode) {
                    exportLauncher.launch("brilliantlist-export.blx")
                } else {
                    importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                }
            }
        )
    }

    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = { showEraseDialog = false },
            title = { Text(stringResource(R.string.settings_erase_confirm_title)) },
            text = { Text(stringResource(R.string.settings_erase_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEraseDialog = false
                        viewModel.eraseEverything()
                    }
                ) {
                    Text(stringResource(R.string.settings_erase_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** A section title. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/**
 * A switch with a label.
 *
 * The label takes the free width of the row, so the switch is never pushed
 * out of the screen when the font is large.
 */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/** A row that starts an action. */
@Composable
private fun ActionRow(
    title: String,
    summary: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClick) {
            Text(
                text = stringResource(
                    if (destructive) R.string.settings_erase_action else R.string.settings_action
                ),
                color = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

/**
 * Region picker.
 *
 * A dropdown instead of a free text field: a typed region like "usa" or
 * "United States" silently fell back to the generic crisis resources before,
 * which is the worst possible failure mode on the crisis page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionPicker(
    region: CrisisRegion?,
    onRegionChange: (CrisisRegion) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = region?.displayName ?: stringResource(R.string.settings_region_unknown),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_region)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CrisisRegion.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onRegionChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Asks for the passphrase of an export.
 *
 * The passphrase is never stored: it is used to encrypt or decrypt the file
 * and then forgotten. It has to be typed twice for an export, because a
 * passphrase that is not written down is a backup that cannot be restored.
 */
@Composable
private fun PassphraseDialog(
    title: String,
    text: String,
    passphrase: String,
    confirmation: String,
    onPassphraseChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val tooShort = passphrase.isNotEmpty() && passphrase.length < MIN_PASSPHRASE_LENGTH
    val mismatch = confirmation.isNotEmpty() && confirmation != passphrase
    val strength = passphraseStrength(passphrase)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    label = { Text(stringResource(R.string.settings_passphrase_label)) },
                    singleLine = true,
                    isError = tooShort,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                if (tooShort) {
                    Text(
                        text = stringResource(R.string.settings_passphrase_too_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (passphrase.isNotEmpty()) {
                    // The passphrase is the only thing that protects the
                    // file, so the user is told how strong it is.
                    Text(
                        text = stringResource(
                            R.string.settings_passphrase_strength,
                            stringResource(strength.label)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = onConfirmationChange,
                    label = { Text(stringResource(R.string.settings_passphrase_repeat)) },
                    singleLine = true,
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                if (mismatch) {
                    Text(
                        text = stringResource(R.string.settings_passphrase_mismatch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = passphrase.length >= MIN_PASSPHRASE_LENGTH &&
                    passphrase == confirmation
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** How strong a passphrase is. */
private enum class PassphraseStrength(val label: Int) {
    WEAK(R.string.settings_passphrase_weak),
    FAIR(R.string.settings_passphrase_fair),
    STRONG(R.string.settings_passphrase_strong)
}

/**
 * Estimates the strength of a passphrase.
 *
 * The length matters most, a passphrase of many words is a good passphrase.
 */
private fun passphraseStrength(passphrase: String): PassphraseStrength {
    if (passphrase.isEmpty()) return PassphraseStrength.WEAK
    val words = passphrase.split(Regex("\\s+")).count { it.isNotEmpty() }
    val hasNumbers = passphrase.any { it.isDigit() }
    val hasSymbols = passphrase.any { !it.isLetterOrDigit() }
    val score = passphrase.length + words * 4 + (if (hasNumbers) 2 else 0) +
        (if (hasSymbols) 2 else 0)
    return when {
        score >= 40 -> PassphraseStrength.STRONG
        score >= 20 -> PassphraseStrength.FAIR
        else -> PassphraseStrength.WEAK
    }
}

/** True when the device has a screen lock, so the app lock can be used. */
private fun Context.hasDeviceLock(): Boolean {
    val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    return keyguard?.isDeviceSecure == true
}

/** The shortest passphrase that is accepted for an export. */
private const val MIN_PASSPHRASE_LENGTH = 8

/** The step size of the interval slider, in minutes. */
private const val INTERVAL_STEP_MINUTES = 15

/** The interval as a readable duration. */
@Composable
private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.settings_minutes, minutes)
    minutes < 1440 -> stringResource(R.string.settings_hours, minutes / 60)
    else -> stringResource(R.string.settings_days, minutes / 1440)
}
