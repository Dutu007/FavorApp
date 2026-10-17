package com.dutu007.favorapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

internal fun parseTogetherSince(value: String?): LocalDate? =
    value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.takeIf { it.year in 1..9999 && it.toString() == value }

internal fun togetherDays(since: String?, today: LocalDate): Long? =
    parseTogetherSince(since)?.takeUnless { it.isAfter(today) }
        ?.let { ChronoUnit.DAYS.between(it, today) + 1 }

@Composable
internal fun rememberLocalDate(): State<LocalDate> {
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState(initialValue = LocalDate.now(), lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = ZonedDateTime.now()
                value = now.toLocalDate()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                // Recheck the device clock and time zone while visible, and at midnight.
                delay(Duration.between(now, midnight).toMillis().coerceIn(1L, 60_000L))
            }
        }
    }
}
