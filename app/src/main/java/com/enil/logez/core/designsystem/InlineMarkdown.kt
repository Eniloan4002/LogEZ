package com.enil.logez.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * The inline Markdown subset exercise instructions are stored in: `**bold**` (or `__bold__`),
 * `*italic*` (or `_italic_`), `***both***`, and backslash escapes such as `\*`.
 *
 * Replaced the compose-rich-editor library on 2026-09-25 (Play-readiness audit). Every release of
 * that library, including the stable ones, pulled the whole app onto an alpha Material3 and a
 * Kotlin 2.4 standard library, ahead of this project's pinned Kotlin 2.3.0, for what is only bold
 * and italic in a How-to step. The storage format is unchanged, so instructions written with the
 * old editor render exactly as before.
 *
 * A marker only opens a style when a matching closer appears later in the same text; an unmatched
 * `*` (for example "3 * 10 reps") stays literal rather than italicising the rest of the step.
 */
object InlineMarkdown {
    private enum class Marker(val token: String, val bold: Boolean) {
        BOLD_STARS("**", true), BOLD_UNDERSCORES("__", true), ITALIC_STAR("*", false), ITALIC_UNDERSCORE("_", false)
    }

    /** One scanned piece of the source: literal text in a style, or a marker (hidden when rendered). */
    private data class Piece(val start: Int, val end: Int, val text: String, val bold: Boolean, val italic: Boolean, val isMarker: Boolean)

    private fun scan(source: String): List<Piece> {
        val pieces = mutableListOf<Piece>()
        var bold = false
        var italic = false
        var openBold: Marker? = null
        var openItalic: Marker? = null
        val literal = StringBuilder()
        var literalStart = 0
        var i = 0

        fun flush(at: Int) {
            if (literal.isNotEmpty()) pieces += Piece(literalStart, at, literal.toString(), bold, italic, isMarker = false)
            literal.clear()
            literalStart = at
        }

        while (i < source.length) {
            val c = source[i]
            if (c == '\\' && i + 1 < source.length && source[i + 1] in ESCAPABLE) {
                // "\*" is a literal asterisk: the backslash is treated like a marker (hidden when
                // rendered, kept in the stored text) and the next character is plain text.
                flush(i)
                pieces += Piece(i, i + 1, "\\", bold, italic, isMarker = true)
                literalStart = i + 1
                literal.append(source[i + 1])
                i += 2
                continue
            }
            val marker = Marker.entries.firstOrNull { source.startsWith(it.token, i) }
            if (marker != null) {
                val closes = if (marker.bold) openBold == marker else openItalic == marker
                val opens = !closes && (if (marker.bold) openBold == null else openItalic == null) &&
                    hasCloser(source, marker.token, i + marker.token.length)
                if (closes || opens) {
                    flush(i)
                    pieces += Piece(i, i + marker.token.length, marker.token, bold, italic, isMarker = true)
                    if (marker.bold) {
                        bold = opens
                        openBold = if (opens) marker else null
                    } else {
                        italic = opens
                        openItalic = if (opens) marker else null
                    }
                    i += marker.token.length
                    literalStart = i
                    continue
                }
            }
            if (literal.isEmpty()) literalStart = i
            literal.append(c)
            i++
        }
        flush(source.length)
        return pieces
    }

    /** A closer must exist later, and not immediately (an empty "****" is two literal pairs). */
    private fun hasCloser(source: String, token: String, from: Int): Boolean {
        val at = source.indexOf(token, from)
        return at > from
    }

    private fun style(bold: Boolean, italic: Boolean) = SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
    )

    /** For display (the How-to tab): markers removed, styles applied. */
    fun toAnnotatedString(source: String): AnnotatedString = buildAnnotatedString {
        scan(source).filterNot { it.isMarker }.forEach { piece ->
            if (piece.bold || piece.italic) {
                pushStyle(style(piece.bold, piece.italic))
                append(piece.text)
                pop()
            } else {
                append(piece.text)
            }
        }
    }

    /** Whether the character just before [offset] is bold / italic, for the editor's toggle buttons. */
    fun stylesAt(source: String, offset: Int): Pair<Boolean, Boolean> {
        val target = (offset - 1).coerceAtLeast(0)
        val piece = scan(source).lastOrNull { !it.isMarker && it.start <= target } ?: return false to false
        return piece.bold to piece.italic
    }

    /**
     * For the editor: the raw text, markers left in place (so the cursor maps one to one and the
     * stored text is exactly what is typed), styled content, and markers dimmed to [markerColor].
     */
    fun editorTransformation(markerColor: Color): VisualTransformation = VisualTransformation { text ->
        val raw = text.text
        val styled = buildAnnotatedString {
            append(raw)
            scan(raw).forEach { piece ->
                if (piece.isMarker) {
                    addStyle(SpanStyle(color = markerColor), piece.start, piece.end)
                } else if (piece.bold || piece.italic) {
                    addStyle(style(piece.bold, piece.italic), piece.start, piece.end)
                }
            }
        }
        TransformedText(styled, OffsetMapping.Identity)
    }

    /**
     * The Bold / Italic button: wraps the selection in [token], or unwraps it when it is already
     * wrapped; with no selection, inserts an empty pair and puts the cursor between the markers.
     */
    fun toggle(value: TextFieldValue, token: String): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val wrapped = start >= token.length && text.startsWith(token, start - token.length) && text.startsWith(token, end)
        return when {
            wrapped -> TextFieldValue(
                text = text.removeRange(end, end + token.length).removeRange(start - token.length, start),
                selection = TextRange(start - token.length, end - token.length),
            )
            start == end -> TextFieldValue(
                text = text.substring(0, start) + token + token + text.substring(start),
                selection = TextRange(start + token.length),
            )
            else -> TextFieldValue(
                text = text.substring(0, start) + token + text.substring(start, end) + token + text.substring(end),
                selection = TextRange(start + token.length, end + token.length),
            )
        }
    }

    private val ESCAPABLE = setOf('*', '_', '\\')
}
