package br.com.usinagemmaster.domain.simulation

enum class OwnerActivity(val label: String) {
    IDLE("Aguardando carga"), COLLECTING("Indo buscar a carga"),
    LOADING("Carregando caixas"), DELIVERING("Levando à entrega"),
    UNLOADING("Descarregando"), AWAITING_PAYMENT("Concluindo entrega"),
    RETURNING("Voltando ao galpão")
}

data class FactoryOwnerFrame(
    val position: FloorPoint = FactoryFloor.ENTRY.point(),
    val activity: OwnerActivity = OwnerActivity.IDLE,
    val walking: Boolean = false,
    val carrying: Boolean = false,
) {
    val busy: Boolean get() = activity != OwnerActivity.IDLE
}

/**
 * Controls only the owner's physical delivery trip. Economy/payment remains in GameStore.
 * V28: the route now uses the same dynamic floor dimensions as the live factory, so
 * warehouse expansion also expands the owner's logistics path instead of clamping it
 * to the original 5x6 layout.
 */
class FactoryOwnerSimulation {
    private var floor = FactoryFloor(emptyList())
    private var topology = emptyList<Triple<String, Int, Int>>()
    private var floorColumns = 5
    private var floorRows = 6
    private var position = floor.entry.point()
    private var activity = OwnerActivity.IDLE
    private var route = emptyList<FloorPoint>()
    private var index = 0
    private var elapsed = 0.0
    private var destination = floor.entry

    fun update(
        machines: List<FactoryMachineInput>,
        gridColumns: Int = 5,
        gridRows: Int = 6,
    ) {
        val next = machines.filter { it.installed }
            .map { Triple(it.id, it.gridX, it.gridY) }
            .sortedBy { it.first }
        val columns = gridColumns.coerceIn(5, 10)
        val rows = gridRows.coerceIn(6, 12)
        if (next == topology && columns == floorColumns && rows == floorRows) return

        topology = next
        floorColumns = columns
        floorRows = rows
        floor = FactoryFloor(machines, columns, rows)
        position = floor.nearestWalkable(position).point()

        // Re-map the semantic destination after an expansion/layout change.
        destination = when (activity) {
            OwnerActivity.COLLECTING, OwnerActivity.LOADING -> floor.staging
            OwnerActivity.DELIVERING, OwnerActivity.UNLOADING, OwnerActivity.AWAITING_PAYMENT -> floor.shipping
            OwnerActivity.RETURNING, OwnerActivity.IDLE -> floor.entry
        }
        if (index < route.size) travel(destination, activity)
    }

    fun start(): Boolean {
        if (activity != OwnerActivity.IDLE) return false
        travel(floor.staging, OwnerActivity.COLLECTING)
        return true
    }

    fun advance(seconds: Double): FactoryOwnerFrame {
        val dt = if (seconds.isFinite()) seconds.coerceIn(0.0, .25) else 0.0
        if (index < route.size) {
            var movement = (dt * 5.0).toFloat()
            while (index < route.size) {
                val target = route[index]
                val distance = position.distanceTo(target)
                if (distance > movement) {
                    position = FloorPoint(
                        position.x + (target.x - position.x) * movement / distance,
                        position.y + (target.y - position.y) * movement / distance,
                    )
                    break
                }
                position = target
                movement -= distance
                index++
            }
            if (index == route.size) {
                activity = when (activity) {
                    OwnerActivity.COLLECTING -> OwnerActivity.LOADING
                    OwnerActivity.DELIVERING -> OwnerActivity.UNLOADING
                    OwnerActivity.RETURNING -> OwnerActivity.IDLE
                    else -> activity
                }
                elapsed = 0.0
            }
        } else {
            elapsed += dt
            if (activity == OwnerActivity.LOADING && elapsed >= 1.2) {
                travel(floor.shipping, OwnerActivity.DELIVERING)
            }
            if (activity == OwnerActivity.UNLOADING && elapsed >= 1.0) {
                activity = OwnerActivity.AWAITING_PAYMENT
            }
        }
        return snapshot()
    }

    fun paymentRecorded() {
        if (activity == OwnerActivity.AWAITING_PAYMENT) {
            travel(floor.entry, OwnerActivity.RETURNING)
        }
    }

    fun cancel() {
        position = floor.entry.point()
        activity = OwnerActivity.IDLE
        route = emptyList()
        index = 0
        elapsed = 0.0
        destination = floor.entry
    }

    fun snapshot() = FactoryOwnerFrame(
        position,
        activity,
        index < route.size,
        activity == OwnerActivity.DELIVERING ||
            activity == OwnerActivity.UNLOADING ||
            activity == OwnerActivity.AWAITING_PAYMENT,
    )

    private fun travel(target: FloorCell, state: OwnerActivity) {
        destination = target
        route = floor.route(floor.nearestWalkable(position), target)
        check(route.isNotEmpty()) { "Expedição sem acesso" }
        index = 0
        elapsed = 0.0
        activity = state
    }
}
