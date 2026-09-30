package com.enil.logez.core.domain.model

/**
 * What one set shows under a SET column: a running number for a normal set, a letter otherwise.
 *
 * [number] is non-null exactly when [setType] is [SetType.NORMAL].
 */
data class SetDisplayLabel(val setType: SetType, val number: Int?) {
    /** "1" for a numbered set, "W" / "F" / "D" otherwise. */
    val text: String get() = number?.toString() ?: SetNumbering.letter(setType)
}

/**
 * P-211 decision 9 (Owner, 2026-09-30): only NORMAL sets are numbered, 1..n counted within one
 * exercise card; warm-up, failure and drop sets keep their letter and do not advance the count.
 * A bench card reads W, 1, 2, F instead of the old positional W, 2, 3, F, so a SET column never
 * seems to skip set 1.
 *
 * Display only. `orderIndex`, the PREVIOUS pairing (by orderIndex) and the CSV `set_index` stay
 * positional; nothing here is ever stored. Circuit rows keep their round-based labels and do not
 * use this.
 */
object SetNumbering {
    /** One label per set, in the order given: [SetType.NORMAL] counts 1..n, the rest are lettered. */
    fun labels(types: List<SetType>): List<SetDisplayLabel> {
        var count = 0
        return types.map { type ->
            if (type == SetType.NORMAL) SetDisplayLabel(type, ++count) else SetDisplayLabel(type, null)
        }
    }

    /** The badge letter the set-type menu already uses ("W — Warm-up Set"); empty for NORMAL. */
    fun letter(type: SetType): String = when (type) {
        SetType.NORMAL -> ""
        SetType.WARMUP -> "W"
        SetType.FAILURE -> "F"
        SetType.DROPSET -> "D"
    }
}
