package br.com.usinagemmaster.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import br.com.usinagemmaster.game.domain.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OnlineCommunityScreen(store: GameStore) {
    var raw by remember { mutableStateOf(onlineCommunityRaw()) }
    var selectedUid by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(currentTimeMillis()) }

    LaunchedEffect(Unit) {
        requestOnlineCommunityRefresh()
        var refreshTicks = 0
        while (true) {
            delay(900L)
            raw = onlineCommunityRaw()
            now = currentTimeMillis()
            refreshTicks++
            if (refreshTicks >= 16) { refreshTicks = 0; requestOnlineCommunityRefresh() }
        }
    }

    val online = remember(raw) { decodeOnlineCommunity(raw) }
    val selected = online.factories.firstOrNull { it.uid == selectedUid }

    LaunchedEffect(online.activeHire?.id, online.activeHire?.boostPct, online.activeHire?.endsAt) {
        val hire = online.activeHire
        store.syncRemoteHire(hire?.ownerUid, hire?.playerName, hire?.boostPct ?: 0, hire?.endsAt ?: 0L)
    }
    LaunchedEffect(online.remoteOperationResult?.token) {
        online.remoteOperationResult?.let { store.claimRemoteOperationXp(it.token, it.xp) }
    }

    if (selected != null) {
        RemoteFactoryVisitor(
            factory = selected,
            employment = online.outgoingRental?.takeIf { it.renterUid == selected.uid },
            now = now,
            onBack = { selectedUid = null },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            HeroCard(
                eyebrow = if (online.signedIn) "FIREBASE CONECTADO" else "MODO OFFLINE",
                title = online.displayName.ifBlank { store.state.profile.name },
                subtitle = online.email.ifBlank {
                    "Entre com Google em Configurações para acessar a comunidade."
                },
                accent = if (online.signedIn) ProductionGreen else SafetyAmber,
            ) {
                if (online.signedIn) {
                    Text(
                        "Firebase UID",
                        style = MaterialTheme.typography.labelSmall,
                        color = Steel200,
                    )
                    Text(
                        online.uid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        online.linkedProfile?.let { linked ->
            item {
                IndustrialCard(
                    "Conta antiga encontrada",
                    when (linked.source) {
                        "public_factories" -> "Fábrica pública atual do Android"
                        "players" -> "Perfil legado encontrado no Firebase"
                        "player_accounts" -> "Conta social encontrada no Firebase"
                        else -> linked.source
                    }
                ) {
                    StatePill("VINCULADA", ProductionGreen)
                    Text(
                        linked.companyName.ifBlank { linked.playerName },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text("Nível ${linked.companyLevel} • reputação ${linked.reputation}")
                    Text(
                        "O vínculo online foi recuperado pelo mesmo Firebase UID. " +
                            "O save local do Android não é sobrescrito automaticamente."
                    )
                }
            }
        }

        if (online.signedIn && online.linkedProfile == null && online.error == null) {
            item {
                IndustrialCard(
                    "Conta Google autenticada",
                    "Ainda não encontrei uma fábrica publicada neste UID"
                ) {
                    StatePill("AUTH OK", ElectricBlue)
                    Text(
                        "Isso pode significar que a conta antiga tinha somente save local Android, " +
                            "ou que um perfil social antigo foi criado sob outro UID."
                    )
                }
            }
        }

        online.error?.let { error ->
            item {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Firestore respondeu com erro", fontWeight = FontWeight.Black)
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "Se for PERMISSION_DENIED, publique/ajuste as regras do Firestore do projeto.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        online.message?.let { message ->
            item {
                IndustrialCard("Firebase", "Última operação") {
                    Text(message)
                }
            }
        }

        item {
            IndustrialCard(
                "Minha fábrica pública",
                "Mesmo schema public_factories usado pelo Android atualizado"
            ) {
                Text(
                    "${store.state.company.name} • nível ${store.state.company.companyLevel} • " +
                        "${store.state.machines.size} máquina(s)"
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Button(
                        onClick = {
                            publishOnlineFactory(encodeOnlineFactoryPublication(store))
                        },
                        enabled = online.signedIn,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Publicar")
                    }
                    OutlinedButton(
                        onClick = { requestOnlineCommunityRefresh() },
                        enabled = online.signedIn,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Atualizar")
                    }
                }
            }
        }

        item {
            SectionTitle(
                "Outras empresas",
                if (online.factories.isEmpty()) {
                    "Nenhuma fábrica pública foi retornada ainda"
                } else {
                    "${online.factories.size} fábrica(s) disponíveis para visita"
                }
            )
        }

        items(online.factories, key = { it.uid }) { factory ->
            ElevatedCard(
                onClick = { selectedUid = factory.uid },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = Steel900),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        color = ElectricBlue.copy(alpha = .13f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🏭", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(factory.companyName, fontWeight = FontWeight.Black)
                        Text("por ${factory.playerName}", color = Steel200)
                        Text(
                            "N${factory.companyLevel} • REP ${factory.reputation} • " +
                                "${factory.machines.size} máquinas • ${factory.employeeCount} pessoas",
                            style = MaterialTheme.typography.labelSmall,
                            color = Steel200,
                        )
                    }
                    Text("VISITAR ›", color = SafetyAmber, fontWeight = FontWeight.Black)
                }
            }
        }

        item {
            IndustrialCard("MEU PERSONAGEM NO MERCADO", "Ofereça o dono da fábrica para contratação por 48 horas") {
                val mine = online.myOffer
                val leased = (mine?.leasedUntil ?: 0L) > now
                val playerLevel = GameProgression.playerProgress(store.state.expansion.playerXp).level
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(store.state.profile.name, fontWeight = FontWeight.Black)
                        Text("Personagem Nv.$playerLevel • ${store.state.expansion.playerSkills.size} skill(s)", style = MaterialTheme.typography.bodySmall, color = Steel200)
                    }
                    StatePill(when { leased -> "CONTRATADO"; mine?.published == true -> "OFERTADO"; else -> "FORA DO MERCADO" }, when { leased -> SafetyAmber; mine?.published == true -> ProductionGreen; else -> Steel500 })
                }
                mine?.takeIf { it.published }?.let { Text("Valor técnico: +${it.boostPct}% • ${it.skills.joinToString(" • ").ifBlank { "sem skills extras" }}", style = MaterialTheme.typography.bodySmall) }
                if (leased) Text("Vínculo ativo por mais ${onlineDurationV28((mine?.leasedUntil ?: 0L) - now)}. Enquanto contratado, você pode operar manualmente na empresa contratante.", color = SafetyAmber)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(onClick = { publishOnlineCharacterOffer(encodeOnlineCharacterOffer(store)) }, enabled = online.signedIn && !leased, modifier = Modifier.weight(1f)) { Text(if (mine?.published == true) "ATUALIZAR OFERTA" else "OFERTAR PERSONAGEM") }
                    OutlinedButton(onClick = ::withdrawOnlineCharacterOffer, enabled = online.signedIn && mine?.published == true && !leased, modifier = Modifier.weight(1f)) { Text("RETIRAR") }
                }
            }
        }

        online.outgoingRental?.let { rental ->
            item {
                val employer = online.factories.firstOrNull { it.uid == rental.renterUid }
                IndustrialCard("TRABALHO EXTERNO ATIVO", employer?.companyName ?: "Empresa contratante") {
                    Text("Seu personagem está contratado por mais ${onlineDurationV28(rental.endsAt - now)}.")
                    Text("Operações manuais feitas: ${rental.manualOps} • cada operação tem recarga de 10 min.", style = MaterialTheme.typography.bodySmall, color = Steel200)
                    Button(onClick = { selectedUid = rental.renterUid }, enabled = employer != null, modifier = Modifier.fillMaxWidth()) {
                        Text(if (employer != null) "ENTRAR NA EMPRESA E OPERAR" else "AGUARDANDO SNAPSHOT DA EMPRESA")
                    }
                }
            }
        }

        online.activeHire?.let { hire ->
            item {
                IndustrialCard("PROFISSIONAL CONTRATADO", "${hire.playerName} • +${hire.boostPct}% de produtividade") {
                    Text("Vínculo termina em ${onlineDurationV28(hire.endsAt - now)} • ${hire.manualOps} operação(ões) manual(is) feitas pelo proprietário.")
                }
            }
        }

        item {
            SectionTitle(
                "Mercado de profissionais",
                "Ofertas assíncronas do mesmo character_offers do Android"
            )
        }

        if (online.offers.isEmpty()) {
            item {
                IndustrialCard("Sem ofertas livres", "Atualize a comunidade para consultar novamente") {
                    Text("Ofertas alugadas por outro jogador ficam fora da lista até o prazo terminar.")
                }
            }
        } else {
            items(online.offers, key = { it.ownerUid }) { offer ->
                IndustrialCard(
                    offer.playerName,
                    "+${offer.boostPct}% por 48 horas"
                ) {
                    if (offer.skills.isNotEmpty()) {
                        Text("Skills: ${offer.skills.joinToString(" • ")}")
                    }
                    Button(
                        onClick = { hireOnlineCharacter(offer.ownerUid) },
                        enabled = online.signedIn,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Contratar profissional")
                    }
                }
            }
        }

        item {
            IndustrialCard(
                "Save local preservado",
                "Firebase continua sendo a camada social"
            ) {
                StatePill("SAVE LOCAL ATIVO", ProductionGreen)
                Text(
                    "Dinheiro, contratos, funcionários e progresso continuam no save do iPhone. " +
                        "A comunidade publica snapshots e mercado, como no Android."
                )
            }
        }
    }
}

@Composable
private fun RemoteFactoryVisitor(
    factory: OnlineFactorySnapshot,
    employment: OnlineOutgoingRental?,
    now: Long,
    onBack: () -> Unit,
) {
    var operationMachine by remember { mutableStateOf<OnlineMachineSnapshot?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("‹ Voltar às empresas") }
        }
        item {
            HeroCard(
                eyebrow = if (employment != null) "TRABALHO EXTERNO • OPERAÇÃO LIBERADA" else "MODO VISITANTE",
                title = factory.companyName,
                subtitle = "por ${factory.playerName} • ${factory.specialty}",
                accent = ElectricBlue,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    CompactStat("Nível", factory.companyLevel.toString(), Modifier.weight(1f))
                    CompactStat("REP", factory.reputation.toString(), Modifier.weight(1f))
                    CompactStat("Equipe", factory.employeeCount.toString(), Modifier.weight(1f))
                }
                Text(
                    "${oneOnline(factory.productionPer10Minutes)} pç/10min • " +
                        "${factory.activeContracts} contrato(s) • ${factory.pendingLots} lote(s)"
                )
            }
        }

        item { RemoteFactoryFloor(factory) }

        item {
            IndustrialCard("Leitura da fábrica", "Snapshot publicado pelo proprietário") {
                Text("${factory.machines.size} máquina(s) • ${factory.workers.size} trabalhador(es)")
                Text(
                    if (employment == null) "Modo visitante: a cena é animada localmente a partir do snapshot do Firebase; você observa, mas não altera a fábrica do outro jogador."
                    else "Seu personagem está contratado nesta empresa. Você pode assumir manualmente uma máquina publicada; a operação registra score no vínculo, dá XP e melhora o bônus temporário do contratante."
                )
            }
        }

        items(factory.machines) { machine ->
            IndustrialCard(
                machine.name,
                "Baia ${machine.x + 1}.${machine.y + 1}"
            ) {
                StatePill(
                    if (machine.operating) "PRODUZINDO" else "EM ESPERA",
                    if (machine.operating) ProductionGreen else SafetyAmber,
                )
                Text("Nível ${machine.level} • condição ${machine.condition / 10}%")
                if (employment != null) {
                    OutlinedButton(onClick = { operationMachine = machine }, modifier = Modifier.fillMaxWidth()) { Text("OPERAR MANUALMENTE") }
                }
            }
        }
    }
    operationMachine?.let { machine ->
        RemoteOperationDialogV28(factory, employment, machine, now) { operationMachine = null }
    }
}

@Composable
private fun RemoteFactoryFloor(factory: OnlineFactorySnapshot) {
    val transition = rememberInfiniteTransition(label = "remote_factory")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "worker_phase",
    )
    val pulse by transition.animateFloat(
        initialValue = .35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "machine_pulse",
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Steel900),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("Fábrica Viva remota", fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp)
                    .background(Steel950, RoundedCornerShape(14.dp)),
            ) {
                val cols = 5
                val rows = 6
                val cw = size.width / cols
                val ch = size.height / rows

                repeat(cols + 1) { x ->
                    drawLine(
                        Steel500.copy(alpha = .22f),
                        Offset(x * cw, 0f),
                        Offset(x * cw, size.height),
                        1f,
                    )
                }
                repeat(rows + 1) { y ->
                    drawLine(
                        Steel500.copy(alpha = .22f),
                        Offset(0f, y * ch),
                        Offset(size.width, y * ch),
                        1f,
                    )
                }

                factory.machines.forEach { machine ->
                    val left = machine.x.coerceIn(0, cols - 1) * cw + 5f
                    val top = machine.y.coerceIn(0, rows - 1) * ch + 5f
                    val color = when {
                        machine.premium -> RoyalPurple
                        machine.operating -> ProductionGreen.copy(alpha = .70f + pulse * .25f)
                        else -> Steel500
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(cw - 10f, ch - 10f),
                    )
                }

                factory.workers.forEachIndexed { index, worker ->
                    val machine = factory.machines.firstOrNull {
                        it.id == worker.assignedMachineId
                    }
                    val baseX = if (machine != null) {
                        (machine.x + .52f) * cw
                    } else {
                        (.6f + (index % cols)) * cw
                    }
                    val baseY = if (machine != null) {
                        (machine.y + .72f) * ch
                    } else {
                        (4.7f + (index % 2) * .25f) * ch
                    }
                    val angle = phase * 6.283185f + index * .73f
                    val x = baseX + cos(angle) * 7f
                    val y = baseY + sin(angle) * 5f
                    drawCircle(
                        color = Color(0xFFFFD3A1),
                        radius = 5.5f,
                        center = Offset(x, y),
                    )
                    drawCircle(
                        color = ElectricBlue,
                        radius = 3.2f,
                        center = Offset(x, y + 8f),
                    )
                }

                val ownerX = size.width * (.10f + phase * .78f)
                val ownerY = size.height * .91f
                drawCircle(
                    color = SafetyAmber,
                    radius = 7f,
                    center = Offset(ownerX, ownerY),
                )
                if (factory.ownerCarrying) {
                    drawRect(
                        color = Color(0xFFB98544),
                        topLeft = Offset(ownerX + 8f, ownerY - 5f),
                        size = Size(10f, 10f),
                    )
                }
            }
        }
    }
}

private fun oneOnline(value: Double): String {
    val scaled = (value * 10.0).toLong()
    return "${scaled / 10L},${kotlin.math.abs(scaled % 10L)}"
}


@Composable
private fun RemoteOperationDialogV28(
    factory: OnlineFactorySnapshot,
    rental: OnlineOutgoingRental?,
    machine: OnlineMachineSnapshot,
    now: Long,
    onDismiss: () -> Unit,
) {
    if (rental == null) return
    var precision by remember(machine.id) { mutableFloatStateOf(72f) }
    var speed by remember(machine.id) { mutableFloatStateOf(68f) }
    val score = ((precision * .68f + speed * .32f)).toInt().coerceIn(0, 100)
    val remaining = (10L * 60L * 1000L - (now - rental.lastManualOpAt)).coerceAtLeast(0L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Operar ${machine.name}", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${factory.companyName} • trabalho externo", color = ElectricBlue)
                Text("Ajuste precisão e ritmo. O score é registrado no vínculo; não altera o save privado da outra empresa.")
                Text("Precisão ${precision.toInt()}%", fontWeight = FontWeight.Bold)
                Slider(value = precision, onValueChange = { precision = it }, valueRange = 45f..100f)
                Text("Ritmo ${speed.toInt()}%", fontWeight = FontWeight.Bold)
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 40f..100f)
                StatePill("SCORE $score", if (score >= 85) ProductionGreen else SafetyAmber)
                if (remaining > 0L) Text("Recarga: ${onlineDurationV28(remaining)}", color = SafetyAmber)
            }
        },
        confirmButton = {
            Button(
                onClick = { operateOnlineRental(encodeOnlineRentalOperation(rental.id, machine.id, score)); onDismiss() },
                enabled = remaining == 0L,
            ) { Text("CONCLUIR OPERAÇÃO") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun onlineDurationV28(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val d = total / 86_400L
    val h = (total % 86_400L) / 3_600L
    val m = (total % 3_600L) / 60L
    return if (d > 0) "${d}d ${h}h ${m}m" else "${h}h ${m}m"
}
