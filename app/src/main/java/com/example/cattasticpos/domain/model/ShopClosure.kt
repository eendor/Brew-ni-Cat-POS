package com.example.cattasticpos.domain.model

/**
 * The owner's "Closed today" switch (Connect FR-111). It is the `closure` row of
 * `public.site_content`; while [closed] is on, the website and the Messenger assistant say the
 * shop is closed and take no online orders.
 */
data class ShopClosure(
    val closed: Boolean,
    /** Optional reason shown to customers, e.g. "Back tomorrow at 1 PM." */
    val note: String = ""
) {
    companion object {
        const val MAX_NOTE = 300
    }
}
