# iOS Feature Parity — Usinagem Master

Gerado em: 2026-09-03T21:14:50.298Z

Fonte de verdade: implementação Android atual + documentação funcional do projeto.

## Sistemas

| Sistema | Evidência no KMP/iOS | Status |
|---|---|---|
| Dashboard | DashboardStatus, Produção, Contratos | 🟡 detectado |
| Fábrica Viva | Fábrica, máquina, operador | 🟡 detectado |
| Máquinas | Machine, condition, operating | 🟡 detectado |
| Funcionários | Employee, exaust | 🟡 detectado |
| Contratos | Contract, reward | 🟡 detectado |
| Produção/economia | ProductionEngine, EconomyBalance | 🟡 detectado |
| Turnos/exaustão | — | ❌ pendente |
| Finanças | finance, transaction, cash | 🟡 detectado |
| Expansão do galpão | warehouse, facility | 🟡 detectado |
| Metas/progressão | Goal, reputation, level | 🟡 detectado |
| Pesquisa/especializações | Skill, specialty | 🟡 detectado |
| Ferramentas | qualityBonus, speedMultiplier | 🟡 detectado |
| Roleta Industrial | — | ❌ pendente |
| Personagem/skins | Skin | ❌ pendente |
| Comunidade/social | Community | ❌ pendente |
| Persistência multiplataforma | persist | ❌ pendente |

## Placeholders detectados em App.kt

- ❌ será migrado
- ❌ nas próximas etapas
- ❌ MigrationSection(

## Critério de conclusão

A migração NÃO está concluída enquanto:
- houver placeholders funcionais;
- algum sistema Android atual estiver ausente no commonMain/iosMain;
- persistência/save não sobreviver ao fechamento do app;
- produção/contratos/turnos/exaustão divergirem das regras Android;
- o GitHub Actions não gerar IPA com sucesso.
