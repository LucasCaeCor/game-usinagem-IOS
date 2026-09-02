# Plano Detalhado de Migração para iOS - Usinagem Master

**Projeto:** `game-usinagem-1788385659680`  
**Estratégia Escolhida:** Compose Multiplatform (Kotlin Multiplatform + Compose Multiplatform UI)  
**Pontuação Inicial de Compatibilidade:** 52%

---

## 1. Visão Geral e Diretrizes Principais
- **Preservação de Regras de Negócio:** Nenhuma regra de economia (dinheiro em `Long` centavos), XP, contratos, contratação/massa salarial de funcionários, exaustão, turnos, manutenção de máquinas e ticks de simulação da Fábrica Viva será removida ou alterada.
- **Aproximação Multiplataforma:** Toda a lógica de domínio, modelos de dados, repositórios e telas Compose reutilizáveis serão centralizados em `commonMain` no módulo `composeApp`.
- **Isolamento de APIs Android:** Dependências específicas da JVM/Android (Hilt, Room com SQLite Android, WorkManager, DataStore Android) serão isoladas via interfaces/abstrações em `commonMain` ou substituídas por soluções nativas KMP (Koin, Room KMP / Gitlive Firebase / expect-actual).

---

## 2. Diagnóstico de Bloqueadores Android & Estratégia de Migração

| Bloqueador Android | Impacto no iOS | Solução de Migração Multiplataforma |
| :--- | :--- | :--- |
| **Hilt / Dagger** | Específico para Android/JVM | Migrar a Injeção de Dependência para **Koin** ou DI manual no `commonMain`. |
| **Room Database** | Requer driver SQLite Android nativo | Migrar para **Room KMP** (support multiplatform) ou abstrair camada Data Source com SQLite/Bundled driver no iOS. |
| **WorkManager** | Background jobs do Android | Substituir por simulação Idle baseada em carimbo de data/hora (`lastSimulationAt`) + Background Tasks nativos via `iosMain` / `BGAppRefreshTask` se necessário. |
| **DataStore** | DataStore Preferences Android | Migrar para **DataStore Multiplatform** (OKIO) ou `NSUserDefaults` / `expect/actual Settings`. |
| **Firebase SDKs** | Dependências Android do Play Services | Utilizar **Gitlive Firebase KMP** ou abstrações `expect/actual` vinculadas ao Firebase iOS SDK nativo via Cocoapods/SPM. |
| **APIs `android.*`** | Incompatíveis com iOS | Mover para `androidMain` e criar abstrações em `commonMain` com implementação Swift/Kotlin em `iosMain`. |

---

## 3. Arquitetura Alvo (Kotlin Multiplatform)

```text
composeApp/
├── src/
│   ├── commonMain/
│   │   ├── kotlin/br/com/usinagemmaster/
│   │   │   ├── domain/        (Modelos: Machine, Employee, Contract, Money, XP, Shift, Exhaustion)
│   │   │   ├── data/          (Repositories, Catalogs, KMP Room DB / DataStore Abstractions)
│   │   │   ├── di/            (Koin Modules)
│   │   │   └── ui/            (Compose Multiplatform Screens, Components, Theme)
│   ├── androidMain/           (Entrypoint Android, Hilt/Koin bridge se necessário, Context)
│   └── iosMain/               (ViewController factory para Swift, driver SQLite iOS)
iosApp/                        (Projeto Xcode / Swift UI Wrapper com MainViewController)
```

---

## 4. Fases Detalhadas de Execução

### Fase 1: Domínio e Regras de Negócio em `commonMain` (Em Progresso / Próximo Lote)
1. Extrair os modelos puros de domínio (`Machine`, `Employee`, `Contract`, `Shift`, `Exhaustion`, `GameSave`, `Catalog`) para `composeApp/src/commonMain`.
2. Garantir que todo manuseio de dinheiro permaneça utilizando `Long` (centavos) para evitar erros de ponto flutuante.
3. Extrair os cálculos da **Fábrica Viva** (simulação idle de até 8 horas, desgaste de máquinas, fadiga/exaustão de funcionários) para funções puras de domínio em `commonMain`.

### Fase 2: Persistência & Injeção de Dependências
1. Configurar Koin em `commonMain` substituindo anotações `@HiltViewModel` e `@Inject`.
2. Configurar Room KMP ou abstração `GameDatabase` compartilhada.
3. Substituir `DataStore Preferences` por versão multiplataforma KMP.

### Fase 3: Camada de Apresentação (Compose Multiplatform)
1. Migrar telas Jetpack Compose (Dashboard, Máquinas, Funcionários, Contratos, Finanças, Fábrica Viva) para `commonMain/ui`.
2. Substituir chamadas de UI dependentes do Android (ex: `Toast`, `Context`, `Resources` do Android) por componentes multiplataforma.

### Fase 4: Integração iOS Nativa e Xcode
1. Exportar framework Kotlin em `iosMain` via `MainViewController.kt` (`ComposeUIViewController`).
2. Integrar com o projeto Xcode em `iosApp/` e `AppDelegate.swift` / `ContentView.swift`.
3. Validar a compilação no macOS CI workflow (`.github/workflows/ios-unsigned.yml`).

---

## 5. Próximo Lote Recomendado
- **Lote 1:** Copiar e isolar os modelos de domínio (`domain`) e o catálogo fixo (`MachineCatalog`) para `commonMain`, removendo quaisquer referências ao `android.*` ou Hilt.
