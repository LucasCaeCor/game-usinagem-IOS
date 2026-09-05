package br.com.usinagemmaster.domain.simulation

import kotlin.math.hypot

/** Logical floor coordinates, independent of pixels, Compose, Room and wall clocks. */
data class FloorPoint(val x: Float, val y: Float) {
    fun distanceTo(other: FloorPoint): Float = hypot(other.x - x, other.y - y)
}

data class FloorCell(val x: Int, val y: Int) {
    fun point() = FloorPoint(x.toFloat(), y.toFloat())
}

enum class WorkerActivity(val label: String) {
    IDLE("Aguardando serviço"), WALKING("Em deslocamento"),
    FETCHING_MATERIAL("Buscando material"), FETCHING_TOOLS("Buscando ferramentas"),
    SETTING_UP("Preparando máquina"), WORKING("Usinando"),
    CARRYING_PART("Levando peças"), INSPECTING("Inspecionando"),
    PACKING("Depositando no estoque de saída"), BREAK("Descansando na copa"),
    STOCKING("Organizando estoque"), QUALITY_SUPPORT("Apoio da qualidade"),
    PROGRAMMING("Programando CNC"),
    GOING_HOME("Saindo do turno"), OFF_SHIFT("Fora do turno"),
    PHONE("No celular"), BLOCKED("Sem acesso à estação")
}

enum class FactoryMachineState(val label: String) {
    OFF("Desligada"), IDLE("Aguardando operador"), SETUP("Preparação"),
    RUNNING("Usinando"), WAITING_MATERIAL("Abastecimento / transporte"),
    MAINTENANCE("Manutenção recomendada"), BROKEN("Parada por desgaste")
}

data class FactoryMachineInput(
    val id: String,
    val gridX: Int,
    val gridY: Int,
    val installed: Boolean = true,
    val condition: Int = 1000,
    val productive: Boolean = false,
    val unitsPerHour: Double = 0.0,
    val machineType: String = "",
)

data class FactoryWorkerInput(
    val id: String,
    val machineId: String? = null,
    val skill: Int = 1,
    val fatigue: Int = 0,
    val resting: Boolean = false,
    val onPhone: Boolean = false,
    val specialty: String = "",
)

data class FactoryInput(
    val machines: List<FactoryMachineInput> = emptyList(),
    val workers: List<FactoryWorkerInput> = emptyList(),
    val open: Boolean = true,
    val cycleStartedAt: Long = 0L,
    val gridColumns: Int = 5,
    val gridRows: Int = 6,
)

data class FactoryWorkerFrame(
    val id: String,
    val position: FloorPoint,
    val activity: WorkerActivity,
    val destinationActivity: WorkerActivity,
    val machineId: String?,
    val walking: Boolean,
    val carrying: Boolean,
    val fatigue: Int,
    val progress: Float,
    val route: List<FloorPoint>,
)

data class FactoryMachineFrame(
    val id: String,
    val state: FactoryMachineState,
    val progress: Float,
    val needsMaintenance: Boolean,
)

data class FactoryFrame(
    val workers: List<FactoryWorkerFrame> = emptyList(),
    val machines: List<FactoryMachineFrame> = emptyList(),
    val open: Boolean = true,
    val depositedLots: Int = 0,
    val owner: FactoryOwnerFrame = FactoryOwnerFrame(),
    val cargoInTransit: List<String> = emptyList(),
)

/** Dynamic factory floor. Four subdivisions per bay preserve connected corridors. */
class FactoryFloor(
    machines: List<FactoryMachineInput>,
    gridColumns: Int = 5,
    gridRows: Int = 6,
) {
    companion object {
        const val WIDTH = 20
        const val HEIGHT = 24
        val ENTRY = FloorCell(0, 0)
        val STOCK = FloorCell(0, 4)
        val TOOLS = FloorCell(0, 10)
        val INSPECTION = FloorCell(20, 16)
        val SHIPPING = FloorCell(20, 24)
        val STAGING = FloorCell(12, 24)
        val BREAK_ROOM = FloorCell(0, 24)
        fun bay(machine: FactoryMachineInput) = FloorCell(
            machine.gridX.coerceIn(0, 4) * 4 + 2,
            machine.gridY.coerceIn(0, 5) * 4 + 4,
        )
    }

    val columns: Int = gridColumns.coerceIn(5, 10)
    val rows: Int = gridRows.coerceIn(6, 12)
    val width: Int = columns * 4
    val height: Int = rows * 4

    val entry = FloorCell(0, 0)
    val stock = FloorCell(0, (height * .20f).toInt().coerceIn(4, height - 4))
    val tools = FloorCell(0, (height * .42f).toInt().coerceIn(6, height - 4))
    val inspection = FloorCell(width, (height * .62f).toInt().coerceIn(4, height - 4))
    val shipping = FloorCell(width, height)
    val staging = FloorCell((width * .60f).toInt(), height)
    val breakRoom = FloorCell(0, height)
    val cncDesk = FloorCell((width * .72f).toInt().coerceIn(2, width - 2), 0)
    val maintenance = FloorCell((width * .38f).toInt().coerceIn(2, width - 2), 0)

    fun bayFor(machine: FactoryMachineInput) = FloorCell(
        machine.gridX.coerceIn(0, columns - 1) * 4 + 2,
        machine.gridY.coerceIn(0, rows - 1) * 4 + 4,
    )

    private val blocked = machines.filter { it.installed }.flatMap { machine ->
        val bay = bayFor(machine)
        val x = bay.x
        val y = bay.y - 2
        (-1..1).flatMap { dx -> (-1..1).map { dy -> FloorCell(x + dx, y + dy) } }
    }.toSet()

    fun walkable(cell: FloorCell) = cell.x in 0..width && cell.y in 0..height && cell !in blocked

    fun nearestWalkable(point: FloorPoint): FloorCell = (0..width).flatMap { x ->
        (0..height).map { y -> FloorCell(x, y) }
    }.filter(::walkable).minBy { it.point().distanceTo(point) }

    /** Breadth-first search with orthogonal edges: no corner cutting or routes through machines. */
    fun route(start: FloorCell, target: FloorCell): List<FloorPoint> {
        if (!walkable(start) || !walkable(target)) return emptyList()
        val previous = mutableMapOf<FloorCell, FloorCell?>(start to null)
        val queue = ArrayDeque<FloorCell>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (cell == target) {
                val result = mutableListOf<FloorPoint>()
                var cursor: FloorCell? = target
                while (cursor != null) {
                    result.add(cursor.point())
                    cursor = previous[cursor]
                }
                return result.asReversed()
            }
            listOf(
                FloorCell(cell.x + 1, cell.y), FloorCell(cell.x, cell.y + 1),
                FloorCell(cell.x - 1, cell.y), FloorCell(cell.x, cell.y - 1),
            ).forEach { next ->
                if (walkable(next) && next !in previous) {
                    previous[next] = cell
                    queue.add(next)
                }
            }
        }
        return emptyList()
    }
}

/**
 * Deterministic operational scene. A batch represents the existing idle throughput; it is
 * deliberately NOT a second ledger. Only GameRepository settles money, contracts and saves.
 * Runtime positions can be rebuilt after process death without changing economic progress.
 */
class FactorySimulation {
    private data class Agent(
        var input: FactoryWorkerInput,
        var position: FloorPoint,
        var activity: WorkerActivity = WorkerActivity.IDLE,
        var task: WorkerActivity = WorkerActivity.IDLE,
        var route: List<FloorPoint> = emptyList(),
        var routeIndex: Int = 0,
        var elapsed: Float = 0f,
        var duration: Float = 0f,
        var carrying: Boolean = false,
        var mode: String = "",
    )

    private var input = FactoryInput()
    private var floor = FactoryFloor(emptyList())
    private var topology = emptyList<Triple<String, Int, Int>>()
    private var floorColumns = 5
    private var floorRows = 6
    private val agents = linkedMapOf<String, Agent>()
    private var remainder = 0.0
    private var depositedLots = 0

    fun update(next: FactoryInput) {
        val nextTopology = next.machines.filter { it.installed }
            .map { Triple(it.id, it.gridX, it.gridY) }.sortedBy { it.first }
        val moved = nextTopology != topology || next.gridColumns != floorColumns || next.gridRows != floorRows
        if (input.cycleStartedAt != next.cycleStartedAt) depositedLots = 0
        input = next
        if (moved) {
            topology = nextTopology
            floorColumns = next.gridColumns
            floorRows = next.gridRows
            floor = FactoryFloor(next.machines, next.gridColumns, next.gridRows)
        }
        val ids = next.workers.map { it.id }.toSet()
        agents.keys.retainAll(ids)
        next.workers.sortedBy { it.id }.forEach { worker ->
            // Rebuild an ongoing shift at each assigned bay, rather than spawning the
            // entire staff on one entrance pixel every time the scene is created.
            val agent = agents.getOrPut(worker.id) {
                val initial = next.machines.firstOrNull { it.id == worker.machineId && it.installed }
                    ?.let { floor.bayFor(it) } ?: supportSeatFor(worker)
                Agent(worker, initial.point())
            }
            val reassigned = agent.input.machineId != worker.machineId
            agent.input = worker
            if (moved) {
                // Layout edits may put a new footprint under an existing worker.
                // Re-anchor in a free corridor and rebuild the route once per edit.
                agent.position = floor.nearestWalkable(agent.position).point()
            }
            val mode = mode(worker)
            if (moved || reassigned || agent.mode != mode) {
                agent.mode = mode
                startMode(agent)
            }
        }
    }

    /** Fixed 50 ms steps; a long suspended frame never triggers a catch-up storm. */
    fun advance(seconds: Double): FactoryFrame {
        if (seconds.isFinite() && seconds > 0.0) remainder += seconds.coerceAtMost(.25)
        while (remainder + 1e-9 >= .05) {
            agents.values.forEach { step(it, .05f) }
            remainder -= .05
        }
        return snapshot()
    }

    fun snapshot(): FactoryFrame {
        val workers = agents.values.map { agent ->
            FactoryWorkerFrame(agent.input.id, agent.position, agent.activity, agent.task,
                agent.input.machineId, agent.routeIndex < agent.route.size, agent.carrying,
                agent.input.fatigue.coerceIn(0, 100), progress(agent), agent.route.drop(agent.routeIndex))
        }
        val byMachine = agents.values.filter { it.input.machineId != null }.associateBy { it.input.machineId }
        val machines = input.machines.map { machine ->
            val agent = byMachine[machine.id]
            val state = when {
                !machine.installed || !input.open -> FactoryMachineState.OFF
                machine.condition <= 80 -> FactoryMachineState.BROKEN
                (agent == null || agent.mode != "production") && machine.condition <= 350 -> FactoryMachineState.MAINTENANCE
                agent == null || agent.mode != "production" -> FactoryMachineState.IDLE
                agent.activity == WorkerActivity.SETTING_UP -> FactoryMachineState.SETUP
                agent.activity == WorkerActivity.WORKING -> FactoryMachineState.RUNNING
                else -> FactoryMachineState.WAITING_MATERIAL
            }
            FactoryMachineFrame(machine.id, state, agent?.let(::progress) ?: 0f, machine.condition in 81..350)
        }
        return FactoryFrame(workers, machines, input.open, depositedLots)
    }

    private fun machine(agent: Agent) = input.machines.firstOrNull { it.id == agent.input.machineId && it.installed }

    private fun mode(worker: FactoryWorkerInput): String = when {
        !input.open -> "home"
        worker.resting -> "break"
        worker.onPhone -> "phone"
        else -> {
            val machine = input.machines.firstOrNull { it.id == worker.machineId && it.installed }
            when {
                machine != null && machine.productive && machine.condition > 80 -> "production"
                worker.machineId == null && worker.specialty == "STOCK_ASSISTANT" -> "stock_support"
                worker.machineId == null && worker.specialty == "QUALITY_INSPECTOR" -> "quality_support"
                worker.machineId == null && worker.specialty == "CNC_PROGRAMMER" -> "cnc_support"
                else -> "idle"
            }
        }
    }

    private fun startMode(agent: Agent) {
        agent.carrying = false
        when (agent.mode) {
            "home" -> travel(agent, floor.entry, WorkerActivity.OFF_SHIFT)
            "break" -> travel(agent, breakSeat(agent), WorkerActivity.BREAK)
            "phone" -> travel(agent, machine(agent)?.let { floor.bayFor(it) } ?: supportSeatFor(agent.input), WorkerActivity.PHONE)
            "production" -> travel(agent, floor.stock, WorkerActivity.FETCHING_MATERIAL)
            "stock_support" -> travel(agent, floor.stock, WorkerActivity.STOCKING)
            "quality_support" -> travel(agent, floor.inspection, WorkerActivity.QUALITY_SUPPORT)
            "cnc_support" -> travel(agent, floor.cncDesk, WorkerActivity.PROGRAMMING)
            else -> travel(agent, machine(agent)?.let { floor.bayFor(it) } ?: supportSeatFor(agent.input), WorkerActivity.IDLE)
        }
    }

    private fun breakSeat(agent: Agent) = breakSeatFor(agent.input.id)

    private fun supportSeatFor(worker: FactoryWorkerInput): FloorCell = when (worker.specialty) {
        "STOCK_ASSISTANT" -> floor.stock
        "QUALITY_INSPECTOR" -> floor.inspection
        "CNC_PROGRAMMER" -> floor.cncDesk
        else -> breakSeatFor(worker.id)
    }

    private fun breakSeatFor(id: String): FloorCell {
        val index = input.workers.map { it.id }.sorted().indexOf(id).coerceAtLeast(0)
        return if (index <= floor.width) FloorCell(index.coerceAtMost(floor.width), floor.height)
        else FloorCell(0, (floor.height - 1 - index + floor.width).coerceAtLeast(0))
    }

    private fun travel(agent: Agent, target: FloorCell, task: WorkerActivity) {
        val start = floor.nearestWalkable(agent.position)
        // The first waypoint returns along the current corridor if a break interrupts movement.
        agent.route = floor.route(start, target)
        agent.routeIndex = 0
        agent.task = task
        agent.elapsed = 0f
        agent.duration = 0f
        agent.activity = when {
            agent.route.isEmpty() -> WorkerActivity.BLOCKED
            task == WorkerActivity.OFF_SHIFT -> WorkerActivity.GOING_HOME
            agent.carrying -> WorkerActivity.CARRYING_PART
            else -> WorkerActivity.WALKING
        }
    }

    private fun step(agent: Agent, dt: Float) {
        if (agent.activity == WorkerActivity.BLOCKED) return
        if (agent.routeIndex < agent.route.size) {
            val target = agent.route[agent.routeIndex]
            val distance = agent.position.distanceTo(target)
            val speed = (2.3f + agent.input.skill.coerceIn(1, 10) * .10f) *
                (1f - agent.input.fatigue.coerceIn(0, 100) * .004f)
            val movement = speed * dt
            if (distance <= movement) {
                agent.position = target
                agent.routeIndex++
                if (agent.routeIndex == agent.route.size) arrive(agent)
            } else {
                agent.position = FloorPoint(agent.position.x + (target.x - agent.position.x) * movement / distance,
                    agent.position.y + (target.y - agent.position.y) * movement / distance)
            }
            return
        }
        if (agent.duration <= 0f) return
        agent.elapsed += dt
        if (agent.elapsed + .0001f < agent.duration) return
        when (agent.activity) {
            WorkerActivity.FETCHING_MATERIAL -> {
                agent.carrying = true
                travel(agent, floor.tools, WorkerActivity.FETCHING_TOOLS)
            }
            WorkerActivity.FETCHING_TOOLS -> machine(agent)?.let { travel(agent, floor.bayFor(it), WorkerActivity.SETTING_UP) }
            WorkerActivity.SETTING_UP -> {
                agent.carrying = false
                agent.activity = WorkerActivity.WORKING
                agent.task = WorkerActivity.WORKING
                agent.elapsed = 0f
                // Representative batch cadence, never displayed as an exact piece count.
                val rate = machine(agent)?.unitsPerHour?.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
                agent.duration = (3600.0 / rate).coerceIn(4.0, 18.0).toFloat()
            }
            WorkerActivity.WORKING -> {
                agent.carrying = true
                travel(agent, floor.inspection, WorkerActivity.INSPECTING)
            }
            WorkerActivity.INSPECTING -> travel(agent, floor.staging, WorkerActivity.PACKING)
            WorkerActivity.PACKING -> {
                depositedLots = (depositedLots + 1).coerceAtMost(1000)
                agent.carrying = false
                travel(agent, floor.stock, WorkerActivity.FETCHING_MATERIAL)
            }
            WorkerActivity.STOCKING -> {
                val nearStock = agent.position.distanceTo(floor.stock.point()) < 2.5f
                agent.carrying = nearStock
                travel(agent, if (nearStock) floor.staging else floor.stock, WorkerActivity.STOCKING)
            }
            WorkerActivity.QUALITY_SUPPORT -> {
                agent.carrying = false
                val nearQuality = agent.position.distanceTo(floor.inspection.point()) < 2.5f
                travel(agent, if (nearQuality) floor.staging else floor.inspection, WorkerActivity.QUALITY_SUPPORT)
            }
            WorkerActivity.PROGRAMMING -> {
                agent.carrying = false
                val cncMachine = input.machines.firstOrNull { it.installed && it.machineType.contains("CNC", ignoreCase = true) }
                val nearDesk = agent.position.distanceTo(floor.cncDesk.point()) < 2.5f
                val target = if (nearDesk && cncMachine != null) floor.nearestWalkable(floor.bayFor(cncMachine).point()) else floor.cncDesk
                travel(agent, target, WorkerActivity.PROGRAMMING)
            }
            else -> Unit
        }
    }

    private fun arrive(agent: Agent) {
        agent.activity = agent.task
        agent.elapsed = 0f
        agent.duration = when (agent.task) {
            WorkerActivity.FETCHING_MATERIAL -> 1.5f
            WorkerActivity.FETCHING_TOOLS -> 1.2f
            WorkerActivity.SETTING_UP -> 4.5f - agent.input.skill.coerceIn(1, 10) * .25f
            WorkerActivity.INSPECTING -> 2.5f
            WorkerActivity.PACKING -> 2f
            WorkerActivity.STOCKING -> 4.5f
            WorkerActivity.QUALITY_SUPPORT -> 4.0f
            WorkerActivity.PROGRAMMING -> 5.0f
            else -> 0f
        }
    }

    private fun progress(agent: Agent): Float =
        if (agent.duration > 0f) (agent.elapsed / agent.duration).coerceIn(0f, 1f) else 0f
}
