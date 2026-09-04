# Usinagem Master iOS — V9 Cloud Save Android V23

## Objetivo

O iPhone usa o mesmo slot de Cloud Save completo criado pelo Android V23.

Caminhos Firestore:

- `cloud_saves/{firebaseUid}/meta/main`
- `cloud_saves/{firebaseUid}/chunks/{revisionPrefix}_cNNNN`
- `player_accounts/{firebaseUid}`

## Compatibilidade copiada do Android V23

- schema: 1
- chunks: 620.000 caracteres Base64
- GZIP
- SHA-256 do JSON descomprimido
- `saveId`
- `revision`
- `chunkPrefix`
- `chunkCount`
- `checksum`
- meta gravado somente depois dos chunks
- outra instalação/`saveId` diferente: nuvem vence
- revisão remota nova + local inalterado: restaura
- ambos alterados: CONFLICT; nenhum sobrescrito
- force restore e force upload disponíveis

## Segurança de conversão Android <-> iOS

O iOS ainda não implementa todos os sistemas do Android. Por isso `LocalSaveV23Adapter`
guarda o JSON Android completo em Application Support e altera somente campos que o KMP
iOS conhece.

Campos Android-only (por exemplo `legendaryMissions`, dados adicionais de `activeGameplay`,
facilities e chaves futuras) permanecem preservados quando o iPhone publica uma nova revisão.

O save textual KMP continua na chave já existente:

`usinagemmaster.kmp.save.v6`

## Fluxo no login

1. Google Sign-In obtém o mesmo Firebase UID.
2. Antes de criar `GameStore`, o iOS lê `cloud_saves/{uid}/meta/main`.
3. Se o iPhone é uma instalação nova ou possui outro `saveId`, baixa a revisão Android.
4. Reúne os chunks, faz Base64 -> GZIP -> JSON.
5. Confere SHA-256.
6. Converte o JSON Android para o save KMP.
7. Adota o `saveId` do Android.
8. Só então abre o Compose/GameStore.

## Dependência Apple

GzipSwift:
`https://github.com/1024jp/GzipSwift`
versão 7.0.0+

## Regra necessária no Firebase

O Android V23 já adiciona a regra privada de `cloud_saves`. Se o Console ainda retornar
`PERMISSION_DENIED`, publique as regras V23 do projeto Android ou mescle o fragmento
`FIRESTORE_V9_CLOUDSAVE_RULES_FRAGMENT.txt`.
