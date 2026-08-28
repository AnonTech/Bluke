package dev.arnv.bluke.mapping

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.arnv.bluke.ui.KeyboardLayouts
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Physical control slots on the gamepad skin. Values 0-13 line up 1:1 with the mappingId /
 * HID button-bit numbering already used by the native Xbox/PS5 skins in GamepadView, so a
 * mapped profile can reuse the exact same widgets and press/release plumbing. Values 100+ are
 * synthetic - they don't exist as HID buttons, they're digitalized directions of the analog
 * sticks and D-pad for profiles that need to send a keyboard key instead of a gamepad report.
 */
object ControlSlot {
    const val FACE_BOTTOM = 0
    const val FACE_RIGHT = 1
    const val FACE_LEFT = 2
    const val FACE_TOP = 3
    const val LEFT_BUMPER = 4
    const val RIGHT_BUMPER = 5
    const val LEFT_TRIGGER = 6
    const val RIGHT_TRIGGER = 7
    const val SELECT = 8
    const val START = 9
    const val LEFT_STICK_CLICK = 10
    const val RIGHT_STICK_CLICK = 11
    const val GUIDE = 12
    const val SHARE = 13

    const val DPAD_UP = 100
    const val DPAD_DOWN = 101
    const val DPAD_LEFT = 102
    const val DPAD_RIGHT = 103
}

/** A single control's binding: the HID key it sends, plus an optional single modifier. */
data class KeyBinding(val keyCode: Int = 0, val modifier: Int = 0) {
    val isBound: Boolean get() = keyCode != 0
}

/**
 * A named set of D-pad/button -> keyboard-key bindings, rendered on top of the existing
 * Xbox/PS5 skin geometry (no bespoke art - only labels, colors and which slots are shown
 * change per profile).
 */
data class ControllerProfile(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    /** Which slots this profile actually uses; everything else is hidden rather than shown unbound. */
    val activeSlots: Set<Int>,
    /** Per-slot display label shown on the button (e.g. "B", "1", "Coin"). */
    val labels: Map<Int, String>,
    /** Per-slot keyboard binding. */
    val bindings: Map<Int, KeyBinding>
) {
    fun bindingFor(slot: Int): KeyBinding = bindings[slot] ?: KeyBinding()
    fun labelFor(slot: Int, fallback: String): String = labels[slot] ?: fallback
}

private fun key(code: Int, modifier: Int = 0) = KeyBinding(code, modifier)

/**
 * Built-in profile catalog. Bindings are a documented starting point, not a guarantee of
 * matching any one emulator build's factory config - every one is fully re-editable by
 * duplicating it into a custom profile.
 */
object ControllerPresets {
    private val K = KeyboardLayouts

    // Verified against snes9x/controls.h's own documented default joypad-1 mapping. Snes9x and
    // "generic SNES" used to be two separate presets, but the generic one was meant to share this
    // same binding family and never actually diverged in practice - merged into one entry.
    val SNES9X = ControllerProfile(
        id = "preset_snes9x",
        name = "Snes9x / SNES",
        isBuiltIn = true,
        activeSlots = setOf(
            ControlSlot.FACE_BOTTOM, ControlSlot.FACE_RIGHT, ControlSlot.FACE_LEFT, ControlSlot.FACE_TOP,
            ControlSlot.LEFT_BUMPER, ControlSlot.RIGHT_BUMPER, ControlSlot.SELECT, ControlSlot.START,
            ControlSlot.DPAD_UP, ControlSlot.DPAD_DOWN, ControlSlot.DPAD_LEFT, ControlSlot.DPAD_RIGHT
        ),
        labels = mapOf(
            ControlSlot.FACE_TOP to "X", ControlSlot.FACE_RIGHT to "A",
            ControlSlot.FACE_BOTTOM to "B", ControlSlot.FACE_LEFT to "Y",
            ControlSlot.LEFT_BUMPER to "L", ControlSlot.RIGHT_BUMPER to "R",
            ControlSlot.SELECT to "SELECT", ControlSlot.START to "START"
        ),
        bindings = mapOf(
            ControlSlot.FACE_TOP to key(K.KEY_D),
            ControlSlot.FACE_RIGHT to key(K.KEY_V),
            ControlSlot.FACE_BOTTOM to key(K.KEY_C),
            ControlSlot.FACE_LEFT to key(K.KEY_X),
            ControlSlot.LEFT_BUMPER to key(K.KEY_A),
            ControlSlot.RIGHT_BUMPER to key(K.KEY_S),
            ControlSlot.SELECT to key(K.KEY_ENTER),
            ControlSlot.START to key(K.KEY_SPACE),
            ControlSlot.DPAD_UP to key(K.KEY_UP),
            ControlSlot.DPAD_DOWN to key(K.KEY_DOWN),
            ControlSlot.DPAD_LEFT to key(K.KEY_LEFT),
            ControlSlot.DPAD_RIGHT to key(K.KEY_RIGHT)
        )
    )

    // Verified against FCEUX's actual default (confirmed independently via its own docs and its
    // man page: arrows for the D-pad, Z=A, X=B, Return=Start, Right Shift=Select). The previous
    // version of this preset had A and B swapped (X=A, Z=B) - fixed here.
    val NES = ControllerProfile(
        id = "preset_nes",
        name = "NES",
        isBuiltIn = true,
        activeSlots = setOf(
            ControlSlot.FACE_RIGHT, ControlSlot.FACE_BOTTOM, ControlSlot.SELECT, ControlSlot.START,
            ControlSlot.DPAD_UP, ControlSlot.DPAD_DOWN, ControlSlot.DPAD_LEFT, ControlSlot.DPAD_RIGHT
        ),
        labels = mapOf(
            ControlSlot.FACE_RIGHT to "A", ControlSlot.FACE_BOTTOM to "B",
            ControlSlot.SELECT to "SELECT", ControlSlot.START to "START"
        ),
        bindings = mapOf(
            ControlSlot.FACE_RIGHT to key(K.KEY_Z),
            ControlSlot.FACE_BOTTOM to key(K.KEY_X),
            ControlSlot.SELECT to key(K.MOD_RSHIFT),
            ControlSlot.START to key(K.KEY_ENTER),
            ControlSlot.DPAD_UP to key(K.KEY_UP),
            ControlSlot.DPAD_DOWN to key(K.KEY_DOWN),
            ControlSlot.DPAD_LEFT to key(K.KEY_LEFT),
            ControlSlot.DPAD_RIGHT to key(K.KEY_RIGHT)
        )
    )

    // Verified against BlastEm's own default.cfg (a/s/d for A/B/C, q/w/e for the 6-button X/Y/Z
    // extension, arrows for the D-pad, Enter for Start) - a well-documented, actively maintained
    // standalone Genesis/Mega Drive emulator.
    val GENESIS = ControllerProfile(
        id = "preset_genesis",
        name = "Genesis / Mega Drive",
        isBuiltIn = true,
        activeSlots = setOf(
            ControlSlot.FACE_LEFT, ControlSlot.FACE_BOTTOM, ControlSlot.FACE_RIGHT,
            ControlSlot.LEFT_BUMPER, ControlSlot.RIGHT_BUMPER, ControlSlot.LEFT_TRIGGER,
            ControlSlot.START,
            ControlSlot.DPAD_UP, ControlSlot.DPAD_DOWN, ControlSlot.DPAD_LEFT, ControlSlot.DPAD_RIGHT
        ),
        labels = mapOf(
            ControlSlot.FACE_LEFT to "A", ControlSlot.FACE_BOTTOM to "B", ControlSlot.FACE_RIGHT to "C",
            ControlSlot.LEFT_BUMPER to "X", ControlSlot.RIGHT_BUMPER to "Y", ControlSlot.LEFT_TRIGGER to "Z",
            ControlSlot.START to "START"
        ),
        bindings = mapOf(
            ControlSlot.FACE_LEFT to key(K.KEY_A),
            ControlSlot.FACE_BOTTOM to key(K.KEY_S),
            ControlSlot.FACE_RIGHT to key(K.KEY_D),
            ControlSlot.LEFT_BUMPER to key(K.KEY_Q),
            ControlSlot.RIGHT_BUMPER to key(K.KEY_W),
            ControlSlot.LEFT_TRIGGER to key(K.KEY_E),
            ControlSlot.START to key(K.KEY_ENTER),
            ControlSlot.DPAD_UP to key(K.KEY_UP),
            ControlSlot.DPAD_DOWN to key(K.KEY_DOWN),
            ControlSlot.DPAD_LEFT to key(K.KEY_LEFT),
            ControlSlot.DPAD_RIGHT to key(K.KEY_RIGHT)
        )
    )

    // Verified against MAME's own documented default keyboard controls: arrows for the stick,
    // Ctrl/Alt/Space/Shift/Z/X for buttons 1-6, "1" to start, "5" to insert a coin. FBNeo has no
    // single documented factory default (it's driven by per-game/per-driver .ini presets) but
    // commonly matches this MAME convention, so it's used here too.
    //
    // GGPO FBA is the odd one out: most people mean Fightcade's GGPO-based FBA client, and the
    // one description of Fightcade's own default keyboard scheme found while checking this
    // (WASD movement + I/O/P for punches) does NOT match MAME's layout - but that source wasn't
    // clear on whether it's Fightcade's actual shipped default or just a common recommendation,
    // so this preset keeps the MAME-style layout rather than switching on an unconfirmed source.
    // Worth another look if you can confirm Fightcade's real out-of-the-box keyboard defaults.
    private val MAME_STYLE_BINDINGS = mapOf(
        ControlSlot.FACE_BOTTOM to key(K.MOD_LCTRL),
        ControlSlot.FACE_RIGHT to key(K.MOD_LALT),
        ControlSlot.FACE_TOP to key(K.KEY_SPACE),
        ControlSlot.FACE_LEFT to key(K.MOD_LSHIFT),
        ControlSlot.LEFT_BUMPER to key(K.KEY_Z),
        ControlSlot.RIGHT_BUMPER to key(K.KEY_X),
        ControlSlot.START to key(K.KEY_1),
        ControlSlot.SELECT to key(K.KEY_5),
        ControlSlot.DPAD_UP to key(K.KEY_UP),
        ControlSlot.DPAD_DOWN to key(K.KEY_DOWN),
        ControlSlot.DPAD_LEFT to key(K.KEY_LEFT),
        ControlSlot.DPAD_RIGHT to key(K.KEY_RIGHT)
    )
    private val MAME_STYLE_LABELS = mapOf(
        ControlSlot.FACE_BOTTOM to "1", ControlSlot.FACE_RIGHT to "2",
        ControlSlot.FACE_TOP to "3", ControlSlot.FACE_LEFT to "4",
        ControlSlot.LEFT_BUMPER to "5", ControlSlot.RIGHT_BUMPER to "6",
        ControlSlot.START to "START", ControlSlot.SELECT to "COIN"
    )
    private val MAME_STYLE_SLOTS = MAME_STYLE_BINDINGS.keys

    val ARCADE = ControllerProfile("preset_arcade", "Arcade / Fight-stick", true, MAME_STYLE_SLOTS, MAME_STYLE_LABELS, MAME_STYLE_BINDINGS)
    val FBNEO = ControllerProfile("preset_fbneo", "FBNeo", true, MAME_STYLE_SLOTS, MAME_STYLE_LABELS, MAME_STYLE_BINDINGS)
    val GGPO_FBA = ControllerProfile("preset_ggpofba", "GGPO FBA", true, MAME_STYLE_SLOTS, MAME_STYLE_LABELS, MAME_STYLE_BINDINGS)

    // A reasonable common Dreamcast/Flycast default - not verified against one specific Flycast
    // build's factory keyboard config (those vary by platform/version), fully editable.
    val FLYCAST = ControllerProfile(
        id = "preset_flycast",
        name = "Flycast",
        isBuiltIn = true,
        activeSlots = setOf(
            ControlSlot.FACE_BOTTOM, ControlSlot.FACE_RIGHT, ControlSlot.FACE_LEFT, ControlSlot.FACE_TOP,
            ControlSlot.LEFT_TRIGGER, ControlSlot.RIGHT_TRIGGER, ControlSlot.START,
            ControlSlot.DPAD_UP, ControlSlot.DPAD_DOWN, ControlSlot.DPAD_LEFT, ControlSlot.DPAD_RIGHT
        ),
        labels = mapOf(
            ControlSlot.FACE_BOTTOM to "A", ControlSlot.FACE_RIGHT to "B",
            ControlSlot.FACE_TOP to "Y", ControlSlot.FACE_LEFT to "X",
            ControlSlot.LEFT_TRIGGER to "L", ControlSlot.RIGHT_TRIGGER to "R",
            ControlSlot.START to "START"
        ),
        bindings = mapOf(
            ControlSlot.FACE_BOTTOM to key(K.KEY_X),
            ControlSlot.FACE_RIGHT to key(K.KEY_C),
            ControlSlot.FACE_TOP to key(K.KEY_D),
            ControlSlot.FACE_LEFT to key(K.KEY_S),
            ControlSlot.LEFT_TRIGGER to key(K.KEY_Q),
            ControlSlot.RIGHT_TRIGGER to key(K.KEY_W),
            ControlSlot.START to key(K.KEY_ENTER),
            ControlSlot.DPAD_UP to key(K.KEY_UP),
            ControlSlot.DPAD_DOWN to key(K.KEY_DOWN),
            ControlSlot.DPAD_LEFT to key(K.KEY_LEFT),
            ControlSlot.DPAD_RIGHT to key(K.KEY_RIGHT)
        )
    )

    val ALL: List<ControllerProfile> = listOf(SNES9X, NES, GENESIS, ARCADE, FBNEO, GGPO_FBA, FLYCAST)

    fun byId(id: String): ControllerProfile? = ALL.firstOrNull { it.id == id }
}

/**
 * Local persistence for user-created/duplicated profiles. Built-in presets are never written
 * here - they live only as the compiled-in [ControllerPresets] constants.
 */
object ControllerProfileStore {
    private const val PREFS_NAME = "controller_profiles"
    private const val KEY_PROFILE_IDS = "profile_ids"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    private const val PROFILE_KEY_PREFIX = "profile_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun newProfileId(): String = "custom_" + UUID.randomUUID().toString()

    fun listCustomProfiles(context: Context): List<ControllerProfile> {
        val p = prefs(context)
        val ids = p.getStringSet(KEY_PROFILE_IDS, emptySet()).orEmpty()
        return ids.mapNotNull { id -> loadProfile(context, id) }.sortedBy { it.name.lowercase() }
    }

    fun loadProfile(context: Context, id: String): ControllerProfile? {
        val json = prefs(context).getString(PROFILE_KEY_PREFIX + id, null) ?: return null
        return try {
            deserialize(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    fun saveProfile(context: Context, profile: ControllerProfile) {
        require(!profile.isBuiltIn) { "Built-in presets are compiled-in and cannot be saved." }
        val p = prefs(context)
        val ids = (p.getStringSet(KEY_PROFILE_IDS, emptySet()).orEmpty() + profile.id).toSet()
        p.edit {
            putStringSet(KEY_PROFILE_IDS, ids)
            putString(PROFILE_KEY_PREFIX + profile.id, serialize(profile).toString())
        }
    }

    fun deleteProfile(context: Context, id: String) {
        val p = prefs(context)
        val ids = p.getStringSet(KEY_PROFILE_IDS, emptySet()).orEmpty() - id
        p.edit {
            putStringSet(KEY_PROFILE_IDS, ids)
            remove(PROFILE_KEY_PREFIX + id)
        }
        if (getActiveProfileId(context) == id) setActiveProfileId(context, null)
    }

    /** Creates and persists an editable copy of [source] (built-in or custom) under a new name. */
    fun duplicateProfile(context: Context, source: ControllerProfile, newName: String): ControllerProfile {
        val copy = source.copy(id = newProfileId(), name = newName, isBuiltIn = false)
        saveProfile(context, copy)
        return copy
    }

    fun getActiveProfileId(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE_PROFILE_ID, null)

    fun setActiveProfileId(context: Context, id: String?) {
        prefs(context).edit {
            if (id == null) remove(KEY_ACTIVE_PROFILE_ID) else putString(KEY_ACTIVE_PROFILE_ID, id)
        }
    }

    /** Resolves the currently active profile across both built-ins and saved customs, if any. */
    fun getActiveProfile(context: Context): ControllerProfile? {
        val id = getActiveProfileId(context) ?: return null
        return ControllerPresets.byId(id) ?: loadProfile(context, id)
    }

    private fun serialize(profile: ControllerProfile): JSONObject = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.name)
        put("activeSlots", JSONArray(profile.activeSlots.toList()))
        put("labels", JSONObject(profile.labels.mapKeys { it.key.toString() }))
        put("bindings", JSONObject(profile.bindings.mapKeys { it.key.toString() }.mapValues { (_, b) ->
            JSONObject().apply { put("keyCode", b.keyCode); put("modifier", b.modifier) }
        }))
    }

    private fun deserialize(json: JSONObject): ControllerProfile {
        val slotsArray = json.getJSONArray("activeSlots")
        val activeSlots = (0 until slotsArray.length()).map { slotsArray.getInt(it) }.toSet()

        val labelsJson = json.getJSONObject("labels")
        val labels = labelsJson.keys().asSequence().associate { k -> k.toInt() to labelsJson.getString(k) }

        val bindingsJson = json.getJSONObject("bindings")
        val bindings = bindingsJson.keys().asSequence().associate { k ->
            val b = bindingsJson.getJSONObject(k)
            k.toInt() to KeyBinding(b.getInt("keyCode"), b.optInt("modifier", 0))
        }

        return ControllerProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            isBuiltIn = false,
            activeSlots = activeSlots,
            labels = labels,
            bindings = bindings
        )
    }
}
