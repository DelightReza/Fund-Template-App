package com.delightreza.fund.utils

import java.util.Locale

object FormatUtils {
    fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            String.format(Locale.US, "%,d", amount.toInt())
        } else {
            String.format(Locale.US, "%,.2f", amount)
        }
    }
}
