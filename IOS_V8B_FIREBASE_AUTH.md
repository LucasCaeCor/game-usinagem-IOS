# Usinagem Master iOS — Firebase Auth V8B

Firebase recebido e validado:

- Project ID: `usinagem-a74a1`
- Bundle ID: `br.com.usinagemmaster.ios`
- Google Client ID: presente
- Reversed Client ID: presente
- Google App ID: presente

## Implementado

- Firebase Core inicializado no `AppDelegate`.
- Firebase Authentication.
- Google Sign-In.
- Login opcional na inicialização.
- Botão `Continuar offline`.
- Sessão Google persistida pelo Firebase Auth.
- Gerenciamento da conta em Configurações.
- Logout não apaga o save local.
- URL Scheme configurado com o `REVERSED_CLIENT_ID` real.
- `GoogleService-Info.plist` real incluído no target.
- Deployment target iOS 15.
- Swift Package Manager:
  - Firebase 12.17+
  - GoogleSignIn 9.2+
- `-ObjC` configurado como recomendado para Firebase Auth.

## Ainda separado propositalmente

O login fica pronto nesta etapa. A sincronização das coleções sociais do Firestore
deve copiar o schema exato do Android atualizado antes de escrever documentos para evitar
divergência ou bloqueio pelas regras do Firestore.

O save do jogo continua em `NSUserDefaults`/KMP e não é substituído pelo Firebase.
