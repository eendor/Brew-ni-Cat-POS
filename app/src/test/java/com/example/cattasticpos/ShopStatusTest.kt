package com.example.cattasticpos

import com.example.cattasticpos.data.repository.parseClosure
import com.example.cattasticpos.data.repository.parseShopState
import com.example.cattasticpos.domain.model.OpeningHours
import com.example.cattasticpos.domain.model.ShopClosure
import com.example.cattasticpos.domain.model.ShopOpenState
import com.example.cattasticpos.domain.model.openState
import com.example.cattasticpos.ui.dashboard.shopStateLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** The shop open/closed switch in the top bar (Connect FR-111, FR-112). */
class ShopStatusTest {
    private val weekday = OpeningHours(LocalTime.of(11, 0), LocalTime.of(21, 0))
    private val hours: Map<DayOfWeek, OpeningHours?> = DayOfWeek.values().associateWith {
        when (it) {
            DayOfWeek.SATURDAY -> null
            DayOfWeek.SUNDAY -> OpeningHours(LocalTime.of(13, 0), LocalTime.of(21, 0))
            else -> weekday
        }
    }

    /** A time in Manila (UTC+8) on 9 to 15 November 2026, Monday to Sunday. */
    private fun manila(day: Int, hour: Int, minute: Int = 0): Instant =
        Instant.parse("2026-11-%02dT00:00:00Z".format(day)).plusSeconds(((hour - 8) * 60L + minute) * 60)

    @Test
    fun `TC-P6-233 open by default inside the hours, outside hours otherwise, and Closed today lasts one day`() {
        val open = ShopClosure(closed = false, hours = hours)
        assertEquals(ShopOpenState.OPEN, open.openState(manila(9, 14)))
        assertEquals(ShopOpenState.OUTSIDE_HOURS, open.openState(manila(9, 10, 59)))
        assertEquals(ShopOpenState.OUTSIDE_HOURS, open.openState(manila(9, 21)))
        assertEquals(ShopOpenState.OUTSIDE_HOURS, open.openState(manila(14, 14)))
        assertEquals(ShopOpenState.OPEN, open.openState(manila(15, 13)))

        val closed = open.copy(closed = true, changedAt = manila(9, 23, 30))
        assertEquals(ShopOpenState.CLOSED_TODAY, closed.openState(manila(9, 23, 45)))
        // The next day in Manila the shop opens by itself.
        assertEquals(ShopOpenState.OPEN, closed.openState(manila(10, 12)))
        // Without the hours, it isn't shown as closed.
        assertEquals(ShopOpenState.OPEN, ShopClosure(closed = false).openState(manila(14, 3)))
        assertEquals("Open", shopStateLabel(ShopOpenState.OPEN))
        assertEquals("Closed today", shopStateLabel(ShopOpenState.CLOSED_TODAY))
        assertEquals("Outside hours", shopStateLabel(ShopOpenState.OUTSIDE_HOURS))
    }

    @Test
    fun `TC-P6-234 the switch and the hours are read together from the server`() {
        val day = """{"opens":"11:00","closes":"21:00"}"""
        val body = """[
            {"section":"hours","content":{"days":{"monday":$day,"tuesday":$day,"wednesday":$day,
              "thursday":$day,"friday":$day,"saturday":null,"sunday":{"opens":"13:00","closes":"21:00"}},"note":""}},
            {"section":"closure","content":{"closed":true,"note":"Back at 1 PM"},"updated_at":"2026-11-10T04:00:00.123456+00:00"}
        ]"""
        val state = parseShopState(body)!!
        assertEquals(true, state.closed)
        assertEquals("Back at 1 PM", state.note)
        assertEquals(Instant.parse("2026-11-10T04:00:00.123456Z"), state.changedAt)
        assertEquals(hours, state.hours)

        // Broken hours are dropped, not guessed.
        val noHours = parseShopState("""[{"section":"closure","content":{"closed":false,"note":""}}]""")!!
        assertEquals(emptyMap<DayOfWeek, OpeningHours?>(), noHours.hours)
        assertNull(parseShopState("""[{"section":"hours","content":{}}]"""))
        // The save's answer has the time too.
        assertEquals(
            Instant.parse("2026-11-10T04:00:00Z"),
            parseClosure("""[{"content":{"closed":true,"note":""},"updated_at":"2026-11-10T04:00:00+00:00"}]""")?.changedAt
        )
    }
}
