package br.com.usinagemmaster.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.domain.model.EmployeeSpecialty
import br.com.usinagemmaster.game.domain.GameProgression
import br.com.usinagemmaster.game.domain.GameStore
import br.com.usinagemmaster.game.domain.currentTimeMillis
import br.com.usinagemmaster.game.model.EmployeeSave
import kotlinx.coroutines.delay

private const val SYSTEMS_V28 = "systems_v28"

@Composable
fun MissionsScreenV28(store: GameStore) {
    var tab by remember { mutableStateOf("DIARIO") }
    var now by remember { mutableLongStateOf(currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = currentTimeMillis()
        }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeroCard(
            eyebrow = "CENTRAL DE MISSÕES",
            title = "Objetivos e recompensas",
            subtitle = "Diárias renovam em ${formatV28Duration(store.dailyMissionResetRemainingMillis)} • lendárias acompanham sua equipe rara",
            accent = SafetyAmber,
        ) {}
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            FilterChip(selected = tab == "DIARIO", onClick = { tab = "DIARIO" }, label = { Text("DIÁRIO") })
            FilterChip(selected = tab == "LENDARIO", onClick = { tab = "LENDARIO" }, label = { Text("LENDÁRIO") })
        }
        when (tab) {
            "DIARIO" -> Box(Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    item { DailyMissionsV27_2(store) }
                }
            }
            else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    if (store.state.legendaryMissions.isEmpty()) {
                        item {
                            IndustrialCard("MISSÕES LENDÁRIAS", "Conquiste um funcionário lendário na Roleta") {
                                Text("Quando um lendário entra na equipe, a missão exclusiva dele aparece aqui.", color = Steel400)
                            }
                        }
                    } else {
                        items(store.state.legendaryMissions, key = { it.id }) { mission ->
                            val progress = mission.progress.coerceAtMost(mission.target)
                            IndustrialCard(mission.title, mission.description) {
                                StatePill("LENDÁRIO", RoyalPurple)
                                LinearProgressIndicator(
                                    progress = (progress.toFloat() / mission.target.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("$progress/${mission.target}", color = Steel400)
                                    Text(GameStore.money(mission.rewardCents), color = ProductionGreen, fontWeight = FontWeight.Black)
                                }
                                Button(
                                    onClick = { store.claimLegendaryMission(mission.id) },
                                    enabled = !mission.claimed && progress >= mission.target,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (mission.claimed) "COLETADA" else "COLETAR RECOMPENSA") }
                            }
                        }
                    }
                }
            }
        }
        // keeps the composable ticking without pushing time state into the save
        if (now < 0L) Text("")
    }
}

@Composable
fun ContractToolAutomationV28(store: GameStore) {
    IndustrialCard("FERRAMENTARIA AUTOMÁTICA", "Reserva a melhor ferramenta disponível para cada contrato") {
        Text(
            "Ativos primeiro • qualidade alta prioriza estabilidade/qualidade • contratos simples priorizam velocidade.",
            style = MaterialTheme.typography.bodySmall,
            color = Steel400,
        )
        Button(onClick = store::autoDistributeContractTools, modifier = Modifier.fillMaxWidth()) {
            Text("AUTO DISTRIBUIR FERRAMENTAS")
        }
    }
}

@Composable
fun FactoryAutomationV28(store: GameStore) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = Steel850,
            border = BorderStroke(1.dp, Steel700),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("ENTREGA AUTOMÁTICA", fontWeight = FontWeight.Black, color = Steel100)
                Text(
                    if (store.autoCargoDeliveryEnabled) "Ativa • o dono despacha quando ficar livre" else "Desligada • você decide quando expedir",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (store.autoCargoDeliveryEnabled) ProductionGreen else Steel400,
                )
                Switch(checked = store.autoCargoDeliveryEnabled, onCheckedChange = store::setAutoCargoDelivery)
            }
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = Steel850,
            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = .45f)),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("GALPÃO REAL", fontWeight = FontWeight.Black, color = Steel100)
                Text("${store.factoryGridColumns} × ${store.factoryGridRows} baias", color = ElectricBlue, fontWeight = FontWeight.Black)
                Text("${store.state.company.warehouseSpace} m² • ${store.factoryBayCapacity} posições lógicas", style = MaterialTheme.typography.labelSmall, color = Steel400)
            }
        }
    }
}

@Composable
fun EmployeeCareerActionsV28(store: GameStore, employee: EmployeeSave) {
    var trainingOpen by remember(employee.id) { mutableStateOf(false) }
    val requirement = store.promotionRequirementMinutes(employee.id)
    val canPromote = employee.jobGrade < 5 && employee.experience >= requirement
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Carreira interna • Grau ${employee.jobGrade}/5", style = MaterialTheme.typography.labelSmall, color = SafetyAmber)
            Text(
                if (employee.jobGrade >= 5) "MÁXIMO" else "${employee.experience}/${requirement} min",
                style = MaterialTheme.typography.labelSmall,
                color = if (canPromote) ProductionGreen else Steel400,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { store.promoteEmployee(employee.id) },
                enabled = canPromote,
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) { Text("Promover", style = MaterialTheme.typography.labelSmall) }
            if (employee.legendaryCode == null) {
                OutlinedButton(
                    onClick = { trainingOpen = !trainingOpen },
                    enabled = employee.jobGrade >= 2 && employee.experience >= 720L,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) { Text("Nova função", style = MaterialTheme.typography.labelSmall) }
            }
        }
        if (trainingOpen) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                EmployeeSpecialty.values().filter { it.name != employee.specialty }.forEach { role ->
                    AssistChip(
                        onClick = { store.crossTrainEmployee(employee.id, role.name); trainingOpen = false },
                        label = { Text(roleLabelV28(role.name)) },
                    )
                }
            }
            Text("Treinamento cruzado custa 50% do salário atual e libera o funcionário para outro setor.", style = MaterialTheme.typography.labelSmall, color = Steel400)
        }
    }
}

fun roleLabelV28(role: String): String = when (role) {
    "TURNER" -> "Torneiro"
    "MILLER" -> "Fresador"
    "WELDER" -> "Soldador"
    "CNC_PROGRAMMER" -> "Programação CNC"
    "GRINDER_OPERATOR" -> "Retífica"
    "DRILL_OPERATOR" -> "Furação"
    "QUALITY_INSPECTOR" -> "Qualidade"
    "STOCK_ASSISTANT" -> "Estoque"
    else -> role.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

fun roleAreaV28(role: String): String = when (role) {
    "STOCK_ASSISTANT" -> "ESTOQUE / LOGÍSTICA"
    "QUALITY_INSPECTOR" -> "QUALIDADE"
    "CNC_PROGRAMMER" -> "PROGRAMAÇÃO CNC"
    "WELDER" -> "CALDEIRARIA / SOLDA"
    else -> "CHÃO DE MÁQUINAS"
}

private fun pad2V28(value: Long): String = value.toString().padStart(2, '0')

private fun formatV28Duration(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val days = total / 86_400L
    val hours = (total % 86_400L) / 3_600L
    val minutes = (total % 3_600L) / 60L
    val seconds = total % 60L
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${pad2V28(hours)}:${pad2V28(minutes)}:${pad2V28(seconds)}"
        else -> "${pad2V28(minutes)}:${pad2V28(seconds)}"
    }
}
