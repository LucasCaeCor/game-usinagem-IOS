package br.com.usinagemmaster.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.simulation.*
import br.com.usinagemmaster.game.domain.GameStore
import br.com.usinagemmaster.game.model.PlayerProfileSave
import kotlin.math.min
import kotlin.math.sin

@Composable
fun FactoryStudio(
    store: GameStore,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val frame = store.factoryFrame

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Steel900),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("CHÃO DE FÁBRICA", fontWeight = FontWeight.Black)
                    Text(
                        if (frame.open) "Turno ativo • simulação operacional" else "Turno fechado • equipe em descanso",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (frame.open) ProductionGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallZoomButton("−") { zoom = (zoom - .18f).coerceIn(.65f, 2.4f) }
                    SmallZoomButton("${(zoom * 100).toInt()}%") { zoom = 1f; pan = Offset.Zero }
                    SmallZoomButton("+") { zoom = (zoom + .18f).coerceIn(.65f, 2.4f) }
                }
            }

            Spacer(Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(Steel950, RoundedCornerShape(18.dp))
                    .border(1.dp, Steel700, RoundedCornerShape(18.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            zoom = (zoom * zoomChange).coerceIn(.65f, 2.4f)
                            pan += panChange
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoom = 1f
                                pan = Offset.Zero
                            }
                        )
                    }
            ) {
                drawFactoryStudioScene(
                    store = store,
                    frame = frame,
                    zoom = zoom,
                    pan = pan,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LegendDot(ProductionGreen, "Produzindo", Modifier.weight(1f))
                LegendDot(SafetyAmber, "Setup/logística", Modifier.weight(1f))
                LegendDot(DangerRed, "Parada", Modifier.weight(1f))
            }
        }
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
) {
    val baseTileW = min(size.width / 22f, size.height / 17f)
    val halfW = baseTileW * zoom
    val halfH = halfW * .46f
    val origin = Offset(size.width * .50f + pan.x, 28f + pan.y)

    fun project(p: FloorPoint): Offset = Offset(
        origin.x + (p.x - p.y) * halfW * .52f,
        origin.y + (p.x + p.y) * halfH * .52f,
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
        drawMachineSilhouette(at, halfW, machine.machineType, color, machineFrame?.progress ?: 0f)
    }

    // Operadores em suas micro-rotinas.
    frame.workers.sortedBy { it.position.x + it.position.y }.forEachIndexed { index, worker ->
        val at = project(worker.position)
        drawWorker(
            base = at,
            scale = (halfW / 18f).coerceIn(.45f, 1.25f),
            activity = worker.activity,
            carrying = worker.carrying,
            fatigue = worker.fatigue,
            phase = (index * .19f + worker.progress) % 1f,
        )
    }

    // Empilhadeira/logística no corredor quando há carga.
    if (store.pendingCargo.isNotEmpty() || frame.depositedLots > 0) {
        val t = ((store.state.company.lastSimulationAt / 250L) % 1000L) / 1000f
        val p = FloorPoint(5f + 9f * t, 22f)
        drawForklift(project(p), halfW)
    }

    // Dono da oficina: usa o avatar configurado.
    val owner = frame.owner
    drawPlayerAvatarFigure(
        base = project(owner.position) + Offset(0f, halfW * .35f),
        avatar = store.state.profile,
        scale = (halfW / 17f).coerceIn(.45f, 1.25f),
        phase = ((store.state.company.lastSimulationAt / 100L) % 1000L) / 1000f,
        walking = owner.walking,
        carrying = owner.carrying,
    )

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
    val w = scale * 1.55f
    val h = scale * .82f
    drawOval(Color.Black.copy(alpha = .33f), at - Offset(w * .52f, h * .05f), Size(w * 1.05f, h * .25f))
    drawRoundRect(
        color = Color(0xFF0C1215),
        topLeft = at - Offset(w * .5f + 2f, h + 2f),
        size = Size(w + 4f, h + 4f),
        cornerRadius = CornerRadius(scale * .15f),
    )
    drawRoundRect(
        color = color.copy(alpha = .90f),
        topLeft = at - Offset(w * .5f, h),
        size = Size(w, h),
        cornerRadius = CornerRadius(scale * .14f),
    )

    val upper = type.uppercase()
    when {
        "LATHE" in upper -> {
            drawCircle(Color(0xFF15232A), scale * .22f, at - Offset(w * .22f, h * .58f))
            drawLine(Color(0xFFD7E0E4), at - Offset(w * .02f, h * .57f), at + Offset(w * .27f, -h * .57f), scale * .09f)
        }
        "MILL" in upper || "MACHINING_CENTER" in upper -> {
            drawRect(Color(0xFF132027), at - Offset(w * .22f, h * .75f), Size(w * .44f, h * .53f))
            drawLine(SafetyAmber.copy(alpha = .7f), at - Offset(w * .34f, h * .18f), at + Offset(w * .34f, -h * .18f), scale * .07f)
        }
        "GRIND" in upper -> {
            drawCircle(Color(0xFFD6DDE0), scale * .23f, at - Offset(w * .19f, h * .55f), style = Stroke(scale * .09f))
            drawCircle(Color(0xFFD6DDE0), scale * .23f, at + Offset(w * .19f, -h * .55f), style = Stroke(scale * .09f))
        }
        "WELD" in upper -> {
            val spark = scale * (.13f + progress * .08f)
            drawLine(Color(0xFFDBE4E8), at - Offset(w * .2f, h * .55f), at + Offset(w * .2f, -h * .32f), scale * .08f)
            drawCircle(Color(0xFFFFD85A), spark, at + Offset(w * .18f, -h * .31f))
        }
        "DRILL" in upper -> {
            drawLine(Color(0xFFD9E0E3), at - Offset(0f, h * .72f), at - Offset(0f, h * .18f), scale * .11f)
            drawRect(Color(0xFF16242B), at - Offset(w * .23f, h * .26f), Size(w * .46f, h * .12f))
        }
        else -> {
            drawRect(Color(0xFF142128), at - Offset(w * .24f, h * .68f), Size(w * .48f, h * .42f))
        }
    }
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
