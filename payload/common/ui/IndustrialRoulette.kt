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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.usinagemmaster.game.domain.GameStore
import br.com.usinagemmaster.game.domain.GachaRewardDef
import br.com.usinagemmaster.game.domain.RarityDef
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val ROULETTE_V27 = "roulette_visual_v28_readable"

private data class RouletteSlice(
    val label: String,
    val short: String,
    val glyph: String,
    val color: Color,
    val description: String,
)

private val rouletteSlices = listOf(
    RouletteSlice("Ferramenta", "FERR.", "🔧", ElectricBlue, "Brocas, fresas, pastilhas e ferramentas de processo"),
    RouletteSlice("Skin", "SKIN", "👕", SafetyAmber, "Visual e bônus do personagem"),
    RouletteSlice("Personagem", "PERS.", "👤", RoyalPurple, "Personagem colecionável"),
    RouletteSlice("Raro", "RARO", "💎", Color(0xFF4DB6AC), "Recompensa de raridade rara"),
    RouletteSlice("Épico", "ÉPICO", "✦", RoyalPurple, "Recompensa de raridade épica"),
    RouletteSlice("Máquina", "MÁQ.", "⚙", SafetyAmberSoft, "Máquina ou tecnologia premium"),
    RouletteSlice("Ferramenta", "FERR.", "🔩", ElectricBlue, "Ferramenta especial para contratos"),
    RouletteSlice("Equipe lendária", "LEND.", "♛", Color(0xFFFFC857), "Funcionário lendário ou recompensa lendária"),
)

@Composable
fun IndustrialRouletteScreen(store: GameStore) {
    val rotation = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
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
                colors = CardDefaults.elevatedCardColors(containerColor = Steel900, contentColor = Steel100),
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
                                    color = slice.color.copy(alpha = if (index % 2 == 0) .92f else .74f),
                                    startAngle = -90f + index * sweep,
                                    sweepAngle = sweep - .8f,
                                    useCenter = true,
                                    topLeft = topLeft,
                                    size = wheelSize,
                                )
                                val angle = (-90.0 + index * sweep.toDouble() + sweep / 2.0) * (PI / 180.0)
                                val iconCenter = Offset(
                                    center.x + cos(angle).toFloat() * d * .315f,
                                    center.y + sin(angle).toFloat() * d * .315f,
                                )
                                drawRouletteGlyphV27(index, iconCenter, d * .055f, Color.White.copy(alpha=.96f))
                                val labelCenter = Offset(
                                    center.x + cos(angle).toFloat() * d * .205f,
                                    center.y + sin(angle).toFloat() * d * .205f,
                                )
                                val labelLayout = textMeasurer.measure(
                                    slice.short,
                                    style = TextStyle(color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black),
                                )
                                drawText(
                                    labelLayout,
                                    topLeft = Offset(labelCenter.x - labelLayout.size.width / 2f, labelCenter.y - labelLayout.size.height / 2f),
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
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                rouletteSlices.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        row.forEach { slice ->
                            Surface(modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp),color=slice.color.copy(alpha=.12f),border=androidx.compose.foundation.BorderStroke(1.dp,slice.color.copy(alpha=.24f))) {
                                Column(Modifier.padding(vertical=8.dp,horizontal=3.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                    Text(slice.glyph,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Black,color=slice.color)
                                    Text(slice.label,style=MaterialTheme.typography.labelSmall,maxLines=2,color=Steel100,fontWeight=FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Surface(shape=RoundedCornerShape(14.dp), color=Steel850, border=androidx.compose.foundation.BorderStroke(1.dp,Steel700)) {
                Column(Modifier.padding(10.dp), verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("LEGENDA DA ROLETA", fontWeight=FontWeight.Black, color=Steel100)
                    rouletteSlices.distinctBy { it.label }.forEach { slice ->
                        Row(horizontalArrangement=Arrangement.spacedBy(7.dp), verticalAlignment=Alignment.CenterVertically) {
                            Text(slice.glyph)
                            Column {
                                Text(slice.label, style=MaterialTheme.typography.labelMedium, fontWeight=FontWeight.Bold, color=slice.color)
                                Text(slice.description, style=MaterialTheme.typography.labelSmall, color=Steel400)
                            }
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
                            GameFeedback.play(GameSoundEffect.MACHINE_START, store.state.uiSettings.soundEnabled)
                            GameFeedback.haptic(store.state.uiSettings.hapticsEnabled)
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
                            GameFeedback.play(GameSoundEffect.REWARD, store.state.uiSettings.soundEnabled)
                            GameFeedback.haptic(store.state.uiSettings.hapticsEnabled)
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
                onClick = { store.claimDailyGachaTicket(); GameFeedback.play(GameSoundEffect.REWARD, store.state.uiSettings.soundEnabled) },
                enabled = !spinning && store.dailyTicketRemainingMillis == 0L,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (store.dailyTicketRemainingMillis == 0L) "🎟 COLETAR FICHA DIÁRIA" else "🎟 PRÓXIMA EM ${formatV27Duration(store.dailyTicketRemainingMillis)}")
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRouletteGlyphV27(index:Int, at:Offset, r:Float, color:Color) {
    val sw=(r*.22f).coerceAtLeast(1.5f)
    when(index) {
        0,6 -> { drawCircle(color,r*.62f,at,style=androidx.compose.ui.graphics.drawscope.Stroke(sw)); drawLine(color,at+Offset(-r*.7f,0f),at+Offset(r*.7f,0f),sw); drawLine(color,at+Offset(0f,-r*.7f),at+Offset(0f,r*.7f),sw) }
        1 -> { val p=Path().apply{moveTo(at.x,at.y-r);lineTo(at.x+r*.28f,at.y-r*.26f);lineTo(at.x+r,at.y-r*.22f);lineTo(at.x+r*.42f,at.y+r*.22f);lineTo(at.x+r*.60f,at.y+r);lineTo(at.x,at.y+r*.52f);lineTo(at.x-r*.60f,at.y+r);lineTo(at.x-r*.42f,at.y+r*.22f);lineTo(at.x-r,at.y-r*.22f);lineTo(at.x-r*.28f,at.y-r*.26f);close()};drawPath(p,color) }
        2 -> { drawCircle(color,r*.42f,at+Offset(0f,-r*.42f)); drawArc(color,190f,160f,false,Offset(at.x-r*.78f,at.y-r*.05f),Size(r*1.56f,r*1.30f),style=androidx.compose.ui.graphics.drawscope.Stroke(sw)) }
        3 -> { val p=Path().apply{moveTo(at.x,at.y-r);lineTo(at.x+r,at.y);lineTo(at.x,at.y+r);lineTo(at.x-r,at.y);close()};drawPath(p,color,style=androidx.compose.ui.graphics.drawscope.Stroke(sw)) }
        4 -> { drawCircle(color,r*.68f,at,style=androidx.compose.ui.graphics.drawscope.Stroke(sw));drawCircle(color,r*.18f,at);repeat(4){i->val a=i*1.5708;drawLine(color,at+Offset((kotlin.math.cos(a)*r*.72f).toFloat(),(kotlin.math.sin(a)*r*.72f).toFloat()),at+Offset((kotlin.math.cos(a)*r).toFloat(),(kotlin.math.sin(a)*r).toFloat()),sw)} }
        5 -> { drawRoundRect(color.copy(alpha=.18f),at-Offset(r,r*.65f),Size(r*2f,r*1.3f),androidx.compose.ui.geometry.CornerRadius(r*.2f));drawRoundRect(color,at-Offset(r,r*.65f),Size(r*2f,r*1.3f),androidx.compose.ui.geometry.CornerRadius(r*.2f),style=androidx.compose.ui.graphics.drawscope.Stroke(sw));drawCircle(color,r*.16f,at+Offset(r*.55f,0f)) }
        else -> { val p=Path().apply{moveTo(at.x-r*.85f,at.y+r*.70f);lineTo(at.x-r*.55f,at.y-r*.60f);lineTo(at.x,at.y-r*.15f);lineTo(at.x+r*.55f,at.y-r*.60f);lineTo(at.x+r*.85f,at.y+r*.70f);close()};drawPath(p,color);drawCircle(Color(0xFFFFC857),r*.18f,at+Offset(0f,r*.15f)) }
    }
}

private fun rouletteTargetIndex(reward: GachaRewardDef): Int = when {
    reward.type == "legendary_employee" -> 7
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
        colors = CardDefaults.elevatedCardColors(containerColor = Steel850, contentColor = Steel100),
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
        colors = CardDefaults.elevatedCardColors(containerColor = Steel900, contentColor = Steel100),
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
