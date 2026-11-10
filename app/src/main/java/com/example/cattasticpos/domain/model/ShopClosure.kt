package com.example.cattasticpos.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The owner's "Closed today" switch (Connect FR-111). It is the `closure` row of
 * `public.site_content`; while [closed] is on, the website and the Messenger assistant say the
 * shop is closed and take no online orders.
 *
 * Update 2026-11-11 (Connect FR-112): the shop is open by default. The switch only counts on the
 * day it was turned on ([changedAt], in Philippine time), and outside the saved [hours] the shop
 * shows as closed by itself.
 */
data class ShopClosure(
    val closed: Boolean,
    /** Optional reason shown to customers, e.g. "Back tomorrow at 1 PM." */
    val note: String = "",
    /** When the switch was last saved (the row's `updated_at`). */
    val changedAt: Instant? = null,
    /** Opening hours by day; a `null` day is closed. Empty when the hours couldn't be read. */
    val hours: Map<DayOfWeek, OpeningHours?> = emptyMap()
) {
    companion object {
        const val MAX_NOTE = 300
    }
}

/** One day's hours; the shop closes before midnight. */
data class OpeningHours(val opens: LocalTime, val closes: LocalTime)

enum class ShopOpenState { OPEN, CLOSED_TODAY, OUTSIDE_HOURS }

/** The Philippines has one time zone and no daylight saving. */
val SHOP_ZONE: ZoneId = ZoneId.of("Asia/Manila")

/** Whether Closed today still applies: it lasts only for the day it was turned on. */
fun ShopClosure.closedToday(now: Instant = Instant.now()): Boolean {
    if (!closed) return false
    val changed = changedAt ?: return true
    return changed.atZone(SHOP_ZONE).toLocalDate() == now.atZone(SHOP_ZONE).toLocalDate()
}

/** Open, closed for today by the owner, or outside the opening hours, same as the website. */
fun ShopClosure.openState(now: Instant = Instant.now()): ShopOpenState {
    if (closedToday(now)) return ShopOpenState.CLOSED_TODAY
    // Without the hours, don't claim the shop is closed.
    if (hours.isEmpty()) return ShopOpenState.OPEN
    val local = now.atZone(SHOP_ZONE)
    val today = hours[local.dayOfWeek] ?: return ShopOpenState.OUTSIDE_HOURS
    val time = local.toLocalTime()
    return if (!time.isBefore(today.opens) && time.isBefore(today.closes)) {
        ShopOpenState.OPEN
    } else {
        ShopOpenState.OUTSIDE_HOURS
    }
}
