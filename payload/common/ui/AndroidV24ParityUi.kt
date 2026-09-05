package br.com.usinagemmaster.game.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.game.domain.*
import br.com.usinagemmaster.game.model.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AndroidV24MainMenu(
    store: GameStore,
    onEnter: () -> Unit,
    onProfile: () -> Unit,
    onCommunity: () -> Unit,
    onSettings: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "landing_v27")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Restart), label = "landing_phase")
    val pulse by transition.animateFloat(.35f, 1f, infiniteRepeatable(tween(1_700), RepeatMode.Reverse), label = "landing_pulse")
    val production = store.production
    val live = store.factoryFrame.open

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Steel980, Steel950, Color(0xFF101B1D)))).statusBarsPadding().navigationBarsPadding()
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val spacing = 38f
            var x = -spacing + (phase * spacing)
            while (x < size.width + spacing) { drawLine(Steel700.copy(alpha=.09f), Offset(x,0f), Offset(x,size.height),1f); x += spacing }
            var y = 0f
            while (y < size.height) { drawLine(Steel700.copy(alpha=.07f), Offset(0f,y), Offset(size.width,y),1f); y += spacing }
            drawCircle(IndustrialAmber.copy(alpha=.035f + pulse*.05f), size.width*.52f, Offset(size.width*.72f,size.height*.27f))
            drawCircle(ElectricBlue.copy(alpha=.025f + pulse*.035f), size.width*.38f, Offset(size.width*.12f,size.height*.73f))
            drawRect(Color.Black.copy(alpha=.34f), Offset(0f,size.height*.67f), Size(size.width,size.height*.33f))
            drawLine(IndustrialAmber.copy(alpha=.55f), Offset(0f,size.height*.80f), Offset(size.width,size.height*.80f),3f)
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom=20.dp, start=6.dp, end=6.dp).graphicsLayer(alpha=.48f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            val machines = store.state.machines.take(3)
            if (machines.isEmpty()) {
                MachineArtworkV27("Torno Mecânico", machineType="MECHANICAL_LATHE", modifier=Modifier.width(112.dp).height(80.dp))
                MachineArtworkV27("Centro CNC", machineType="CNC_MACHINING_CENTER", modifier=Modifier.width(112.dp).height(80.dp))
            } else machines.forEach { machine ->
                val name=MachineCatalog.byType(machine.machineType)?.name ?: machine.machineType
                MachineArtworkV27(name,machineType=machine.machineType,modifier=Modifier.width(108.dp).height(78.dp))
            }
        }

        // Operadores ao fundo: a landing já mostra que a fábrica está viva.
        Canvas(Modifier.align(Alignment.BottomEnd).padding(bottom=64.dp,end=20.dp).size(118.dp,170.dp).graphicsLayer(alpha=.72f)) {
            drawPlayerAvatarFigure(Offset(size.width*.32f,size.height*.93f),store.state.profile,size.minDimension/88f,phase,true,false)
            val employee=store.state.employees.firstOrNull()
            if(employee!=null) drawPlayerAvatarFigure(Offset(size.width*.72f,size.height*.93f), employeeAvatarForLandingV27(employee), size.minDimension/96f, (phase+.32f)%1f, true, false)
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=16.dp),
            verticalArrangement=Arrangement.Center,
        ) {
            Surface(shape=RoundedCornerShape(999.dp),color=(if(live)ProductionGreen else SafetyAmber).copy(alpha=.12f),border=BorderStroke(1.dp,(if(live)ProductionGreen else SafetyAmber).copy(alpha=.42f))) {
                Text(if(live)"● FÁBRICA ONLINE" else "● FORA DO TURNO",Modifier.padding(horizontal=11.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Black,color=if(live)ProductionGreen else SafetyAmber)
            }
            Spacer(Modifier.height(14.dp))
            Text("USINAGEM\nMASTER",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Black,color=Steel100)
            Text("IMPÉRIO DO AÇO",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Black,color=IndustrialAmber)
            Spacer(Modifier.height(8.dp))
            Text("Sua fábrica respira: operadores trabalham, máquinas produzem e cada decisão aparece no chão de fábrica.",style=MaterialTheme.typography.bodyLarge,color=Steel200)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                LandingStatV27("CAIXA",GameStore.money(store.state.company.cashCents),Modifier.weight(1f))
                LandingStatV27("PRODUÇÃO","${oneV24(production.totalUnitsPer10Minutes)} pç",Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick={GameFeedback.play(GameSoundEffect.MACHINE_START,store.state.uiSettings.soundEnabled);GameFeedback.haptic(store.state.uiSettings.hapticsEnabled);onEnter()},modifier=Modifier.fillMaxWidth().height(58.dp)) { Text("ENTRAR NA FÁBRICA  →",fontWeight=FontWeight.Black) }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick=onProfile,modifier=Modifier.weight(1f)){Text("Personagem")}
                OutlinedButton(onClick=onCommunity,modifier=Modifier.weight(1f)){Text("Comunidade")}
                OutlinedButton(onClick=onSettings,modifier=Modifier.weight(1f)){Text("Ajustes")}
            }
            Spacer(Modifier.height(8.dp))
            Text("${store.state.company.name} • N${store.state.company.companyLevel} • ${store.state.machines.size} máquinas",style=MaterialTheme.typography.labelSmall,color=Steel400)
        }
    }
}

@Composable
private fun LandingStatV27(label:String,value:String,modifier:Modifier=Modifier){
    Surface(modifier,shape=RoundedCornerShape(14.dp),color=Steel900.copy(alpha=.90f),border=BorderStroke(1.dp,Steel700.copy(alpha=.75f))){Column(Modifier.padding(10.dp)){Text(label,style=MaterialTheme.typography.labelSmall,color=Steel400);Text(value,fontWeight=FontWeight.Black,color=Steel100,maxLines=1)}}
}

private fun employeeAvatarForLandingV27(employee:EmployeeSave):PlayerProfileSave = PlayerProfileSave(
    name=employee.name,gender=if(employee.name.substringBefore(' ') in setOf("Luciana","Camila","Fernanda","Amanda","Juliana","Mariana","Beatriz","Renata"))"FEMALE" else "MALE",
    skinStyle=when(employee.legendaryCode){"tatu_banhado"->"TATUZAO";"kendao"->"KENDAO_KIMONO";"magrao"->"MAGRAO";else->"WORKSHOP"},bodyType="STANDARD",skinTone="MEDIUM",hairStyle="SHORT",hairColor="DARK",uniformColor="NAVY",helmetColor="YELLOW",accessory="NONE",onboardingComplete=true,
)

@Composable
fun AndroidWorkLifeHomeCard(store: GameStore) {
    val now = currentTimeMillis()
    val continuous = store.state.shiftMode == ShiftMode.CONTINUOUS_24H
    val playerResting = store.state.playerRestingUntil > now
    val playerFatigue = store.state.playerFatigue.coerceIn(0.0, 100.0)

    IndustrialCard(
        "Turno e vida da fábrica",
        if (continuous) "🟢 Operação 24h • exaustão ativa"
        else if (WorkLifeRules.factoryOpen(store.state.shiftMode, now))
            "🟢 Turno aberto • equipe trabalha até 19:00"
        else
            "🏠 Fábrica fechada • equipe em casa • contratos pausados"
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(
                selected = !continuous,
                onClick = { store.setShiftMode(ShiftMode.DAY_12H) },
                label = { Text("12h • 07–19") },
            )
            FilterChip(
                selected = continuous,
                onClick = { store.setShiftMode(ShiftMode.CONTINUOUS_24H) },
                label = { Text("24h • exaustão") },
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Seu personagem", fontWeight = FontWeight.Black)
                Text(
                    "${playerFatigue.roundToInt()}% • ${WorkLifeRules.exhaustionLabel(playerFatigue)}" +
                        if (playerResting) " • na Copa" else "",
                    color = Steel400,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (playerResting) {
                TextButton(onClick = store::returnPlayerFromBreak) { Text("Voltar") }
            } else {
                TextButton(onClick = store::restPlayer) { Text("Descansar 2h") }
            }
        }

        LinearProgressIndicator(
            progress = { playerFatigue.toFloat() / 100f },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Descanso automático em 88%", Modifier.weight(1f))
            Switch(
                checked = store.state.autoRest,
                onCheckedChange = store::setAutoRest,
            )
        }
    }
}

@Composable
fun AndroidDashboardProgress(store: GameStore) {
    val p = store.production
    val factoryRepInside = (
        store.state.company.reputation - (store.state.company.companyLevel - 1) * 20
        ).coerceIn(0, 20)
    val player = GameProgression.playerProgress(store.state.expansion.playerXp)
    val now = currentTimeMillis()
    val elapsed = (now - store.state.company.lastSimulationAt).coerceAtLeast(0L)
    val remaining = (10L * 60L * 1000L - elapsed % (10L * 60L * 1000L))
        .coerceIn(0L, 10L * 60L * 1000L)

    IndustrialCard("Evolução da fábrica", "XP visual e próximo fechamento de produção") {
        Text("Fábrica • nível ${store.state.company.companyLevel}", fontWeight = FontWeight.Black)
        LinearProgressIndicator(
            progress = { factoryRepInside / 20f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${factoryRepInside * 100} / 2.000 XP • reputação ${store.state.company.reputation}",
            style = MaterialTheme.typography.labelSmall,
            color = Steel400,
        )
        Spacer(Modifier.height(6.dp))
        Text("Personagem • nível ${player.level}", fontWeight = FontWeight.Black)
        LinearProgressIndicator(progress = { player.fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            "${player.current} / ${player.needed} XP",
            style = MaterialTheme.typography.labelSmall,
            color = Steel400,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Próxima CARGA", color = Steel400)
            Text(formatCountdownV24(remaining), fontWeight = FontWeight.Black, color = IndustrialAmber)
        }
        Text(
            "${oneV24(p.totalUnitsPer10Minutes)} pç / ciclo • Q${p.averageQuality}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun OwnerCareerPanel(store: GameStore) {
    val batch = store.state.career.activeBatch
    var showOperation by remember { mutableStateOf(false) }

    IndustrialCard(
        "Jornada do dono",
        "Faça a operação, leve ao Q, embale no P e entregue no E"
    ) {
        if (batch == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CompactStat(
                    "Manual",
                    store.state.career.totalManualOperations.toString(),
                    Modifier.weight(1f)
                )
                CompactStat(
                    "Perfeitas",
                    store.state.career.perfectOperations.toString(),
                    Modifier.weight(1f)
                )
                CompactStat(
                    "Expedidos",
                    store.state.career.shippedBatches.toString(),
                    Modifier.weight(1f)
                )
            }
            Button(
                onClick = { showOperation = true },
                enabled = store.state.machines.isNotEmpty() &&
                    store.state.contracts.any { it.status == "ACTIVE" },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("OPERAR EU MESMO", fontWeight = FontWeight.Black)
            }
            Text(
                "Sem cooldown: a operação manual é uma forma de adiantar contratos, " +
                    "não uma obrigação para a produção automática.",
                style = MaterialTheme.typography.bodySmall,
                color = Steel400,
            )
        } else {
            val stage = runCatching { ProductionStage.valueOf(batch.stage) }
                .getOrDefault(ProductionStage.MACHINED)
            StatePill(stage.label.uppercase(), stageColorV24(stage))
            Text(
                "${batch.producedQuantity} pç • Q${batch.quality} • " +
                    "precisão ${batch.precision}% • ${if (batch.manual) "manual" else "assistido"}",
                fontWeight = FontWeight.Black,
            )

            when (stage) {
                ProductionStage.MACHINED, ProductionStage.WAITING_QC -> {
                    Button(
                        onClick = store::moveOwnerBatchToQuality,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("LEVAR AO Q • QUALIDADE") }
                }
                ProductionStage.QC -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(
                            onClick = { store.inspectOwnerBatch(true) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Aprovar medida") }
                        OutlinedButton(
                            onClick = { store.inspectOwnerBatch(false) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Reprovar") }
                    }
                }
                ProductionStage.REWORK -> {
                    ReworkButton(store)
                }
                ProductionStage.APPROVED -> {
                    Button(
                        onClick = store::packOwnerBatch,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("LEVAR AO P • EMBALAGEM") }
                }
                ProductionStage.READY_TO_SHIP -> {
                    Button(
                        onClick = store::shipOwnerBatch,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("LEVAR AO E • EXPEDIÇÃO") }
                }
                else -> Unit
            }

            TextButton(onClick = store::scrapOwnerBatch) { Text("Refugar/descartar lote") }
        }
    }

    if (showOperation) {
        OwnerOperationDialog(store = store, onDismiss = { showOperation = false })
    }
}

@Composable
private fun ReworkButton(store: GameStore) {
    var a by remember { mutableFloatStateOf(52f) }
    var b by remember { mutableFloatStateOf(52f) }
    val batch = store.state.career.activeBatch ?: return
    val contract = store.state.contracts.firstOrNull { it.id == batch.contractId }
    val blueprint = MachineMinigameCatalog.blueprint(
        batch.machineType,
        contract?.difficulty ?: 1,
    )
    val score = scoreParamsV24(
        a, blueprint.targetA, blueprint.toleranceA,
        b, blueprint.targetB, blueprint.toleranceB,
    )
    Text("Retrabalho • ${(score * 100).roundToInt()}%", fontWeight = FontWeight.Black)
    Slider(value = a, onValueChange = { a = it }, valueRange = 0f..100f)
    Slider(value = b, onValueChange = { b = it }, valueRange = 0f..100f)
    Button(
        onClick = {
            store.reworkOwnerBatch(
                MinigameResult(score, score, .72f, score, if (score < .45f) 1 else 0)
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("CONCLUIR RETRABALHO") }
}

@Composable
private fun OwnerOperationDialog(
    store: GameStore,
    onDismiss: () -> Unit,
) {
    val machines = store.state.machines.filter { it.installed }
    val contracts = store.state.contracts.filter {
        it.status == "ACTIVE" && it.completedQuantity < it.quantity
    }
    var machineId by remember { mutableStateOf(machines.firstOrNull()?.id.orEmpty()) }
    var contractId by remember { mutableStateOf(contracts.firstOrNull()?.id.orEmpty()) }
    val machine = machines.firstOrNull { it.id == machineId } ?: machines.firstOrNull()
    val contract = contracts.firstOrNull { it.id == contractId } ?: contracts.firstOrNull()
    val blueprint = MachineMinigameCatalog.blueprint(
        machine?.machineType.orEmpty(),
        contract?.difficulty ?: 1,
    )
    var a by remember(machineId, contractId) { mutableFloatStateOf(50f) }
    var b by remember(machineId, contractId) { mutableFloatStateOf(50f) }
    val score = scoreParamsV24(
        a, blueprint.targetA, blueprint.toleranceA,
        b, blueprint.targetB, blueprint.toleranceB,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(blueprint.title, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(blueprint.goal, color = Steel400)

                Text("Máquina", fontWeight = FontWeight.Black)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    machines.forEach { m ->
                        FilterChip(
                            selected = m.id == machineId,
                            onClick = { machineId = m.id },
                            label = { Text(MachineCatalog.byType(m.machineType)?.name ?: m.machineType) },
                        )
                    }
                }

                Text("Contrato", fontWeight = FontWeight.Black)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    contracts.forEach { c ->
                        FilterChip(
                            selected = c.id == contractId,
                            onClick = { contractId = c.id },
                            label = { Text(c.clientName) },
                        )
                    }
                }

                Text("${blueprint.parameterA}: ${a.roundToInt()}")
                Slider(value = a, onValueChange = { a = it }, valueRange = 0f..100f)
                Text("${blueprint.parameterB}: ${b.roundToInt()}")
                Slider(value = b, onValueChange = { b = it }, valueRange = 0f..100f)

                StatePill(
                    "SCORE ${(score * 100).roundToInt()}%",
                    if (score >= .75f) ProductionGreen else IndustrialAmber,
                )
                val mastery = store.state.career.mastery(machine?.machineType.orEmpty())
                Text(
                    "Maestria ${mastery.level}/20 • +${mastery.quantityBonusPct}% quantidade • +" +
                        "${mastery.qualityBonus} qualidade",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (machine != null && contract != null) {
                        val mistakes = when {
                            score >= .80f -> 0
                            score >= .55f -> 1
                            else -> 2
                        }
                        store.operateMachine(
                            machine.id,
                            contract.id,
                            MinigameResult(
                                score = score,
                                precision = score,
                                speed = (.45f + score * .55f).coerceAtMost(1f),
                                quality = score,
                                mistakes = mistakes,
                            ),
                            manual = true,
                        )
                        onDismiss()
                    }
                },
                enabled = machine != null && contract != null,
            ) { Text("USINAR LOTE") }
        },
        dismissButton = {
            Column {
                TextButton(
                    onClick = {
                        if (machine != null && contract != null) {
                            store.operateMachine(
                                machine.id,
                                contract.id,
                                MinigameResult(.46f, .54f, .34f, .52f),
                                manual = false,
                            )
                            onDismiss()
                        }
                    },
                    enabled = machine != null && contract != null,
                ) { Text("Ciclo assistido") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
fun LegendaryEmployeesPanel(store: GameStore) {
    val hiredCodes = store.state.employees.mapNotNull { it.legendaryCode }.toSet()
    val available = LegendaryEmployeeCatalog.all.count {
        it.unlockLevel <= store.state.company.companyLevel && it.code !in hiredCodes
    }

    IndustrialCard(
        "Equipe lendária",
        "${hiredCodes.size}/${LegendaryEmployeeCatalog.all.size} coletados • $available elegíveis na roleta"
    ) {
        StatePill("EXCLUSIVOS DA ROLETA", RoyalPurple)
        Text(
            "Não existe contratação direta. Cada lendário entra na sua equipe somente quando é obtido na Roleta Industrial; duplicatas são bloqueadas e a missão própria nasce junto com o prêmio.",
            style = MaterialTheme.typography.bodySmall,
            color = Steel200,
        )
    }

    LegendaryEmployeeCatalog.all.forEach { def ->
        val employee = store.state.employees.firstOrNull { it.legendaryCode == def.code }
        val unlocked = def.unlockLevel <= store.state.company.companyLevel
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (employee != null) RoyalPurple.copy(alpha = .12f) else Steel900
            ),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(def.name, fontWeight = FontWeight.Black)
                    StatePill(
                        when {
                            employee != null -> "COLETADO"
                            unlocked -> "ELEGÍVEL"
                            else -> "NÍVEL ${def.unlockLevel}"
                        },
                        when {
                            employee != null -> RoyalPurple
                            unlocked -> ProductionGreen
                            else -> Steel500
                        }
                    )
                }
                Text(def.description, style = MaterialTheme.typography.bodySmall, color = Steel400)
                Text(
                    "${def.specialty} • skill ${def.skillLevel} • moral ${def.morale} • ${def.trait}",
                    style = MaterialTheme.typography.labelSmall,
                )
                if (employee != null) {
                    LegendaryEmployeeCatalog.quote(
                        def.code,
                        employee.assignedMachineId != null,
                        ((currentTimeMillis() / 5_000L) % 10).toInt(),
                    )?.let { Text("“$it”", color = IndustrialAmber) }
                }
            }
        }
    }

    if (store.state.legendaryMissions.isNotEmpty()) {
        SectionTitle("Missões lendárias", "Produza com cada especialista para receber bônus")
        store.state.legendaryMissions.forEach { mission ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = Steel900),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(mission.title, fontWeight = FontWeight.Black)
                    Text(mission.description, style = MaterialTheme.typography.bodySmall, color = Steel400)
                    LinearProgressIndicator(
                        progress = {
                            if (mission.target <= 0L) 1f
                            else (mission.progress.toFloat() / mission.target.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${mission.progress}/${mission.target} min • ${GameStore.money(mission.rewardCents)}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Button(
                        onClick = { store.claimLegendaryMission(mission.id) },
                        enabled = !mission.claimed && mission.progress >= mission.target,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (mission.claimed) "COLETADO" else "COLETAR RECOMPENSA")
                    }
                }
            }
        }
    }
}

@Composable
fun IndustrialCareerTree(store: GameStore) {
    IndustrialCareerStoryboardV27(store)
}

@Composable
fun PrestigeCharacterNotice(store: GameStore) {
    val prestige = GameProgression.characters.filter { it.rarity.rank >= RarityDef.EPIC.rank }
    val owned = prestige.count { it.id in store.state.expansion.ownedCharacters }
    IndustrialCard(
        "PERSONAGENS DE PRESTÍGIO",
        "$owned/${prestige.size} conquistados • somente roleta, pity, metas e eventos"
    ) {
        Text(
            "Não há botão de compra direta. Marcos da carreira também podem conceder personagens de prestígio.",
            style = MaterialTheme.typography.bodySmall,
            color = Steel400,
        )
    }
}

private fun scoreParamsV24(
    a: Float,
    targetA: Float,
    toleranceA: Float,
    b: Float,
    targetB: Float,
    toleranceB: Float,
): Float {
    fun part(value: Float, target: Float, tolerance: Float): Float =
        (1f - abs(value - target) / (tolerance * 2.4f)).coerceIn(0f, 1f)
    return (part(a, targetA, toleranceA) * .5f + part(b, targetB, toleranceB) * .5f)
        .coerceIn(0f, 1f)
}

private fun stageColorV24(stage: ProductionStage): Color = when (stage) {
    ProductionStage.APPROVED, ProductionStage.READY_TO_SHIP, ProductionStage.SHIPPED -> ProductionGreen
    ProductionStage.REWORK, ProductionStage.SCRAP -> DangerRed
    ProductionStage.QC, ProductionStage.WAITING_QC -> ElectricBlue
    else -> IndustrialAmber
}

private fun formatCountdownV24(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

private fun oneV24(value: Double): String {
    val scaled = (value * 10.0).roundToInt()
    return "${scaled / 10},${abs(scaled % 10)}"
}
