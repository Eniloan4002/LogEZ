package com.enil.logez.feature.exercises

import com.mohamedrejeb.richeditor.model.RichTextState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M20g: the exercise editor stores instructions as plain "one step per line" text and only
 * converts to Markdown at the UI boundary (`RichTextState.toMarkdown()`/`setMarkdown()`). This
 * pins the one fact that decision depends on: a plain multi-line string with no Markdown syntax
 * must round-trip through the editor's state object byte-identical, interior `\n` included.
 */
class RichTextStateRoundTripTest {
    @Test
    fun `plain multi-line text round-trips through setMarkdown and toMarkdown unchanged`() {
        val state = RichTextState()
        state.setMarkdown("Step 1: grip the bar.\nStep 2: brace and lift.")

        assertEquals("Step 1: grip the bar.\nStep 2: brace and lift.", state.toMarkdown())
    }

    @Test
    fun `a step containing bold markdown round-trips its markers unchanged`() {
        val state = RichTextState()
        state.setMarkdown("Grip the **bar** wider than shoulders.")

        assertEquals("Grip the **bar** wider than shoulders.", state.toMarkdown())
    }

    @Test
    fun `a blank string round-trips to a blank string`() {
        val state = RichTextState()
        state.setMarkdown("")

        assertEquals("", state.toMarkdown())
    }
}
