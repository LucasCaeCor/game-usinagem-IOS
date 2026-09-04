# Usinagem Master iOS — Firestore + Safe Area V8C

## Corrigido

- HUD respeita status bar / notch / Dynamic Island via `statusBarsPadding()`.
- Barra inferior respeita o Home Indicator via `navigationBarsPadding()`.
- FirebaseFirestore adicionado ao target iOS.
- Login Google agora dispara recuperação da identidade social.
- Procura de conta/fábrica em:
  1. `public_factories/{uid}` — schema atual Android V20;
  2. `players/{uid}` — perfil legado;
  3. `player_accounts/{uid}` — compatibilidade.
- Tela Comunidade deixou de ser placeholder.
- Publicação da fábrica local em `public_factories/{uid}`.
- Lista de até 80 fábricas públicas.
- Visita com Fábrica Viva remota animada a partir do snapshot.
- Mercado `character_offers`.
- Contratação por 48h com transaction e registro em `character_rentals`.
- Erros reais do Firestore aparecem na UI.

## Importante sobre a "conta antiga"

O login Firebase recupera a identidade e os snapshots sociais existentes do mesmo UID.
O Android mantém o save principal local (Room/DataStore), e o Firestore é a camada social.
Portanto dinheiro/contratos/máquinas locais antigos só podem ser restaurados como save completo
se eles tiverem sido enviados a uma estrutura específica de cloud save. A V8C não sobrescreve
o save do iPhone usando um snapshot público incompleto.

## Regras

`FIRESTORE_V8C_RULES_FRAGMENT.txt` é apenas um fragmento de referência.
Mescle-o nas regras existentes e publique no Firebase Console se a UI retornar PERMISSION_DENIED.
