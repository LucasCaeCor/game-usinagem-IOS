# Usinagem Master iOS — Core V6

## Implementado nesta etapa

- Save local persistente via storage nativo do iOS.
- Estado central compartilhado em `commonMain`.
- Empresa inicial `Oficina Império do Aço`, R$ 35.000,00 e torno inicial.
- Ciclos de produção de 10 minutos.
- Produção offline limitada a 24h por abertura.
- Carga persistente: produção não entra no caixa até o dono realizar a entrega.
- Viagem de entrega captura os IDs existentes; carga nova fica para a próxima viagem.
- Contratos com qualidade, progresso, recompensa, reputação, multa e expiração.
- Máquinas: compra, manutenção, revenda e posição no layout.
- Funcionários: contratação, especialidade, experiência, skill, moral, exaustão, máquina e Copa.
- Turno 07:00–19:00 e operação 24h.
- Expansão do galpão.
- Metas e recompensas.
- Especializações e árvore de skills.
- Ferramentas, skins, personagem e Roleta Industrial.
- Finanças locais.
- Fábrica Viva funcional simplificada em Compose Canvas.
- Perfil público local.

## Ainda externo ao core local

Firebase iOS (Auth/Firestore), ranking, presença, visitas online e apoio entre jogadores exigem configuração
do projeto Firebase para o bundle iOS. O jogo local não depende deles.

## Validação

Execute o GitHub Actions `iOS - Unsigned IPA`. Depois, no projeto local:

```bash
node check-usinagem-ios-parity-v5.mjs
```

O gate V5 deve deixar de encontrar `MigrationSection`, `será migrado` e `nas próximas etapas`.
