package dev.arnv.bluke

import dev.arnv.bluke.ui.HidCharMap
import dev.arnv.bluke.ui.KeyboardLayouts
import dev.arnv.bluke.ui.computeImeDiff
import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the translation from IME text back into HID keystrokes.
 *
 * The diffing is the fragile part: the IME rewrites text it has already handed us whenever
 * autocorrect or a word suggestion fires, so the reconciliation has to stay exact or the host
 * ends up with duplicated or half-deleted words.
 */
class ImeInputTest {

  private fun strokeCodes(sent: String, target: String) =
    computeImeDiff(sent, target).strokes.map { it.keyCode }

  @Test
  fun plainAppend_sendsOnlyTheNewCharacters() {
    val diff = computeImeDiff("hel", "hello")
    assertEquals(0, diff.backspaces)
    assertEquals(listOf(KeyboardLayouts.KEY_L, KeyboardLayouts.KEY_O), diff.strokes.map { it.keyCode })
  }

  @Test
  fun emptyToText_sendsEverything() {
    val diff = computeImeDiff("", "hi")
    assertEquals(0, diff.backspaces)
    assertEquals(listOf(KeyboardLayouts.KEY_H, KeyboardLayouts.KEY_I), diff.strokes.map { it.keyCode })
  }

  @Test
  fun noChange_producesNothing() {
    val diff = computeImeDiff("hello", "hello")
    assertEquals(0, diff.backspaces)
    assertTrue(diff.strokes.isEmpty())
    assertEquals("", diff.droppedChars)
  }

  @Test
  fun deletion_sendsBackspacesOnly() {
    val diff = computeImeDiff("hello", "hel")
    assertEquals(2, diff.backspaces)
    assertTrue(diff.strokes.isEmpty())
  }

  @Test
  fun autocorrectRewrite_backspacesTheDivergingTailAndRetypesIt() {
    // Gboard turning "teh" into "the": common prefix is "t", so two backspaces then "he".
    val diff = computeImeDiff("teh", "the")
    assertEquals(2, diff.backspaces)
    assertEquals(listOf(KeyboardLayouts.KEY_H, KeyboardLayouts.KEY_E), diff.strokes.map { it.keyCode })
  }

  @Test
  fun wordSuggestion_completingAWord_appendsWithoutDeleting() {
    val diff = computeImeDiff("keyb", "keyboard")
    assertEquals(0, diff.backspaces)
    assertEquals("oard".length, diff.strokes.size)
  }

  @Test
  fun uppercase_isMarkedAsShifted() {
    val diff = computeImeDiff("", "Hi")
    assertEquals(2, diff.strokes.size)
    assertTrue(diff.strokes[0].shift)
    assertEquals(KeyboardLayouts.KEY_H, diff.strokes[0].keyCode)
    assertFalse(diff.strokes[1].shift)
    assertEquals(KeyboardLayouts.KEY_I, diff.strokes[1].keyCode)
  }

  @Test
  fun shiftedSymbols_mapToTheirBaseKey() {
    assertEquals(HidCharMap.Stroke(KeyboardLayouts.KEY_1, shift = true), HidCharMap.strokeFor('!'))
    assertEquals(HidCharMap.Stroke(KeyboardLayouts.KEY_SLASH, shift = true), HidCharMap.strokeFor('?'))
    assertEquals(HidCharMap.Stroke(KeyboardLayouts.KEY_SEMICOLON, shift = true), HidCharMap.strokeFor(':'))
    assertEquals(HidCharMap.Stroke(KeyboardLayouts.KEY_2, shift = true), HidCharMap.strokeFor('@'))
  }

  @Test
  fun unshiftedPunctuation_needsNoShift() {
    assertEquals(HidCharMap.Stroke(KeyboardLayouts.KEY_COMMA, shift = false), HidCharMap.strokeFor(','))
    assertEquals(HidCharMap.Stroke(KeyboardLayouts.KEY_SPACE, shift = false), HidCharMap.strokeFor(' '))
  }

  @Test
  fun smartQuotes_fallBackToAsciiEquivalents() {
    // Autocorrect emits these constantly; dropping them would mangle ordinary sentences.
    assertEquals(listOf(KeyboardLayouts.KEY_APOSTROPHE), strokeCodes("", "’"))
    assertEquals(listOf(KeyboardLayouts.KEY_APOSTROPHE), strokeCodes("", "“"))
    assertEquals(listOf(KeyboardLayouts.KEY_MINUS), strokeCodes("", "—"))
    assertEquals("", computeImeDiff("", "it’s").droppedChars)
  }

  @Test
  fun ellipsis_expandsToThreePeriods() {
    assertEquals(
      listOf(KeyboardLayouts.KEY_PERIOD, KeyboardLayouts.KEY_PERIOD, KeyboardLayouts.KEY_PERIOD),
      strokeCodes("", "…")
    )
  }

  @Test
  fun untypableCharacters_areReportedNotSilentlySkipped() {
    val diff = computeImeDiff("", "hi 😀")
    assertEquals(3, diff.strokes.size) // "hi " survives
    assertTrue("emoji should be reported as dropped", diff.droppedChars.isNotEmpty())
  }

  @Test
  fun accentedLetters_haveNoUsLayoutKey() {
    assertTrue(HidCharMap.strokesFor('é').isEmpty())
    assertTrue(HidCharMap.strokesFor('ß').isEmpty())
  }

  @Test
  fun newline_mapsToEnter() {
    assertEquals(HidCharMap.Stroke(KeyboardLayouts.KEY_ENTER, shift = false), HidCharMap.strokeFor('\n'))
  }
}
