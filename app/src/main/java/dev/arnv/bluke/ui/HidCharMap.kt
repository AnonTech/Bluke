package dev.arnv.bluke.ui

/**
 * Translates printable characters into the HID usage code (plus shift state) that a US-layout
 * physical keyboard would emit for them.
 *
 * The system IME (Gboard, Samsung Keyboard, ...) hands us finished text rather than key presses,
 * so anything typed there has to be turned back into scancodes before it can go out over HID.
 * The host applies its own keyboard layout to whatever we send, so this map assumes the host is
 * on US QWERTY - the same assumption the on-screen layouts in [KeyboardLayouts] already make.
 */
object HidCharMap {

    /** A single character expressed as a keystroke: a HID usage code, optionally shifted. */
    data class Stroke(val keyCode: Int, val shift: Boolean)

    private val UNSHIFTED: Map<Char, Int> = buildMap {
        for (c in 'a'..'z') put(c, KeyboardLayouts.KEY_A + (c - 'a'))
        put('1', KeyboardLayouts.KEY_1)
        put('2', KeyboardLayouts.KEY_2)
        put('3', KeyboardLayouts.KEY_3)
        put('4', KeyboardLayouts.KEY_4)
        put('5', KeyboardLayouts.KEY_5)
        put('6', KeyboardLayouts.KEY_6)
        put('7', KeyboardLayouts.KEY_7)
        put('8', KeyboardLayouts.KEY_8)
        put('9', KeyboardLayouts.KEY_9)
        put('0', KeyboardLayouts.KEY_0)
        put(' ', KeyboardLayouts.KEY_SPACE)
        put('\n', KeyboardLayouts.KEY_ENTER)
        put('\t', KeyboardLayouts.KEY_TAB)
        put('-', KeyboardLayouts.KEY_MINUS)
        put('=', KeyboardLayouts.KEY_EQUAL)
        put('[', KeyboardLayouts.KEY_LBRACKET)
        put(']', KeyboardLayouts.KEY_RBRACKET)
        put('\\', KeyboardLayouts.KEY_BACKSLASH)
        put(';', KeyboardLayouts.KEY_SEMICOLON)
        put('\'', KeyboardLayouts.KEY_APOSTROPHE)
        put('`', KeyboardLayouts.KEY_GRAVE)
        put(',', KeyboardLayouts.KEY_COMMA)
        put('.', KeyboardLayouts.KEY_PERIOD)
        put('/', KeyboardLayouts.KEY_SLASH)
    }

    private val SHIFTED: Map<Char, Int> = buildMap {
        for (c in 'A'..'Z') put(c, KeyboardLayouts.KEY_A + (c - 'A'))
        put('!', KeyboardLayouts.KEY_1)
        put('@', KeyboardLayouts.KEY_2)
        put('#', KeyboardLayouts.KEY_3)
        put('$', KeyboardLayouts.KEY_4)
        put('%', KeyboardLayouts.KEY_5)
        put('^', KeyboardLayouts.KEY_6)
        put('&', KeyboardLayouts.KEY_7)
        put('*', KeyboardLayouts.KEY_8)
        put('(', KeyboardLayouts.KEY_9)
        put(')', KeyboardLayouts.KEY_0)
        put('_', KeyboardLayouts.KEY_MINUS)
        put('+', KeyboardLayouts.KEY_EQUAL)
        put('{', KeyboardLayouts.KEY_LBRACKET)
        put('}', KeyboardLayouts.KEY_RBRACKET)
        put('|', KeyboardLayouts.KEY_BACKSLASH)
        put(':', KeyboardLayouts.KEY_SEMICOLON)
        put('"', KeyboardLayouts.KEY_APOSTROPHE)
        put('~', KeyboardLayouts.KEY_GRAVE)
        put('<', KeyboardLayouts.KEY_COMMA)
        put('>', KeyboardLayouts.KEY_PERIOD)
        put('?', KeyboardLayouts.KEY_SLASH)
    }

    /**
     * Characters an IME emits freely that have no US-layout key but do have an obvious ASCII
     * stand-in. Smart quotes in particular arrive constantly from autocorrect, and silently
     * dropping them would mangle ordinary sentences.
     */
    private val SUBSTITUTES: Map<Char, Char> = mapOf(
        '‘' to '\'', // left single quote
        '’' to '\'', // right single quote / apostrophe
        '“' to '"',  // left double quote
        '”' to '"',  // right double quote
        '–' to '-',  // en dash
        '—' to '-',  // em dash
        '…' to '.',  // ellipsis (expanded by the caller into three periods)
        ' ' to ' ',  // non-breaking space
        '\r' to '\n'
    )

    /** Substitute character for [c], or null when there is no sensible ASCII equivalent. */
    fun substitute(c: Char): Char? = SUBSTITUTES[c]

    /**
     * The keystroke that produces [c], or null when the character cannot be typed on a US
     * layout (emoji, CJK, accented letters - the caller decides how to report that).
     */
    fun strokeFor(c: Char): Stroke? {
        UNSHIFTED[c]?.let { return Stroke(it, shift = false) }
        SHIFTED[c]?.let { return Stroke(it, shift = true) }
        return null
    }

    /**
     * Expands [c] into the keystrokes needed to type it, applying substitutions.
     * Returns an empty list for characters that cannot be represented.
     */
    fun strokesFor(c: Char): List<Stroke> {
        strokeFor(c)?.let { return listOf(it) }
        val sub = substitute(c) ?: return emptyList()
        // The ellipsis is the one substitution that is not one-for-one.
        if (c == '…') {
            val dot = strokeFor('.') ?: return emptyList()
            return listOf(dot, dot, dot)
        }
        return strokeFor(sub)?.let { listOf(it) } ?: emptyList()
    }
}
