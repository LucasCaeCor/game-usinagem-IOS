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

    LaunchedEffect(Unit) {
        requestOnlineCommunityRefresh()
        while (true) {
            delay(900L)
            raw = onlineCommunityRaw()
        }
    }

    val online = remember(raw) { decodeOnlineCommunity(raw) }
    val selected = online.factories.firstOrNull { it.uid == selectedUid }

    if (selected != null) {
        RemoteFactoryVisitor(
            factory = selected,
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
                        color = Steel300,
                    )
                    Text(
                        online.uid,
                        style = MaterialTheme.typography.bodySmall,
                        color = Steel100,
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
                            color = Steel300,
                        )
                    }
                    Text("VISITAR ›", color = SafetyAmber, fontWeight = FontWeight.Black)
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
    onBack: () -> Unit,
) {
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
                eyebrow = "MODO VISITANTE",
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
                    "Modo visitante: a cena é animada localmente a partir do snapshot do Firebase; " +
                        "você observa, mas não altera a fábrica do outro jogador."
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
            }
        }
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
