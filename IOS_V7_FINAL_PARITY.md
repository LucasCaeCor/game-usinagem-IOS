# Usinagem Master iOS — Final Parity V7

Esta etapa substitui o protótipo de conversão por uma implementação iOS/KMP orientada à paridade com o Android atual.

## Paridade local coberta

- Dashboard industrial.
- Fábrica Viva com simulação operacional determinística.
- Rotas de operadores sem atravessar células.
- Máquina em OFF / IDLE / SETUP / RUNNING / WAITING / MAINTENANCE / BROKEN.
- Micro-rotinas: material → ferramentas → setup → usinagem → inspeção → carga.
- Dono da oficina fazendo coleta → carregamento → entrega → pagamento → retorno.
- Carga capturada por viagem: produção nova espera a próxima entrega.
- Produção em ciclos de 10 minutos e produção offline.
- Multiplicador econômico 3x.
- Máquinas, compra, manutenção, revenda e layout.
- Funcionários, atribuição, experiência, moral, traços, exaustão e Copa.
- Turno 07:00–19:00 e operação 24h.
- Ociosidade/celular, bronca e proteção por salgados.
- Contratos, especiais, dificuldade, qualidade, prazo, multas, histórico.
- Ferramentas reservadas por contrato e consumidas na conclusão.
- Metas iniciais e late-game, inclusive fichas.
- Especializações e pesquisas.
- Personagem com gênero, corpo, pele, cabelo, uniforme, capacete e acessórios.
- Estilos Tatuzão, Princesa, Pinóquio, Magrão, Kendão, Treme-treme e Bêbado.
- Skins com bônus.
- Personagens de roleta.
- Roleta com pity épico/lendário, sem ficha como prêmio e sem duplicar colecionáveis únicos.
- Máquinas premium e seus modificadores.
- Minigame de precisão com cooldown de 15 minutos.
- Bônus diário e ficha diária.
- Finanças.
- Save iOS com migração automática do schema V6 para V7.

## UI/UX

- Tema industrial escuro e consistente.
- Bottom navigation para áreas primárias.
- Cards de atenção para carga e ociosidade.
- Fábrica rolável, sem painéis cobrindo as máquinas.
- Fábrica 2.5D com pinch zoom, pan, +/− e duplo toque para reset.
- Estados de produção coloridos.
- Perfil visual usado também pelo dono na Fábrica Viva.
- Contratos com filtros e ferramentas no próprio contexto.
- Metas, galpão, especialidade e pesquisa organizados em abas.
- Roleta/personagem organizados no mesmo hub.

## Única dependência externa não falsificada

O multiplayer Firebase continua opcional, assim como no Android. Para ranking/presença/visitas/apoio reais no iPhone,
é necessário cadastrar o Bundle ID iOS no projeto Firebase e adicionar a configuração iOS correspondente.
Sem isso, o jogo local permanece funcional e o app não simula respostas online.
