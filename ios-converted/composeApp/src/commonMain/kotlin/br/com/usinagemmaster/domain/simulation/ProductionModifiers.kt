package br.com.usinagemmaster.domain.simulation

data class ProductionModifiers(
    val globalSpeedMultiplier: Double = 1.0,
    val qualityBonus: Int = 0,
    val energyMultiplier: Double = 1.0,
    val turningMultiplier: Double = 1.0,
    val millingMultiplier: Double = 1.0,
    val drillingMultiplier: Double = 1.0,
    val grindingMultiplier: Double = 1.0,
    val weldingMultiplier: Double = 1.0,
    val cncMultiplier: Double = 1.0,
) {
    fun multiplierForMachine(machineType: String): Double {
        val type = machineType.uppercase()
        var value = 1.0
        if ("LATHE" in type || "TURN" in type) value *= turningMultiplier
        if ("MILL" in type || "MACHINING_CENTER" in type) value *= millingMultiplier
        if ("DRILL" in type) value *= drillingMultiplier
        if ("GRINDER" in type || "GRIND" in type) value *= grindingMultiplier
        if ("WELD" in type) value *= weldingMultiplier
        if ("CNC" in type || "MACHINING_CENTER" in type || "EDM" in type) value *= cncMultiplier
        return value
    }
}
