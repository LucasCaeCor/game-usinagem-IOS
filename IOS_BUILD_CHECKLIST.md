# iOS Build Checklist — Usinagem Master

Este arquivo define os gates objetivos para considerar o pipeline iOS pronto.

## Gate 1 — KMP
- [ ] `gradle :composeApp:compileKotlinMetadata` termina com sucesso.
- [ ] `gradle :composeApp:linkDebugFrameworkIosArm64` termina com sucesso.
- [ ] `Shared.framework` contém `Shared`, `Headers/Shared.h` e exporta `MainViewController`.

## Gate 2 — Xcode shell
- [ ] `xcodegen generate` cria `UsinagemConverted.xcodeproj`.
- [ ] `xcodebuild -project ... -list` encontra o scheme `UsinagemConverted`.
- [ ] `xcodebuild ... -sdk iphoneos CODE_SIGNING_ALLOWED=NO build` termina com `BUILD SUCCEEDED`.

## Gate 3 — App bundle
- [ ] Existe um `.app` em `build/iphoneos`.
- [ ] `Info.plist` possui `CFBundleExecutable` e `CFBundleIdentifier`.
- [ ] O executável principal é ARM64 e existe dentro do `.app`.

## Gate 4 — IPA
- [ ] `UsinagemMaster-unsigned.ipa` contém `Payload/*.app`.
- [ ] O artifact `UsinagemMaster-iOS-unsigned` aparece no GitHub Actions.

Quando os quatro gates estiverem verdes, o pipeline está pronto para assinatura/sideload no iPhone.
Isso ainda não significa que toda a migração funcional do jogo terminou; significa que a cadeia KMP → Xcode → IPA está validada.
