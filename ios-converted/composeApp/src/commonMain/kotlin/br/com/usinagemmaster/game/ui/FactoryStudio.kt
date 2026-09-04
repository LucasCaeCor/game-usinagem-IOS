package br.com.usinagemmaster.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.core.util.Formatters
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.simulation.*
import br.com.usinagemmaster.game.domain.GameStore
import br.com.usinagemmaster.game.domain.MachineMinigameCatalog
import br.com.usinagemmaster.game.domain.MinigameKind
import br.com.usinagemmaster.game.domain.MinigameResult
import br.com.usinagemmaster.game.domain.MachineMastery
import br.com.usinagemmaster.game.domain.ProductionStage
import br.com.usinagemmaster.game.model.EmployeeSave
import br.com.usinagemmaster.game.model.MachineSave
import br.com.usinagemmaster.game.model.OwnerWorkBatchSave
import br.com.usinagemmaster.game.model.PlayerProfileSave
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun FactoryStudio(
    store: GameStore,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selectedMachineId by remember { mutableStateOf<String?>(null) }
    var selectedWorkerId by remember { mutableStateOf<String?>(null) }
    var machineDialogId by remember { mutableStateOf<String?>(null) }
    var operationMachineId by remember { mutableStateOf<String?>(null) }
    var reworkMachineId by remember { mutableStateOf<String?>(null) }
    var showQuality by remember { mutableStateOf(false) }
    var saleMachineId by remember { mutableStateOf<String?>(null) }
    var reprimandTargetId by remember { mutableStateOf<String?>(null) }

    val frame = store.factoryFrame
    val batch = store.state.career.activeBatch
    val machineInputs = remember(store.state.machines) {
        store.state.machines.map {
            FactoryMachineInput(it.id, it.gridX, it.gridY, it.installed, it.condition)
        }
    }
    val sceneFloor = remember(machineInputs) { FactoryFloor(machineInputs) }

    val transition = rememberInfiniteTransition(label = "factory_studio_v25")
    val workPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "work_phase",
    )
    val pulse by transition.animateFloat(
        initialValue = .34f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1450), RepeatMode.Reverse),
        label = "scene_pulse",
    )

    var ownerWorkCell by remember { mutableStateOf(FactoryFloor.ENTRY) }
    var ownerWorkRoute by remember { mutableStateOf(listOf(FactoryFloor.ENTRY.point())) }
    val ownerWorkProgress = remember { Animatable(1f) }
    var reprimandRoute by remember { mutableStateOf(listOf(FactoryFloor.ENTRY.point())) }
    val reprimandProgress = remember { Animatable(1f) }

    LaunchedEffect(batch?.id, batch?.stage, machineInputs) {
        val target = studioBatchTargetCell(batch, store.state.machines, sceneFloor)
        ownerWorkRoute = sceneFloor.route(ownerWorkCell, target).ifEmpty { listOf(target.point()) }
        ownerWorkProgress.snapTo(0f)
        ownerWorkProgress.animateTo(
            1f,
            tween((ownerWorkRoute.size * 135).coerceIn(350, 4500), easing = LinearEasing),
        )
        ownerWorkCell = target
    }

    LaunchedEffect(reprimandTargetId, frame.owner.busy, machineInputs) {
        val targetId = reprimandTargetId ?: return@LaunchedEffect
        if (frame.owner.busy) {
            reprimandTargetId = null
            return@LaunchedEffect
        }
        val worker = store.factoryFrame.workers.firstOrNull { it.id == targetId }
        if (worker == null || worker.activity == WorkerActivity.OFF_SHIFT) {
            reprimandTargetId = null
            return@LaunchedEffect
        }
        val start = studioBatchTargetCell(store.state.career.activeBatch, store.state.machines, sceneFloor)
        val target = sceneFloor.nearestWalkable(worker.position)
        reprimandRoute = sceneFloor.route(start, target).ifEmpty { listOf(target.point()) }
        reprimandProgress.snapTo(0f)
        reprimandProgress.animateTo(
            1f,
            tween((reprimandRoute.size * 125).coerceIn(300, 3600), easing = LinearEasing),
        )
        store.reprimandEmployee(targetId)
        reprimandRoute = sceneFloor.route(target, start).ifEmpty { listOf(start.point()) }
        reprimandProgress.snapTo(0f)
        reprimandProgress.animateTo(
            1f,
            tween((reprimandRoute.size * 120).coerceIn(300, 3200), easing = LinearEasing),
        )
        reprimandTargetId = null
    }

    val ownerOverride = when {
        frame.owner.busy -> null
        reprimandTargetId != null -> studioRoutePoint(reprimandRoute, reprimandProgress.value)
        batch != null -> studioRoutePoint(ownerWorkRoute, ownerWorkProgress.value)
        else -> FactoryFloor.ENTRY.point()
    }
    val ownerWalking = when {
        frame.owner.busy -> frame.owner.walking
        reprimandTargetId != null -> reprimandProgress.isRunning
        batch != null -> ownerWorkProgress.isRunning
        else -> false
    }
    val ownerCarrying = frame.owner.carrying || studioBatchCarrying(batch)

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Steel900),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("CHÃO DE FÁBRICA • INTERATIVO", fontWeight = FontWeight.Black)
                    Text(
                        if (frame.open) "Turno ativo • toque em máquinas, operadores, Q, P e E" else "Turno fechado • equipe em descanso",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (frame.open) ProductionGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallZoomButton("−") { zoom = (zoom - .18f).coerceIn(.78f, 3.25f) }
                    SmallZoomButton("${(zoom * 100).toInt()}%") { zoom = 1f; pan = Offset.Zero }
                    SmallZoomButton("+") { zoom = (zoom + .18f).coerceIn(.78f, 3.25f) }
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .background(Steel950, RoundedCornerShape(18.dp))
                    .border(1.dp, Steel700, RoundedCornerShape(18.dp)),
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            // Um dedo continua livre para o scroll vertical. A câmera só captura 2+ dedos.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var event = awaitPointerEvent()
                                while (event.changes.any { it.pressed }) {
                                    if (event.changes.count { it.pressed } >= 2) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        zoom = (zoom * zoomChange).coerceIn(.78f, 3.25f)
                                        pan = studioClampPan(pan + panChange, zoom)
                                        event.changes.forEach { it.consume() }
                                    }
                                    event = awaitPointerEvent()
                                }
                            }
                        }
                        .pointerInput(
                            store.state.machines,
                            store.factoryFrame.workers,
                            batch?.stage,
                            zoom,
                            pan,
                        ) {
                            detectTapGestures(
                                onDoubleTap = {
                                    zoom = if (zoom < 1.75f) 2.05f else 1f
                                    pan = Offset.Zero
                                },
                                onTap = { tap ->
                                    val width = size.width.toFloat()
                                    val height = size.height.toFloat()
                                    val base = studioBaseTile(width, height) * zoom
                                    fun point(p: FloorPoint) = studioProject(p, width, height, zoom, pan)
                                    fun distance(a: Offset, b: Offset) = studioDistance(a, b)
                                    val stage = studioStage(batch)
                                    val stationRadius = (base * .92f).coerceAtLeast(26f)

                                    val q = point(FactoryFloor.INSPECTION.point())
                                    if (distance(tap, q) <= stationRadius && batch != null) {
                                        when (stage) {
                                            ProductionStage.MACHINED -> store.moveOwnerBatchToQuality()
                                            ProductionStage.WAITING_QC, ProductionStage.QC -> showQuality = true
                                            else -> Unit
                                        }
                                        return@detectTapGestures
                                    }
                                    val p = point(FactoryFloor.STAGING.point())
                                    if (distance(tap, p) <= stationRadius) {
                                        if (stage == ProductionStage.APPROVED) store.packOwnerBatch()
                                        else if (store.pendingCargo.isNotEmpty() && !frame.owner.busy) store.startCargoDelivery()
                                        return@detectTapGestures
                                    }
                                    val e = point(FactoryFloor.SHIPPING.point())
                                    if (distance(tap, e) <= stationRadius && batch != null) {
                                        if (stage == ProductionStage.READY_TO_SHIP) store.shipOwnerBatch()
                                        return@detectTapGestures
                                    }

                                    val workerScale = (base / 18f).coerceIn(.45f, 1.25f)
                                    val touchedWorker = store.factoryFrame.workers
                                        .asSequence()
                                        .filter { it.activity != WorkerActivity.OFF_SHIFT }
                                        .map { it to distance(tap, point(it.position) + Offset(0f, -24f * workerScale)) }
                                        .minByOrNull { it.second }
                                        ?.takeIf { it.second <= (28f * workerScale).coerceAtLeast(22f) }
                                        ?.first
                                    if (touchedWorker != null) {
                                        selectedWorkerId = touchedWorker.id
                                        selectedMachineId = null
                                        if (
                                            touchedWorker.activity == WorkerActivity.PHONE &&
                                            reprimandTargetId == null &&
                                            !frame.owner.busy
                                        ) {
                                            reprimandTargetId = touchedWorker.id
                                        }
                                        return@detectTapGestures
                                    }

                                    val touchedMachine = store.state.machines
                                        .filter { it.installed }
                                        .map { machine ->
                                            val input = FactoryMachineInput(machine.id, machine.gridX, machine.gridY, machine.installed, machine.condition)
                                            machine to distance(tap, point(FactoryFloor.bay(input).point()))
                                        }
                                        .minByOrNull { it.second }
                                        ?.takeIf { it.second <= (base * 1.05f).coerceAtLeast(28f) }
                                        ?.first
                                    if (touchedMachine != null) {
                                        selectedMachineId = touchedMachine.id
                                        selectedWorkerId = null
                                        machineDialogId = touchedMachine.id
                                    } else {
                                        selectedMachineId = null
                                        selectedWorkerId = null
                                    }
                                },
                            )
                        },
                ) {
                    drawFactoryStudioScene(
                        store = store,
                        frame = frame,
                        zoom = zoom,
                        pan = pan,
                        selectedMachineId = selectedMachineId,
                        selectedWorkerId = selectedWorkerId,
                        ownerOverride = ownerOverride,
                        ownerOverrideWalking = ownerWalking,
                        ownerOverrideCarrying = ownerCarrying,
                        phase = workPhase,
                        pulse = pulse,
                    )
                }

                StudioSceneHud(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    frame = frame,
                    batch = batch,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LegendDot(ProductionGreen, "Produzindo", Modifier.weight(1f))
                LegendDot(SafetyAmber, "Setup/logística", Modifier.weight(1f))
                LegendDot(DangerRed, "Parada", Modifier.weight(1f))
            }

            StudioOwnerActionStrip(store, batch)
            selectedWorkerId?.let { id -> StudioSelectedWorkerCard(store, id) }
        }
    }

    machineDialogId?.let { id ->
        val machine = store.state.machines.firstOrNull { it.id == id }
        if (machine == null) machineDialogId = null else StudioMachineManagementDialog(
            store = store,
            machine = machine,
            onDismiss = { machineDialogId = null },
            onOperate = {
                machineDialogId = null
                operationMachineId = machine.id
            },
            onRework = {
                machineDialogId = null
                reworkMachineId = machine.id
            },
            onSell = {
                machineDialogId = null
                saleMachineId = machine.id
            },
        )
    }

    operationMachineId?.let { id ->
        val machine = store.state.machines.firstOrNull { it.id == id }
        if (machine == null) operationMachineId = null else StudioOwnerOperationDialog(
            store = store,
            machine = machine,
            onDismiss = { operationMachineId = null },
        )
    }

    reworkMachineId?.let { id ->
        val machine = store.state.machines.firstOrNull { it.id == id }
        val active = store.state.career.activeBatch
        val contract = active?.let { b -> store.state.contracts.firstOrNull { it.id == b.contractId } }
        if (machine == null || active == null || contract == null) {
            reworkMachineId = null
        } else {
            StudioMachineMinigameDialog(
                store = store,
                machine = machine,
                contractId = contract.id,
                rework = true,
                onDismiss = { reworkMachineId = null },
            )
        }
    }

    if (showQuality) {
        StudioQualityInspectionDialog(store = store, onDismiss = { showQuality = false })
    }

    saleMachineId?.let { id ->
        val machine = store.state.machines.firstOrNull { it.id == id }
        if (machine == null) saleMachineId = null else StudioConfirmMachineSale(
            machine = machine,
            onDismiss = { saleMachineId = null },
            onConfirm = {
                store.sellMachine(id)
                if (selectedMachineId == id) selectedMachineId = null
                saleMachineId = null
            },
        )
    }
}

@Composable
private fun SmallZoomButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(34.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun LegendDot(color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(9.dp)) { drawCircle(color) }
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

private fun DrawScope.drawFactoryStudioScene(
    store: GameStore,
    frame: FactoryFrame,
    zoom: Float,
    pan: Offset,
    selectedMachineId: String?,
    selectedWorkerId: String?,
    ownerOverride: FloorPoint?,
    ownerOverrideWalking: Boolean,
    ownerOverrideCarrying: Boolean,
    phase: Float,
    pulse: Float,
) {
    val baseTileW = min(size.width / 22f, size.height / 17f)
    val halfW = baseTileW * zoom
    val halfH = halfW * .46f
    val origin = Offset(size.width * .50f + pan.x, 28f + pan.y)

    fun project(p: FloorPoint): Offset = Offset(
        origin.x + (p.x - p.y) * halfW * .52f,
        origin.y + (p.x + p.y) * halfH * .52f,
    )

    // Parede, vigas, iluminação e ponte rolante — direção Studio Android.
    drawRect(Color(0xFF111A1F), topLeft = Offset.Zero, size = Size(size.width, size.height))
    drawRect(
        Color(0xFF1D2A31),
        topLeft = Offset(size.width * .035f, size.height * .025f),
        size = Size(size.width * .93f, size.height * .20f),
    )
    repeat(6) { i ->
        val x = size.width * (.08f + i * .17f)
        drawRect(
            Color(0xFF5E6B72).copy(alpha = .32f),
            topLeft = Offset(x, size.height * .035f),
            size = Size(3f, size.height * .18f),
        )
        drawCircle(
            Color(0xFFFFE0A0).copy(alpha = .18f),
            radius = halfW * .34f,
            center = Offset(x + halfW * .15f, size.height * .16f),
        )
    }

    val craneY = size.height * .235f
    drawLine(Color(0xFFD89A32), Offset(size.width * .08f, craneY), Offset(size.width * .92f, craneY), halfW * .10f)
    drawLine(Color(0xFF704E21), Offset(size.width * .08f, craneY + halfW * .17f), Offset(size.width * .92f, craneY + halfW * .17f), halfW * .05f)
    val trolleyPhase = ((store.state.company.lastSimulationAt / 80L) % 1000L) / 1000f
    val trolleyX = size.width * (.22f + trolleyPhase * .56f)
    drawRoundRect(
        SafetyAmber,
        topLeft = Offset(trolleyX - halfW * .22f, craneY - halfW * .13f),
        size = Size(halfW * .44f, halfW * .26f),
        cornerRadius = CornerRadius(halfW * .05f),
    )
    drawLine(
        Color(0xFFCBD3D7),
        Offset(trolleyX, craneY + halfW * .10f),
        Offset(trolleyX, craneY + halfW * .72f),
        halfW * .045f,
    )

    // Galpão isométrico.
    val floorPath = Path().apply {
        moveTo(project(FloorPoint(0f, 0f)).x, project(FloorPoint(0f, 0f)).y)
        lineTo(project(FloorPoint(FactoryFloor.WIDTH.toFloat(), 0f)).x, project(FloorPoint(FactoryFloor.WIDTH.toFloat(), 0f)).y)
        lineTo(project(FloorPoint(FactoryFloor.WIDTH.toFloat(), FactoryFloor.HEIGHT.toFloat())).x, project(FloorPoint(FactoryFloor.WIDTH.toFloat(), FactoryFloor.HEIGHT.toFloat())).y)
        lineTo(project(FloorPoint(0f, FactoryFloor.HEIGHT.toFloat())).x, project(FloorPoint(0f, FactoryFloor.HEIGHT.toFloat())).y)
        close()
    }
    drawPath(floorPath, Color(0xFF28343A))
    drawPath(floorPath, Color(0xFF66737A), style = Stroke(1.5f))

    // Linhas de piso / faixas de segurança.
    for (x in 0..FactoryFloor.WIDTH step 4) {
        drawLine(Color.White.copy(alpha = .055f), project(FloorPoint(x.toFloat(), 0f)), project(FloorPoint(x.toFloat(), FactoryFloor.HEIGHT.toFloat())), 1f)
    }
    for (y in 0..FactoryFloor.HEIGHT step 4) {
        drawLine(Color.White.copy(alpha = .055f), project(FloorPoint(0f, y.toFloat())), project(FloorPoint(FactoryFloor.WIDTH.toFloat(), y.toFloat())), 1f)
    }

    drawLine(
        SafetyAmber.copy(alpha = .58f),
        project(FloorPoint(0f, 21.8f)),
        project(FloorPoint(20f, 21.8f)),
        (halfW * .10f).coerceAtLeast(2f),
    )
    drawLine(
        Color.White.copy(alpha = .18f),
        project(FloorPoint(0f, 19.7f)),
        project(FloorPoint(20f, 19.7f)),
        (halfW * .04f).coerceAtLeast(1f),
    )

    drawZone(project(FactoryFloor.STOCK.point()), halfW, Color(0xFF735327))
    drawZone(project(FactoryFloor.TOOLS.point()), halfW, Color(0xFF315D79))
    drawZone(project(FactoryFloor.INSPECTION.point()), halfW, Color(0xFF4B457A))
    drawZone(project(FactoryFloor.STAGING.point()), halfW, if (store.pendingCargo.isNotEmpty()) SafetyAmber else Color(0xFF65552F))
    drawZone(project(FactoryFloor.BREAK_ROOM.point()), halfW, Color(0xFF376548))
    drawZone(project(FactoryFloor.SHIPPING.point()), halfW, ElectricBlue)

    // Máquinas desenhadas por estado e tipo.
    store.state.machines.forEach { machine ->
        val input = FactoryMachineInput(
            id = machine.id, gridX = machine.gridX, gridY = machine.gridY,
            installed = machine.installed, condition = machine.condition
        )
        val at = project(FactoryFloor.bay(input).point())
        val machineFrame = frame.machines.firstOrNull { it.id == machine.id }
        val color = when (machineFrame?.state) {
            FactoryMachineState.RUNNING -> ProductionGreen
            FactoryMachineState.SETUP, FactoryMachineState.WAITING_MATERIAL -> SafetyAmber
            FactoryMachineState.BROKEN -> DangerRed
            FactoryMachineState.MAINTENANCE -> Color(0xFFE28A4B)
            FactoryMachineState.OFF -> Color(0xFF4B555B)
            else -> Color(0xFF70808A)
        }
        if (machine.id == selectedMachineId) {
            drawOval(
                ElectricBlue.copy(alpha = .32f + pulse * .24f),
                topLeft = at - Offset(halfW * 1.15f, halfW * .48f),
                size = Size(halfW * 2.30f, halfW * .96f),
                style = Stroke((halfW * .10f).coerceAtLeast(2f)),
            )
        }
        if (machineFrame?.state == FactoryMachineState.RUNNING) {
            drawCircle(
                ProductionGreen.copy(alpha = .05f + pulse * .10f),
                radius = halfW * (1.08f + pulse * .14f),
                center = at - Offset(0f, halfW * .28f),
            )
        }
        drawMachineSilhouette(at, halfW, machine.machineType, color, machineFrame?.progress ?: phase)
    }

    // Operadores em suas micro-rotinas.
    frame.workers.sortedBy { it.position.x + it.position.y }.forEachIndexed { index, worker ->
        val at = project(worker.position)
        if (worker.id == selectedWorkerId) {
            if (worker.route.size > 1) {
                worker.route.zipWithNext().forEach { (a, b) ->
                    drawLine(ElectricBlue.copy(alpha = .62f), project(a), project(b), (halfW * .07f).coerceAtLeast(2f))
                }
            }
            drawOval(
                ElectricBlue.copy(alpha = .58f),
                topLeft = at - Offset(halfW * .52f, halfW * .20f),
                size = Size(halfW * 1.04f, halfW * .40f),
                style = Stroke((halfW * .07f).coerceAtLeast(2f)),
            )
        }
        val workerScale = (halfW / 17f).coerceIn(.45f, 1.25f)
        val employee = store.state.employees.firstOrNull { it.id == worker.id }
        if (employee != null) {
            drawPlayerAvatarFigure(
                base = at + Offset(0f, halfW * .35f),
                avatar = studioEmployeeAvatar(employee),
                scale = workerScale,
                phase = (phase + index * .19f + worker.progress * .35f) % 1f,
                walking = worker.walking,
                carrying = worker.carrying,
            )
            drawWorkerActivityProp(
                base = at + Offset(0f, halfW * .35f),
                scale = workerScale,
                activity = worker.activity,
                fatigue = worker.fatigue,
                pulse = pulse,
            )
        } else {
            drawWorker(
                base = at,
                scale = workerScale,
                activity = worker.activity,
                carrying = worker.carrying,
                fatigue = worker.fatigue,
                phase = (index * .19f + worker.progress) % 1f,
            )
        }
    }

    // Empilhadeira/logística no corredor quando há carga.
    if (store.pendingCargo.isNotEmpty() || frame.depositedLots > 0) {
        val t = ((store.state.company.lastSimulationAt / 250L) % 1000L) / 1000f
        val p = FloorPoint(5f + 9f * t, 22f)
        drawForklift(project(p), halfW)
    }

    // Dono da oficina: entrega real tem prioridade; fora dela, a rota visual acompanha o lote/repreensão.
    val owner = frame.owner
    val ownerPoint = if (owner.busy) owner.position else ownerOverride ?: owner.position
    val ownerWalking = if (owner.busy) owner.walking else ownerOverrideWalking
    val ownerCarrying = if (owner.busy) owner.carrying else ownerOverrideCarrying
    val ownerAt = project(ownerPoint) + Offset(0f, halfW * .35f)
    if (!owner.busy && ownerOverride != null) {
        drawCircle(ElectricBlue.copy(alpha = .08f + pulse * .07f), halfW * .72f, ownerAt - Offset(0f, halfW * .35f))
    }
    drawPlayerAvatarFigure(
        base = ownerAt,
        avatar = store.state.profile,
        scale = (halfW / 17f).coerceIn(.45f, 1.25f),
        phase = phase,
        walking = ownerWalking,
        carrying = ownerCarrying,
    )
    if (ownerCarrying) drawOwnerCargoCrate(ownerAt, halfW)

    // Exaustores/luminárias.
    repeat(5) { i ->
        val at = project(FloorPoint(2f + i * 4f, 1f))
        drawCircle(Color(0xFF9CB0B8).copy(alpha = .28f), halfW * .22f, at)
        drawCircle(Color(0xFFFFE0A0).copy(alpha = .12f), halfW * .65f, at)
    }
}

private fun DrawScope.drawZone(at: Offset, scale: Float, color: Color) {
    drawRoundRect(
        color.copy(alpha = .58f),
        topLeft = at - Offset(scale * .65f, scale * .32f),
        size = Size(scale * 1.3f, scale * .64f),
        cornerRadius = CornerRadius(scale * .12f),
    )
}

private fun DrawScope.drawMachineSilhouette(
    at: Offset,
    scale: Float,
    type: String,
    color: Color,
    progress: Float,
) {
    val w = scale * 1.62f
    val h = scale * .88f
    val upper = type.uppercase()
    val motion = (sin(progress * 6.28318f) * .5f + .5f).coerceIn(0f, 1f)

    drawOval(
        Color.Black.copy(alpha = .35f),
        at - Offset(w * .55f, h * .03f),
        Size(w * 1.10f, h * .27f),
    )
    drawRoundRect(
        color = Color(0xFF091013),
        topLeft = at - Offset(w * .5f + 2f, h + 2f),
        size = Size(w + 4f, h + 4f),
        cornerRadius = CornerRadius(scale * .15f),
    )
    drawRoundRect(
        color = color.copy(alpha = .92f),
        topLeft = at - Offset(w * .5f, h),
        size = Size(w, h),
        cornerRadius = CornerRadius(scale * .14f),
    )

    when {
        "CNC_MACHINING_CENTER" in upper -> {
            val window = at - Offset(w * .24f, h * .76f)
            drawRoundRect(Color(0xFF101C22), window, Size(w * .48f, h * .55f), CornerRadius(scale * .06f))
            drawRoundRect(Color(0xFF2B4854).copy(alpha = .75f), window + Offset(scale * .04f, scale * .04f), Size(w * .48f - scale * .08f, h * .55f - scale * .08f), CornerRadius(scale * .05f))
            val headX = at.x + (motion - .5f) * w * .22f
            drawRect(Color(0xFFD3DADF), Offset(headX - scale * .08f, at.y - h * .68f), Size(scale * .16f, h * .34f))
            drawCircle(Color(0xFFFFD45A), scale * (.06f + motion * .03f), Offset(headX, at.y - h * .31f))
            drawStudioControlPanel(at + Offset(w * .36f, -h * .52f), scale)
        }
        "CNC_LATHE" in upper -> {
            val window = at - Offset(w * .27f, h * .72f)
            drawRoundRect(Color(0xFF14262E), window, Size(w * .54f, h * .47f), CornerRadius(scale * .06f))
            val chuck = at + Offset(-w * .18f, -h * .49f)
            drawCircle(Color(0xFFC8D2D6), scale * .20f, chuck, style = Stroke(scale * .07f))
            drawLine(Color(0xFFE5EAEC), chuck, chuck + Offset(w * .30f, 0f), scale * .07f)
            drawCircle(Color(0xFF77D8EE).copy(alpha = .28f + motion * .25f), scale * .10f, chuck)
            drawStudioControlPanel(at + Offset(w * .37f, -h * .50f), scale)
        }
        "ROBOTIC_WELDING" in upper -> {
            val shoulder = at + Offset(-w * .20f, -h * .48f)
            val elbow = at + Offset(-w * .02f, -h * (.70f - motion * .10f))
            val torch = at + Offset(w * (.18f + motion * .07f), -h * .37f)
            drawCircle(Color(0xFF263238), scale * .14f, shoulder)
            drawLine(Color(0xFFE3A22F), shoulder, elbow, scale * .13f)
            drawCircle(Color(0xFF263238), scale * .11f, elbow)
            drawLine(Color(0xFFE3A22F), elbow, torch, scale * .10f)
            drawCircle(Color(0xFFFFE77A), scale * .09f, torch)
            drawStudioSparks(torch, scale, motion)
        }
        "EDM" in upper -> {
            val tank = at - Offset(w * .26f, h * .47f)
            drawRoundRect(Color(0xFF1A333D), tank, Size(w * .52f, h * .31f), CornerRadius(scale * .05f))
            drawRect(Color(0xFF5BC5E8).copy(alpha = .28f), tank + Offset(scale * .04f, scale * .04f), Size(w * .52f - scale * .08f, h * .12f))
            val electrodeX = at.x + (motion - .5f) * w * .14f
            drawLine(Color(0xFFD4DBDF), Offset(electrodeX, at.y - h * .78f), Offset(electrodeX, at.y - h * .35f), scale * .08f)
            drawCircle(Color(0xFF7BE6FF).copy(alpha = .45f + motion * .45f), scale * .08f, Offset(electrodeX, at.y - h * .31f))
        }
        "LASER" in upper || "PLASMA" in upper -> {
            drawLine(Color(0xFF17252B), at - Offset(w * .34f, h * .63f), at + Offset(w * .34f, -h * .63f), scale * .10f)
            val headX = at.x - w * .28f + w * .56f * motion
            drawLine(Color(0xFFD8E0E3), Offset(headX, at.y - h * .71f), Offset(headX, at.y - h * .31f), scale * .07f)
            val cut = Offset(headX, at.y - h * .26f)
            drawCircle(if ("LASER" in upper) Color(0xFF77DFFF) else Color(0xFFFFC04D), scale * .09f, cut)
            drawStudioSparks(cut, scale, motion)
        }
        "CNC_GRINDER" in upper -> {
            val window = at - Offset(w * .24f, h * .72f)
            drawRoundRect(Color(0xFF14242A), window, Size(w * .48f, h * .45f), CornerRadius(scale * .05f))
            drawCircle(Color(0xFFD6DDE0), scale * .21f, at + Offset(-w * .12f, -h * .50f), style = Stroke(scale * .07f))
            drawCircle(Color(0xFF9BE4FF).copy(alpha = .16f + motion * .14f), scale * .28f, at + Offset(-w * .12f, -h * .50f))
            drawStudioControlPanel(at + Offset(w * .36f, -h * .50f), scale)
        }
        "CNC_DRILL" in upper -> {
            val window = at - Offset(w * .22f, h * .73f)
            drawRoundRect(Color(0xFF14242A), window, Size(w * .44f, h * .48f), CornerRadius(scale * .05f))
            drawLine(Color(0xFFD9E0E3), at - Offset(0f, h * .68f), at - Offset(0f, h * (.30f + motion * .06f)), scale * .10f)
            drawStudioControlPanel(at + Offset(w * .36f, -h * .50f), scale)
        }
        "LATHE" in upper -> {
            val chuck = at - Offset(w * .22f, h * .56f)
            drawCircle(Color(0xFF15232A), scale * .23f, chuck)
            drawCircle(Color(0xFFD7E0E4), scale * .17f, chuck, style = Stroke(scale * .055f))
            drawLine(Color(0xFFD7E0E4), at - Offset(w * .02f, h * .57f), at + Offset(w * .27f, -h * .57f), scale * .09f)
            drawCircle(Color(0xFFFFD56A).copy(alpha = .18f + motion * .18f), scale * .07f, at + Offset(w * .03f, -h * .57f))
        }
        "MILL" in upper || "MACHINING_CENTER" in upper -> {
            drawRect(Color(0xFF132027), at - Offset(w * .22f, h * .75f), Size(w * .44f, h * .53f))
            drawLine(SafetyAmber.copy(alpha = .7f), at - Offset(w * .34f, h * .18f), at + Offset(w * .34f, -h * .18f), scale * .07f)
            val cutter = at + Offset((motion - .5f) * w * .16f, -h * .48f)
            drawCircle(Color(0xFFC9D3D7), scale * .08f, cutter)
        }
        "GRIND" in upper -> {
            drawCircle(Color(0xFFD6DDE0), scale * .23f, at - Offset(w * .19f, h * .55f), style = Stroke(scale * .09f))
            drawCircle(Color(0xFFD6DDE0), scale * .23f, at + Offset(w * .19f, -h * .55f), style = Stroke(scale * .09f))
            drawStudioSparks(at + Offset(w * .05f, -h * .40f), scale, motion)
        }
        "WELD" in upper -> {
            val spark = scale * (.13f + motion * .08f)
            drawLine(Color(0xFFDBE4E8), at - Offset(w * .2f, h * .55f), at + Offset(w * .2f, -h * .32f), scale * .08f)
            val weldAt = at + Offset(w * .18f, -h * .31f)
            drawCircle(Color(0xFFFFD85A), spark, weldAt)
            drawStudioSparks(weldAt, scale, motion)
        }
        "DRILL" in upper -> {
            drawLine(Color(0xFFD9E0E3), at - Offset(0f, h * .72f), at - Offset(0f, h * (.18f + motion * .07f)), scale * .11f)
            drawRect(Color(0xFF16242B), at - Offset(w * .23f, h * .26f), Size(w * .46f, h * .12f))
        }
        else -> {
            drawRect(Color(0xFF142128), at - Offset(w * .24f, h * .68f), Size(w * .48f, h * .42f))
        }
    }
}

private fun DrawScope.drawStudioControlPanel(at: Offset, scale: Float) {
    drawRoundRect(Color(0xFF11191D), at - Offset(scale * .10f, scale * .18f), Size(scale * .20f, scale * .36f), CornerRadius(scale * .035f))
    drawRect(Color(0xFF67C5E2), at - Offset(scale * .065f, scale * .125f), Size(scale * .13f, scale * .10f))
    drawCircle(Color(0xFF70D388), scale * .025f, at + Offset(-scale * .035f, scale * .08f))
    drawCircle(Color(0xFFE06158), scale * .025f, at + Offset(scale * .035f, scale * .08f))
}

private fun DrawScope.drawStudioSparks(at: Offset, scale: Float, phase: Float) {
    val len = scale * (.16f + phase * .14f)
    val sparkColor = Color(0xFFFFD85A).copy(alpha = .58f + phase * .34f)
    drawLine(sparkColor, at, at + Offset(len, -len * .55f), (scale * .025f).coerceAtLeast(1f))
    drawLine(sparkColor, at, at + Offset(-len * .72f, -len * .78f), (scale * .022f).coerceAtLeast(1f))
    drawLine(sparkColor, at, at + Offset(len * .48f, len * .70f), (scale * .020f).coerceAtLeast(1f))
}

private fun DrawScope.drawWorker(
    base: Offset,
    scale: Float,
    activity: WorkerActivity,
    carrying: Boolean,
    fatigue: Int,
    phase: Float,
) {
    val cycle = phase * 6.28318f
    val bob = if (activity == WorkerActivity.WALKING || activity == WorkerActivity.CARRYING_PART) {
        kotlin.math.abs(sin(cycle)) * 1.5f * scale
    } else 0f
    val uniform = when (activity) {
        WorkerActivity.PHONE -> Color(0xFFE76E6E)
        WorkerActivity.BREAK -> Color(0xFF62B889)
        WorkerActivity.WORKING -> Color(0xFF3974A8)
        WorkerActivity.FETCHING_MATERIAL, WorkerActivity.FETCHING_TOOLS, WorkerActivity.CARRYING_PART -> Color(0xFFB67A32)
        else -> Color(0xFF496473)
    }
    val x = base.x
    val y = base.y - bob
    drawOval(Color.Black.copy(alpha = .3f), Offset(x - 7f * scale, y - 1f), Size(14f * scale, 4f * scale))
    drawLine(uniform, Offset(x - 3f * scale, y - 18f * scale), Offset(x - 4f * scale, y - 3f * scale), 4f * scale)
    drawLine(uniform, Offset(x + 3f * scale, y - 18f * scale), Offset(x + 4f * scale, y - 3f * scale), 4f * scale)
    drawRoundRect(uniform, Offset(x - 7f * scale, y - 35f * scale), Size(14f * scale, 18f * scale), CornerRadius(3f * scale))
    drawCircle(Color(0xFFD2A078), 5f * scale, Offset(x, y - 43f * scale))
    drawArc(SafetyAmber, 180f, 180f, true, Offset(x - 6f * scale, y - 50f * scale), Size(12f * scale, 7f * scale))

    if (carrying) {
        drawRoundRect(Color(0xFFB77B39), Offset(x + 5f * scale, y - 30f * scale), Size(10f * scale, 7f * scale), CornerRadius(1.5f * scale))
    }
    if (activity == WorkerActivity.PHONE) {
        drawRect(Color(0xFF74B9E7), Offset(x + 6f * scale, y - 34f * scale), Size(3f * scale, 5f * scale))
    }
    if (fatigue >= 75) {
        drawCircle(DangerRed, 2.4f * scale, Offset(x + 7f * scale, y - 47f * scale))
    }
}

private fun DrawScope.drawForklift(at: Offset, scale: Float) {
    drawRoundRect(Color(0xFFE09B25), at - Offset(scale * .42f, scale * .35f), Size(scale * .72f, scale * .42f), CornerRadius(scale * .08f))
    drawCircle(Color(0xFF12181B), scale * .13f, at - Offset(scale * .25f, 0f))
    drawCircle(Color(0xFF12181B), scale * .13f, at + Offset(scale * .18f, 0f))
    drawLine(Color(0xFFE09B25), at + Offset(scale * .30f, -scale * .28f), at + Offset(scale * .52f, -scale * .28f), scale * .06f)
}


private val studioFemaleFirstNames = setOf(
    "Luciana", "Patrícia", "Camila", "Fernanda", "Amanda", "Juliana", "Mariana",
    "Beatriz", "Renata", "Larissa", "Daniela", "Aline", "Carolina", "Bianca",
    "Vanessa", "Jéssica", "Natália", "Priscila", "Letícia", "Isabela",
)

/** Mesma regra determinística do Android: o funcionário mantém a identidade visual entre sessões. */
private fun studioEmployeeAvatar(employee: EmployeeSave): PlayerProfileSave {
    val legendaryStyle = when (employee.legendaryCode) {
        "tatu_banhado" -> "TATUZAO"
        "kendao" -> "KENDAO_KIMONO"
        "nikao_narizudo" -> "PINOQUIO"
        "magrao" -> "MAGRAO"
        "nelsinho_treme_treme" -> "TREME_TREME"
        "chupa_engole" -> "BEBADO"
        else -> null
    }
    val firstName = employee.name.substringBefore(' ')
    val female = firstName in studioFemaleFirstNames
    val rawSeed = (employee.id.ifBlank { employee.name }).hashCode().toLong()
    val seed = if (rawSeed < 0L) -rawSeed else rawSeed
    val variant = (seed % 100L).toInt()
    val style = legendaryStyle ?: when {
        female && variant < 18 -> "PRINCESA"
        !female && variant < 7 -> "TATUZAO"
        !female && variant in 7..13 -> "MAGRAO"
        variant in 14..17 -> "TREME_TREME"
        else -> "WORKSHOP"
    }
    val hairStyle = when {
        style == "PRINCESA" -> "LONG"
        female && seed % 3L == 0L -> "PONYTAIL"
        female && seed % 3L == 1L -> "LONG"
        female -> "CURLY"
        seed % 5L == 0L -> "BUZZ"
        else -> "SHORT"
    }
    val hairColor = when ((seed % 4L).toInt()) {
        0 -> "DARK"
        1 -> "BROWN"
        2 -> "BLONDE"
        else -> "GRAY"
    }
    return PlayerProfileSave(
        name = employee.name,
        gender = if (female) "FEMALE" else "MALE",
        skinStyle = style,
        bodyType = when (style) {
            "TATUZAO" -> "STRONG"
            "MAGRAO" -> "SLIM"
            else -> "STANDARD"
        },
        skinTone = "MEDIUM",
        hairStyle = hairStyle,
        hairColor = hairColor,
        uniformColor = if (female) "BLUE" else "NAVY",
        helmetColor = if (style == "PRINCESA") "NONE" else "YELLOW",
        accessory = if (employee.legendaryCode == "gumersvaldo") "GLASSES" else "NONE",
        onboardingComplete = true,
    )
}

private fun DrawScope.drawWorkerActivityProp(
    base: Offset,
    scale: Float,
    activity: WorkerActivity,
    fatigue: Int,
    pulse: Float,
) {
    if (activity == WorkerActivity.PHONE) {
        val phone = base + Offset(10f * scale, -34f * scale)
        drawRoundRect(Color(0xFF101619), phone + Offset(-3.5f * scale, -5.5f * scale), Size(7f * scale, 11f * scale), CornerRadius(1.2f * scale))
        drawRoundRect(Color(0xFF66C7EE), phone + Offset(-2.4f * scale, -4.2f * scale), Size(4.8f * scale, 7.2f * scale), CornerRadius(.8f * scale))
        drawCircle(DangerRed.copy(alpha = .22f + pulse * .24f), 14f * scale, phone)
    }
    if (activity == WorkerActivity.INSPECTING) {
        val c = base + Offset(10f * scale, -31f * scale)
        drawRoundRect(Color(0xFFE8E4D5), c + Offset(-5f * scale, -7f * scale), Size(10f * scale, 14f * scale), CornerRadius(1.5f * scale))
        repeat(3) { i ->
            drawLine(Color(0xFF889397), c + Offset(-3f * scale, (-3f + i * 3f) * scale), c + Offset(3f * scale, (-3f + i * 3f) * scale), .8f * scale)
        }
    }
    if (activity == WorkerActivity.BREAK) {
        val cup = base + Offset(10f * scale, -28f * scale)
        drawRoundRect(Color(0xFFDDA65A), cup + Offset(-4f * scale, -4f * scale), Size(8f * scale, 7f * scale), CornerRadius(1.5f * scale))
        drawCircle(Color(0xFFDDA65A), 3f * scale, cup + Offset(5f * scale, -1f * scale), style = Stroke(1.2f * scale))
    }
    if (fatigue >= 75) {
        drawCircle(DangerRed, 2.6f * scale, base + Offset(10f * scale, -50f * scale))
    }
}

private fun studioBaseTile(width: Float, height: Float): Float = min(width / 22f, height / 17f)

private fun studioProject(p: FloorPoint, width: Float, height: Float, zoom: Float, pan: Offset): Offset {
    val halfW = studioBaseTile(width, height) * zoom
    val halfH = halfW * .46f
    val origin = Offset(width * .50f + pan.x, 28f + pan.y)
    return Offset(
        origin.x + (p.x - p.y) * halfW * .52f,
        origin.y + (p.x + p.y) * halfH * .52f,
    )
}

private fun studioDistance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun studioClampPan(pan: Offset, zoom: Float): Offset {
    val limitX = 420f * zoom
    val limitY = 360f * zoom
    return Offset(pan.x.coerceIn(-limitX, limitX), pan.y.coerceIn(-limitY, limitY))
}

private fun studioRoutePoint(route: List<FloorPoint>, progress: Float): FloorPoint {
    if (route.isEmpty()) return FactoryFloor.ENTRY.point()
    if (route.size == 1) return route.first()
    val scaled = progress.coerceIn(0f, 1f) * (route.size - 1)
    val index = scaled.toInt().coerceIn(0, route.lastIndex)
    val next = (index + 1).coerceAtMost(route.lastIndex)
    val local = scaled - index
    val a = route[index]
    val b = route[next]
    return FloorPoint(a.x + (b.x - a.x) * local, a.y + (b.y - a.y) * local)
}

private fun studioStage(batch: OwnerWorkBatchSave?): ProductionStage? =
    batch?.stage?.let { runCatching { ProductionStage.valueOf(it) }.getOrNull() }

private fun studioBatchCarrying(batch: OwnerWorkBatchSave?): Boolean = studioStage(batch) in setOf(
    ProductionStage.MACHINED,
    ProductionStage.WAITING_QC,
    ProductionStage.APPROVED,
    ProductionStage.PACKING,
    ProductionStage.READY_TO_SHIP,
)

private fun studioBatchTargetCell(
    batch: OwnerWorkBatchSave?,
    machines: List<MachineSave>,
    floor: FactoryFloor,
): FloorCell = when (studioStage(batch)) {
    ProductionStage.MACHINED, ProductionStage.REWORK, ProductionStage.MACHINING -> {
        val machine = machines.firstOrNull { it.id == batch?.machineId }
        if (machine != null) {
            floor.nearestWalkable(
                FactoryFloor.bay(
                    FactoryMachineInput(machine.id, machine.gridX, machine.gridY, machine.installed, machine.condition)
                ).point()
            )
        } else FactoryFloor.ENTRY
    }
    ProductionStage.WAITING_QC, ProductionStage.QC, ProductionStage.APPROVED -> FactoryFloor.INSPECTION
    ProductionStage.PACKING, ProductionStage.READY_TO_SHIP -> FactoryFloor.STAGING
    ProductionStage.SHIPPED -> FactoryFloor.SHIPPING
    ProductionStage.RAW, ProductionStage.WAITING_MACHINE -> FactoryFloor.STOCK
    ProductionStage.SCRAP -> FactoryFloor.STAGING
    null -> FactoryFloor.ENTRY
}

private fun DrawScope.drawOwnerCargoCrate(ownerAt: Offset, halfW: Float) {
    val s = (halfW / 18f).coerceIn(.45f, 1.25f)
    val base = ownerAt + Offset(-18f * s, -8f * s)
    drawRoundRect(
        Color(0xFFB77B39),
        topLeft = base,
        size = Size(18f * s, 11f * s),
        cornerRadius = CornerRadius(2f * s),
    )
    drawLine(Color(0xFFE2B36B), base + Offset(9f * s, 0f), base + Offset(9f * s, 11f * s), 1.2f * s)
}

@Composable
private fun StudioSceneHud(
    modifier: Modifier,
    frame: FactoryFrame,
    batch: OwnerWorkBatchSave?,
) {
    val running = frame.machines.count { it.state == FactoryMachineState.RUNNING }
    val waiting = frame.machines.count { it.state == FactoryMachineState.IDLE || it.state == FactoryMachineState.WAITING_MATERIAL }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Steel950.copy(alpha = .86f),
        border = BorderStroke(1.dp, Steel700.copy(alpha = .8f)),
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Text("● $running operando  •  $waiting aguardando", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            batch?.let {
                val stage = studioStage(it)
                Text("DONO • ${stage?.label ?: it.stage}", style = MaterialTheme.typography.labelSmall, color = SafetyAmber, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun StudioOwnerActionStrip(store: GameStore, batch: OwnerWorkBatchSave?) {
    Spacer(Modifier.height(8.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Steel950.copy(alpha = .68f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Steel700.copy(alpha = .7f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val stage = studioStage(batch)
            if (batch == null) {
                Text("Dono disponível", fontWeight = FontWeight.Black)
                Text("Toque numa máquina para assumir um lote. O minigame é opcional; funcionários seguem automáticos.", style = MaterialTheme.typography.bodySmall, color = Steel400)
            } else {
                Text("Lote do dono • ${stage?.label ?: batch.stage}", fontWeight = FontWeight.Black)
                Text("${batch.producedQuantity} pç • Q${batch.quality} • precisão ${batch.precision}%", style = MaterialTheme.typography.bodySmall)
                Text(
                    when (stage) {
                        ProductionStage.MACHINED -> "Leve o dono até Q (Qualidade)."
                        ProductionStage.WAITING_QC, ProductionStage.QC -> "Toque em Q e faça a medição dimensional."
                        ProductionStage.REWORK -> "Toque na máquina do lote e execute o retrabalho."
                        ProductionStage.APPROVED -> "Toque em P para embalar."
                        ProductionStage.READY_TO_SHIP -> "Toque em E para expedir."
                        else -> "Acompanhe a rota do lote no chão de fábrica."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SafetyAmber,
                )
            }
        }
    }
}

@Composable
private fun StudioSelectedWorkerCard(store: GameStore, employeeId: String) {
    val employee = store.state.employees.firstOrNull { it.id == employeeId } ?: return
    val worker = store.factoryFrame.workers.firstOrNull { it.id == employeeId }
    val machine = store.state.machines.firstOrNull { it.id == employee.assignedMachineId }
    val machineName = machine?.let { MachineCatalog.byType(it.machineType)?.name ?: it.machineType } ?: "Sem máquina"
    Spacer(Modifier.height(8.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Steel950.copy(alpha = .72f),
        border = BorderStroke(1.dp, if (worker?.activity == WorkerActivity.PHONE) DangerRed.copy(alpha = .65f) else Steel700),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(employee.name, fontWeight = FontWeight.Black)
                    Text("${employee.specialty} • Nv.${employee.skillLevel} • ${employee.trait}", style = MaterialTheme.typography.bodySmall, color = Steel400)
                }
                Text(worker?.activity?.label ?: "Fora da cena", style = MaterialTheme.typography.labelSmall, color = if (worker?.activity == WorkerActivity.PHONE) DangerRed else ProductionGreen)
            }
            Text("Posto: $machineName • fadiga ${(employee.fatigue * 100).roundToInt().coerceIn(0, 100)}%", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = { store.restEmployee(employee.id) }, modifier = Modifier.weight(1f)) { Text("Enviar à Copa") }
                OutlinedButton(onClick = { store.assignEmployeeNext(employee.id) }, modifier = Modifier.weight(1f)) { Text("Trocar posto") }
            }
        }
    }
}

@Composable
private fun StudioMachineManagementDialog(
    store: GameStore,
    machine: MachineSave,
    onDismiss: () -> Unit,
    onOperate: () -> Unit,
    onRework: () -> Unit,
    onSell: () -> Unit,
) {
    val def = MachineCatalog.byType(machine.machineType)
    val operator = store.state.employees.firstOrNull { it.assignedMachineId == machine.id }
    val production = store.production.machineProduction.firstOrNull { it.machineId == machine.id }
    val activeBatch = store.state.career.activeBatch
    val reworkHere = studioStage(activeBatch) == ProductionStage.REWORK && activeBatch?.machineId == machine.id
    val candidates = store.state.employees.sortedWith(
        compareByDescending<EmployeeSave> { it.specialty == def?.specialty?.name }
            .thenByDescending { it.skillLevel }
    )
    val conditionFactor = .5 + machine.condition.coerceIn(0, 1000) / 2000.0
    val resale = ((def?.priceCents ?: 0L) * .60 * conditionFactor).toLong()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(def?.name ?: machine.machineType, fontWeight = FontWeight.Black)
                Text(
                    if (production?.isOperating == true) "● PRODUZINDO" else "● EM ESPERA",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (production?.isOperating == true) ProductionGreen else SafetyAmber,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioMetricBox("CONSERVAÇÃO", "${machine.condition / 10}%", Modifier.weight(1f))
                    StudioMetricBox("PÇ / 10 MIN", if (production?.isOperating == true) studioOneDecimal(production.unitsPer10Minutes) else "Parada", Modifier.weight(1f))
                }
                Text("Nível ${machine.level} • Especialidade: ${def?.specialty?.name ?: "-"}", style = MaterialTheme.typography.bodySmall)

                if (reworkHere) {
                    Button(onClick = onRework, modifier = Modifier.fillMaxWidth()) { Text("FAZER RETRABALHO", fontWeight = FontWeight.Black) }
                } else {
                    Button(
                        onClick = onOperate,
                        enabled = activeBatch == null && store.state.contracts.any { it.status == "ACTIVE" && it.completedQuantity < it.quantity },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("OPERAR EU MESMO", fontWeight = FontWeight.Black) }
                }
                Text("Só o dono executa minigames. Funcionários continuam automáticos.", style = MaterialTheme.typography.bodySmall, color = ElectricBlue)

                HorizontalDivider()
                Text("Operador atual: ${operator?.name ?: "Nenhum operador"}", fontWeight = FontWeight.Bold)
                if (operator != null) {
                    TextButton(onClick = { store.clearMachineOperator(machine.id) }) { Text("Remover operador") }
                }
                Text("Trocar / atribuir operador", fontWeight = FontWeight.Bold)
                if (candidates.isEmpty()) {
                    Text("Contrate funcionários para colocar esta máquina em produção.", style = MaterialTheme.typography.bodySmall)
                } else {
                    candidates.forEach { employee ->
                        val recommended = employee.specialty == def?.specialty?.name
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { store.assignEmployeeToMachine(employee.id, machine.id) },
                            shape = RoundedCornerShape(11.dp),
                            color = if (operator?.id == employee.id) ElectricBlue.copy(alpha = .12f) else Steel900,
                            border = if (recommended) BorderStroke(1.dp, ElectricBlue.copy(alpha = .38f)) else null,
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(employee.name, fontWeight = FontWeight.Bold)
                                Text("${employee.specialty} • Nv.${employee.skillLevel}${if (recommended) " • recomendado" else ""}", style = MaterialTheme.typography.bodySmall, color = Steel400)
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = { store.moveMachineNext(machine.id) }, modifier = Modifier.weight(1f)) { Text("Mover baia") }
                    Button(onClick = { store.repairMachine(machine.id) }, enabled = machine.condition < 1000, modifier = Modifier.weight(1f)) { Text("Manutenção") }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .26f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .28f)),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Revender máquina", fontWeight = FontWeight.Black)
                        Text("Valor atual: ${Formatters.money(resale)} • libera operador e espaço.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onSell, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Vender máquina") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun StudioMetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = Steel950.copy(alpha = .75f)) {
        Column(Modifier.padding(9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Steel400)
            Text(value, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun StudioOwnerOperationDialog(store: GameStore, machine: MachineSave, onDismiss: () -> Unit) {
    val contracts = store.state.contracts.filter { it.status == "ACTIVE" && it.completedQuantity < it.quantity }
    var selectedId by remember(contracts) { mutableStateOf(contracts.firstOrNull()?.id) }
    val selected = contracts.firstOrNull { it.id == selectedId }
    val mastery = MachineMastery(machine.machineType, store.state.career.masteryXp[machine.machineType] ?: 0)
    val definition = MachineCatalog.byType(machine.machineType)
    var launchManual by remember { mutableStateOf(false) }

    if (launchManual && selected != null) {
        StudioMachineMinigameDialog(
            store = store,
            machine = machine,
            contractId = selected.id,
            rework = false,
            onDismiss = { launchManual = false },
            onFinished = { onDismiss() },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assumir ${definition?.name ?: "máquina"}", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Somente o dono faz minigame. Funcionários continuam automáticos; o ciclo manual acelera a produção sem ser obrigatório.", style = MaterialTheme.typography.bodySmall)
                Surface(color = ElectricBlue.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text("Proficiência Nv.${mastery.level}", fontWeight = FontWeight.Bold)
                        Text("+${mastery.quantityBonusPct}% potencial • +${mastery.qualityBonus} qualidade", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (contracts.isEmpty()) {
                    Text("Aceite um contrato antes de iniciar um lote.", color = MaterialTheme.colorScheme.error)
                } else {
                    contracts.take(6).forEach { contract ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedId = contract.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedId == contract.id, onClick = { selectedId = contract.id })
                            Column {
                                Text(contract.clientName, fontWeight = FontWeight.Bold)
                                Text("${contract.completedQuantity}/${contract.quantity} • qualidade ${contract.requiredQuality}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text("OPERAR EU MESMO", fontWeight = FontWeight.Black)
                Text("Desafio específico da máquina; desempenho vira peças, qualidade, XP e proficiência.", style = MaterialTheme.typography.bodySmall)
                Text("CICLO ASSISTIDO", fontWeight = FontWeight.Black)
                Text("Sem minigame e sem bônus; o lote ainda passa fisicamente por Q → P → E.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { launchManual = true }, enabled = selected != null && store.state.career.activeBatch == null) { Text("OPERAR EU MESMO") }
        },
        dismissButton = {
            Row {
                OutlinedButton(
                    onClick = {
                        val contract = selected ?: return@OutlinedButton
                        store.operateMachine(machine.id, contract.id, MinigameResult(.46f, .54f, .34f, .52f), manual = false)
                        onDismiss()
                    },
                    enabled = selected != null && store.state.career.activeBatch == null,
                ) { Text("Ciclo assistido") }
                Spacer(Modifier.width(5.dp))
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        },
    )
}

private data class StudioProcessChoice(val prompt: String, val labels: List<String>, val correct: Int)

private fun studioProcessChoice(kind: MinigameKind, difficulty: Int): StudioProcessChoice = when (kind) {
    MinigameKind.LATHE -> StudioProcessChoice("Escolha a ferramenta para o passe final", listOf("HSS", "Pastilha CNMG", "Pastilha acabamento"), if (difficulty >= 4) 2 else 1)
    MinigameKind.MILLING -> StudioProcessChoice("Escolha a estratégia de trajetória", listOf("Contorno direto", "Desbaste adaptativo", "Passe aleatório"), 1)
    MinigameKind.DRILLING -> StudioProcessChoice("Escolha a ferramenta de furação", listOf("Broca HSS", "Broca metal duro", "Escareador"), if (difficulty >= 3) 1 else 0)
    MinigameKind.GRINDING -> StudioProcessChoice("Escolha o passe de acabamento", listOf("0,10 mm", "0,03 mm", "0,30 mm"), 1)
    MinigameKind.WELDING -> StudioProcessChoice("Escolha o modo de transferência", listOf("Curto-circuito", "Spray", "Energia máxima"), if (difficulty >= 4) 1 else 0)
    MinigameKind.EDM -> StudioProcessChoice("Escolha o regime da descarga", listOf("Desbaste", "Semiacabamento", "Acabamento"), if (difficulty >= 4) 2 else 1)
    MinigameKind.LASER -> StudioProcessChoice("Escolha o gás de assistência", listOf("N₂", "O₂", "Sem gás"), if (difficulty >= 4) 0 else 1)
    MinigameKind.PLASMA -> StudioProcessChoice("Escolha o processo de corte", listOf("Ar", "N₂", "Corrente máxima"), if (difficulty >= 4) 1 else 0)
    else -> StudioProcessChoice("Escolha a estratégia do processo", listOf("Conservador", "Janela recomendada", "Agressivo"), 1)
}

@Composable
private fun StudioMachineMinigameDialog(
    store: GameStore,
    machine: MachineSave,
    contractId: String,
    rework: Boolean,
    onDismiss: () -> Unit,
    onFinished: () -> Unit = onDismiss,
) {
    val contract = store.state.contracts.firstOrNull { it.id == contractId } ?: return
    val blueprint = remember(machine.machineType, contract.difficulty) { MachineMinigameCatalog.blueprint(machine.machineType, contract.difficulty) }
    val choiceData = remember(blueprint.kind, contract.difficulty) { studioProcessChoice(blueprint.kind, contract.difficulty) }
    val expectedSequence = remember { listOf("Facear", "Furar", "Contornar") }
    val availableSequence = remember(contract.id) { listOf("Contornar", "Facear", "Furar") }
    var parameterA by remember(machine.id, contract.id) { mutableFloatStateOf(50f) }
    var parameterB by remember(machine.id, contract.id) { mutableFloatStateOf(50f) }
    var selectedChoice by remember(machine.id, contract.id) { mutableIntStateOf(-1) }
    val sequence = remember(machine.id, contract.id) { mutableStateListOf<String>() }

    val parameterScore = studioScoreParams(parameterA, blueprint.targetA, blueprint.toleranceA, parameterB, blueprint.targetB, blueprint.toleranceB)
    val processErrors = if (blueprint.kind == MinigameKind.CNC) {
        expectedSequence.indices.count { sequence.getOrNull(it) != expectedSequence[it] }
    } else if (selectedChoice == choiceData.correct) 0 else 1
    val mastery = MachineMastery(machine.machineType, store.state.career.masteryXp[machine.machineType] ?: 0)
    val skillAssist = (if ("preparador" in store.state.career.unlockedSkills) .04f else 0f) +
        (if (blueprint.kind == MinigameKind.CNC && "operador_cnc" in store.state.career.unlockedSkills) .05f else 0f) +
        ((mastery.level - 1) * .006f).coerceAtMost(.08f)
    val score = (parameterScore - processErrors * .13f + skillAssist).coerceIn(0f, 1f)
    val precision = (1f - abs(parameterA - blueprint.targetA) / (blueprint.toleranceA * 2f)).coerceIn(0f, 1f)
    val speed = (1f - abs(parameterB - blueprint.targetB) / (blueprint.toleranceB * 2f)).coerceIn(0f, 1f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(if (rework) "Retrabalho • ${blueprint.title}" else blueprint.title, fontWeight = FontWeight.Black)
                Text("Contrato ${contract.clientName} • exigência Q${contract.requiredQuality}", style = MaterialTheme.typography.labelSmall)
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(blueprint.goal)
                StudioMachineProcessHint(blueprint.kind)
                if (blueprint.kind == MinigameKind.CNC) {
                    Text("1. Monte a OP10: Facear → Furar → Contornar", fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        availableSequence.forEach { operation ->
                            FilterChip(selected = operation in sequence, onClick = { if (operation !in sequence) sequence += operation }, label = { Text(operation) }, enabled = operation !in sequence)
                        }
                        TextButton(onClick = { sequence.clear() }) { Text("Limpar") }
                    }
                    Text("Sequência: ${if (sequence.isEmpty()) "—" else sequence.joinToString(" → ")}", style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("1. ${choiceData.prompt}", fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        choiceData.labels.forEachIndexed { index, label ->
                            FilterChip(selected = selectedChoice == index, onClick = { selectedChoice = index }, label = { Text(label) })
                        }
                    }
                }
                Text("2. Ajuste ${blueprint.parameterA}", fontWeight = FontWeight.Bold)
                StudioProcessSlider(parameterA, { parameterA = it }, blueprint.targetA, blueprint.toleranceA)
                Text("3. Ajuste ${blueprint.parameterB}", fontWeight = FontWeight.Bold)
                StudioProcessSlider(parameterB, { parameterB = it }, blueprint.targetB, blueprint.toleranceB)
                Surface(
                    color = when {
                        score >= .90f -> Color(0xFF173B2C)
                        score >= .70f -> ElectricBlue.copy(alpha = .14f)
                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .7f)
                    },
                    shape = RoundedCornerShape(11.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text("Eficiência estimada ${(score * 100).roundToInt()}%", fontWeight = FontWeight.Black)
                        Text("Precisão ${(precision * 100).roundToInt()}% • ritmo ${(speed * 100).roundToInt()}% • erros $processErrors", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val result = MinigameResult(score, precision, speed, (score * .85f + precision * .15f).coerceIn(0f, 1f), processErrors)
                if (rework) store.reworkOwnerBatch(result) else store.operateMachine(machine.id, contract.id, result, manual = true)
                onFinished()
            }) { Text(if (rework) "CONCLUIR RETRABALHO" else "CONCLUIR OPERAÇÃO") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Sair") } },
    )
}

@Composable
private fun StudioMachineProcessHint(kind: MinigameKind) {
    val (icon, title, detail) = when (kind) {
        MinigameKind.LATHE -> Triple("🌀", "Torneamento", "Controle corte, avanço e ferramenta sem ultrapassar a medida.")
        MinigameKind.MILLING -> Triple("🧭", "Fresagem", "Escolha uma estratégia de passe eficiente antes de ajustar o corte.")
        MinigameKind.DRILLING -> Triple("🎯", "Furação", "Ferramenta correta + profundidade correta evitam peça perdida.")
        MinigameKind.GRINDING -> Triple("📐", "Retífica", "Passe pequeno e controle fino: aqui centésimos importam.")
        MinigameKind.CNC -> Triple("⌨️", "Programação CNC", "A ordem das operações importa tanto quanto os parâmetros.")
        MinigameKind.WELDING -> Triple("⚡", "Soldagem", "Equilibre energia e velocidade para evitar falta de fusão e empeno.")
        MinigameKind.EDM -> Triple("✨", "Eletroerosão", "Gap e descarga precisam permanecer estáveis para manter precisão.")
        MinigameKind.LASER -> Triple("🔦", "Laser", "Foco, gás e velocidade definem rebarba e qualidade do corte.")
        MinigameKind.PLASMA -> Triple("🔥", "Plasma", "Corrente e avanço controlam largura do corte e acabamento.")
        MinigameKind.QUALITY -> Triple("🔎", "Metrologia", "Leia a dimensão e decida conforme a tolerância do desenho.")
    }
    Surface(color = Steel950.copy(alpha = .72f), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon)
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = Steel400)
            }
        }
    }
}

@Composable
private fun StudioProcessSlider(value: Float, onValue: (Float) -> Unit, target: Float, tolerance: Float) {
    Column {
        Slider(value = value, onValueChange = onValue, valueRange = 0f..100f)
        Text(
            "Atual ${value.roundToInt()} • janela ${(target - tolerance).roundToInt()}–${(target + tolerance).roundToInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = if (abs(value - target) <= tolerance) ProductionGreen else Steel400,
        )
    }
}

private fun studioScoreParams(a: Float, ta: Float, tolA: Float, b: Float, tb: Float, tolB: Float): Float {
    val da = abs(a - ta) / (tolA * 2f).coerceAtLeast(1f)
    val db = abs(b - tb) / (tolB * 2f).coerceAtLeast(1f)
    return (1f - (da + db) * .5f).coerceIn(0f, 1f)
}

@Composable
private fun StudioQualityInspectionDialog(store: GameStore, onDismiss: () -> Unit) {
    val batch = store.state.career.activeBatch ?: return
    val contract = store.state.contracts.firstOrNull { it.id == batch.contractId } ?: return
    val delta = (batch.quality - contract.requiredQuality).coerceIn(-20, 20)
    val measuredMilli = (25_000 + delta * 12 / 10)
    val measured = "${measuredMilli / 1000},${(measuredMilli % 1000).toString().padStart(3, '0')} mm"
    val eye = "olho_treinado" in store.state.career.unlockedSkills

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Controle de Qualidade", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Leia a medida e decida. Aprovar lote fora do requisito não burla o sistema: ele volta para retrabalho.")
                Surface(shape = RoundedCornerShape(12.dp), color = Steel950.copy(alpha = .78f)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Desenho: Ø25,000 • tolerância de processo", fontWeight = FontWeight.Black)
                        Text(if (contract.difficulty >= 3) "Instrumento: micrômetro" else "Instrumento: paquímetro")
                        Text("Medição $measured", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    }
                }
                Text("Qualidade ${batch.quality}/100 • exige ${contract.requiredQuality}/100")
                if (eye) {
                    Text(if (batch.quality >= contract.requiredQuality) "👁 Tendência conforme" else "👁 Desvio detectado; retrabalho recomendado", color = ElectricBlue, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(onClick = { store.inspectOwnerBatch(true); onDismiss() }) { Text("APROVAR") }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = { store.inspectOwnerBatch(false); onDismiss() }) { Text("RETRABALHAR") }
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        },
    )
}

@Composable
private fun StudioConfirmMachineSale(machine: MachineSave, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val def = MachineCatalog.byType(machine.machineType)
    val conditionFactor = .5 + machine.condition.coerceIn(0, 1000) / 2000.0
    val resale = ((def?.priceCents ?: 0L) * .60 * conditionFactor).toLong()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vender ${def?.name ?: "máquina"}?", fontWeight = FontWeight.Black) },
        text = { Text("Você receberá ${Formatters.money(resale)}. O operador será liberado e o espaço voltará ao galpão. Essa ação não pode ser desfeita.") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("VENDER") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun studioOneDecimal(value: Double): String = "${(value * 10.0).roundToInt() / 10.0}"

