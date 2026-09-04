package br.com.usinagemmaster.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.game.domain.*
import br.com.usinagemmaster.game.model.*
import kotlinx.coroutines.delay

private enum class Screen(val title: String) {
    HOME("Visão geral"),
    FACTORY("Fábrica Viva"),
    MACHINES("Máquinas e loja"),
    EMPLOYEES("Funcionários e Copa"),
    CONTRACTS("Contratos"),
    FINANCE("Finanças"),
    PROGRESSION("Empresa e pesquisa"),
    PROFILE("Meu personagem"),
    COMMUNITY("Comunidade"),
    SETTINGS("Configurações"),
}

@Composable
fun GameApp(store: GameStore) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    LaunchedEffect(store) {
        while (true) {
            delay(1_000L)
            store.tick()
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                Surface(tonalElevation = 4.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (screen != Screen.HOME) {
                            TextButton(onClick = { screen = Screen.HOME }) { Text("‹ Início") }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("USINAGEM MASTER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                            Text(screen.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        }
                        Text(GameStore.money(store.state.company.cashCents), fontWeight = FontWeight.Bold)
                    }
                }
            },
            snackbarHost = {
                store.message?.let { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(msg, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.inverseOnSurface)
                            TextButton(onClick = store::clearMessage) { Text("OK") }
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(store) { screen = it }
                    Screen.FACTORY -> FactoryScreen(store)
                    Screen.MACHINES -> MachinesScreen(store)
                    Screen.EMPLOYEES -> EmployeesScreen(store)
                    Screen.CONTRACTS -> ContractsScreen(store)
                    Screen.FINANCE -> FinanceScreen(store)
                    Screen.PROGRESSION -> ProgressionScreen(store)
                    Screen.PROFILE -> ProfileScreen(store)
                    Screen.COMMUNITY -> CommunityScreen(store)
                    Screen.SETTINGS -> SettingsScreen(store)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(store: GameStore, open: (Screen) -> Unit) {
    val d = store.dashboard
    val p = store.production
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            CardBlock("Oficina Império do Aço") {
                Text(d.companyName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Nível ${d.companyLevel} • Reputação ${d.reputation}")
                Text("Galpão ${d.usedWarehouseSpace}/${d.warehouseSpace} m²")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Produção / 10 min", one(p.totalUnitsPer10Minutes) + " pç", Modifier.weight(1f))
                Metric("Carga pendente", GameStore.money(store.pendingCargoCents), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Qualidade", "${p.averageQuality}%", Modifier.weight(1f))
                Metric("Contratos ativos", d.activeContracts.toString(), Modifier.weight(1f))
            }
        }
        if (store.pendingCargo.isNotEmpty()) {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CARGA PRONTA", fontWeight = FontWeight.Black)
                        Text("${one(store.pendingCargoUnits)} peças • ${GameStore.money(store.pendingCargoCents)}")
                        Button(onClick = { open(Screen.FACTORY) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Ir ao galpão e entregar")
                        }
                    }
                }
            }
        }
        item {
            Text("Gestão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
        items(Screen.values().filter { it != Screen.HOME }) { item ->
            ElevatedCard(onClick = { open(item) }, modifier = Modifier.fillMaxWidth()) {
                Text(item.title, Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FactoryScreen(store: GameStore) {
    val p = store.production
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            FactoryCanvas(store)
        }
        item {
            CardBlock("Expedição") {
                Text("Carga: ${one(store.pendingCargoUnits)} peças")
                Text("Valor liberado: ${GameStore.money(store.pendingCargoCents)}", fontWeight = FontWeight.Black)
                Text("Dono: ${store.ownerActivity.label}")
                Button(
                    onClick = store::startCargoDelivery,
                    enabled = store.pendingCargo.isNotEmpty() && store.ownerActivity == OwnerActivity.IDLE,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Levar carga à entrega")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = store::boost10Minutes, modifier = Modifier.weight(1f)) {
                    Text("+10 MIN (${store.state.boostTokens})")
                }
                OutlinedButton(onClick = store::dailyBonus, modifier = Modifier.weight(1f)) {
                    Text("Bônus diário")
                }
            }
        }
        item {
            OutlinedButton(onClick = store::buySnack, modifier = Modifier.fillMaxWidth()) {
                Text("Copa • cento de salgados ${GameStore.money(25_000L)}")
            }
        }
        item {
            CardBlock("Produção agora") {
                Text(if (WorkLifeRules.factoryOpen(store.state.shiftMode, currentTimeMillis())) "TURNO ABERTO" else "TURNO FECHADO", fontWeight = FontWeight.Black)
                Text("${p.operatingMachines} operando • ${p.idleMachines} paradas")
                Text("${one(p.totalUnitsPer10Minutes)} pç/10min • qualidade ${p.averageQuality}%")
                Text("Próxima carga estimada: ${GameStore.money(p.netPer10MinutesCents)}")
            }
        }
        items(store.state.machines) { machine ->
            val def = MachineCatalog.byType(machine.machineType)
            CardBlock(def?.name ?: machine.machineType) {
                val mp = p.machineProduction.firstOrNull { it.machineId == machine.id }
                Text("Condição ${machine.condition}/1000 • nível ${machine.level}")
                Text("Posição ${machine.gridX + 1},${machine.gridY + 1}")
                Text(if (mp?.isOperating == true) "Produzindo ${one(mp.unitsPer10Minutes)} pç/10min" else "Parada")
            }
        }
    }
}

@Composable
private fun FactoryCanvas(store: GameStore) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Chão de fábrica", fontWeight = FontWeight.Black)
            Text("Operadores • máquinas • CARGA • expedição", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color(0xFF15191D), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF424A52), RoundedCornerShape(14.dp))
            ) {
                val cellW = size.width / 5f
                val cellH = (size.height - 70f) / 6f
                for (x in 0..5) drawLine(Color(0xFF353C43), Offset(x * cellW, 0f), Offset(x * cellW, cellH * 6f), 1f)
                for (y in 0..6) drawLine(Color(0xFF353C43), Offset(0f, y * cellH), Offset(size.width, y * cellH), 1f)

                store.state.machines.forEach { m ->
                    val x = m.gridX.coerceIn(0, 4) * cellW + 5f
                    val y = m.gridY.coerceIn(0, 5) * cellH + 5f
                    val operating = store.production.machineProduction.firstOrNull { it.machineId == m.id }?.isOperating == true
                    drawRoundRect(
                        color = if (operating) Color(0xFF38B66A) else Color(0xFF6F7780),
                        topLeft = Offset(x, y),
                        size = Size(cellW - 10f, cellH - 10f),
                    )
                }

                val cargoWidth = size.width * .44f
                drawRoundRect(
                    color = if (store.pendingCargo.isNotEmpty()) Color(0xFFF1B84B) else Color(0xFF5B4B2A),
                    topLeft = Offset(8f, size.height - 58f),
                    size = Size(cargoWidth, 48f),
                )
                drawRoundRect(
                    color = Color(0xFF3978C2),
                    topLeft = Offset(size.width - cargoWidth - 8f, size.height - 58f),
                    size = Size(cargoWidth, 48f),
                )
            }
        }
    }
}

@Composable
private fun MachinesScreen(store: GameStore) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Instaladas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
        items(store.state.machines) { machine ->
            val def = MachineCatalog.byType(machine.machineType)
            CardBlock(def?.name ?: machine.machineType) {
                Text("Condição ${machine.condition}/1000 • Nível ${machine.level}")
                Text("Operador: ${store.state.employees.firstOrNull { it.assignedMachineId == machine.id }?.name ?: "sem operador"}")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { store.repairMachine(machine.id) }) { Text("Manutenção") }
                    OutlinedButton(onClick = { store.moveMachineNext(machine.id) }) { Text("Mover") }
                }
                TextButton(onClick = { store.sellMachine(machine.id) }) { Text("Revender máquina") }
            }
        }
        item { Text("Loja de máquinas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
        items(store.machineShop) { def ->
            CardBlock(def.name) {
                Text("${GameStore.money(def.priceCents)} • ${def.space} m² • qualidade ${def.quality}")
                Text("${one(def.baseProductionPerHour)} pç/h • ${one(def.powerKw)} kW")
                Button(onClick = { store.buyMachine(def.type.name) }, modifier = Modifier.fillMaxWidth()) { Text("Comprar") }
            }
        }
    }
}

@Composable
private fun EmployeesScreen(store: GameStore) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Button(onClick = store::hireEmployee, modifier = Modifier.fillMaxWidth()) { Text("Contratar funcionário") }
        }
        items(store.state.employees) { employee ->
            CardBlock(employee.name) {
                Text("${employee.specialty} • skill ${employee.skillLevel} • moral ${employee.morale}")
                Text("Traço: ${employee.trait}")
                Text("Exaustão: ${employee.fatigue}%")
                LinearProgressIndicator(progress = employee.fatigue / 100f, modifier = Modifier.fillMaxWidth())
                Text("Máquina: ${store.state.machines.firstOrNull { it.id == employee.assignedMachineId }?.let { MachineCatalog.byType(it.machineType)?.name } ?: "sem máquina"}")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { store.assignEmployeeNext(employee.id) }) { Text("Atribuir") }
                    OutlinedButton(onClick = { store.restEmployee(employee.id) }) { Text("Copa") }
                }
            }
        }
        if (store.state.employees.isEmpty()) {
            item { Text("Sua primeira contratação libera a produção da máquina inicial.") }
        }
    }
}

@Composable
private fun ContractsScreen(store: GameStore) {
    val contracts = store.state.contracts.sortedWith(compareBy<ContractSave> { statusOrder(it.status) }.thenByDescending { it.difficulty })
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(contracts) { c ->
            CardBlock("${c.clientName} • ${c.type}") {
                Text("Dificuldade ${c.difficulty} • qualidade ${c.requiredQuality}%")
                Text("${c.completedQuantity}/${c.quantity} peças • prêmio ${GameStore.money(c.rewardCents)}")
                val progress = if (c.quantity <= 0) 0f else (c.productionProgressMilli / (c.quantity * 1000f)).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                Text("Status: ${contractLabel(c.status)}", fontWeight = FontWeight.Bold)
                when (c.status) {
                    "AVAILABLE" -> Button(onClick = { store.acceptContract(c.id) }) { Text("Aceitar") }
                    "ACTIVE" -> OutlinedButton(onClick = { store.cancelContract(c.id) }) { Text("Cancelar • multa ${GameStore.money(c.penaltyCents)}") }
                }
            }
        }
    }
}

@Composable
private fun FinanceScreen(store: GameStore) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            CardBlock("Caixa") {
                Text(GameStore.money(store.state.company.cashCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Carga ainda não entregue não faz parte do caixa.")
            }
        }
        items(store.state.finances.asReversed()) { f ->
            ElevatedCard {
                Column(Modifier.padding(12.dp)) {
                    Text(f.description, fontWeight = FontWeight.Bold)
                    Text("${f.type} • ${f.category}")
                    Text(
                        (if (f.type == "EXPENSE") "- " else "+ ") + GameStore.money(f.amountCents),
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressionScreen(store: GameStore) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            CardBlock("Galpão") {
                val level = ((store.state.company.warehouseSpace - 100) / 50) + 1
                val cost = 2_000_000L * level
                Text("${store.state.company.usedWarehouseSpace}/${store.state.company.warehouseSpace} m²")
                Button(onClick = store::expandWarehouse, modifier = Modifier.fillMaxWidth()) {
                    Text("Expandir +50 m² • ${GameStore.money(cost)}")
                }
            }
        }
        item { Text("Metas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
        items(store.state.goals) { g ->
            CardBlock(g.title) {
                Text("${store.goalProgress(g)}/${g.target} • ${GameStore.money(g.rewardCents)}")
                Button(onClick = { store.claimGoal(g.id) }, enabled = !g.claimed && store.goalProgress(g) >= g.target) {
                    Text(if (g.claimed) "Resgatada" else "Resgatar")
                }
            }
        }
        item { Text("Especialização", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
        items(GameProgression.specialties) { s ->
            CardBlock(s.label) {
                Text(s.description)
                Button(onClick = { store.setSpecialty(s.code) }, enabled = store.state.company.companyLevel >= s.minLevel) {
                    Text(if (store.state.expansion.specialty == s.code) "Ativa" else "Escolher • nível ${s.minLevel}")
                }
            }
        }
        item {
            Text(
                "Pesquisa da empresa • ${GameProgression.companySkillPoints(store.state.company.companyLevel, store.state.expansion.companySkills)} ponto(s)",
                fontWeight = FontWeight.Black,
            )
        }
        items(GameProgression.companySkills) { skill ->
            SkillCard(skill, skill.id in store.state.expansion.companySkills) { store.unlockCompanySkill(skill.id) }
        }
    }
}

@Composable
private fun ProfileScreen(store: GameStore) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            CardBlock(store.state.profile.name) {
                Text("XP ${store.state.expansion.playerXp}")
                Text("${store.state.profile.bodyType} • ${store.state.profile.skinTone} • ${store.state.profile.hair}")
                Text("${store.state.profile.uniform} • capacete ${if (store.state.profile.helmet) "sim" else "não"}")
            }
        }
        item {
            CardBlock("Roleta Industrial") {
                Text("${store.state.expansion.gachaTickets} ficha(s)")
                Button(onClick = store::spinGacha, modifier = Modifier.fillMaxWidth()) { Text("Girar roleta") }
                Text("A roleta entrega skins, personagens e ferramentas; ficha não é prêmio.")
            }
        }
        item { Text("Skins", fontWeight = FontWeight.Black) }
        items(GameProgression.skins) { skin ->
            CardBlock("${skin.name} • ${skin.rarity}") {
                Text(skin.description)
                val owned = skin.id in store.state.expansion.ownedSkins
                Button(onClick = { store.equipSkin(skin.id) }, enabled = owned) {
                    Text(
                        when {
                            store.state.expansion.equippedSkin == skin.id -> "Equipada"
                            owned -> "Equipar"
                            else -> "Bloqueada"
                        }
                    )
                }
            }
        }
        item {
            Text(
                "Skills pessoais • ${GameProgression.playerSkillPoints(store.state.company.companyLevel, store.state.expansion.playerSkills)} ponto(s)",
                fontWeight = FontWeight.Black,
            )
        }
        items(GameProgression.playerSkills) { skill ->
            SkillCard(skill, skill.id in store.state.expansion.playerSkills) { store.unlockPlayerSkill(skill.id) }
        }
    }
}

@Composable
private fun CommunityScreen(store: GameStore) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            CardBlock("Perfil público local") {
                Text(store.state.profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("${store.state.company.name} • nível ${store.state.company.companyLevel}")
                Text("Reputação ${store.state.company.reputation} • ${store.state.machines.size} máquina(s)")
                Text("Produção ${one(store.production.totalUnitsPer10Minutes)} pç/10min")
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Firebase / multiplayer", fontWeight = FontWeight.Black)
                    Text(
                        "O jogo permanece totalmente funcional offline. O ranking, presença, visitas e apoio entre jogadores " +
                            "dependem da configuração do Firebase para o bundle iOS."
                    )
                    Text("Nenhum save local é apagado por falta de conexão.")
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(store: GameStore) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            CardBlock("Turno de trabalho") {
                Button(
                    onClick = { store.setShift(ShiftMode.DAY_12H) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("07:00–19:00 ${if (store.state.shiftMode == ShiftMode.DAY_12H) "✓" else ""}") }
                OutlinedButton(
                    onClick = { store.setShift(ShiftMode.CONTINUOUS_24H) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Operação 24 horas ${if (store.state.shiftMode == ShiftMode.CONTINUOUS_24H) "✓" else ""}") }
                Text("No modo 24h a exaustão cresce mais rápido e reduz produtividade.")
            }
        }
        item {
            CardBlock("Save local") {
                Text("O progresso é salvo no armazenamento nativo do iPhone após cada ação.")
                TextButton(onClick = store::resetSave) { Text("Apagar save e iniciar nova oficina") }
            }
        }
    }
}

@Composable
private fun SkillCard(skill: SkillDef, owned: Boolean, onUnlock: () -> Unit) {
    CardBlock(skill.name) {
        Text(skill.description)
        Text("Nível mínimo ${skill.minLevel}")
        Button(onClick = onUnlock, enabled = !owned) { Text(if (owned) "Desbloqueada" else "Desbloquear") }
    }
}

@Composable
private fun CardBlock(title: String, body: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            body()
        }
    }
}

@Composable
private fun Metric(title: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
    }
}

private fun one(value: Double): String {
    val scaled = (value * 10.0).toLong()
    return "${scaled / 10L},${kotlin.math.abs(scaled % 10L)}"
}

private fun statusOrder(status: String): Int = when (status) {
    "ACTIVE" -> 0
    "AVAILABLE" -> 1
    "COMPLETED" -> 2
    "FAILED" -> 3
    else -> 4
}

private fun contractLabel(status: String): String = when (status) {
    "AVAILABLE" -> "Disponível"
    "ACTIVE" -> "Ativo"
    "COMPLETED" -> "Concluído"
    "FAILED" -> "Falhou"
    "CANCELLED" -> "Cancelado"
    else -> status
}
