import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.domain.model.DashboardStatus
import br.com.usinagemmaster.domain.model.ProductionSnapshot

private enum class GameSection(
    val title: String,
    val subtitle: String,
) {
    HOME("Início", "Visão geral da sua usinagem"),
    FACTORY("Fábrica viva", "Máquinas, operadores, produção e logística"),
    EMPLOYEES("Funcionários", "Equipe, produtividade e exaustão"),
    CONTRACTS("Contratos", "Produção, qualidade, prazo e recompensa"),
    STORE("Loja", "Máquinas, melhorias e modernização"),
    FACILITY("Reforma", "Expansão e infraestrutura do galpão"),
    FINANCE("Finanças", "Receitas, despesas e fluxo de caixa"),
    GOALS("Metas", "Objetivos, progressão e recompensas"),
    PROFILE("Meu personagem", "Dono da fábrica, XP, skills e skins"),
    COMMUNITY("Comunidade", "Ranking, visitas e mercado profissional"),
    SETTINGS("Configurações", "Som, falas e preferências"),
}

private data class DashboardAction(
    val section: GameSection,
)

@Composable
fun App() {
    MaterialTheme {
        var section by remember { mutableStateOf(GameSection.HOME) }

        // Enquanto a persistência multiplataforma ainda não estiver ligada,
        // o iOS usa o estado inicial verdadeiro do domínio compartilhado.
        val dashboard = remember { DashboardStatus() }
        val production = remember { ProductionSnapshot() }

        Surface(modifier = Modifier.fillMaxSize()) {
            if (section == GameSection.HOME) {
                DashboardHome(
                    dashboard = dashboard,
                    production = production,
                    onNavigate = { section = it },
                )
            } else {
                MigrationSection(
                    section = section,
                    onBack = { section = GameSection.HOME },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardHome(
    dashboard: DashboardStatus,
    production: ProductionSnapshot,
    onNavigate: (GameSection) -> Unit,
) {
    val actions = remember {
        listOf(
            DashboardAction(GameSection.FACTORY),
            DashboardAction(GameSection.EMPLOYEES),
            DashboardAction(GameSection.CONTRACTS),
            DashboardAction(GameSection.STORE),
            DashboardAction(GameSection.FACILITY),
            DashboardAction(GameSection.FINANCE),
            DashboardAction(GameSection.GOALS),
            DashboardAction(GameSection.PROFILE),
            DashboardAction(GameSection.COMMUNITY),
            DashboardAction(GameSection.SETTINGS),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "USINAGEM MASTER",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = dashboard.companyName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 14.dp,
                bottom = 30.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CompanyStatusCard(
                    dashboard = dashboard,
                    production = production,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard(
                        title = "Produção / 10 min",
                        value = formatOneDecimal(production.totalUnitsPer10Minutes) + " pç",
                        subtitle = "${production.operatingMachines} máquinas operando",
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        title = "Lucro / 10 min",
                        value = money(production.netPer10MinutesCents),
                        subtitle = "Energia ${money(production.energyPer10MinutesCents)}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard(
                        title = "Qualidade",
                        value = "${production.averageQuality}%",
                        subtitle = if (production.averageQuality >= 80) {
                            "Padrão industrial alto"
                        } else {
                            "Produção ainda sem leitura"
                        },
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        title = "Contratos",
                        value = dashboard.activeContracts.toString(),
                        subtitle = "${production.idleMachines} máquinas em espera",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Gestão da fábrica",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            items(actions) { action ->
                ActionCard(
                    action = action,
                    onClick = { onNavigate(action.section) },
                )
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "Versão iOS em migração funcional",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "O runtime, Kotlin/Native, Compose, Xcode e IPA já estão operacionais. " +
                                "Agora as telas e a persistência do jogo serão conectadas gradualmente ao commonMain.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanyStatusCard(
    dashboard: DashboardStatus,
    production: ProductionSnapshot,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Sua empresa",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatusValue("Saldo", money(dashboard.cashCents))
                StatusValue("Nível", dashboard.companyLevel.toString())
                StatusValue("Reputação", dashboard.reputation.toString())
            }

            val used = dashboard.usedWarehouseSpace.coerceAtLeast(0)
            val total = dashboard.warehouseSpace.coerceAtLeast(1)
            val progress = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Galpão", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "$used/$total m²",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = if (production.operatingMachines > 0) {
                    "TURNO ATIVO • ${production.operatingMachines} máquina(s) operando"
                } else {
                    "SEM PRODUÇÃO ATIVA"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (production.operatingMachines > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StatusValue(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionCard(
    action: DashboardAction,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                action.section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                action.section.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Abrir")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MigrationSection(
    section: GameSection,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "USINAGEM MASTER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(section.title)
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        section.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "A navegação desta área já está funcionando no iPhone. " +
                            "O conteúdo Android deste módulo será migrado para commonMain nas próximas etapas.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Voltar para o início")
                    }
                }
            }
        }
    }
}

private fun money(cents: Long): String {
    val safe = if (cents == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(cents)
    val reais = safe / 100L
    val centavos = safe % 100L
    val sign = if (cents < 0L) "-" else ""
    return "${sign}R$ $reais,${centavos.toString().padStart(2, '0')}"
}

private fun formatOneDecimal(value: Double): String {
    val scaled = (value * 10.0).toLong()
    val whole = scaled / 10L
    val decimal = kotlin.math.abs(scaled % 10L)
    return "$whole,$decimal"
}
