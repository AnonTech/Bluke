package dev.arnv.bluke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.arnv.bluke.mapping.ControlSlot
import dev.arnv.bluke.mapping.ControllerPresets
import dev.arnv.bluke.mapping.ControllerProfile
import dev.arnv.bluke.mapping.ControllerProfileStore
import dev.arnv.bluke.mapping.KeyBinding
import dev.arnv.bluke.ui.KeyboardLayouts
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.theme.MyApplicationTheme

/** Human-readable name + slot order used by the editor; only the slots a profile actually uses are shown. */
private val SLOT_ORDER: List<Pair<Int, String>> = listOf(
    ControlSlot.DPAD_UP to "D-Pad Up",
    ControlSlot.DPAD_DOWN to "D-Pad Down",
    ControlSlot.DPAD_LEFT to "D-Pad Left",
    ControlSlot.DPAD_RIGHT to "D-Pad Right",
    ControlSlot.FACE_TOP to "Face: Top",
    ControlSlot.FACE_RIGHT to "Face: Right",
    ControlSlot.FACE_BOTTOM to "Face: Bottom",
    ControlSlot.FACE_LEFT to "Face: Left",
    ControlSlot.LEFT_BUMPER to "Left Bumper",
    ControlSlot.RIGHT_BUMPER to "Right Bumper",
    ControlSlot.LEFT_TRIGGER to "Left Trigger",
    ControlSlot.RIGHT_TRIGGER to "Right Trigger",
    ControlSlot.SELECT to "Select",
    ControlSlot.START to "Start",
    ControlSlot.GUIDE to "Guide",
    ControlSlot.SHARE to "Share"
)

/** Keys offered in the picker, grouped for a readable grid. Values are HID usage codes from [KeyboardLayouts]. */
private object PickerKeys {
    val K = KeyboardLayouts
    val letters = ('A'..'Z').map { it.toString() to (K.KEY_A + (it - 'A')) }
    val digits = (1..9).map { it.toString() to (K.KEY_1 + (it - 1)) } + listOf("0" to K.KEY_0)
    val arrows = listOf("↑" to K.KEY_UP, "↓" to K.KEY_DOWN, "←" to K.KEY_LEFT, "→" to K.KEY_RIGHT)
    val whitespace = listOf(
        "Space" to K.KEY_SPACE, "Enter" to K.KEY_ENTER, "Tab" to K.KEY_TAB,
        "Esc" to K.KEY_ESC, "Backspace" to K.KEY_BACKSPACE
    )
    val function = (1..12).map { "F$it" to (K.KEY_F1 + (it - 1)) }
    val modifiers = listOf(
        "LCtrl" to K.MOD_LCTRL, "LShift" to K.MOD_LSHIFT, "LAlt" to K.MOD_LALT, "LWin" to K.MOD_LWIN,
        "RCtrl" to K.MOD_RCTRL, "RShift" to K.MOD_RSHIFT, "RAlt" to K.MOD_RALT, "RWin" to K.MOD_RWIN
    )

    fun labelFor(keyCode: Int): String = (letters + digits + arrows + whitespace + function + modifiers)
        .firstOrNull { it.second == keyCode }?.first ?: "?"
}

private sealed class MapperScreen {
    object ProfileList : MapperScreen()
    data class Editor(val profileId: String) : MapperScreen()
}

class ControllerMapperActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                var screen by remember { mutableStateOf<MapperScreen>(MapperScreen.ProfileList) }
                when (val s = screen) {
                    is MapperScreen.ProfileList -> ProfileListScreen(
                        onBack = { finish() },
                        onOpenEditor = { id -> screen = MapperScreen.Editor(id) }
                    )
                    is MapperScreen.Editor -> ProfileEditorScreen(
                        profileId = s.profileId,
                        onBack = { screen = MapperScreen.ProfileList }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileListScreen(onBack: () -> Unit, onOpenEditor: (String) -> Unit) {
    val context = LocalContext.current
    var customProfiles by remember { mutableStateOf(ControllerProfileStore.listCustomProfiles(context)) }
    var duplicateSource by remember { mutableStateOf<ControllerProfile?>(null) }
    var profilePendingDelete by remember { mutableStateOf<ControllerProfile?>(null) }

    val refresh = { customProfiles = ControllerProfileStore.listCustomProfiles(context) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Controller Profiles") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("New profile") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    val fresh = ControllerProfile(
                        id = ControllerProfileStore.newProfileId(),
                        name = "New Profile",
                        isBuiltIn = false,
                        activeSlots = SLOT_ORDER.map { it.first }.toSet(),
                        labels = emptyMap(),
                        bindings = emptyMap()
                    )
                    ControllerProfileStore.saveProfile(context, fresh)
                    refresh()
                    onOpenEditor(fresh.id)
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Choose a preset to try it as-is, or duplicate it to make your own editable copy. " +
                    "Presets send keyboard keys instead of real gamepad input, for emulators that only listen for a keyboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            SettingsCardGroup(
                title = "Presets",
                items = ControllerPresets.ALL.map { preset ->
                    SettingsItemData(
                        title = preset.name,
                        subtitle = "Built-in · duplicate to customize",
                        action = {
                            TextButton(onClick = { duplicateSource = preset }) { Text("Duplicate") }
                        }
                    )
                }
            )

            if (customProfiles.isNotEmpty()) {
                SettingsCardGroup(
                    title = "My Profiles",
                    items = customProfiles.map { profile ->
                        SettingsItemData(
                            title = profile.name,
                            subtitle = "${profile.bindings.values.count { it.isBound }} of ${profile.activeSlots.size} bound",
                            onClick = { onOpenEditor(profile.id) },
                            action = {
                                Row {
                                    IconButton(onClick = { duplicateSource = profile }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
                                    }
                                    IconButton(onClick = { profilePendingDelete = profile }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        )
                    }
                )
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    duplicateSource?.let { source ->
        NameProfileDialog(
            initialName = "${source.name} Copy",
            confirmLabel = "Duplicate",
            onDismiss = { duplicateSource = null },
            onConfirm = { name ->
                val copy = ControllerProfileStore.duplicateProfile(context, source, name)
                duplicateSource = null
                refresh()
                onOpenEditor(copy.id)
            }
        )
    }

    profilePendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profilePendingDelete = null },
            title = { Text("Delete \"${profile.name}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    ControllerProfileStore.deleteProfile(context, profile.id)
                    profilePendingDelete = null
                    refresh()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { profilePendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditorScreen(profileId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var profile by remember(profileId) {
        mutableStateOf(ControllerProfileStore.loadProfile(context, profileId))
    }
    var showRename by remember { mutableStateOf(false) }
    var editingSlot by remember { mutableStateOf<Int?>(null) }

    val current = profile
    if (current == null) {
        // Deleted from elsewhere, or never saved - nothing to edit.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val updateBinding = { slot: Int, binding: KeyBinding ->
        val updated = current.copy(bindings = current.bindings + (slot to binding))
        ControllerProfileStore.saveProfile(context, updated)
        profile = updated
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(current.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        val rows = SLOT_ORDER.filter { current.activeSlots.contains(it.first) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            SettingsCardGroup(
                items = rows.map { (slot, label) ->
                    val binding = current.bindingFor(slot)
                    SettingsItemData(
                        title = current.labelFor(slot, label),
                        subtitle = label,
                        onClick = { editingSlot = slot },
                        action = {
                            AssistChip(
                                onClick = { editingSlot = slot },
                                label = { Text(bindingSummary(binding)) }
                            )
                        }
                    )
                }
            )
            Spacer(Modifier.height(96.dp))
        }
    }

    if (showRename) {
        NameProfileDialog(
            initialName = current.name,
            confirmLabel = "Save",
            onDismiss = { showRename = false },
            onConfirm = { newName ->
                val updated = current.copy(name = newName)
                ControllerProfileStore.saveProfile(context, updated)
                profile = updated
                showRename = false
            }
        )
    }

    editingSlot?.let { slot ->
        KeyPickerDialog(
            current = current.bindingFor(slot),
            onDismiss = { editingSlot = null },
            onConfirm = { binding ->
                updateBinding(slot, binding)
                editingSlot = null
            }
        )
    }
}

private fun bindingSummary(binding: KeyBinding): String {
    if (!binding.isBound) return "Unbound"
    val keyLabel = PickerKeys.labelFor(binding.keyCode)
    val modLabel = if (binding.modifier != 0) PickerKeys.labelFor(binding.modifier) + "+" else ""
    return modLabel + keyLabel
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun KeyPickerDialog(current: KeyBinding, onDismiss: () -> Unit, onConfirm: (KeyBinding) -> Unit) {
    var selectedModifier by remember { mutableIntStateOf(current.modifier) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Bind a key", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Optional modifier, then tap the key to send.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedModifier == 0,
                        onClick = { selectedModifier = 0 },
                        label = { Text("None") }
                    )
                    PickerKeys.modifiers.forEach { (label, code) ->
                        FilterChip(
                            selected = selectedModifier == code,
                            onClick = { selectedModifier = code },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Key", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                @Composable
                fun keyGrid(title: String, keys: List<Pair<String, Int>>) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        keys.forEach { (label, code) ->
                            AssistChip(
                                onClick = { onConfirm(KeyBinding(code, selectedModifier)) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                keyGrid("Letters", PickerKeys.letters)
                keyGrid("Digits", PickerKeys.digits)
                keyGrid("Arrows", PickerKeys.arrows)
                keyGrid("Whitespace", PickerKeys.whitespace)
                keyGrid("Function", PickerKeys.function)
                keyGrid("Modifier as key", PickerKeys.modifiers)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onConfirm(KeyBinding()) }) { Text("Clear") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun NameProfileDialog(
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile name") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
