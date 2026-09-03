@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.usinagemmaster.game.domain

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSDate
import platform.Foundation.NSTimeIntervalSince1970
import platform.Foundation.NSTimeZone
import platform.Foundation.systemTimeZone
import platform.posix.gettimeofday
import platform.posix.timeval

/**
 * Implementações iOS dos expect/fun definidos em commonMain.
 *
 * currentTimeMillis usa gettimeofday() para manter precisão em milissegundos.
 * currentHourOfDay converte Unix epoch -> NSDate usando o initializer
 * timeIntervalSinceReferenceDate, que é exposto de forma estável pelo
 * interop Foundation do Kotlin/Native.
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

    // NSDate usa 01/01/2001 como reference date.
    // NSTimeIntervalSince1970 representa a distância, em segundos,
    // entre 01/01/1970 e 01/01/2001.
    val date = NSDate(
        timeIntervalSinceReferenceDate =
            (now.toDouble() / 1000.0) - NSTimeIntervalSince1970
    )

    val components = calendar.components(
        NSCalendarUnitHour,
        fromDate = date
    )

    return components.hour.toInt()
}
