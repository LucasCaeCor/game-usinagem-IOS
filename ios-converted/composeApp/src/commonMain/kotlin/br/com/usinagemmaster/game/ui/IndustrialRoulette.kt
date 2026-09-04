package br.com.usinagemmaster.game.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.game.domain.GameStore
import br.com.usinagemmaster.game.domain.GachaRewardDef
import br.com.usinagemmaster.game.domain.RarityDef
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class RouletteSlice(
    val label: String,
    val glyph: String,
    val color: Color,
)

private val rouletteSlices = listOf(
    RouletteSlice("Ferramenta", "◆", ElectricBlue),
    RouletteSlice("Skin", "★", SafetyAmber),
    RouletteSlice("Personagem", "●", RoyalPurple),
    RouletteSlice("Raro", "◇", Color(0xFF4DB6AC)),
    RouletteSlice("Épico", "✦", RoyalPurple),
    RouletteSlice("Máquina", "⚙", SafetyAmberSoft),
    RouletteSlice("Ferramenta", "◆", ElectricBlue),
    RouletteSlice("Lendário", "✶", Color(0xFFFFC857)),
)

@Composable
fun IndustrialRouletteScreen(store: GameStore) {
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var spinning by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RouletteHero(
                title = "${store.state.expansion.gachaTickets} ficha(s)",
                subtitle = "Coleção, ferramentas, personagens, skins e tecnologia.",
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RouletteStat(
                        "Pity épico",
                        "${store.state.expansion.pityEpic}/40",
                        Modifier.weight(1f),
                    )
                    RouletteStat(
                        "Pity lendário",
                        "${store.state.expansion.pityLegendary}/100",
                        Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Steel900),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(312.dp)) {
                        val d = size.minDimension
                        val wheelSize = Size(d * .88f, d * .88f)
                        val topLeft = Offset(
                            (size.width - wheelSize.width) / 2f,
                            (size.height - wheelSize.height) / 2f,
                        )
                        val sweep = 360f / rouletteSlices.size

                        drawCircle(
                            Color.Black.copy(alpha = .35f),
                            radius = d * .47f,
                            center = center,
                        )
                        drawCircle(
                            SafetyAmber.copy(alpha = .18f),
                            radius = d * .46f,
                            center = center,
                        )

                        rotate(rotation.value, pivot = center) {
                            rouletteSlices.forEachIndexed { index, slice ->
                                drawArc(
                                    color = slice.color.copy(
                                        alpha = if (index % 2 == 0) .92f else .74f
                                    ),
                                    startAngle = -90f + index * sweep,
                                    sweepAngle = sweep - .8f,
                                    useCenter = true,
                                    topLeft = topLeft,
                                    size = wheelSize,
                                )
                            }

                            repeat(rouletteSlices.size) { index ->
                                val angle =
                                    (-90.0 + index * sweep.toDouble()) * (PI / 180.0)
                                val radius = d * .39f
                                val end = Offset(
                                    center.x + cos(angle).toFloat() * radius,
                                    center.y + sin(angle).toFloat() * radius,
                                )
                                drawLine(
                                    color = Color(0xFF10171B),
                                    start = center,
                                    end = end,
                                    strokeWidth = 2.4f,
                                )
                            }
                        }

                        drawCircle(Steel950, radius = d * .105f, center = center)
                        drawCircle(SafetyAmber, radius = d * .072f, center = center)
                        drawCircle(
                            Color.White.copy(alpha = .72f),
                            radius = d * .018f,
                            center = center,
                        )

                        val pointer = Path().apply {
                            moveTo(center.x, d * .015f)
                            lineTo(center.x - d * .045f, d * .115f)
                            lineTo(center.x + d * .045f, d * .115f)
                            close()
                        }
                        drawPath(pointer, Color(0xFFFFE7A8))
                        drawLine(
                            color = Color.White.copy(alpha = .75f),
                            start = Offset(center.x, d * .04f),
                            end = Offset(center.x, d * .095f),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rouletteSlices.take(4).forEach { slice ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = slice.color.copy(alpha = .12f),
                    ) {
                        Column(
                            Modifier.padding(vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                slice.glyph,
                                fontWeight = FontWeight.Black,
                                color = slice.color,
                            )
                            Text(
                                slice.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        item {
            store.lastGachaReward?.let { reward ->
                RouletteCard(
                    title = "Última recompensa",
                    subtitle = "${reward.rarity.label} • ${reward.title}",
                ) {
                    RoulettePill(
                        reward.rarity.label.uppercase(),
                        rouletteRarityColor(reward.rarity.label),
                    )
                    Text(
                        "O item já foi registrado no inventário/coleção correspondente."
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (!spinning && store.state.expansion.gachaTickets > 0) {
                        scope.launch {
                            spinning = true
                            val reward = store.spinGacha()
                            if (reward == null) {
                                spinning = false
                                return@launch
                            }
                            val start = rotation.value % 360f
                            rotation.snapTo(start)
                            val sweep = 360f / rouletteSlices.size
                            val targetIndex = rouletteTargetIndex(reward)
                            // O ponteiro está a -90°. O centro do setor premiado deve terminar ali.
                            val sectorCenter = targetIndex * sweep + sweep / 2f
                            val normalizedStart = ((start % 360f) + 360f) % 360f
                            val desiredModulo = (360f - sectorCenter) % 360f
                            val delta = (desiredModulo - normalizedStart + 360f) % 360f
                            rotation.animateTo(
                                targetValue = start + 1_440f + delta,
                                animationSpec = tween(
                                    durationMillis = 2_800,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                            spinning = false
                        }
                    }
                },
                enabled = !spinning && store.state.expansion.gachaTickets > 0,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(
                    if (spinning) "GIRANDO..." else "GIRAR ROLETA",
                    fontWeight = FontWeight.Black,
                )
            }
        }

        item {
            OutlinedButton(
                onClick = store::claimDailyGachaTicket,
                enabled = !spinning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Coletar ficha diária")
            }
        }

        item {
            Text(
                "A ficha é consumida para girar e nunca aparece como prêmio. " +
                    "Colecionáveis únicos não se repetem enquanto houver opção nova.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun rouletteTargetIndex(reward: GachaRewardDef): Int = when {
    reward.rarity == RarityDef.LEGENDARY -> 7
    reward.rarity == RarityDef.EPIC -> 4
    reward.type.contains("PREMIUM", ignoreCase = true) || reward.type.contains("MACHINE", ignoreCase = true) -> 5
    reward.type.contains("CHAR", ignoreCase = true) -> 2
    reward.type.contains("SKIN", ignoreCase = true) -> 1
    reward.rarity == RarityDef.RARE -> 3
    reward.type.contains("TOOL", ignoreCase = true) -> 0
    else -> 6
}

@Composable
private fun RouletteHero(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Steel850),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                "ROLETA INDUSTRIAL",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = SafetyAmber,
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Steel200,
            )
            content()
        }
    }
}

@Composable
private fun RouletteCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
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
            Text(title, fontWeight = FontWeight.Black)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Steel200,
            )
            content()
        }
    }
}

@Composable
private fun RouletteStat(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Steel800,
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = Steel200,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun RoulettePill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = .14f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = color,
        )
    }
}

private fun rouletteRarityColor(label: String): Color = when (label.lowercase()) {
    "lendário", "lendaria", "lendário" -> Color(0xFFFFC857)
    "épico", "epico" -> RoyalPurple
    "raro" -> ElectricBlue
    else -> Steel200
}
