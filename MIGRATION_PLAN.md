# Droid2iOS Migration Plan

Projeto: **game-usinagem-IOS**

Estratégia: **compose-multiplatform**

Compatibilidade inicial: **60%**

## Sinais detectados
- Jetpack Compose
- WorkManager
- Android Gradle Plugin
- Room
- Hilt
- Firebase
- DataStore

## Bloqueadores iOS
- [ ] WorkManager precisa de equivalente iOS
- [ ] Room requer camada multiplataforma ou implementação iOS
- [ ] Hilt é Android/JVM; migrar DI para Koin/manual/common
- [ ] Firebase precisa de configuração/SDK iOS
- [ ] DataStore precisa ser isolado/substituído para iOS
- [ ] APIs android.* precisam ser isoladas de commonMain

## Plano do agente
1. Manter o projeto Android de origem intacto no repositório original.
2. Extrair modelos, regras de negócio e estado para commonMain.
3. Migrar UI Compose reutilizável para commonMain.
4. Isolar android.* e bibliotecas Android-only.
5. Criar implementações equivalentes em iosMain.
6. Fazer o workflow macOS compilar até ficar verde.
7. Gerar IPA sem assinatura para sideload manual.
