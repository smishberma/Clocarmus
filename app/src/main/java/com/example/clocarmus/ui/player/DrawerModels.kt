package com.example.clocarmus.ui.player

internal fun drawerPinnedRows(visiblePins: Int): Int = (visiblePins.coerceIn(0, 6) + 2) / 3

internal fun drawerRegularRange(page: Int, pinnedRows: Int, size: Int): IntRange {
    val firstCapacity = 9 - pinnedRows * 3
    val start = if (page == 0) 0 else firstCapacity + (page - 1) * 9
    val count = if (page == 0) firstCapacity else 9
    return start.coerceAtMost(size) until (start + count).coerceAtMost(size)
}

internal fun toggleDrawerPin(pins: List<String>, identity: String): List<String> = when {
    identity in pins -> pins - identity
    pins.size < 6 -> pins + identity
    else -> pins
}

internal fun drawerNameMatches(label: String, query: String): Boolean = label.contains(query, ignoreCase = true)
