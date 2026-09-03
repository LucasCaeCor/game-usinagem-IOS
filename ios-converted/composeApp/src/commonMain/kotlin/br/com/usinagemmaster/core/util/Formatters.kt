package br.com.usinagemmaster.core.util

object Formatters {
    fun money(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val absCents = if (cents < 0) -cents else cents
        val reais = absCents / 100
        val remainder = absCents % 100
        val centsStr = remainder.toString().padStart(2, '0')

        val reaisStr = reais.toString()
        val formattedReais = StringBuilder()
        val len = reaisStr.length
        for (i in 0 until len) {
            if (i > 0 && (len - i) % 3 == 0) {
                formattedReais.append('.')
            }
            formattedReais.append(reaisStr[i])
        }
        return "${sign}R$ $formattedReais,$centsStr"
    }

    fun duration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}min"
            h > 0 -> "${h}h"
            else -> "${m}min"
        }
    }
}
