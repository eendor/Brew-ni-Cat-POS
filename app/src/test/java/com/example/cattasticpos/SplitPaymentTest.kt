package com.example.cattasticpos

import com.example.cattasticpos.domain.model.SplitPayment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Split payment (FR-096): cash plus GCash adding up to the total. */
class SplitPaymentTest {

    /** The same cut the daily cash total makes in SQL: substr(ref, 6, instr(ref, '|') - 6). */
    private fun sqlCashPart(ref: String): Double = ref.substring(5, ref.indexOf('|')).toDouble()

    @Test
    fun `TC-P6-208 the two parts must be above zero and add up to the total`() {
        assertTrue(SplitPayment.isValid(150.0, 50.0, 100.0))
        assertTrue(SplitPayment.isValid(149.5, 49.5, 100.0))
        assertFalse(SplitPayment.isValid(150.0, 0.0, 150.0))
        assertFalse(SplitPayment.isValid(150.0, 60.0, 100.0))
        assertFalse(SplitPayment.isValid(150.0, -10.0, 160.0))
    }

    @Test
    fun `TC-P6-209 the reference keeps both parts and the cash part comes first for the totals`() {
        val ref = SplitPayment.encode(50.0, 100.0, "Personal", " 1234 ")
        assertEquals("cash=50.00|gcash=100.00|account=Personal|ref=1234", ref)
        assertEquals(50.0, sqlCashPart(ref), 0.0)
        assertEquals(150.0 - 50.0, 150.0 - sqlCashPart(ref), 0.0)
        val parts = SplitPayment.parse(ref)!!
        assertEquals(100.0, parts.gcash, 0.0)
        assertEquals("Personal", parts.account)
        assertEquals("1234", parts.reference)

        val noRef = SplitPayment.encode(20.5, 9.5, "Main|SIM", null)
        assertEquals("cash=20.50|gcash=9.50|account=Main SIM", noRef)
        assertNull(SplitPayment.parse(noRef)!!.reference)
    }

    @Test
    fun `TC-P6-210 receipts show both parts and other methods are unchanged`() {
        assertEquals(
            "CASH ₱50.00 + GCASH ₱100.00",
            SplitPayment.describe("SPLIT", "cash=50.00|gcash=100.00|account=Personal")
        )
        assertEquals("CASH + GCASH", SplitPayment.describe("SPLIT", "broken"))
        assertEquals("GCASH", SplitPayment.describe("GCASH", "account=Personal"))
        assertEquals("CASH", SplitPayment.describe("CASH", null))
    }
}
