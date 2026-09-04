package br.com.usinagemmaster.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.simulation.FactoryMachineState
import br.com.usinagemmaster.domain.simulation.OwnerActivity
import br.com.usinagemmaster.game.domain.*
import br.com.usinagemmaster.game.model.*
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class Screen(val title: String, val short: String, val glyph: String) {
    HOME("Painel executivo", "Início", "⌂"),
    FACTORY("Fábrica Viva • Studio", "Fábrica", "▦"),
    CONTRACTS("Contratos industriais", "Contratos", "▤"),
    EMPLOYEES("Equipe e disciplina", "Equipe", "♟"),
    MACHINES("Máquinas e tecnologia", "Máquinas", "⚙"),
    FINANCE("Finanças", "Finanças", "R$"),
    PROGRESSION("Empresa e pesquisa", "Evolução", "↗"),
    PROFILE("Meu personagem", "Perfil", "●"),
    MINIGAME("Desafio de precisão", "Precisão", "◎"),
    COMMUNITY("Comunidade", "Social", "◉"),
    SETTINGS("Configurações", "Ajustes", "☷"),
    MORE("Gestão completa", "Mais", "•••"),
}

private val bottomTabs = listOf(
    Screen.HOME,
    Screen.FACTORY,
    Screen.CONTRACTS,
    Screen.EMPLOYEES,
    Screen.MORE,
)

@Composable
fun GameApp(store: GameStore) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(store) {
        var economyAccumulated = 0L
        while (true) {
            delay(50L)
            store.advanceVisual(.05)
            economyAccumulated += 50L
            if (economyAccumulated >= 1_000L) {
                economyAccumulated = 0L
                store.tick()
            }
        }
    }

    LaunchedEffect(store.message) {
        val text = store.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text, withDismissAction = true)
        store.clearMessage()
    }

    UsinagemMasterTheme {
        Scaffold(
            containerColor = Steel950,
            topBar = {
                IndustrialTopBar(
                    store = store,
                    screen = screen,
                    onHome = { screen = Screen.HOME },
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Steel900, tonalElevation = 8.dp) {
                    bottomTabs.forEach { destination ->
                        val selected = screen == destination ||
                            (destination == Screen.MORE && screen !in bottomTabs.dropLast(1))
                        NavigationBarItem(
                            selected = selected,
                            onClick = { screen = destination },
                            icon = {
                                Text(
                                    destination.glyph,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                )
                            },
                            label = { Text(destination.short, maxLines = 1) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(store, onOpen = { screen = it })
                    Screen.FACTORY -> FactoryScreen(store)
                    Screen.CONTRACTS -> ContractsScreen(store)
                    Screen.EMPLOYEES -> EmployeesScreen(store)
                    Screen.MACHINES -> MachinesScreen(store)
                    Screen.FINANCE -> FinanceScreen(store)
                    Screen.PROGRESSION -> ProgressionScreen(store)
                    Screen.PROFILE -> ProfileScreen(store)
                    Screen.MINIGAME -> PrecisionMinigameScreen(store)
                    Screen.COMMUNITY -> CommunityScreen(store)
                    Screen.SETTINGS -> SettingsScreen(store)
                    Screen.MORE -> MoreScreen(onOpen = { screen = it })
                }
            }
        }
    }
}

@Composable
private fun IndustrialTopBar(
    store: GameStore,
    screen: Screen,
    onHome: () -> Unit,
) {
    Surface(color = Steel900, tonalElevation = 6.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (screen != Screen.HOME) {
                TextButton(
                    onClick = onHome,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) { Text("‹") }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "USINAGEM MASTER",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = SafetyAmber,
                )
                Text(
                    screen.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(GameStore.money(store.state.company.cashCents), fontWeight = FontWeight.Black)
                Text(
                    "N${store.state.company.companyLevel} • REP ${store.state.company.reputation}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    store: GameStore,
    onOpen: (Screen) -> Unit,
) {
    val d = store.dashboard
    val p = store.production
    val idle = store.idleEmployee
    val open = WorkLifeRules.factoryOpen(store.state.shiftMode, currentTimeMillis())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            HeroCard(
                eyebrow = if (open) "TURNO ATIVO" else "FORA DO EXPEDIENTE",
                title = d.companyName,
                subtitle = "${d.machines} máquina(s) • ${d.employees} funcionário(s) • ${d.activeContracts} contrato(s) ativo(s)",
                accent = if (open) ProductionGreen else Steel500,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactStat("Caixa", GameStore.money(d.cashCents), Modifier.weight(1f))
                    CompactStat("Carga", GameStore.money(store.pendingCargoCents), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                WarehouseBar(d.usedWarehouseSpace, d.warehouseSpace)
            }
        }

        if (store.pendingCargo.isNotEmpty()) {
            item {
                AttentionCard(
                    title = "CARGA AGUARDANDO EXPEDIÇÃO",
                    text = "${one(store.pendingCargoUnits)} peças • ${GameStore.money(store.pendingCargoCents)} ainda fora do caixa.",
                    action = "Abrir Fábrica Viva",
                    onAction = { onOpen(Screen.FACTORY) },
                )
            }
        }

        if (idle != null) {
            item {
                AttentionCard(
                    title = "${idle.name} está no celular",
                    text = "A máquina do funcionário perde produção até ele voltar ou você aplicar uma bronca.",
                    action = "Gerenciar equipe",
                    onAction = { onOpen(Screen.EMPLOYEES) },
                    danger = true,
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Produção / 10 min", "${one(p.totalUnitsPer10Minutes)} pç", "Qualidade ${p.averageQuality}%", Modifier.weight(1f))
                MetricCard("Lucro / 10 min", GameStore.money(p.netPer10MinutesCents), "3x no fechamento", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Operação", "${p.operatingMachines}/${d.machines}", "máquinas produzindo", Modifier.weight(1f))
                MetricCard("Impulsos", store.state.boostTokens.toString(), if (store.minigameAvailable) "Precisão disponível" else "Minigame em recarga", Modifier.weight(1f))
            }
        }

        item { SectionTitle("Ações do dono", "Atalhos para as rotinas que mudam o jogo") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = store::dailyBonus, modifier = Modifier.weight(1f)) {
                    Text("Bônus diário")
                }
                OutlinedButton(onClick = { onOpen(Screen.MINIGAME) }, modifier = Modifier.weight(1f)) {
                    Text("Precisão")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = store::boost10Minutes, modifier = Modifier.weight(1f)) {
                    Text("+10 min")
                }
                OutlinedButton(onClick = store::claimDailyGachaTicket, modifier = Modifier.weight(1f)) {
                    Text("Ficha diária")
                }
            }
        }

        item { SectionTitle("Gestão industrial", "Tudo que existe no jogo, sem telas de conversão") }
        item {
            ManagementGrid(
                entries = listOf(
                    Triple(Screen.FACTORY, "Fábrica Viva", "2.5D, carga e chão de fábrica"),
                    Triple(Screen.MACHINES, "Máquinas", "Loja, manutenção e células premium"),
                    Triple(Screen.EMPLOYEES, "Funcionários", "Equipe, exaustão, Copa e disciplina"),
                    Triple(Screen.CONTRACTS, "Contratos", "Qualidade, ferramentas, prazo e prêmio"),
                    Triple(Screen.PROGRESSION, "Evolução", "Metas, pesquisa e especialização"),
                    Triple(Screen.PROFILE, "Personagem", "Avatar, skins, skills e roleta"),
                    Triple(Screen.FINANCE, "Finanças", "Caixa e histórico de lançamentos"),
                    Triple(Screen.COMMUNITY, "Comunidade", "Perfil público e camada online"),
                ),
                onOpen = onOpen,
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun FactoryScreen(store: GameStore) {
    val p = store.production
    val owner = store.ownerFrame
    val idle = store.idleEmployee

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { FactoryStudio(store) }

        item {
            StatusStrip(
                items = listOf(
                    "Produção" to "${one(p.totalUnitsPer10Minutes)} pç",
                    "Qualidade" to "${p.averageQuality}%",
                    "Carga" to GameStore.money(store.pendingCargoCents),
                )
            )
        }

        item {
            IndustrialCard("Expedição do dono", "A carga só vira caixa depois da viagem") {
                Text(
                    "${one(store.pendingCargoUnits)} peças • ${GameStore.money(store.pendingCargoCents)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Status: ${owner.activity.label}",
                    color = if (owner.busy) SafetyAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = store::startCargoDelivery,
                    enabled = store.pendingCargo.isNotEmpty() && !owner.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (owner.busy) "Entrega em andamento" else "Levar CARGA para entrega")
                }
            }
        }

        item {
            IndustrialCard("Ritmo de produção", "Controles do turno ficam no fluxo da página e não cobrem as máquinas") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = store::boost10Minutes, modifier = Modifier.weight(1f)) {
                        Text("+10 MIN • ${store.state.boostTokens}")
                    }
                    OutlinedButton(onClick = store::dailyBonus, modifier = Modifier.weight(1f)) {
                        Text("Bônus")
                    }
                }
                OutlinedButton(onClick = store::buySnack, modifier = Modifier.fillMaxWidth()) {
                    Text(if (store.snackActive) "Copa abastecida • foco ativo" else "Comprar cento de salgados • R$ 250,00")
                }
            }
        }

        if (idle != null) {
            item {
                AttentionCard(
                    title = "${idle.name} está no celular",
                    text = "A Fábrica Viva mostra esse operador parado. A bronca encerra a ociosidade e dá 1h de tolerância.",
                    action = "Dar bronca",
                    onAction = store::reprimandIdleEmployee,
                    danger = true,
                )
            }
        }

        item { SectionTitle("Células de produção", "Estado real de cada máquina") }
        items(store.state.machines) { machine ->
            val def = MachineCatalog.byType(machine.machineType)
            val frame = store.factoryFrame.machines.firstOrNull { it.id == machine.id }
            IndustrialCard(def?.name ?: machine.machineType, "Baia ${machine.gridX + 1}.${machine.gridY + 1}") {
                StatePill(frame?.state?.label ?: "Aguardando simulação", machineStateColor(frame?.state))
                Text("Condição ${machine.condition}/1000 • nível ${machine.level}")
                Text(
                    "Operador: ${store.state.employees.firstOrNull { it.assignedMachineId == machine.id }?.name ?: "não atribuído"}"
                )
                if (frame?.needsMaintenance == true) {
                    Text("Manutenção recomendada", color = SafetyAmber, fontWeight = FontWeight.Bold)
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MachinesScreen(store: GameStore) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle(
                "Parque fabril",
                "${store.state.machines.size} instalada(s) • ${store.state.company.usedWarehouseSpace}/${store.state.company.warehouseSpace} m²"
            )
        }
        items(store.state.machines) { machine ->
            val def = MachineCatalog.byType(machine.machineType)
            IndustrialCard(def?.name ?: machine.machineType, "Condição ${machine.condition}/1000") {
                val operator = store.state.employees.firstOrNull { it.assignedMachineId == machine.id }
                Text("Operador: ${operator?.name ?: "sem operador"}")
                Text("Produção-base: ${one(def?.baseProductionPerHour ?: 0.0)} pç/h")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { store.repairMachine(machine.id) }, modifier = Modifier.weight(1f)) { Text("Manutenção") }
                    OutlinedButton(onClick = { store.moveMachineNext(machine.id) }, modifier = Modifier.weight(1f)) { Text("Mover") }
                }
                TextButton(onClick = { store.sellMachine(machine.id) }) { Text("Revender máquina") }
            }
        }

        item { SectionTitle("Loja de máquinas", "Máquinas convencionais e CNC do catálogo") }
        items(store.machineShop) { def ->
            IndustrialCard(def.name, "Nível de qualidade ${def.quality}") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(GameStore.money(def.priceCents), fontWeight = FontWeight.Black)
                    Text("${def.space} m²")
                }
                Text("${one(def.baseProductionPerHour)} pç/h • ${one(def.powerKw)} kW")
                Button(onClick = { store.buyMachine(def.type.name) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Comprar e instalar")
                }
            }
        }

        item { SectionTitle("Tecnologia premium", "Bônus permanentes da expansão industrial") }
        items(GameProgression.premiumMachines) { premium ->
            val owned = premium.id in store.state.expansion.premiumMachines
            IndustrialCard("${premium.name} • ${premium.rarity.label}", "Libera no nível ${premium.minLevel}") {
                Text(premium.description)
                Text(GameStore.money(premium.priceCents), fontWeight = FontWeight.Black)
                Button(
                    onClick = { store.buyPremiumMachine(premium.id) },
                    enabled = !owned && store.state.company.companyLevel >= premium.minLevel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (owned) "Adquirida" else "Adquirir célula premium")
                }
            }
        }
    }
}

@Composable
private fun EmployeesScreen(store: GameStore) {
    val idle = store.idleEmployee
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = store::hireEmployee, modifier = Modifier.weight(1f)) { Text("Contratar") }
                OutlinedButton(onClick = store::buySnack, modifier = Modifier.weight(1f)) {
                    Text(if (store.snackActive) "Foco ✓" else "Salgados")
                }
            }
        }

        if (idle != null) {
            item {
                AttentionCard(
                    title = "CELULAR • ${idle.name}",
                    text = "Ociosidade temporária reduz a produção da máquina desse operador.",
                    action = "Dar bronca",
                    onAction = store::reprimandIdleEmployee,
                    danger = true,
                )
            }
        }

        item {
            IndustrialCard("Copa e exaustão", "Turno ${if (store.state.shiftMode == ShiftMode.DAY_12H) "07:00–19:00" else "24 horas"}") {
                Text("Em 24h a exaustão sobe mais rápido. A Copa recupera a equipe e funcionários muito cansados reduzem a eficiência.")
                if (store.snackActive) {
                    StatePill("Foco protegido por salgados", ProductionGreen)
                }
            }
        }

        items(store.state.employees) { employee ->
            val isIdle = idle?.id == employee.id
            IndustrialCard(
                employee.name,
                "${employee.specialty} • skill ${employee.skillLevel} • moral ${employee.morale}",
            ) {
                if (employee.legendaryCode != null) StatePill("LENDÁRIO", RoyalPurple)
                if (isIdle) StatePill("NO CELULAR", DangerRed)
                Text("Traço: ${employee.trait}")
                Text("Experiência: ${employee.experience} min")
                Text("Exaustão: ${employee.fatigue.toInt()}%")
                LinearProgressIndicator(
                    progress = (employee.fatigue / 100.0).toFloat().coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
                val machine = store.state.machines.firstOrNull { it.id == employee.assignedMachineId }
                Text("Posto: ${machine?.let { MachineCatalog.byType(it.machineType)?.name } ?: "sem máquina"}")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { store.assignEmployeeNext(employee.id) }, modifier = Modifier.weight(1f)) { Text("Atribuir") }
                    OutlinedButton(onClick = { store.restEmployee(employee.id) }, modifier = Modifier.weight(1f)) { Text("Copa") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { store.unassignEmployee(employee.id) }) { Text("Liberar posto") }
                    TextButton(onClick = { store.fireEmployee(employee.id) }) { Text("Desligar") }
                }
            }
        }

        if (store.state.employees.isEmpty()) {
            item {
                EmptyState("A máquina inicial precisa de operador", "Contrate seu primeiro funcionário para iniciar produção.")
            }
        }
    }
}

@Composable
private fun ContractsScreen(store: GameStore) {
    var filter by remember { mutableStateOf("ATIVOS") }
    val all = store.state.contracts
    val filtered = when (filter) {
        "ATIVOS" -> all.filter { it.status == "ACTIVE" }
        "DISPONÍVEIS" -> all.filter { it.status == "AVAILABLE" && store.contractLockReason(it) == "Disponível" }
        "HISTÓRICO" -> all.filter { it.status in setOf("COMPLETED", "FAILED", "CANCELLED") }
        else -> all
    }.sortedWith(compareBy<ContractSave> { statusOrder(it.status) }.thenByDescending { it.difficulty })

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("ATIVOS", "DISPONÍVEIS", "HISTÓRICO", "TODOS").forEach { value ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value) })
                }
            }
        }

        items(filtered) { contract ->
            ContractCard(store, contract)
        }

        if (filtered.isEmpty()) {
            item { EmptyState("Nenhum contrato nesta aba", "A lista se atualiza de acordo com sua progressão.") }
        }
    }
}

@Composable
private fun ContractCard(store: GameStore, contract: ContractSave) {
    val toolId = store.state.expansion.contractTools[contract.id]
    val tool = GameProgression.tools.firstOrNull { it.id == toolId }
    val progress = if (contract.quantity <= 0) 0f
    else (contract.productionProgressMilli / (contract.quantity * 1000f)).coerceIn(0f, 1f)
    val lockedReason = store.contractLockReason(contract)

    IndustrialCard(
        "${if (contract.special) "⚠ " else ""}${contract.clientName}",
        "${contract.type} • dificuldade ${contract.difficulty}",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${contract.completedQuantity}/${contract.quantity} peças")
            Text("${contract.requiredQuality}% qualidade", fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(GameStore.money(contract.rewardCents), fontWeight = FontWeight.Black, color = ProductionGreen)
            Text("REP +${contract.reputationReward}")
        }
        Text("Prazo: ${deadlineText(contract.deadlineAt)}")
        StatePill(contractLabel(contract.status), contractStateColor(contract.status))

        if (contract.status in setOf("AVAILABLE", "ACTIVE")) {
            Text("Ferramenta: ${tool?.name ?: "nenhuma"}", style = MaterialTheme.typography.bodySmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    AssistChip(
                        onClick = { store.bindTool(contract.id, null) },
                        label = { Text("Sem ferramenta") },
                    )
                }
                items(GameProgression.tools.filter { (store.state.expansion.tools[it.id] ?: 0) > 0 }) { def ->
                    AssistChip(
                        onClick = { store.bindTool(contract.id, def.id) },
                        label = { Text("${def.name} ×${store.state.expansion.tools[def.id] ?: 0}") },
                    )
                }
            }
        }

        when (contract.status) {
            "AVAILABLE" -> {
                if (lockedReason == "Disponível") {
                    Button(onClick = { store.acceptContract(contract.id) }, modifier = Modifier.fillMaxWidth()) { Text("Aceitar contrato") }
                } else {
                    Text(lockedReason, color = SafetyAmber)
                }
            }
            "ACTIVE" -> {
                OutlinedButton(onClick = { store.cancelContract(contract.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar • multa ${GameStore.money(contract.penaltyCents)}")
                }
            }
            "COMPLETED", "FAILED", "CANCELLED" -> {
                TextButton(onClick = { store.archiveContract(contract.id) }) { Text("Enviar para arquivo") }
            }
        }
    }
}

@Composable
private fun FinanceScreen(store: GameStore) {
    val income = store.state.finances.filter { it.type == "INCOME" }.sumOf { it.amountCents }
    val expenses = store.state.finances.filter { it.type == "EXPENSE" }.sumOf { it.amountCents }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Entradas", GameStore.money(income), "histórico salvo", Modifier.weight(1f), ProductionGreen)
                MetricCard("Saídas", GameStore.money(expenses), "histórico salvo", Modifier.weight(1f), DangerRed)
            }
        }
        item {
            IndustrialCard("Caixa atual", "Carga sem entrega não entra aqui") {
                Text(GameStore.money(store.state.company.cashCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Em expedição: ${GameStore.money(store.pendingCargoCents)}")
            }
        }
        items(store.state.finances.asReversed()) { finance ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = Steel900),
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(finance.description, fontWeight = FontWeight.Bold)
                        Text("${finance.category} • ${finance.type}", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        (if (finance.type == "EXPENSE") "− " else "+ ") + GameStore.money(finance.amountCents),
                        color = if (finance.type == "EXPENSE") DangerRed else ProductionGreen,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressionScreen(store: GameStore) {
    var tab by remember { mutableStateOf("METAS") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("METAS", "GALPÃO", "ESPECIALIDADE", "PESQUISA").forEach {
                    FilterChip(selected = tab == it, onClick = { tab = it }, label = { Text(it) })
                }
            }
        }

        when (tab) {
            "METAS" -> {
                items(store.state.goals) { goal ->
                    val progress = store.goalProgress(goal)
                    IndustrialCard(goal.title, "$progress/${goal.target}") {
                        LinearProgressIndicator(
                            progress = (progress.toFloat() / goal.target.coerceAtLeast(1)).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            GameStore.money(goal.rewardCents) +
                                if (goal.ticketReward > 0) " • +${goal.ticketReward} ficha(s)" else ""
                        )
                        Button(
                            onClick = { store.claimGoal(goal.id) },
                            enabled = !goal.claimed && progress >= goal.target,
                        ) { Text(if (goal.claimed) "Resgatada" else "Resgatar") }
                    }
                }
            }
            "GALPÃO" -> item {
                val level = ((store.state.company.warehouseSpace - 100) / 50) + 1
                val cost = 2_000_000L * level
                IndustrialCard("Expansão do galpão", "Nível estrutural $level") {
                    WarehouseBar(store.state.company.usedWarehouseSpace, store.state.company.warehouseSpace)
                    Text("Nova área: ${store.state.company.warehouseSpace + 50} m²")
                    Text("Preço: ${GameStore.money(cost)}")
                    Text("Saldo após: ${GameStore.money(store.state.company.cashCents - cost)}")
                    Button(onClick = store::expandWarehouse, modifier = Modifier.fillMaxWidth()) {
                        Text("Expandir +50 m²")
                    }
                }
            }
            "ESPECIALIDADE" -> {
                items(GameProgression.specialties) { specialty ->
                    val active = store.state.expansion.specialty == specialty.code
                    IndustrialCard(specialty.label, "Nível mínimo ${specialty.minLevel}") {
                        Text(specialty.description)
                        Button(
                            onClick = { store.setSpecialty(specialty.code) },
                            enabled = store.state.company.companyLevel >= specialty.minLevel,
                        ) { Text(if (active) "Especialidade ativa" else "Escolher") }
                    }
                }
            }
            "PESQUISA" -> {
                item {
                    Text(
                        "Pontos disponíveis: ${GameProgression.companySkillPoints(store.state.company.companyLevel, store.state.expansion.companySkills)}",
                        fontWeight = FontWeight.Black,
                    )
                }
                items(GameProgression.companySkills) { skill ->
                    SkillCard(
                        skill = skill,
                        owned = skill.id in store.state.expansion.companySkills,
                        onUnlock = { store.unlockCompanySkill(skill.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(store: GameStore) {
    var draft by remember(store.state.profile) { mutableStateOf(store.state.profile) }
    var tab by remember { mutableStateOf("AVATAR") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PlayerAvatarPreview(
                avatar = draft,
                modifier = Modifier.fillMaxWidth(),
                size = 190.dp,
                phase = .18f,
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("AVATAR", "SKINS", "SKILLS", "ROULETA", "PERSONAGENS").forEach {
                    FilterChip(selected = tab == it, onClick = { tab = it }, label = { Text(it) })
                }
            }
        }

        when (tab) {
            "AVATAR" -> {
                item {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it.take(26)) },
                        label = { Text("Nome") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { ChoiceSection("Gênero", listOf("MALE" to "Homem", "FEMALE" to "Mulher"), draft.gender) { draft = draft.copy(gender = it) } }
                item {
                    ChoiceSection(
                        "Estilo",
                        listOf(
                            "WORKSHOP" to "Oficina", "TATUZAO" to "Tatuzão", "PRINCESA" to "Princesa",
                            "PINOQUIO" to "Pinóquio", "MAGRAO" to "Magrão", "KENDAO_KIMONO" to "Kendão",
                            "TREME_TREME" to "Treme-treme", "BEBADO" to "Bêbado",
                        ),
                        draft.skinStyle,
                    ) { draft = draft.copy(skinStyle = it) }
                }
                item { ChoiceSection("Corpo", listOf("SLIM" to "Magro", "STANDARD" to "Padrão", "STRONG" to "Forte"), draft.bodyType) { draft = draft.copy(bodyType = it) } }
                item { ChoiceSection("Pele", listOf("LIGHT" to "Clara", "MEDIUM" to "Média", "TAN" to "Bronzeada", "DARK" to "Escura"), draft.skinTone) { draft = draft.copy(skinTone = it) } }
                item { ChoiceSection("Cabelo", listOf("SHORT" to "Curto", "BUZZ" to "Raspado", "MOHAWK" to "Moicano", "LONG" to "Longo", "PONYTAIL" to "Rabo", "CURLY" to "Cacheado", "BALD" to "Careca"), draft.hairStyle) { draft = draft.copy(hairStyle = it) } }
                item { ChoiceSection("Cor do cabelo", listOf("DARK" to "Escuro", "BROWN" to "Castanho", "BLONDE" to "Loiro", "GRAY" to "Cinza"), draft.hairColor) { draft = draft.copy(hairColor = it) } }
                item { ChoiceSection("Uniforme", listOf("NAVY" to "Marinho", "BLUE" to "Azul", "GRAPHITE" to "Grafite", "GREEN" to "Verde", "ORANGE" to "Laranja"), draft.uniformColor) { draft = draft.copy(uniformColor = it) } }
                item { ChoiceSection("Capacete", listOf("YELLOW" to "Amarelo", "WHITE" to "Branco", "BLUE" to "Azul", "RED" to "Vermelho", "BLACK" to "Preto", "NONE" to "Sem"), draft.helmetColor) { draft = draft.copy(helmetColor = it) } }
                item { ChoiceSection("Acessório", listOf("NONE" to "Nenhum", "GLASSES" to "Óculos", "HEADSET" to "Headset"), draft.accessory) { draft = draft.copy(accessory = it) } }
                item {
                    Button(
                        onClick = { store.updateProfile(draft) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Salvar personagem") }
                }
            }
            "SKINS" -> items(GameProgression.skins) { skin ->
                val owned = skin.id in store.state.expansion.ownedSkins
                IndustrialCard("${skin.name} • ${skin.rarity.label}", "Nível ${skin.minLevel}") {
                    Text(skin.description)
                    Button(
                        onClick = { store.equipSkin(skin.id) },
                        enabled = owned,
                    ) {
                        Text(
                            when {
                                store.state.expansion.equippedSkin == skin.id -> "Equipada"
                                owned -> "Equipar"
                                skin.gachaOnly -> "Somente na roleta"
                                else -> "Bloqueada por nível"
                            }
                        )
                    }
                }
            }
            "SKILLS" -> {
                item {
                    Text(
                        "Pontos: ${GameProgression.playerSkillPoints(store.state.company.companyLevel, store.state.expansion.playerSkills)}",
                        fontWeight = FontWeight.Black,
                    )
                }
                items(GameProgression.playerSkills) { skill ->
                    SkillCard(skill, skill.id in store.state.expansion.playerSkills) {
                        store.unlockPlayerSkill(skill.id)
                    }
                }
            }
            "ROULETA" -> item {
                IndustrialCard("Roleta Industrial", "Pity épico ${store.state.expansion.pityEpic}/30 • lendário ${store.state.expansion.pityLegendary}/80") {
                    Text("${store.state.expansion.gachaTickets} ficha(s)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Não sorteia ficha como prêmio. Personagens/skins/máquinas únicas não repetem enquanto houver opção nova.")
                    store.lastGachaReward?.let {
                        StatePill("${it.rarity.label} • ${it.title}", rarityColor(it.rarity))
                    }
                    Button(onClick = store::spinGacha, modifier = Modifier.fillMaxWidth()) { Text("GIRAR") }
                    OutlinedButton(onClick = store::claimDailyGachaTicket, modifier = Modifier.fillMaxWidth()) { Text("Coletar ficha diária") }
                }
            }
            "PERSONAGENS" -> {
                item {
                    AssistChip(onClick = { store.equipCharacter(null) }, label = { Text("Sem personagem bônus") })
                }
                items(GameProgression.characters) { character ->
                    val owned = character.id in store.state.expansion.ownedCharacters
                    IndustrialCard("${character.name} • ${character.rarity.label}", "Nível ${character.minLevel}") {
                        Text(character.description)
                        Button(
                            onClick = { store.equipCharacter(character.id) },
                            enabled = owned && store.state.company.companyLevel >= character.minLevel,
                        ) {
                            Text(
                                when {
                                    store.state.expansion.equippedCharacter == character.id -> "Em uso"
                                    owned -> "Usar na empresa"
                                    else -> "Não obtido"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrecisionMinigameScreen(store: GameStore) {
    var active by remember { mutableStateOf(false) }
    var marker by remember { mutableStateOf(0f) }
    var direction by remember { mutableStateOf(1f) }

    LaunchedEffect(active) {
        while (active) {
            delay(16L)
            var next = marker + direction * .018f
            if (next >= 1f) {
                next = 1f
                direction = -1f
            } else if (next <= 0f) {
                next = 0f
                direction = 1f
            }
            marker = next
        }
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        IndustrialCard("Relógio comparador", "Pare o ponteiro no centro da tolerância") {
            Text(
                if (store.minigameAvailable) "Disponível agora" else "Recarga: ${durationText(store.minigameRemainingMillis)}",
                color = if (store.minigameAvailable) ProductionGreen else SafetyAmber,
                fontWeight = FontWeight.Bold,
            )
            Box(
                Modifier.fillMaxWidth().height(42.dp).background(Steel800, RoundedCornerShape(14.dp))
            ) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .width(54.dp)
                        .fillMaxHeight()
                        .background(ProductionGreen.copy(alpha = .28f))
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .offset(x = ((marker - .5f) * 270f).dp)
                        .align(Alignment.Center)
                        .background(SafetyAmber)
                )
            }
            val liveScore = (1.0 - abs(marker - .5f) * 2.0).coerceIn(0.0, 1.0)
            Text("Precisão estimada: ${GameStore.percent(liveScore)}")
            Button(
                onClick = {
                    if (!active) {
                        marker = 0f
                        direction = 1f
                        active = true
                    } else {
                        active = false
                        store.settlePrecisionMinigame(liveScore)
                    }
                },
                enabled = store.minigameAvailable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (active) "TRAVAR MEDIÇÃO" else "INICIAR DESAFIO")
            }
            Text("Melhor resultado: ${GameStore.percent(store.state.bestMinigameScore)}")
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatarPreview(store.state.profile, size = 110.dp)
                Column(Modifier.weight(1f)) {
                    Text(store.state.profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(store.state.company.name)
                    Text("Nível ${store.state.company.companyLevel} • REP ${store.state.company.reputation}")
                    Text("${one(store.production.totalUnitsPer10Minutes)} pç/10min")
                }
            }
        }
        item {
            IndustrialCard("Modo offline completo", "Seu progresso local não depende do Firebase") {
                StatePill("SAVE LOCAL ATIVO", ProductionGreen)
                Text("Máquinas, contratos, fábrica, carga, equipe, personagem, economia e progressão continuam funcionando sem login.")
            }
        }
        item {
            IndustrialCard("Multiplayer assíncrono", "Camada opcional do Android") {
                StatePill("FIREBASE iOS NÃO CONFIGURADO", SafetyAmber)
                Text("Ranking, presença, visitas a outras fábricas e envio de apoio exigem cadastrar o bundle iOS no mesmo projeto Firebase e fornecer GoogleService-Info.plist.")
                Text("A integração online não é simulada: sem credencial iOS, nenhuma ação falsa é exibida.")
            }
        }
    }
}

@Composable
private fun SettingsScreen(store: GameStore) {
    val settings = store.state.uiSettings
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            IndustrialCard("Experiência", "Preferências persistidas no save") {
                SettingToggle("Som industrial", settings.soundEnabled) {
                    store.updateUiSettings(settings.copy(soundEnabled = it))
                }
                SettingToggle("Resposta tátil", settings.hapticsEnabled) {
                    store.updateUiSettings(settings.copy(hapticsEnabled = it))
                }
                SettingToggle("Falas de lendários", settings.legendarySpeechEnabled) {
                    store.updateUiSettings(settings.copy(legendarySpeechEnabled = it))
                }
                Text("Duração da fala: ${settings.legendarySpeechSeconds}s")
                Slider(
                    value = settings.legendarySpeechSeconds.toFloat(),
                    onValueChange = {
                        store.updateUiSettings(settings.copy(legendarySpeechSeconds = it.toInt().coerceIn(2, 12)))
                    },
                    valueRange = 2f..12f,
                    steps = 9,
                )
            }
        }
        item {
            IndustrialCard("Turno de trabalho", "A opção altera produção e exaustão") {
                Button(onClick = { store.setShift(ShiftMode.DAY_12H) }, modifier = Modifier.fillMaxWidth()) {
                    Text("07:00–19:00 ${if (store.state.shiftMode == ShiftMode.DAY_12H) "✓" else ""}")
                }
                OutlinedButton(onClick = { store.setShift(ShiftMode.CONTINUOUS_24H) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Operação 24 horas ${if (store.state.shiftMode == ShiftMode.CONTINUOUS_24H) "✓" else ""}")
                }
            }
        }
        item {
            IndustrialCard("Persistência", "Schema 3 • compatível com o save criado na V6") {
                Text("O estado é gravado no armazenamento nativo do iPhone após cada transação relevante.")
                TextButton(onClick = store::resetSave) { Text("Apagar save e iniciar nova oficina") }
            }
        }
    }
}

@Composable
private fun MoreScreen(onOpen: (Screen) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionTitle("Gestão completa", "Áreas secundárias sem esconder recursos") }
        items(
            listOf(
                Triple(Screen.MACHINES, "Máquinas e loja", "Parque fabril + premium"),
                Triple(Screen.FINANCE, "Finanças", "Caixa e lançamentos"),
                Triple(Screen.PROGRESSION, "Empresa e pesquisa", "Metas, galpão, especialidade e skills"),
                Triple(Screen.PROFILE, "Meu personagem", "Avatar, skins, personagens e roleta"),
                Triple(Screen.MINIGAME, "Desafio de precisão", "Recompensa e impulsos"),
                Triple(Screen.COMMUNITY, "Comunidade", "Perfil público e Firebase"),
                Triple(Screen.SETTINGS, "Configurações", "Turno, experiência e save"),
            )
        ) { entry ->
            ElevatedCard(onClick = { onOpen(entry.first) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(entry.second, fontWeight = FontWeight.Black)
                    Text(entry.third, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SkillCard(skill: SkillDef, owned: Boolean, onUnlock: () -> Unit) {
    IndustrialCard(skill.name, "Nível mínimo ${skill.minLevel}") {
        Text(skill.description)
        skill.prerequisite?.let { Text("Pré-requisito: $it", style = MaterialTheme.typography.bodySmall) }
        Button(onClick = onUnlock, enabled = !owned) {
            Text(if (owned) "Desbloqueada" else "Desbloquear")
        }
    }
}

@Composable
private fun ChoiceSection(
    title: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    IndustrialCard(title, "Personalização visual usada também na Fábrica Viva") {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            choices.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun IndustrialCard(
    title: String,
    subtitle: String? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Steel900),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            body()
        }
    }
}

@Composable
private fun HeroCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    body: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Steel850),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(eyebrow, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            body()
        }
    }
}

@Composable
private fun AttentionCard(
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
    danger: Boolean = false,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (danger) DangerRed.copy(alpha = .14f) else SafetyAmber.copy(alpha = .12f)
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontWeight = FontWeight.Black, color = if (danger) DangerRed else SafetyAmber)
            Text(text)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = SafetyAmber,
) {
    ElevatedCard(modifier = modifier, colors = CardDefaults.elevatedCardColors(containerColor = Steel900)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = accent)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactStat(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Steel900, RoundedCornerShape(14.dp)).padding(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StatusStrip(items: List<Pair<String, String>>) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Steel900)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    Text(value, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WarehouseBar(used: Int, total: Int) {
    val safeTotal = total.coerceAtLeast(1)
    val progress = (used.toFloat() / safeTotal).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Ocupação do galpão", style = MaterialTheme.typography.labelSmall)
        Text("$used/$total m²", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun StatePill(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color.copy(alpha = .18f), shape = RoundedCornerShape(999.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun EmptyState(title: String, text: String) {
    IndustrialCard(title, text) {
        Text("A progressão será refletida aqui automaticamente.")
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ManagementGrid(
    entries: List<Triple<Screen, String, String>>,
    onOpen: (Screen) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { entry ->
                    ElevatedCard(
                        onClick = { onOpen(entry.first) },
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.elevatedCardColors(containerColor = Steel900),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(entry.second, fontWeight = FontWeight.Black)
                            Text(entry.third, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun one(value: Double): String {
    val scaled = (value * 10.0).toLong()
    return "${scaled / 10L},${abs(scaled % 10L)}"
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
    "CANCELLED" -> "Cancelado com multa"
    else -> status
}

private fun contractStateColor(status: String) = when (status) {
    "ACTIVE" -> ElectricBlue
    "COMPLETED" -> ProductionGreen
    "FAILED", "CANCELLED" -> DangerRed
    else -> SafetyAmber
}

private fun machineStateColor(state: FactoryMachineState?) = when (state) {
    FactoryMachineState.RUNNING -> ProductionGreen
    FactoryMachineState.SETUP, FactoryMachineState.WAITING_MATERIAL -> SafetyAmber
    FactoryMachineState.BROKEN -> DangerRed
    FactoryMachineState.MAINTENANCE -> androidx.compose.ui.graphics.Color(0xFFE28A4B)
    else -> Steel500
}

private fun rarityColor(rarity: RarityDef) = when (rarity) {
    RarityDef.COMMON -> Steel200
    RarityDef.RARE -> ElectricBlue
    RarityDef.EPIC -> RoyalPurple
    RarityDef.LEGENDARY -> SafetyAmber
}

private fun deadlineText(deadline: Long): String {
    val remaining = (deadline - currentTimeMillis()).coerceAtLeast(0L)
    return if (remaining <= 0L) "expirado"
    else "${remaining / 3_600_000L}h ${(remaining % 3_600_000L) / 60_000L}min"
}

private fun durationText(millis: Long): String =
    "${millis / 60_000L}min ${(millis % 60_000L) / 1_000L}s"
