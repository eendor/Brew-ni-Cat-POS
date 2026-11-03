package com.example.cattasticpos.data.local

import android.content.Context
import com.example.cattasticpos.domain.usecase.DeductedOrderStore

/** [DeductedOrderStore] kept in the app's private preferences. */
class DeductedOrderPreferences(context: Context) : DeductedOrderStore {
    private val prefs = context.getSharedPreferences("online_order_stock", Context.MODE_PRIVATE)

    @Synchronized
    override fun claim(reference: String): Boolean {
        val done = prefs.getStringSet(KEY, emptySet()).orEmpty()
        if (reference in done) return false
        // commit(), not apply(): the claim has to be on disk before the stock moves
        return prefs.edit().putStringSet(KEY, done + reference).commit()
    }

    @Synchronized
    override fun release(reference: String): Boolean {
        val done = prefs.getStringSet(KEY, emptySet()).orEmpty()
        if (reference !in done) return false
        return prefs.edit().putStringSet(KEY, done - reference).commit()
    }

    private companion object {
        const val KEY = "deducted_references"
    }
}
