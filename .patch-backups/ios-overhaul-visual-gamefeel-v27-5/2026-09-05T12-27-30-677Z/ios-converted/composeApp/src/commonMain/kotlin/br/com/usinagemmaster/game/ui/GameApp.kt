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
import androidx.compose.ui.graphics.Color
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

private const val GAME_APP_V27 = "game_app_visual_v27_4"

private enum class Screen(val title: String, val short: String, val glyph: String) {
    HOME("Painel executivo", "Início", "⌂"),
    FACTORY("Fábrica Viva • Studio", "Fábrica", "▦"),
    CONTRACTS("Contratos industriais", "Contratos", "▤"),
    EMPLOYEES("Equipe e disciplina", "Equipe", "♟"),
    MACHINES("Máquinas instaladas", "Máquinas", "⚙"),
    STORE("Loja industrial", "Loja", "▣"),
    FINANCE("Finanças", "Finanças", "R$"),
    PROGRESSION("Empresa e pesquisa", "Evolução", "↗"),
    PROFILE("Meu personagem", "Perfil", "●"),
    ROULETTE("Roleta Industrial", "Roleta", "◈"),
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
    var enteredFactory by remember { mutableStateOf(false) }
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
        if (!enteredFactory) {
            AndroidV24MainMenu(
                store = store,
                onEnter = {
                    screen = Screen.HOME
                    enteredFactory = true
                },
                onProfile = {
                    screen = Screen.PROFILE
                    enteredFactory = true
                },
                onCommunity = {
                    screen = Screen.COMMUNITY
                    enteredFactory = true
                },
                onSettings = {
                    screen = Screen.SETTINGS
                    enteredFactory = true
                },
            )
            return@UsinagemMasterTheme
        }

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
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = Steel900,
                    tonalElevation = 8.dp,
                ) {
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
                    Screen.FACTORY -> FactoryScreen(store, onOpen = { screen = it })
                    Screen.CONTRACTS -> ContractsScreen(store)
                    Screen.EMPLOYEES -> EmployeesScreen(store)
                    Screen.MACHINES -> MachinesScreen(store)
                    Screen.STORE -> StoreScreen(store)
                    Screen.FINANCE -> FinanceScreen(store)
                    Screen.PROGRESSION -> ProgressionScreen(store)
                    Screen.PROFILE -> ProfileScreen(store, onOpen = { screen = it })
                    Screen.ROULETTE -> IndustrialRouletteScreen(store)
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
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                    color = Steel100,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(GameStore.money(store.state.company.cashCents), fontWeight = FontWeight.Black, color = Steel100)
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
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            HeroCard(
                eyebrow = if (open) "● OPERAÇÃO AO VIVO" else "● TURNO ENCERRADO",
                title = d.companyName,
                subtitle = "Nível ${d.companyLevel} • REP ${d.reputation} • ${d.machines} máquinas • ${d.employees} pessoas",
                accent = if (open) ProductionGreen else SafetyAmber,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    DashboardArtCardV27(DashboardVisualV27.CASH,"Caixa",GameStore.money(d.cashCents),"Disponível",Modifier.weight(1f))
                    DashboardArtCardV27(DashboardVisualV27.CARGO,"Carga",GameStore.money(store.pendingCargoCents),"${one(store.pendingCargoUnits)} pç aguardando",Modifier.weight(1f)){onOpen(Screen.FACTORY)}
                }
                Spacer(Modifier.height(7.dp))
                WarehouseBar(d.usedWarehouseSpace,d.warehouseSpace)
            }
        }

        if (store.pendingCargo.isNotEmpty() || idle != null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    if(store.pendingCargo.isNotEmpty()) DashboardArtCardV27(DashboardVisualV27.FACTORY,"Expedição","${one(store.pendingCargoUnits)} pç","Toque para levar carga",Modifier.weight(1f)){onOpen(Screen.FACTORY)}
                    if(idle!=null) DashboardArtCardV27(DashboardVisualV27.TEAM,"Atenção",idle!!.name,"Está no celular",Modifier.weight(1f)){onOpen(Screen.EMPLOYEES)}
                }
            }
        }

        item { ShiftCommandDeckV27(store) { onOpen(Screen.MINIGAME) } }
        item { DailyMissionsV27_2(store) }
        item { AndroidDashboardProgress(store) }

        item { SectionTitle("Central de gestão", "Abra só o setor que precisa da sua atenção") }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    DashboardArtCardV27(DashboardVisualV27.FACTORY,"Fábrica","${p.operatingMachines}/${d.machines}","produção e layout",Modifier.weight(1f)){onOpen(Screen.FACTORY)}
                    DashboardArtCardV27(DashboardVisualV27.CONTRACT,"Contratos",d.activeContracts.toString(),"ativos agora",Modifier.weight(1f)){onOpen(Screen.CONTRACTS)}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    DashboardArtCardV27(DashboardVisualV27.TEAM,"Equipe",d.employees.toString(),"escalação e fadiga",Modifier.weight(1f)){onOpen(Screen.EMPLOYEES)}
                    DashboardArtCardV27(DashboardVisualV27.RESEARCH,"Evolução",store.state.career.availableSkillPoints().toString(),"pontos industriais",Modifier.weight(1f)){onOpen(Screen.PROGRESSION)}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    DashboardArtCardV27(DashboardVisualV27.ROULETTE,"Roleta",store.state.expansion.gachaTickets.toString(),"fichas disponíveis",Modifier.weight(1f)){onOpen(Screen.ROULETTE)}
                    DashboardArtCardV27(DashboardVisualV27.FACTORY,"Loja",GameStore.money(store.state.company.cashCents),"modernizar parque",Modifier.weight(1f)){onOpen(Screen.STORE)}
                }
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

@Composable
private fun FactoryScreen(
    store: GameStore,
    onOpen: (Screen) -> Unit,
) {
    val p = store.production
    val owner = store.ownerFrame
    val frame = store.factoryFrame
    var mode by remember { mutableStateOf("LIVE") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Surface(color=Steel950) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    StatePill(if(frame.open)"● AO VIVO" else "● FECHADO",if(frame.open)ProductionGreen else Steel500)
                    StatePill("⚙ ${p.operatingMachines} produzindo",ElectricBlue)
                    if(store.pendingCargo.isNotEmpty()) StatePill("📦 ${one(store.pendingCargoUnits)} pç",SafetyAmber)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                FilterChip(selected=mode=="LIVE",onClick={mode="LIVE"},label={Text("● Ao vivo")})
                FilterChip(selected=mode=="LAYOUT",onClick={mode="LAYOUT"},label={Text("▦ Layout")})
                FilterChip(selected=mode=="LIST",onClick={mode="LIST"},label={Text("☷ Lista técnica")})
            }
        }
        when(mode){
            "LIVE" -> {
                item { ShiftCommandDeckV27(store){onOpen(Screen.MINIGAME)} }
                item { FactoryStudio(store,modifier=Modifier.padding(horizontal=2.dp)) }
                item {
                    IndustrialCard("EXPEDIÇÃO DO DONO","A carga só entra no caixa após a viagem") {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                            Column{Text("${one(store.pendingCargoUnits)} peças",fontWeight=FontWeight.Black,color=Steel100);Text(GameStore.money(store.pendingCargoCents),color=ProductionGreen,fontWeight=FontWeight.Black)}
                            StatePill(owner.activity.label,if(owner.busy)SafetyAmber else ProductionGreen)
                        }
                        Button(onClick={store.startCargoDelivery();GameFeedback.play(GameSoundEffect.MACHINE_START,store.state.uiSettings.soundEnabled)},enabled=store.pendingCargo.isNotEmpty()&&!owner.busy,modifier=Modifier.fillMaxWidth()){Text(if(owner.busy)"ENTREGA EM ANDAMENTO" else "LEVAR CARGA PARA ENTREGA")}
                    }
                }
            }
            "LAYOUT" -> item { Box(Modifier.padding(horizontal=12.dp)){ FactoryLayoutEditorV27(store) } }
            else -> item { Box(Modifier.padding(horizontal=12.dp)){ TechnicalListV27(store) } }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MachinesScreen(store: GameStore) {
    LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item { SectionTitle("Parque fabril","${store.state.machines.size} máquinas • ${store.state.company.usedWarehouseSpace}/${store.state.company.warehouseSpace} m²") }
        item { OutlinedButton(onClick=store::autoDistributeOperators,modifier=Modifier.fillMaxWidth()){Text("AUTO DISTRIBUIR MELHOR EQUIPE") } }
        items(store.state.machines){machine->
            val def=MachineCatalog.byType(machine.machineType)
            val frame=store.factoryFrame.machines.firstOrNull{it.id==machine.id}
            val operator=store.state.employees.firstOrNull{it.assignedMachineId==machine.id}
            MachineCatalogCardV27(def?.name ?: machine.machineType,machine.machineType,"BAIA ${machine.gridX+1}.${machine.gridY+1}",status="${frame?.state?.label ?: "Aguardando"} • ${machine.condition/10}%") {
                Text("${one(def?.baseProductionPerHour ?: 0.0)} pç/h • operador ${operator?.name ?: "não atribuído"}",style=MaterialTheme.typography.bodySmall,color=Steel400)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    OutlinedButton(onClick={store.repairMachine(machine.id)},modifier=Modifier.weight(1f)){Text("Manutenção")}
                    OutlinedButton(onClick={store.assignBestOperator(machine.id)},modifier=Modifier.weight(1f)){Text("Melhor operador")}
                }
            }
        }
    }
}

@Composable
private fun StoreScreen(store: GameStore) {
    var tab by remember { mutableStateOf("CATALOG") }
    LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item {
            HeroCard("SHOWROOM INDUSTRIAL","Modernize o parque fabril","A miniatura da loja usa a mesma família visual que aparece no galpão.",SafetyAmber) {
                DashboardArtCardV27(DashboardVisualV27.CASH,"Saldo disponível",GameStore.money(store.state.company.cashCents),"${store.state.company.usedWarehouseSpace}/${store.state.company.warehouseSpace} m² ocupados")
            }
        }
        item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){FilterChip(selected=tab=="CATALOG",onClick={tab="CATALOG"},label={Text("Máquinas")});FilterChip(selected=tab=="PREMIUM",onClick={tab="PREMIUM"},label={Text("Tecnologia premium")})} }
        if(tab=="CATALOG") {
            items(store.machineShop){def->
                val hasSpace=store.state.company.usedWarehouseSpace+def.space<=store.state.company.warehouseSpace
                MachineCatalogCardV27(def.name,def.type.name,"${def.specialty.name} • ${def.space} m²",price=GameStore.money(def.priceCents),status="${one(def.baseProductionPerHour)} pç/h • qualidade ${def.quality}") {
                    Text("${one(def.powerKw)} kW • manutenção ${GameStore.money(def.maintenanceCents)}",style=MaterialTheme.typography.bodySmall,color=Steel400)
                    Button(onClick={store.buyMachine(def.type.name);GameFeedback.play(GameSoundEffect.MACHINE_START,store.state.uiSettings.soundEnabled)},enabled=store.state.company.cashCents>=def.priceCents&&hasSpace,modifier=Modifier.fillMaxWidth()){Text(if(hasSpace)"COMPRAR E INSTALAR" else "GALPÃO SEM ESPAÇO")}
                }
            }
        } else {
            items(GameProgression.premiumMachines){premium->
                val owned=premium.id in store.state.expansion.premiumMachines
                MachineCatalogCardV27(premium.name,premium.id,"${premium.rarity.label} • Nv.${premium.minLevel}",price=GameStore.money(premium.priceCents),status=premium.description) {
                    Button(onClick={store.buyPremiumMachine(premium.id)},enabled=!owned&&store.state.company.companyLevel>=premium.minLevel&&store.state.company.cashCents>=premium.priceCents,modifier=Modifier.fillMaxWidth()){Text(if(owned)"ADQUIRIDA" else "ADQUIRIR TECNOLOGIA")}
                }
            }
        }
    }
}

@Composable
private fun EmployeesScreen(store: GameStore) {
    val idle=store.idleEmployee
    LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                Button(onClick=store::hireEmployee,modifier=Modifier.weight(1f)){Text("Contratar")}
                Button(onClick=store::autoDistributeOperators,modifier=Modifier.weight(1f)){Text("Auto distribuir")}
            }
        }
        item {
            IndustrialCard("FOLHA MENSAL", "Salários são debitados a cada ciclo de 30 dias") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total da equipe", color = Steel400)
                    Text(GameStore.money(store.monthlyPayrollCents), color = DangerRed, fontWeight = FontWeight.Black)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Próximo pagamento", color = Steel400)
                    Text(formatV27Duration(store.monthlyPayrollRemainingMillis), color = SafetyAmber, fontWeight = FontWeight.Bold)
                }
                Text("O primeiro salário é pago na contratação; depois entra na folha mensal.", style = MaterialTheme.typography.labelSmall, color = Steel400)
            }
        }
        item {
            IndustrialCard("ESCALAÇÃO INTELIGENTE","A melhor máquina recebe o operador com maior encaixe técnico") {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Modo foco",color=Steel100,fontWeight=FontWeight.Black);Text(if(store.focusModeRemainingMillis>0L)formatV27Duration(store.focusModeRemainingMillis) else "Disponível",color=if(store.focusModeRemainingMillis>0L)SafetyAmber else ProductionGreen,fontWeight=FontWeight.Black)}
                Button(onClick=store::buySnack,enabled=store.focusModeRemainingMillis==0L,modifier=Modifier.fillMaxWidth()){Text(if(store.focusModeRemainingMillis>0L)"FOCO ATIVO • NÃO ACUMULA" else "ATIVAR MODO FOCO • 8H")}
            }
        }
        if(idle!=null) item { AttentionCard("${idle.name} está no celular","A produção cai enquanto ele não retorna ao posto.","Dar bronca",store::reprimandIdleEmployee,true) }
        item { LegendaryEmployeesPanel(store) }
        items(store.state.employees){employee->
            val machine=store.state.machines.firstOrNull{it.id==employee.assignedMachineId}
            ElevatedCard(colors=CardDefaults.elevatedCardColors(containerColor=Steel900,contentColor=Steel100),shape=RoundedCornerShape(18.dp)){
                Row(Modifier.padding(11.dp),horizontalArrangement=Arrangement.spacedBy(11.dp)){
                    EmployeePortraitV27(employee,Modifier.size(82.dp),idle?.id==employee.id)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(employee.name,fontWeight=FontWeight.Black,color=Steel100);if(employee.legendaryCode!=null)StatePill("LENDÁRIO",RoyalPurple)}
                        Text("${employee.specialty} • Nv.${employee.skillLevel} • ${employee.experience} min",style=MaterialTheme.typography.bodySmall,color=Steel400)
                        Text("Posto: ${machine?.let{MachineCatalog.byType(it.machineType)?.name} ?: "disponível"}",style=MaterialTheme.typography.bodySmall,color=if(machine==null)ProductionGreen else Steel200)
                        Text("Salário mensal: ${GameStore.money(employee.salaryCents)}", style=MaterialTheme.typography.bodySmall, color=SafetyAmber)
                        LinearProgressIndicator(progress=(employee.fatigue/100.0).toFloat().coerceIn(0f,1f),modifier=Modifier.fillMaxWidth())
                        Text("Fadiga ${employee.fatigue.toInt()}% • moral ${employee.morale}",style=MaterialTheme.typography.labelSmall,color=Steel400)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=11.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    OutlinedButton(onClick={store.assignEmployeeNext(employee.id)},modifier=Modifier.weight(1f)){Text("Trocar posto")}
                    OutlinedButton(onClick={store.restEmployee(employee.id)},modifier=Modifier.weight(1f)){Text("Copa")}
                }
            }
        }
        if(store.state.employees.isEmpty()) item{EmptyState("Sua primeira contratação","Contrate um operador para liberar a produção automática da máquina inicial.")}
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
                colors = CardDefaults.elevatedCardColors(containerColor = Steel900, contentColor = Steel100),
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
                listOf("METAS", "GALPÃO", "ESPECIALIDADE", "PESQUISA", "CARREIRA").forEach {
                    FilterChip(selected = tab == it, onClick = { tab = it }, label = { Text(it) })
                }
            }
        }

        when (tab) {
            "METAS" -> {
                item { DailyMissionsV27_2(store) }
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
            "PESQUISA" -> item { CompanySkillStoryboardV27(store) }
            "CARREIRA" -> item { IndustrialCareerStoryboardV27(store) }
        }
    }
}

@Composable
private fun ProfileScreen(
    store: GameStore,
    onOpen: (Screen) -> Unit,
) {
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
                listOf("AVATAR", "SKINS", "SKILLS", "ROLETA", "PERSONAGENS").forEach {
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
            "SKILLS" -> item { PlayerSkillStoryboardV27(store) }
            "ROLETA" -> item {
                IndustrialCard(
                    "Roleta Industrial",
                    "Roda visual, animação, ponteiro, pity e revelação da recompensa"
                ) {
                    Text("${store.state.expansion.gachaTickets} ficha(s)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    store.lastGachaReward?.let {
                        StatePill("Último prêmio • ${it.rarity.label}: ${it.title}", rarityColor(it.rarity))
                    }
                    Button(onClick = { onOpen(Screen.ROULETTE) }, modifier = Modifier.fillMaxWidth()) {
                        Text("ABRIR ROLETA")
                    }
                    OutlinedButton(onClick = store::claimDailyGachaTicket, modifier = Modifier.fillMaxWidth()) {
                        Text(if (store.dailyTicketRemainingMillis == 0L) "Coletar ficha diária" else "Próxima ficha em ${formatV27Duration(store.dailyTicketRemainingMillis)}")
                    }
                }
            }
            "PERSONAGENS" -> {
                item { PrestigeCharacterNotice(store) }
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
    OnlineCommunityScreen(store)
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
                SettingToggle("Falas dos operadores", settings.legendarySpeechEnabled) {
                    store.updateUiSettings(settings.copy(legendarySpeechEnabled = it))
                }
                val speechPace = when {
                    settings.legendarySpeechSeconds >= 11 -> "muito lento"
                    settings.legendarySpeechSeconds >= 8 -> "lento"
                    settings.legendarySpeechSeconds >= 5 -> "normal"
                    else -> "rápido"
                }
                Text("Ritmo das falas: $speechPace • ${settings.legendarySpeechSeconds}s em tela")
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
            IndustrialCard("Conta online", onlineAccountLabel()) {
                Text(
                    "A conta Google libera a identidade online sem substituir ou apagar o save local da oficina."
                )
                Button(
                    onClick = { openOnlineAccountPanel() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Gerenciar conta Google")
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
                Triple(Screen.MACHINES, "Máquinas", "Parque fabril instalado e manutenção"),
                Triple(Screen.STORE, "Loja", "Catálogo de máquinas e tecnologia premium"),
                Triple(Screen.FINANCE, "Finanças", "Caixa e lançamentos"),
                Triple(Screen.PROGRESSION, "Empresa, pesquisa e carreira", "Metas, galpão e árvore industrial"),
                Triple(Screen.PROFILE, "Meu personagem", "Avatar, skins, personagens e skills"),
                Triple(Screen.ROULETTE, "Roleta Industrial", "Roda animada e recompensas"),
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
fun IndustrialCard(
    title: String,
    subtitle: String? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Steel900, contentColor = Steel100),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Steel100)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            body()
        }
    }
}

@Composable
fun HeroCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    body: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Steel850, contentColor = Steel100),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(eyebrow, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Steel100)
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
    ElevatedCard(modifier = modifier, colors = CardDefaults.elevatedCardColors(containerColor = Steel900, contentColor = Steel100)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = accent)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CompactStat(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Steel900, RoundedCornerShape(14.dp)).padding(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Black, color = Steel100)
    }
}

@Composable
private fun StatusStrip(items: List<Pair<String, String>>) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Steel900, contentColor = Steel100)) {
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
fun StatePill(text: String, color: androidx.compose.ui.graphics.Color) {
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
fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Steel100)
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
                        colors = CardDefaults.elevatedCardColors(containerColor = Steel900, contentColor = Steel100),
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
