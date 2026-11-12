package com.example.clocarmus.library

/** Prefer a tagged year, accepting full dates in either field, then fall back to date. */
internal fun releaseYear(year: String?, date: String?): String? =
    sequenceOf(year, date).mapNotNull { value ->
        value?.trim()?.let { Regex("(?<![0-9])[0-9]{4}(?![0-9])").find(it)?.value }
    }.firstOrNull()
