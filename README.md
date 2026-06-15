# Lyvox Vault Next

Reconstrução do Lyvox Vault em uma base nova, local-first e offline-first. O projeto antigo fica como referência e não como base de remendo.

## Princípios de Produto
- **Local-first & Offline-first**: O controle total e os dados permanecem estritamente no dispositivo do usuário.
- **Zero Cloud Database**: Sem bancos de dados centralizados ou telemetria em nuvem.
- **Privacidade & Segurança**: Sem telemetria, analytics, Crashlytics ou rastreamento.
- **Backup & Sincronização Sob Controle**: Backups criptografados gerados e controlados manualmente pelo usuário.

---

## Downloads / Artefatos de Release

Os artefatos compilados, validados e prontos para uso estão disponíveis na pasta `release/` do projeto:

### Android (Mobile)
- **Caminho**: [Lyvox-Vault-Next-Android.apk](file:///c:/Users/pedro/OneDrive/Documentos/00-Projetos/testes/teste%20claude/lyvox-vault-next/release/Lyvox-Vault-Next-Android.apk)
- **Nome do Arquivo**: `Lyvox-Vault-Next-Android.apk`
- **Versão**: `1.0.3` (Debug/QA)

### Windows (Desktop)
- **Caminho**: [Lyvox-Vault-Next-Desktop-Windows-x64-Setup.exe](file:///c:/Users/pedro/OneDrive/Documentos/00-Projetos/testes/teste%20claude/lyvox-vault-next/release/Lyvox-Vault-Next-Desktop-Windows-x64-Setup.exe)
- **Nome do Arquivo**: `Lyvox-Vault-Next-Desktop-Windows-x64-Setup.exe`
- **Versão**: `1.0.3`

---

## Guia de Instalação e Uso

### 1. Como Instalar

#### No Desktop (Windows)
1. Navegue até a pasta `release/` e execute o instalador `Lyvox-Vault-Next-Desktop-Windows-x64-Setup.exe`.
2. Siga as instruções do assistente do instalador (NSIS).
3. O aplicativo será instalado no diretório padrão `%LOCALAPPDATA%\Lyvox Vault Next` e criará um atalho de acesso rápido no menu iniciar/desktop.
4. Execute o app diretamente a partir do atalho gerado.

#### No Android
1. Transfira o arquivo `Lyvox-Vault-Next-Android.apk` na pasta `release/` para o seu celular.
2. Certifique-se de que a opção "Instalar de fontes desconhecidas" está habilitada nas configurações do Android.
3. Abra o arquivo APK usando o gerenciador de arquivos e conclua a instalação.
4. Abra o aplicativo instalado sob a marca premium Lyvox Vault.

### 2. Configuração Inicial e Senha Mestra
- **Criar Senha Mestra**: Na primeira inicialização do app (tanto mobile quanto desktop), você será solicitado a definir uma Senha Mestra. Esta senha é usada para derivar as chaves de criptografia Argon2id + AES-256-GCM. **ATENÇÃO: Não há botão de "esqueci minha senha" na nuvem. Guarde-a de forma segura.**
- **Desbloquear**: Toda vez que o app for reaberto ou atingir o tempo limite de inatividade (auto-lock), será necessário digitar a Senha Mestra para descriptografar os dados na memória.

### 3. Principais Módulos do Sistema

#### Cofre de Credenciais
- Permite gerenciar seus logins e senhas com criptografia de ponta a ponta.
- Exibe o nível de força da senha em tempo real usando a barra de força integrada.
- Suporta busca rápida de credenciais.

#### Notas Seguras
- Bloco de notas criptografado localmente no dispositivo.
- Ideal para anotações críticas como chaves privadas, códigos de segurança ou documentos confidenciais.

#### Cofre de Mídia
- Importação direta de fotos e vídeos confidenciais para dentro do cofre criptografado.
- Os arquivos originais importados são copiados e encriptados na base do app. O app alerta e pede permissão para que o usuário decida se quer excluir o original do rolo da câmera, garantindo que arquivos importados não vazem na galeria pública.

#### Gerador de Senhas
- Cria senhas seguras com controle de tamanho, uso de caracteres especiais, números e letras maiúsculas/minúsculas.
- Permite copiar o resultado de forma imediata com o Clipboard seguro.

#### Clipboard Seguro
- Limpa o clipboard do sistema operacional automaticamente após um intervalo configurável nas configurações (`15/30/60/120` segundos), evitando que aplicativos de terceiros acessem a senha copiada.

#### Auto-lock
- Bloqueia o aplicativo automaticamente se ele perder o foco ou ficar ocioso por um período configurável (`1/5/15/30` minutos).

#### Modo Somente Leitura
- Ao ativar a opção "Modo Somente Leitura" nas Configurações, o app entra em estado de exibição protegida:
  - Todo o CRUD (criação, edição, exclusão), importação de arquivos CSV, sincronizações destrutivas e restaurações são bloqueados.
  - Para desativar o modo, o aplicativo solicita a confirmação através da Senha Mestra.

#### Backup e Restauração
- **Backup**: Gera um arquivo `.vault` criptografado localmente usando as chaves de criptografia.
- **Restauração**: Permite restaurar bases antigas a partir de arquivos `.vault` válidos mediante validação da Senha Mestra original correspondente ao backup.

#### Sincronização Local (Sync Local e QR Code)
- **Sync Local**: Exporta ou importa o arquivo `.vaultsync` contendo chaves de sincronização.
- **QR Code Sync**: Inicializa uma conexão local criptografada direta (sem servidores em nuvem) via rede local Wi-Fi entre o Desktop e o Android por meio do escaneamento do QR Code gerado pelo app de origem.

#### Biometria
- **Android**: Permite ativar o desbloqueio biométrico (impressão digital ou reconhecimento facial). Se o dispositivo não possuir biometria configurada, exibe o fallback por senha mestra.
- **Desktop**: Apresenta-se como card informativo específico para dispositivos Android, mantendo a consistência visual sem botões falsos ou opções quebradas.

#### Recuperação de Senha
- Fornece um fluxo de geração de chaves criptográficas de recuperação offline baseadas em perguntas/respostas para redefinir o acesso em caso de perda, sem depender de servidores centralizados.

---

## Como Compilar o Projeto do Zero

Se precisar recompilar as aplicações a partir do código-fonte, utilize os seguintes comandos em seu terminal (PowerShell no Windows):

### 1. Testar e Buildar o Core (Rust)
```powershell
cd core
cargo check
cargo test
```

### 2. Testar e Buildar o Desktop (Tauri / Svelte)
```powershell
cd apps/desktop
# Instalar dependências se necessário
npm install
# Rodar build do frontend UI
npm run build
# Compilar e empacotar o aplicativo de Desktop (gera instalador NSIS)
npm run tauri build
```

### 3. Testar e Buildar o Android (Kotlin / Compose)
```powershell
cd apps/android
# Executar testes unitários
.\gradlew.bat test
# Gerar APK de debug (QA)
.\gradlew.bat assembleDebug
# Executar análise estática (lint)
.\gradlew.bat lintDebug
```

---

## Histórico de QA e Validações
O app foi extensivamente testado no Android (`emulator-5554`) e Desktop (Windows 11) com sucesso:
- **Core Rust**: Todos os testes criptográficos e de validação de schemas estão passando.
- **Desktop Tauri**: Compilação sem avisos de erro e instalador NSIS validado. O app instalado foi verificado no caminho `%LOCALAPPDATA%\Lyvox Vault Next\lyvox-vault.exe`.
- **Android**: Lint limpo e APK debug testado com fluxos de interface reais.
