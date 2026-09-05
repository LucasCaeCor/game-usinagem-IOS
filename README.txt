USINAGEM MASTER — FIX V26 (UI/UX + FÁBRICA VIVA)
===================================================

FOCO DA V26
-----------
Esta versão ataca os problemas de visual, legibilidade e interação citados pelo usuário:

- Fábrica Viva menos inclinada e com área útil melhor aproveitada.
- HUD mais compacta e menos poluída.
- Ações do turno mais claras.
- Operadores com frases no card selecionado.
- Lista técnica com seleção do operador disponível até o mais experiente.
- Botão para “Melhor operador” e para “Auto distribuir” toda a equipe.
- Modo foco com temporizador e bloqueio enquanto ativo.
- Timer para ficha diária e bônus diário.
- “Rouleta” corrigido para “Roleta”.
- Lendários deixam de ser contratados diretamente; agora a função avisa que eles são obtidos apenas por Roleta/Missões.
- Valor de dinheiro com separador de milhar (ex.: R$ 2.533.703,69).

O QUE A V26 ALTERA DIRETAMENTE
------------------------------
- FactoryStudio.kt
- GameStore.kt
- rótulos literais em arquivos .kt (quando encontrar “Rouleta” e “Contratar lendário”)

OBSERVAÇÃO IMPORTANTE
---------------------
A V26 é um passe visual/funcional seguro sobre o núcleo KMP atual.
Ela NÃO altera:
- save KMP V4;
- economia principal;
- contratos;
- sincronização Firebase/Cloud Save;
- engine de produção.

SOBRE SOM E EFEITOS
-------------------
Esta V26 reforça feedback visual (HUD, destaques, timers, estado do operador e leitura do turno).
Se a sua branch atual não tiver uma camada multiplataforma de áudio já definida, o patch não injeta um player nativo do zero.
Ou seja: melhora a sensação visual e a leitura da cena agora, sem arriscar quebrar o build com integração de áudio ad-hoc.

COMO APLICAR
------------
Extraia o ZIP mantendo a pasta payload.

Na raiz do game-usinagem-IOS:

    node aplicar-ios-ui-ux-fabrica-viva-v26.mjs

Ou:

    node aplicar-ios-ui-ux-fabrica-viva-v26.mjs --root "C:\caminho\game-usinagem-IOS"

VALIDAR
-------

    node aplicar-ios-ui-ux-fabrica-viva-v26.mjs --check

SIMULAR
-------

    node aplicar-ios-ui-ux-fabrica-viva-v26.mjs --dry-run

DEPOIS
------
Faça commit/push e rode novamente o GitHub Actions / build iOS.
