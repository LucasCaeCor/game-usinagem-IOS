package br.com.usinagemmaster.game.domain

import java.util.Calendar

/**
 * Implementação Android (JVM) dos expect/fun definidos em commonMain.
 * Mantém compatibilidade exata com o comportamento original
 * (java.util.Calendar + System.currentTimeMillis()).
 */

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun currentHourOfDay(now: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    return calendar.get(Calendar.HOUR_OF_DAY)
}
