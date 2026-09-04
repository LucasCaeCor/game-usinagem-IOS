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
        val fatigue = employee.fatigue.coerceIn(0.0, 100.0)
        return (1.0 - fatigue / 145.0).coerceIn(.35, 1.0)
    }

    /**
     * Mesma base do FatigueAccrual Android:
     * - sem máquina: 1.2/h
     * - turno normal atribuído: 4.0/h
     * - 24h atribuído: 6.5/h
     * - pausa fora de expediente: -8.5/h
     * - descanso na Copa: -28/h
     */
    fun advanceFatigue(
        employee: EmployeeSave,
        assigned: Boolean,
        continuous: Boolean,
        workHours: Double,
        pausedHours: Double,
        restHours: Double,
    ): EmployeeSave {
        val working = workHours.coerceAtLeast(0.0)
        val restingHours = restHours.coerceIn(0.0, working)
        val rate = when {
            !assigned -> 1.2
            continuous -> 6.5
            else -> 4.0
        }
        val next = (
            employee.fatigue +
                rate * (working - restingHours) -
                8.5 * pausedHours.coerceAtLeast(0.0) -
                28.0 * restingHours
            ).coerceIn(0.0, 100.0)
        return employee.copy(fatigue = next)
    }

    fun afterWorked(employee: EmployeeSave, minutes: Long, mode: ShiftMode): EmployeeSave =
        advanceFatigue(
            employee = employee,
            assigned = employee.assignedMachineId != null,
            continuous = mode == ShiftMode.CONTINUOUS_24H,
            workHours = minutes.coerceAtLeast(0L) / 60.0,
            pausedHours = 0.0,
            restHours = 0.0,
        )

    fun afterRest(employee: EmployeeSave, minutes: Long): EmployeeSave =
        advanceFatigue(
            employee = employee,
            assigned = employee.assignedMachineId != null,
            continuous = false,
            workHours = minutes.coerceAtLeast(0L) / 60.0,
            pausedHours = 0.0,
            restHours = minutes.coerceAtLeast(0L) / 60.0,
        )

    fun afterClosedShift(employee: EmployeeSave, minutes: Long): EmployeeSave =
        advanceFatigue(
            employee = employee,
            assigned = employee.assignedMachineId != null,
            continuous = false,
            workHours = 0.0,
            pausedHours = minutes.coerceAtLeast(0L) / 60.0,
            restHours = 0.0,
        )
}
