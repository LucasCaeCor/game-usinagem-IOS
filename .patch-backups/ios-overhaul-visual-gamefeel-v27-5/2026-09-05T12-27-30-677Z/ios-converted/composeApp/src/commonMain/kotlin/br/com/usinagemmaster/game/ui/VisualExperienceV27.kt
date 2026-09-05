package br.com.usinagemmaster.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.game.domain.*
import br.com.usinagemmaster.game.model.DailyMissionSave
import br.com.usinagemmaster.game.model.EmployeeSave
import br.com.usinagemmaster.game.model.MachineSave

private const val VISUAL_V27 = "visual_experience_v27_4"

enum class DashboardVisualV27 { CASH, CARGO, PRODUCTION, TEAM, CONTRACT, RESEARCH, ROULETTE, FACTORY }

@Composable
fun DashboardArtCardV27(
    kind: DashboardVisualV27,
    title: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val accent = when (kind) {
        DashboardVisualV27.CASH -> ProductionGreen
        DashboardVisualV27.CARGO -> SafetyAmber
        DashboardVisualV27.PRODUCTION -> ElectricBlue
        DashboardVisualV27.TEAM -> Color(0xFF72D7B0)
        DashboardVisualV27.CONTRACT -> Color(0xFFFFC95D)
        DashboardVisualV27.RESEARCH -> RoyalPurple
        DashboardVisualV27.ROULETTE -> Color(0xFFFF7AA8)
        DashboardVisualV27.FACTORY -> Color(0xFF7AD7F0)
    }
    val click = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Card(
        modifier = modifier.then(click),
        colors = CardDefaults.elevatedCardColors(containerColor = Steel900, contentColor = Steel100),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Steel700.copy(alpha = .7f)),
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = .12f), border = BorderStroke(1.dp, accent.copy(alpha = .28f))) {
                Canvas(Modifier.size(36.dp).padding(6.dp)) { drawDashboardGlyphV27(kind, accent) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = Steel400)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = Steel100, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(note, style = MaterialTheme.typography.labelSmall, color = Steel400, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun DrawScope.drawDashboardGlyphV27(kind: DashboardVisualV27, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = (size.minDimension * .075f).coerceAtLeast(2f)
    when (kind) {
        DashboardVisualV27.CASH -> {
            drawRoundRect(color.copy(alpha = .18f), Offset(w*.08f,h*.22f), Size(w*.84f,h*.58f), CornerRadius(w*.1f))
            drawRoundRect(color, Offset(w*.15f,h*.30f), Size(w*.70f,h*.42f), CornerRadius(w*.08f), style = Stroke(stroke))
            drawCircle(color, w*.11f, Offset(w*.50f,h*.51f), style = Stroke(stroke*.75f))
        }
        DashboardVisualV27.CARGO -> {
            drawRoundRect(color.copy(alpha=.18f), Offset(w*.12f,h*.30f), Size(w*.76f,h*.52f), CornerRadius(w*.05f))
            drawRect(color, Offset(w*.14f,h*.32f), Size(w*.72f,h*.48f), style = Stroke(stroke))
            drawLine(color, Offset(w*.14f,h*.48f), Offset(w*.86f,h*.48f), stroke*.7f)
            drawLine(color, Offset(w*.50f,h*.32f), Offset(w*.50f,h*.80f), stroke*.7f)
        }
        DashboardVisualV27.PRODUCTION -> {
            drawLine(color, Offset(w*.12f,h*.76f), Offset(w*.12f,h*.28f), stroke)
            drawLine(color, Offset(w*.12f,h*.76f), Offset(w*.88f,h*.76f), stroke)
            val p=Path().apply { moveTo(w*.18f,h*.66f); lineTo(w*.38f,h*.50f); lineTo(w*.55f,h*.58f); lineTo(w*.82f,h*.25f) }
            drawPath(p,color,style=Stroke(stroke))
            drawCircle(color,w*.06f,Offset(w*.82f,h*.25f))
        }
        DashboardVisualV27.TEAM -> {
            drawCircle(color,w*.12f,Offset(w*.35f,h*.33f)); drawCircle(color,w*.11f,Offset(w*.67f,h*.38f))
            drawArc(color,190f,160f,false,Offset(w*.14f,h*.40f),Size(w*.42f,h*.42f),style=Stroke(stroke))
            drawArc(color,190f,160f,false,Offset(w*.48f,h*.46f),Size(w*.38f,h*.34f),style=Stroke(stroke))
        }
        DashboardVisualV27.CONTRACT -> {
            drawRoundRect(color.copy(alpha=.12f),Offset(w*.20f,h*.12f),Size(w*.60f,h*.76f),CornerRadius(w*.07f),style=Stroke(stroke))
            repeat(3){i-> drawLine(color,Offset(w*.32f,h*(.34f+i*.16f)),Offset(w*.70f,h*(.34f+i*.16f)),stroke*.6f)}
            drawCircle(color,w*.055f,Offset(w*.27f,h*.34f))
        }
        DashboardVisualV27.RESEARCH -> {
            drawCircle(color,w*.18f,Offset(w*.50f,h*.43f),style=Stroke(stroke))
            repeat(6){i-> val a=i*1.0472; val x=(kotlin.math.cos(a)*w*.30f).toFloat(); val y=(kotlin.math.sin(a)*h*.30f).toFloat(); drawLine(color,Offset(w*.5f+x*.55f,h*.43f+y*.55f),Offset(w*.5f+x,h*.43f+y),stroke*.6f); drawCircle(color,w*.05f,Offset(w*.5f+x,h*.43f+y)) }
        }
        DashboardVisualV27.ROULETTE -> {
            drawCircle(color,w*.32f,Offset(w*.50f,h*.50f),style=Stroke(stroke))
            repeat(6){i-> val a=i*1.0472; drawLine(color,Offset(w*.50f,h*.50f),Offset(w*.50f+(kotlin.math.cos(a)*w*.31f).toFloat(),h*.50f+(kotlin.math.sin(a)*h*.31f).toFloat()),stroke*.55f)}
            drawCircle(color,w*.06f,Offset(w*.50f,h*.50f))
        }
        DashboardVisualV27.FACTORY -> {
            val p=Path().apply { moveTo(w*.10f,h*.78f); lineTo(w*.10f,h*.42f); lineTo(w*.34f,h*.54f); lineTo(w*.34f,h*.38f); lineTo(w*.58f,h*.52f); lineTo(w*.58f,h*.25f); lineTo(w*.82f,h*.25f); lineTo(w*.82f,h*.78f); close() }
            drawPath(p,color.copy(alpha=.18f)); drawPath(p,color,style=Stroke(stroke))
            drawRect(color,Offset(w*.65f,h*.55f),Size(w*.10f,h*.23f))
        }
    }
}

@Composable
fun ShiftCommandDeckV27(store: GameStore, onPrecision: () -> Unit) {
    val focus = store.focusModeRemainingMillis
    val bonus = store.dailyBonusRemainingMillis
    val ticket = store.dailyTicketRemainingMillis
    val profit = store.production.netPer10MinutesCents
    Card(
        colors = CardDefaults.elevatedCardColors(containerColor = Steel900, contentColor = Steel100),
        border = BorderStroke(1.dp, Steel700),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TURNO", fontWeight = FontWeight.Black, color = Steel100)
                    Text("Precisão, lucro e foco.", style = MaterialTheme.typography.labelSmall, color = Steel400)
                }
                StatePill(if (store.factoryFrame.open) "AO VIVO" else "FECHADO", if (store.factoryFrame.open) ProductionGreen else Steel500)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TurnMetricCompactV27_2(
                    icon = "◎",
                    title = "Precisão",
                    value = if (store.minigameAvailable) "Disponível" else formatV27Duration(store.minigameRemainingMillis),
                    accent = ElectricBlue,
                    modifier = Modifier.weight(1f),
                    onClick = onPrecision,
                )
                TurnMetricCompactV27_2(
                    icon = "R$",
                    title = "Lucro",
                    value = GameStore.money(profit),
                    accent = ProductionGreen,
                    modifier = Modifier.weight(1f),
                )
                TurnMetricCompactV27_2(
                    icon = "◉",
                    title = "Foco",
                    value = if (focus > 0L) formatV27Duration(focus) else "Disponível",
                    accent = if (focus > 0L) SafetyAmber else Color(0xFF72D7B0),
                    modifier = Modifier.weight(1f),
                    onClick = { if (focus == 0L) store.buySnack(); GameFeedback.play(GameSoundEffect.UI_CLICK, store.state.uiSettings.soundEnabled) },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { store.dailyBonus(); GameFeedback.play(GameSoundEffect.REWARD, store.state.uiSettings.soundEnabled) },
                    modifier = Modifier.weight(1f).height(34.dp),
                    enabled = bonus == 0L,
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                ) { Text(if (bonus == 0L) "🎁 Bônus" else formatV27Duration(bonus), style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(
                    onClick = { store.claimDailyGachaTicket(); GameFeedback.play(GameSoundEffect.REWARD, store.state.uiSettings.soundEnabled) },
                    modifier = Modifier.weight(1f).height(34.dp),
                    enabled = ticket == 0L,
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                ) { Text(if (ticket == 0L) "🎟 Ficha" else formatV27Duration(ticket), style = MaterialTheme.typography.labelSmall) }
            }
            Button(
                onClick = {
                    store.boost10Minutes()
                    GameFeedback.play(GameSoundEffect.MACHINE_START, store.state.uiSettings.soundEnabled)
                    GameFeedback.haptic(store.state.uiSettings.hapticsEnabled)
                },
                enabled = store.state.boostTokens > 0 && store.factoryFrame.open && store.production.operatingMachines > 0,
                modifier = Modifier.fillMaxWidth().height(38.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text("⏩ ADIANTAR 10 MIN • ×${store.state.boostTokens}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun TurnMetricCompactV27_2(
    icon: String,
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val click = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    Surface(
        modifier = modifier.then(click).height(68.dp),
        shape = RoundedCornerShape(12.dp),
        color = Steel950.copy(alpha = .72f),
        border = BorderStroke(1.dp, accent.copy(alpha = .35f)),
    ) {
        Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(icon, color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                Text(title, color = Steel400, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Text(value, color = Steel100, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MachineCatalogCardV27(
    title: String,
    machineType: String,
    eyebrow: String,
    price: String? = null,
    status: String? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.elevatedCardColors(containerColor = Steel900, contentColor = Steel100),
        border = BorderStroke(1.dp, Steel700.copy(alpha=.72f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MachineArtworkV27(title, machineType = machineType, modifier = Modifier.width(126.dp).height(92.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = SafetyAmber, fontWeight = FontWeight.Black)
                Text(title, style = MaterialTheme.typography.titleMedium, color = Steel100, fontWeight = FontWeight.Black)
                if (price != null) Text(price, color = ProductionGreen, fontWeight = FontWeight.Black)
                if (status != null) Text(status, style = MaterialTheme.typography.bodySmall, color = Steel400)
                body()
            }
        }
    }
}

@Composable
fun EmployeePortraitV27(employee: EmployeeSave, modifier: Modifier = Modifier, selected: Boolean = false) {
    val avatar = employeeAvatarV27(employee)
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = Steel850, border = BorderStroke(1.dp, if (selected) ElectricBlue else Steel700)) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            drawPlayerAvatarFigure(
                base = Offset(size.width*.50f, size.height*.94f),
                avatar = avatar,
                scale = size.minDimension / 86f,
                phase = .21f,
                walking = false,
                carrying = false,
            )
        }
    }
}

private val femaleNamesV27 = setOf("Luciana","Patrícia","Camila","Fernanda","Amanda","Juliana","Mariana","Beatriz","Renata","Larissa","Daniela","Aline","Carolina","Bianca","Vanessa","Jéssica","Natália","Priscila","Letícia","Isabela")
private fun employeeAvatarV27(employee: EmployeeSave): br.com.usinagemmaster.game.model.PlayerProfileSave {
    val legendary = when(employee.legendaryCode){"tatu_banhado"->"TATUZAO";"kendao"->"KENDAO_KIMONO";"nikao_narizudo"->"PINOQUIO";"magrao"->"MAGRAO";"nelsinho_treme_treme"->"TREME_TREME";"chupa_engole"->"BEBADO";else->null}
    val female=employee.name.substringBefore(' ') in femaleNamesV27
    val seed=kotlin.math.abs((employee.id.ifBlank{employee.name}).hashCode())
    return br.com.usinagemmaster.game.model.PlayerProfileSave(
        name=employee.name, gender=if(female)"FEMALE" else "MALE", skinStyle=legendary ?: if(female && seed%9==0)"PRINCESA" else "WORKSHOP",
        bodyType=when(legendary){"TATUZAO"->"STRONG";"MAGRAO"->"SLIM";else->"STANDARD"}, skinTone=if(seed%4==0)"TAN" else "MEDIUM",
        hairStyle=if(female) listOf("LONG","PONYTAIL","CURLY")[seed%3] else if(seed%5==0)"BUZZ" else "SHORT",
        hairColor=listOf("DARK","BROWN","BLONDE","GRAY")[seed%4], uniformColor=if(female)"BLUE" else "NAVY", helmetColor="YELLOW",
        accessory=if(employee.legendaryCode=="gumersvaldo")"GLASSES" else "NONE", onboardingComplete=true,
    )
}

@Composable
fun FactoryLayoutEditorV27(store: GameStore) {
    var selected by remember { mutableStateOf(store.state.machines.firstOrNull()?.id) }
    val machine = store.state.machines.firstOrNull { it.id == selected }
    Card(colors = CardDefaults.elevatedCardColors(containerColor = Steel900, contentColor = Steel100), border = BorderStroke(1.dp, Steel700)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LAYOUT DINÂMICO", color = Steel100, fontWeight = FontWeight.Black)
                    Text("Toque numa baia vazia para mover a máquina selecionada.", style = MaterialTheme.typography.bodySmall, color = Steel400)
                }
                OutlinedButton(onClick = store::autoLayoutMachines) { Text("Auto layout") }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(6) { y ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(5) { x ->
                            val occupying = store.state.machines.firstOrNull { it.gridX == x && it.gridY == y }
                            val isSelected = occupying?.id == selected
                            Surface(
                                modifier = Modifier.weight(1f).height(54.dp).clickable {
                                    if (occupying != null) selected = occupying.id else if (selected != null) store.moveMachineTo(selected!!, x, y)
                                    GameFeedback.play(GameSoundEffect.UI_CLICK, store.state.uiSettings.soundEnabled)
                                },
                                shape = RoundedCornerShape(9.dp),
                                color = when { isSelected -> ElectricBlue.copy(alpha=.18f); occupying != null -> Steel850; else -> Steel950 },
                                border = BorderStroke(1.dp, when { isSelected -> ElectricBlue; occupying != null -> Steel700; else -> Steel700.copy(alpha=.45f) }),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(occupying?.let { machineShortV27(it.machineType) } ?: "+", color = if(occupying!=null) Steel100 else Steel500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
            machine?.let {
                val def=MachineCatalog.byType(it.machineType)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MachineArtworkV27(def?.name ?: it.machineType, machineType=it.machineType, modifier=Modifier.width(118.dp).height(82.dp))
                    Column(Modifier.weight(1f)) {
                        Text(def?.name ?: it.machineType, fontWeight=FontWeight.Black, color=Steel100)
                        Text("Baia ${it.gridX+1}.${it.gridY+1} • ${def?.space ?: 0} m²", style=MaterialTheme.typography.bodySmall, color=Steel400)
                    }
                }
            }
        }
    }
}

private fun machineShortV27(type:String):String { val u=type.uppercase(); return when { "LATHE" in u->"TOR";"MILL" in u||"MACHINING" in u->"FRE";"DRILL" in u->"FUR";"GRIND" in u->"RET";"WELD" in u->"SOL";"LASER" in u->"LAS";"PLASMA" in u->"PLA";"EDM" in u->"EDM";else->"MÁQ" } }

@Composable
fun TechnicalListV27(store: GameStore) {
    var expanded by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("LISTA TÉCNICA", fontWeight=FontWeight.Black,color=Steel100)
            Text("Disponíveis primeiro; depois melhor encaixe e experiência.",style=MaterialTheme.typography.bodySmall,color=Steel400)
            OutlinedButton(
                onClick=store::autoDistributeOperators,
                modifier=Modifier.height(34.dp).widthIn(max=148.dp),
                contentPadding=PaddingValues(horizontal=9.dp,vertical=0.dp),
            ){ Text("Auto distribuir", style=MaterialTheme.typography.labelSmall) }
        }
        store.state.machines.forEach { machine ->
            val def=MachineCatalog.byType(machine.machineType)
            val current=store.state.employees.firstOrNull{it.assignedMachineId==machine.id}
            MachineCatalogCardV27(def?.name ?: machine.machineType,machine.machineType,"BAIA ${machine.gridX+1}.${machine.gridY+1}",status="Operador: ${current?.name ?: "não atribuído"}") {
                Text("Condição ${machine.condition/10}% • Nível ${machine.level}",style=MaterialTheme.typography.bodySmall,color=Steel400)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick={store.assignBestOperator(machine.id)},
                        modifier=Modifier.height(34.dp),
                        contentPadding=PaddingValues(horizontal=9.dp,vertical=0.dp),
                    ){Text("Melhor operador",style=MaterialTheme.typography.labelSmall)}
                    OutlinedButton(
                        onClick={expanded=if(expanded==machine.id)null else machine.id},
                        modifier=Modifier.height(34.dp),
                        contentPadding=PaddingValues(horizontal=9.dp,vertical=0.dp),
                    ){Text(if(expanded==machine.id)"Fechar" else "Selecionar",style=MaterialTheme.typography.labelSmall)}
                }
            }
            if(expanded==machine.id){
                val now=currentTimeMillis()
                val candidates=store.state.employees.sortedWith(
                    compareBy<EmployeeSave>{ if(it.restingUntil<=now && (it.assignedMachineId==null || it.assignedMachineId==machine.id)) 0 else 1 }
                        .thenByDescending{store.operatorFitScore(it.id,machine.id)}
                        .thenByDescending{it.experience}
                )
                candidates.forEach { employee ->
                    val available=employee.restingUntil<=now && (employee.assignedMachineId==null || employee.assignedMachineId==machine.id)
                    Surface(
                        modifier=Modifier.fillMaxWidth().clickable(enabled=employee.restingUntil<=now){store.assignEmployeeToMachine(employee.id,machine.id);expanded=null;GameFeedback.play(GameSoundEffect.UI_CLICK, store.state.uiSettings.soundEnabled)},
                        shape=RoundedCornerShape(14.dp),color=if(current?.id==employee.id) ElectricBlue.copy(alpha=.12f) else Steel850,border=BorderStroke(1.dp,if(available)Steel700 else Steel700.copy(alpha=.35f))
                    ) {
                        Row(Modifier.padding(9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            EmployeePortraitV27(employee,Modifier.size(58.dp),current?.id==employee.id)
                            Column(Modifier.weight(1f)){
                                Text(employee.name,fontWeight=FontWeight.Black,color=Steel100)
                                Text("${employee.specialty} • Nv.${employee.skillLevel} • ${employee.experience} min",style=MaterialTheme.typography.bodySmall,color=Steel400)
                                Text("${store.operatorFitLabel(employee.id,machine.id)} • score ${store.operatorFitScore(employee.id,machine.id)}${if(!available)" • em outro posto" else " • disponível"}",style=MaterialTheme.typography.labelSmall,color=if(available)ProductionGreen else SafetyAmber)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyMissionsV27_2(store: GameStore) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Card(
            colors=CardDefaults.elevatedCardColors(containerColor=Steel850,contentColor=Steel100),
            border=BorderStroke(1.dp,SafetyAmber.copy(alpha=.45f)),
            shape=RoundedCornerShape(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(11.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("📋 MISSÕES DIÁRIAS",fontWeight=FontWeight.Black,color=Steel100)
                    Text("3 objetivos reais • renovam em ${formatV27Duration(store.dailyMissionResetRemainingMillis)}",style=MaterialTheme.typography.bodySmall,color=Steel400)
                }
                StatePill("${store.dailyMissions.count { it.claimed }}/3",SafetyAmber)
            }
        }
        store.dailyMissions.forEach { mission -> DailyMissionCardV27_2(store, mission) }
    }
}

@Composable
private fun DailyMissionCardV27_2(store: GameStore, mission: DailyMissionSave) {
    val progress = store.dailyMissionProgress(mission)
    val reward = when(mission.rewardType) {
        "XP" -> "+${mission.rewardValue} XP personagem"
        "TOOL" -> {
            val name=GameProgression.tools.firstOrNull{it.id==mission.rewardItemId}?.name ?: mission.rewardItemId
            "+${mission.rewardValue} $name"
        }
        else -> GameStore.money(mission.rewardValue)
    }
    Surface(
        shape=RoundedCornerShape(14.dp),
        color=Steel900,
        border=BorderStroke(1.dp,if(mission.claimed)ProductionGreen.copy(alpha=.42f) else Steel700),
    ) {
        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(mission.title,fontWeight=FontWeight.Black,color=Steel100)
                    Text(mission.description,style=MaterialTheme.typography.bodySmall,color=Steel400)
                }
                Text(reward,color=SafetyAmber,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall)
            }
            LinearProgressIndicator(progress=(progress.toFloat()/mission.target.coerceAtLeast(1L).toFloat()).coerceIn(0f,1f),modifier=Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("$progress/${mission.target}",style=MaterialTheme.typography.labelSmall,color=Steel400)
                OutlinedButton(
                    onClick={store.claimDailyMission(mission.id);GameFeedback.play(GameSoundEffect.REWARD,store.state.uiSettings.soundEnabled)},
                    enabled=!mission.claimed && progress>=mission.target,
                    modifier=Modifier.height(32.dp),
                    contentPadding=PaddingValues(horizontal=9.dp,vertical=0.dp),
                ){Text(if(mission.claimed)"Coletada" else "Coletar",style=MaterialTheme.typography.labelSmall)}
            }
        }
    }
}

@Composable
fun CompanySkillStoryboardV27(store: GameStore) {
    SkillStoryboardV27("PESQUISA DA EMPRESA", "Da oficina enxuta ao gêmeo digital", GameProgression.companySkills, store.state.expansion.companySkills, GameProgression.companySkillPoints(store.state.company.companyLevel,store.state.expansion.companySkills), store.state.company.companyLevel) { store.unlockCompanySkill(it) }
}

@Composable
fun PlayerSkillStoryboardV27(store: GameStore) {
    SkillStoryboardV27("JORNADA DO PERSONAGEM", "O dono evolui de operador a mestre de processo", GameProgression.playerSkills, store.state.expansion.playerSkills, GameProgression.playerSkillPoints(store.state.company.companyLevel,store.state.expansion.playerXp,store.state.expansion.playerSkills), store.state.company.companyLevel) { store.unlockPlayerSkill(it) }
}

@Composable
private fun SkillStoryboardV27(title:String,subtitle:String,skills:List<SkillDef>,owned:Set<String>,points:Int,level:Int,onUnlock:(String)->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Card(colors=CardDefaults.elevatedCardColors(containerColor=Steel850,contentColor=Steel100),border=BorderStroke(1.dp,RoyalPurple.copy(alpha=.55f))){
            Column(Modifier.padding(13.dp)){Text("🌳 $title",fontWeight=FontWeight.Black,color=Steel100);Text(subtitle,color=Steel400,style=MaterialTheme.typography.bodySmall);Text("$points ponto(s) disponível(is)",color=SafetyAmber,fontWeight=FontWeight.Bold)}
        }
        skills.forEachIndexed{index,skill->
            val isOwned=skill.id in owned
            val prereqOk=skill.prerequisite==null || skill.prerequisite in owned
            val can=!isOwned && level>=skill.minLevel && prereqOk && points>0
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.CenterVertically){
                Column(horizontalAlignment=Alignment.CenterHorizontally){
                    Surface(shape=RoundedCornerShape(999.dp),color=if(isOwned)ProductionGreen.copy(alpha=.18f) else if(can)RoyalPurple.copy(alpha=.20f) else Steel850,border=BorderStroke(2.dp,if(isOwned)ProductionGreen else if(can)RoyalPurple else Steel700)){
                        Box(Modifier.size(46.dp),contentAlignment=Alignment.Center){Text(skillGlyphV27(skill.id),style=MaterialTheme.typography.titleLarge)}
                    }
                    if(index<skills.lastIndex) Canvas(Modifier.width(4.dp).height(24.dp)){drawLine(if(isOwned)ProductionGreen else Steel700,Offset(size.width/2,0f),Offset(size.width/2,size.height),3f)}
                }
                Surface(modifier=Modifier.weight(1f),shape=RoundedCornerShape(15.dp),color=Steel900,border=BorderStroke(1.dp,if(isOwned)ProductionGreen.copy(alpha=.45f) else Steel700.copy(alpha=.7f))){
                    Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
                        Text("CAPÍTULO ${index+1} • ${skill.name}",fontWeight=FontWeight.Black,color=Steel100)
                        Text(skill.description,style=MaterialTheme.typography.bodySmall,color=Steel400)
                        Text("Nível ${skill.minLevel}${skill.prerequisite?.let{" • requer ${skills.firstOrNull{d->d.id==it}?.name ?: it}"} ?: ""}",style=MaterialTheme.typography.labelSmall,color=SafetyAmber)
                        Button(onClick={onUnlock(skill.id)},enabled=can,modifier=Modifier.fillMaxWidth()){Text(if(isOwned)"CONCLUÍDO" else if(can)"DESBLOQUEAR" else "BLOQUEADO")}
                    }
                }
            }
        }
    }
}

private fun skillGlyphV27(id:String)=when{ "lean" in id->"↗";"qualidade" in id||"metro" in id->"📏";"cnc" in id->"⌨";"lider" in id->"👥";"comercial" in id||"negocia" in id->"🤝";"digital" in id->"◈";"energia" in id->"⚡";"setup" in id->"🔧";else->"⚙" }

@Composable
fun IndustrialCareerStoryboardV27(store: GameStore){
    var branch by remember{mutableStateOf(IndustrialSkillBranch.OPERATION)}
    val nodes=IndustrialSkillCatalog.all.filter{it.branch==branch}.sortedBy{it.tier}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Card(colors=CardDefaults.elevatedCardColors(containerColor=Steel850,contentColor=Steel100),border=BorderStroke(1.dp,ElectricBlue.copy(alpha=.45f))){Column(Modifier.padding(13.dp)){Text("🧭 STORYBOARD INDUSTRIAL",fontWeight=FontWeight.Black,color=Steel100);Text("Escolha um caminho. Cada nó altera o gameplay e abre o próximo capítulo.",style=MaterialTheme.typography.bodySmall,color=Steel400);Text("${store.state.career.availableSkillPoints()} ponto(s) • ${store.state.career.unlockedSkills.size} aprendidas",color=SafetyAmber,fontWeight=FontWeight.Bold)}}
        CareerHowToProgressV27_2(store)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){IndustrialSkillBranch.entries.forEach{item->FilterChip(selected=branch==item,onClick={branch=item},label={Text("${item.icon} ${item.label}")})}}
        nodes.forEachIndexed{index,skill->
            val owned=skill.id in store.state.career.unlockedSkills
            val missing=skill.prerequisites.filterNot{it in store.state.career.unlockedSkills}
            val can=!owned && store.state.company.companyLevel>=skill.minCompanyLevel && missing.isEmpty() && store.state.career.availableSkillPoints()>=skill.cost
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Column(horizontalAlignment=Alignment.CenterHorizontally){Surface(shape=RoundedCornerShape(999.dp),color=if(owned)ProductionGreen.copy(alpha=.18f) else Steel850,border=BorderStroke(2.dp,if(owned)ProductionGreen else if(can)SafetyAmber else Steel700)){Box(Modifier.size(48.dp),contentAlignment=Alignment.Center){Text(branch.icon)}};if(index<nodes.lastIndex)Canvas(Modifier.width(4.dp).height(26.dp)){drawLine(if(owned)ProductionGreen else Steel700,Offset(2f,0f),Offset(2f,size.height),3f)}}
                Surface(Modifier.weight(1f),shape=RoundedCornerShape(16.dp),color=Steel900,border=BorderStroke(1.dp,if(owned)ProductionGreen.copy(alpha=.4f) else Steel700)){Column(Modifier.padding(11.dp)){Text("T${skill.tier} • ${skill.name}",fontWeight=FontWeight.Black,color=Steel100);Text(skill.description,style=MaterialTheme.typography.bodySmall,color=Steel400);Text("Custo ${skill.cost} • fábrica Nv.${skill.minCompanyLevel}",style=MaterialTheme.typography.labelSmall,color=SafetyAmber);if(missing.isNotEmpty())Text("Requer ${missing.mapNotNull(IndustrialSkillCatalog::byId).joinToString{it.name}}",style=MaterialTheme.typography.labelSmall,color=DangerRed);Button(onClick={store.unlockIndustrialSkill(skill.id)},enabled=can,modifier=Modifier.fillMaxWidth()){Text(if(owned)"APRENDIDA" else "APRENDER")}}}
            }
        }
    }
}

@Composable
private fun CareerHowToProgressV27_2(store: GameStore) {
    val c=store.state.career
    val bestMastery=c.masteryXp.maxOfOrNull { (type,xp) -> MachineMastery(type,xp).level } ?: 0
    val milestones=listOf(
        Triple("Operações manuais",c.totalManualOperations.toLong(),250L),
        Triple("Peças perfeitas",c.perfectOperations.toLong(),20L),
        Triple("Lotes aprovados",c.approvedBatches.toLong(),10L),
        Triple("Lotes expedidos",c.shippedBatches.toLong(),20L),
        Triple("Retrabalhos concluídos",c.reworkedBatches.toLong(),10L),
        Triple("Maior maestria",bestMastery.toLong(),10L),
    )
    Surface(shape=RoundedCornerShape(16.dp),color=Steel900,border=BorderStroke(1.dp,Steel700)) {
        Column(Modifier.padding(11.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("COMO EVOLUIR NA CARREIRA",fontWeight=FontWeight.Black,color=Steel100)
            Text("Carreira usa pontos industriais, não o XP do personagem. Você ganha pontos ao colocar o dono para trabalhar e atingir marcos.",style=MaterialTheme.typography.bodySmall,color=Steel400)
            Text("Marcos que dão pontos: 1/10/25/50/100/250 operações manuais; 5/20 perfeitas; 10 lotes aprovados; 20 expedidos; 10 retrabalhos; maestria Nv.10.",style=MaterialTheme.typography.bodySmall,color=ElectricBlue)
            Text("XP do personagem é separado: contratos, minigames, aluguel e missões diárias aumentam o nível do personagem e a árvore do personagem.",style=MaterialTheme.typography.bodySmall,color=SafetyAmber)
            milestones.forEach { (label,current,target) ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(label,style=MaterialTheme.typography.labelSmall,color=Steel400)
                    Text("$current/$target",style=MaterialTheme.typography.labelSmall,color=Steel100,fontWeight=FontWeight.Bold)
                }
            }
        }
    }
}

fun formatV27Duration(millis: Long): String {
    val total = (millis / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    fun pad(value: Long) = value.toString().padStart(2, '0')
    return if (h > 0L) "${pad(h)}:${pad(m)}:${pad(s)}" else "${pad(m)}:${pad(s)}"
}
