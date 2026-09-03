package br.com.usinagemmaster.game.domain

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSTimeZone
import platform.Foundation.systemTimeZone
import platform.posix.gettimeofday
import platform.posix.timeval
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr

/**
 * Implementações iOS dos expect/fun definidos em commonMain.
 * Usa gettimeofday() (POSIX) e NSCalendar para extrair a hora local,
 * evitando dependências extras como kotlinx-datetime neste momento.
 */

actual fun currentTimeMillis(): Long = memScoped {
    val tv = alloc<timeval>()
    gettimeofday(tv.ptr, null)
    tv.tv_sec * 1000L + tv.tv_usec / 1000L
}

actual fun currentHourOfDay(now: Long): Int {
    val calendar = NSCalendar.currentCalendar.apply {
        timeZone = NSTimeZone.systemTimeZone()
    }
    val components = calendar.components(
        NSCalendarUnitHour,
        fromDate = NSDate.dateWithTimeIntervalSince1970(now / 1000.0)
    )
    return components.hour.toInt()
}
