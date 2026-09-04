USINAGEM MASTER — iOS/KMP — FIX V25.2
======================================

Esta é a versão CONSOLIDADA da V25 + correção Kotlin/Native do mastery.

O QUE O LOG MOSTROU
-------------------
FactoryStudio.kt ainda estava compilando com:

    store.state.career.mastery(machine.machineType)

Essa API não existe em CareerSave. A classe correta já existe em:

    br.com.usinagemmaster.game.domain.MachineMastery

A V25.2 usa:

    MachineMastery(
        machine.machineType,
        store.state.career.masteryXp[machine.machineType] ?: 0
    )

IMPORTANTE
----------
A hotfix V25.1 anterior indicava por engano o import game.model.MachineMastery.
A V25.2 corrige automaticamente esse import caso a V25.1 já tenha sido aplicada.

ESTADOS SUPORTADOS
------------------
1. Projeto ainda pré-V25 -> instala V25.2 completa.
2. V25 aplicada e quebrando em mastery -> corrige in-place.
3. V25.1 anterior aplicada -> troca game.model -> game.domain e valida.
4. V25.2 já aplicada -> --check retorna OK.

COMO APLICAR
------------
Extraia o ZIP mantendo a pasta payload.

Na raiz do game-usinagem-IOS:

    node aplicar-ios-fabrica-viva-paridade-android-v25-2.mjs

Ou:

    node aplicar-ios-fabrica-viva-paridade-android-v25-2.mjs --root "C:\caminho\game-usinagem-IOS"

VALIDAR
-------

    node aplicar-ios-fabrica-viva-paridade-android-v25-2.mjs --check

SIMULAR
-------

    node aplicar-ios-fabrica-viva-paridade-android-v25-2.mjs --dry-run

GITHUB ACTIONS
--------------
Se o script mostrar arquivos modificados no Git, a build remota NÃO verá a fix até você fazer commit e push.
Exemplo:

    git status
    git add ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/ui/FactoryStudio.kt
    git add ios-converted/composeApp/src/commonMain/kotlin/br/com/usinagemmaster/game/domain/GameStore.kt
    git commit -m "fix ios factory studio v25.2"
    git push

Depois rode o Actions novamente.

A V25.2 NÃO ALTERA
------------------
- GameSaveCodec / schema KMP V4
- economia
- contratos
- produção automática
- FactorySimulation
- FactoryOwnerSimulation
- Firebase/Auth/Cloud Save

Ela mantém toda a Fábrica Viva V25 e corrige apenas a incompatibilidade de compilação + instalação consolidada.
