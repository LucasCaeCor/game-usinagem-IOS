package br.com.usinagemmaster.game.domain

import br.com.usinagemmaster.game.model.EmployeeSave
import br.com.usinagemmaster.game.model.ShiftMode

object WorkLifeRules {
    fun factoryOpen(mode: ShiftMode, now: Long): Boolean =
        mode == ShiftMode.CONTINUOUS_24H || currentHourOfDay(now) in 7..18

    fun resting(employee: EmployeeSave, now: Long): Boolean =
        employee.restingUntil > now

    fun efficiency(employee: EmployeeSave, now: Long): Double {
        if (resting(employee, now)) return 0.0
        return (1.0 - employee.fatigue.coerceIn(0, 100) / 145.0).coerceIn(.35, 1.0)
    }

    fun afterWorked(employee: EmployeeSave, minutes: Long, mode: ShiftMode): EmployeeSave {
        val gainPer10 = if (mode == ShiftMode.CONTINUOUS_24H) 5 else 3
        val gain = ((minutes / 10L).coerceAtLeast(1L) * gainPer10).toInt()
        return employee.copy(fatigue = (employee.fatigue + gain).coerceAtMost(100))
    }

    fun afterRest(employee: EmployeeSave, minutes: Long): EmployeeSave {
        val recovered = ((minutes / 10L).coerceAtLeast(1L) * 8).toInt()
        return employee.copy(fatigue = (employee.fatigue - recovered).coerceAtLeast(0))
    }
}
