USINAGEM MASTER — iOS/KMP — V27.1 OVERHAUL VISUAL + GAME FEEL
================================================================

Esta V27 é a revisão completa pedida para não deixar a experiência pela metade.
Ela parte do projeto iOS/KMP real enviado pelo usuário e reaproveita a lógica Android/V25.2.

ENTREGAS PRINCIPAIS
-------------------
1. FÁBRICA VIVA
- câmera mais frontal/ortogonal, menos inclinada;
- viewport maior e pan/zoom preservados;
- toque em máquinas, operadores, Q/P/E;
- efeitos de máquina rodando, CNC, cavaco, laser/plasma/solda;
- fala em balão sobre operadores, incluindo frases próprias dos lendários;
- fluxo do dono visualmente explícito: MÁQUINA -> Q -> P -> E;
- áudio ambiente e efeitos vinculados ao estado real de produção.

2. ÁUDIO / FEEDBACK
- factory_ambient.wav (fonte Android do próprio projeto);
- machine_tick.wav (Android);
- weld_spark.wav (Android);
- ui_click.wav;
- machine_start.wav;
- quality_pass.wav;
- reward_sting.wav;
- haptic iOS quando habilitado.

3. HOME / DASHBOARD
- landing “Entrar na fábrica” refeita com máquinas e operadores ao fundo;
- cards compactos com desenho/ícone da categoria;
- hierarquia visual reduzindo a parede de informação;
- caixa/carga/produção/contratos/equipe/evolução/roleta organizados por contexto;
- texto claro sobre fundo escuro.

4. MÁQUINAS / LOJA / GALPÃO
- arte vetorial distinta por família de máquina;
- a Loja mostra a forma da máquina antes da compra;
- parque instalado usa a mesma linguagem visual;
- editor de layout em grade, baia por baia;
- Auto Layout por família/valor tecnológico.

5. EQUIPE / LISTA TÉCNICA
- score de encaixe por especialidade, experiência, skill, moral, fadiga e traço;
- “Melhor operador” por máquina;
- “Auto distribuir” toda a equipe;
- lista ordenada: disponível primeiro, depois melhor encaixe/experiência;
- retratos dos funcionários usando o avatar vetorial;
- foco com cronômetro e sem acumular tempo.

6. ROLETA
- grafia ROLETA corrigida;
- setores com símbolos vetoriais explícitos;
- setor de Equipe Lendária;
- números/textos claros;
- áudio/haptic na rotação e na recompensa;
- timer da ficha diária;
- pity preservado.

7. LENDÁRIOS
- removida a contratação direta da interface;
- hireLegendaryEmployee() não compra mais ninguém;
- lendários entram no pool LENDÁRIO da Roleta;
- duplicatas continuam bloqueadas;
- ao ganhar, o funcionário é criado e a missão lendária correspondente nasce junto.

8. TALENTOS / STORYBOARD
- pesquisa da empresa em jornada visual ligada por nós;
- skills do personagem no mesmo padrão;
- carreira industrial de 31 skills separada por ramos e tiers;
- pré-requisitos, custo, nível e progresso aparecem no contexto do nó.

9. PERSONAGEM
- ajustes finos de proporção, respiração/idle, ombros, uniforme, orelhas,
  sobrancelhas, olhos, nariz, expressão e detalhes faciais;
- estilos Tatuzão, Princesa, Pinóquio, Magrão, Kendão, Treme-treme e Bêbado preservados;
- não adiciona campos ao save.

10. ECONOMIA / TIMERS
- dinheiro: R$ 2.533.703,69;
- timer do bônus diário;
- timer da ficha diária;
- timer do modo foco;
- foco não pode ser reativado/acumulado enquanto ativo.

SEGURANÇA
---------
NÃO altera PersistentGameModels.kt.
NÃO altera GameSaveCodec.
NÃO altera schema 4.
NÃO altera Cloud Save/Firebase/Auth.
NÃO reseta dinheiro, contratos, máquinas ou progresso.
Cria backup automático em .patch-backups/ios-overhaul-visual-gamefeel-v27/.

COMO APLICAR
------------
Extraia o ZIP inteiro. Na raiz do game-usinagem-IOS:

    node aplicar-ios-overhaul-visual-gamefeel-v27.mjs

Ou:

    node aplicar-ios-overhaul-visual-gamefeel-v27.mjs --root "C:\Users\PC-NOVO\Desktop\game-usinagem-IOS"

Antes de alterar:

    node aplicar-ios-overhaul-visual-gamefeel-v27.mjs --dry-run

Depois de aplicar:

    node aplicar-ios-overhaul-visual-gamefeel-v27.mjs --check

Depois faça commit/push e rode o GitHub Actions iOS.

VALIDAÇÃO DE DESENVOLVIMENTO
----------------------------
- verificação sintática Kotlin executada nos arquivos alterados: sem erros de parser;
- checagem de marcadores/APIs entre GameApp, FactoryStudio e GameStore;
- aplicação do .mjs testada em cópia limpa do projeto;
- o ambiente usado para montar a fix não conseguiu baixar o Gradle pela rede, portanto o gate
  final Kotlin/Native continua sendo o workflow macOS/GitHub Actions do próprio projeto.


HOTFIX KOTLIN/NATIVE V27.1
---------------------------
O GitHub Actions da V27 revelou duas incompatibilidades reais de Compose:

1) AndroidV24ParityUi.kt usava BorderStroke sem importar:
   androidx.compose.foundation.BorderStroke

2) FactoryStudio.kt chamava a sobrecarga errada:
   drawText(measurer, layout, ...)

   A V27.1 usa a sobrecarga compatível com o TextLayoutResult já medido:
   drawText(layout, ...)

Essas correções não removem nenhuma funcionalidade da V27 e não alteram save, economia, engine, Cloud Save ou Firebase.

APLICAR
-------
node aplicar-ios-overhaul-visual-gamefeel-v27-1.mjs --root "C:\caminho\game-usinagem-IOS"

VALIDAR
--------
node aplicar-ios-overhaul-visual-gamefeel-v27-1.mjs --root "C:\caminho\game-usinagem-IOS" --check
