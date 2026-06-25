<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { listen } from "@tauri-apps/api/event";
  import { save, open } from "@tauri-apps/plugin-dialog";
  import QRCode from "svelte-qrcode";
  import { showToast, themeChanged, currentTab } from "../stores/session";
  import type { AppSettings, RecoveryStatus } from "../types";

  let settings = $state<AppSettings>({
    auto_lock_minutes: 5,
    clipboard_clear_seconds: 30,
    theme: "dark",
    privacy_mode: false,
    read_only_mode: false,
    password_gen_defaults: {
      length: 24,
      use_uppercase: true,
      use_lowercase: true,
      use_numbers: true,
      use_special: true,
      special_chars: "!@#$%^&*()-_=+[]{}|;:,.<>?",
      exclude_ambiguous: false,
    },
  });

  let recoveryStatus = $state<RecoveryStatus | null>(null);
  let loading = $state(true);
  let backupPassword = $state("");
  let backupPasswordConfirm = $state("");
  let showBackupForm = $state(false);
  let exporting = $state(false);
  let importPassword = $state("");
  let importing = $state(false);
  let importReplace = $state(true);
  let importFileContent = $state<string | null>(null);
  let importFileReady = $state(false);

  // Clear data
  let showClearForm = $state(false);
  let clearPassword = $state("");
  let clearPasswordConfirm = $state("");
  let clearing = $state(false);

  // SincronizaÃ§Ã£o Local
  let showSyncExportForm = $state(false);
  let syncExportPassword = $state("");
  let syncExportPasswordConfirm = $state("");
  let syncingExport = $state(false);
  let syncImportPassword = $state("");
  let syncing = $state(false);
  let syncFileContent = $state<string | null>(null);
  let syncFileReady = $state(false);

  // Sync Server
  let qrCodeData = $state<any>(null);
  let syncServerRunning = $state(false);

  // Updater
  let checkingUpdate = $state(false);

  // Auto-save state
  let autoSaving = $state(false);
  let autoSaved = $state(false);
  let settingsLoaded = $state(false);
  let autoSaveTimer: ReturnType<typeof setTimeout> | null = null;

  $effect(() => {
    loadSettings();
    loadRecoveryStatus();

    const unlistenStatus = listen("sync_status", (event) => {
      syncing = false;
      showToast(String(event.payload), "success");
    });
    const unlistenError = listen("sync_error", (event) => {
      syncing = false;
      showToast(String(event.payload), "error");
    });
    const unlistenStarted = listen("sync_started", (event) => {
      syncing = true;
      showToast(String(event.payload), "info");
    });

    return () => {
      unlistenStatus.then(f => f());
      unlistenError.then(f => f());
      unlistenStarted.then(f => f());
    };
  });

  async function loadSettings() {
    loading = true;
    try {
      settings = await invoke<AppSettings>("get_settings");
      setTimeout(() => { settingsLoaded = true; }, 50);
    } catch (e) {
      showToast(String(e), "error");
    } finally {
      loading = false;
    }
  }

  async function loadRecoveryStatus() {
    try {
      recoveryStatus = await invoke<RecoveryStatus>("get_recovery_status");
    } catch {
      // Recovery not configured
    }
  }

  $effect(() => {
    const theme = settings.theme;
    const autoLock = settings.auto_lock_minutes;
    const clipboard = settings.clipboard_clear_seconds;
    const privacy = settings.privacy_mode;
    const readOnly = settings.read_only_mode;

    if (!settingsLoaded) return;

    if (autoSaveTimer) clearTimeout(autoSaveTimer);

    autoSaveTimer = setTimeout(async () => {
      autoSaving = true;
      try {
        await invoke("update_settings", {
          autoLockMinutes: autoLock,
          clipboardClearSeconds: clipboard,
          theme: theme,
          privacyMode: privacy,
          readOnlyMode: readOnly,
        });
        themeChanged.update((n) => n + 1);
        autoSaved = true;
        setTimeout(() => { autoSaved = false; }, 2000);
      } catch (e) {
        showToast("Erro ao salvar configuraÃ§Ã£o: " + String(e), "error");
      } finally {
        autoSaving = false;
      }
    }, 600);
  });

  async function saveSettings() {
    try {
      await invoke("update_settings", {
        autoLockMinutes: settings.auto_lock_minutes,
        clipboardClearSeconds: settings.clipboard_clear_seconds,
        theme: settings.theme,
        privacyMode: settings.privacy_mode,
        readOnlyMode: settings.read_only_mode,
      });
      showToast("ConfiguraÃ§Ãµes salvas", "success");
      themeChanged.update((n) => n + 1);
    } catch (e) {
      showToast(String(e), "error");
    }
  }

  // â”€â”€â”€ Sync Server â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
  async function startSyncServer() {
    try {
      qrCodeData = await invoke("start_sync_server");
      syncServerRunning = true;
      showToast("Servidor de sincronizaÃ§Ã£o iniciado", "success");
    } catch (e) {
      showToast("Erro ao iniciar servidor: " + String(e), "error");
    }
  }

  async function stopSyncServer() {
    try {
      await invoke("stop_sync_server");
      syncServerRunning = false;
      qrCodeData = null;
    } catch (e) {
      showToast("Erro ao parar servidor: " + String(e), "error");
    }
  }

  // â”€â”€â”€ Backup â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  async function handleExportBackup() {
    if (!backupPassword || backupPassword.length < 8) {
      showToast("A senha de backup deve ter no mÃ­nimo 8 caracteres.", "error");
      return;
    }
    if (backupPassword !== backupPasswordConfirm) {
      showToast("As senhas nÃ£o coincidem.", "error");
      return;
    }

    exporting = true;
    try {
      const envelopeJson = await invoke<string>("export_backup", {
        backupPassword,
      });

      // Abre diÃ¡logo para salvar arquivo
      const filePath = await save({
        defaultPath: `lyvox-vault-backup-${new Date().toISOString().slice(0, 10)}.vault`,
        filters: [{ name: "lyvox vault Backup", extensions: ["vault"] }],
      });

      if (!filePath) {
        showToast("ExportaÃ§Ã£o cancelada.", "info");
        return;
      }

      // Escreve o arquivo via comando Rust customizado
      await invoke("write_backup_file", {
        path: filePath,
        content: envelopeJson,
      });

      showToast("Backup exportado com sucesso!", "success");
      backupPassword = "";
      backupPasswordConfirm = "";
      showBackupForm = false;
    } catch (e) {
      showToast("Erro ao exportar backup: " + String(e), "error");
    } finally {
      exporting = false;
    }
  }

  async function handleSelectImportFile() {
    try {
      const filePath = await open({
        multiple: false,
        filters: [{ name: "lyvox vault Backup", extensions: ["vault"] }],
      });

      if (!filePath) return;

      // LÃª o arquivo via comando Rust customizado
      const content = await invoke<string>("read_backup_file", {
        path: String(filePath),
      });

      importFileContent = content;
      importFileReady = true;
      showToast("Arquivo de backup carregado.", "info");
    } catch (e) {
      showToast("Erro ao ler arquivo: " + String(e), "error");
    }
  }

  async function handleImportBackup() {
    if (!importPassword || importPassword.length < 8) {
      showToast("A senha de backup deve ter no mÃ­nimo 8 caracteres.", "error");
      return;
    }
    if (!importFileContent) {
      showToast("Selecione um arquivo de backup primeiro.", "error");
      return;
    }

    importing = true;
    try {
      const result = await invoke<string>("import_backup", {
        backupData: importFileContent,
        backupPassword: importPassword,
        replace: importReplace,
      });

      showToast(result, "success");
      importPassword = "";
      importFileContent = null;
      importFileReady = false;
    } catch (e) {
      showToast("Erro ao restaurar backup: " + String(e), "error");
    } finally {
      importing = false;
    }
  }

  // â”€â”€â”€ Excluir Dados â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  async function handleClearData() {
    if (!clearPassword || clearPassword.length < 8) {
      showToast("Digite sua senha mestra.", "error");
      return;
    }
    if (clearPassword !== clearPasswordConfirm) {
      showToast("As senhas nÃ£o coincidem.", "error");
      return;
    }

    clearing = true;
    try {
      const result = await invoke<string>("clear_all_vault_data", {
        password: clearPassword,
      });
      showToast(result, "success");
      clearPassword = "";
      clearPasswordConfirm = "";
      showClearForm = false;
    } catch (e) {
      showToast(String(e), "error");
    } finally {
      clearing = false;
    }
  }

  // â”€â”€â”€ SincronizaÃ§Ã£o Local â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  async function handleExportSync() {
    if (!syncExportPassword || syncExportPassword.length < 8) {
      showToast("A senha de sincronizaÃ§Ã£o deve ter no mÃ­nimo 8 caracteres.", "error");
      return;
    }
    if (syncExportPassword !== syncExportPasswordConfirm) {
      showToast("As senhas nÃ£o coincidem.", "error");
      return;
    }

    syncingExport = true;
    try {
      const syncJson = await invoke<string>("export_sync_package", {
        syncPassword: syncExportPassword,
      });

      // Abre diÃ¡logo para salvar arquivo
      const filePath = await save({
        defaultPath: `lyvox-vault-sync-${new Date().toISOString().slice(0, 10)}.vaultsync`,
        filters: [{ name: "lyvox vault Sync Package", extensions: ["vaultsync"] }],
      });

      if (!filePath) {
        showToast("SincronizaÃ§Ã£o cancelada.", "info");
        return;
      }

      // Escreve o arquivo
      await invoke("write_backup_file", {
        path: filePath,
        content: syncJson,
      });

      showToast("Pacote de sincronizaÃ§Ã£o exportado com sucesso!", "success");
      syncExportPassword = "";
      syncExportPasswordConfirm = "";
      showSyncExportForm = false;
    } catch (e) {
      showToast("Erro ao exportar sincronizaÃ§Ã£o: " + String(e), "error");
    } finally {
      syncingExport = false;
    }
  }

  async function handleSelectSyncFile() {
    try {
      const filePath = await open({
        multiple: false,
        filters: [{ name: "lyvox vault Sync Package", extensions: ["vaultsync"] }],
      });

      if (!filePath) return;

      // LÃª o arquivo
      const content = await invoke<string>("read_backup_file", {
        path: String(filePath),
      });

      syncFileContent = content;
      syncFileReady = true;
      showToast("Pacote de sincronizaÃ§Ã£o carregado.", "info");
    } catch (e) {
      showToast("Erro ao ler arquivo: " + String(e), "error");
    }
  }

  async function handleImportSync() {
    if (!syncImportPassword || syncImportPassword.length < 8) {
      showToast("Digite a senha de sincronizaÃ§Ã£o.", "error");
      return;
    }
    if (!syncFileContent) {
      showToast("Selecione um pacote de sincronizaÃ§Ã£o primeiro.", "error");
      return;
    }

    syncing = true;
    try {
      const result = await invoke<string>("import_sync_package", {
        syncData: syncFileContent,
        syncPassword: syncImportPassword,
      });

      showToast(result, "success");
      syncImportPassword = "";
      syncFileContent = null;
      syncFileReady = false;
    } catch (e) {
      showToast("Erro na sincronizaÃ§Ã£o: " + String(e), "error");
    } finally {
      syncing = false;
    }
  }

  // â”€â”€â”€ Updater â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

  async function handleCheckUpdate() {
    showToast("Atualizacoes automaticas externas desativadas. Use apenas instaladores locais gerados pelo projeto.", "info");
  }
</script>

<div class="settings-panel animate-fade-in">
  <div class="panel-header">
    <h2>ConfiguraÃ§Ãµes</h2>
    {#if autoSaving}
      <span class="autosave-indicator autosave-saving">Salvandoâ€¦</span>
    {:else if autoSaved}
      <span class="autosave-indicator autosave-done">âœ“ Salvo</span>
    {/if}
  </div>

  {#if loading}
    <p class="loading-text">Carregandoâ€¦</p>
  {:else}
    <div class="settings-card">
      <!-- SeguranÃ§a -->
      <div class="settings-section">
        <h3>SeguranÃ§a</h3>

        <label class="setting-row">
          <span class="setting-label">Bloqueio automÃ¡tico apÃ³s</span>
          <div class="setting-control">
            <select bind:value={settings.auto_lock_minutes}>
              <option value={0}>Nunca</option>
              <option value={1}>1 minuto</option>
              <option value={2}>2 minutos</option>
              <option value={5}>5 minutos</option>
              <option value={10}>10 minutos</option>
              <option value={15}>15 minutos</option>
              <option value={30}>30 minutos</option>
              <option value={60}>60 minutos</option>
            </select>
          </div>
        </label>

        <label class="setting-row">
          <span class="setting-label">Limpar clipboard apÃ³s</span>
          <div class="setting-control">
            <select bind:value={settings.clipboard_clear_seconds}>
              <option value={10}>10 segundos</option>
              <option value={30}>30 segundos</option>
              <option value={60}>1 minuto</option>
              <option value={120}>2 minutos</option>
            </select>
          </div>
        </label>

        <label class="setting-row">
          <span class="setting-label">Modo Privacidade (ocultar logins na listagem)</span>
          <div class="setting-control">
            <input type="checkbox" bind:checked={settings.privacy_mode} style="width: auto; cursor: pointer;" />
          </div>
        </label>

        <label class="setting-row">
          <span class="setting-label">Modo Somente Leitura (desativar ediÃ§Ãµes/exclusÃµes)</span>
          <div class="setting-control">
            <input type="checkbox" bind:checked={settings.read_only_mode} style="width: auto; cursor: pointer;" />
          </div>
        </label>
      </div>

      <!-- AparÃªncia -->
      <div class="settings-section">
        <h3>AparÃªncia</h3>

        <label class="setting-row">
          <span class="setting-label">Tema</span>
          <div class="setting-control">
            <select bind:value={settings.theme}>
              <option value="dark">Escuro</option>
              <option value="light">Claro</option>
              <option value="system">Sistema</option>
            </select>
          </div>
        </label>
      </div>

      <!-- Auditoria de SeguranÃ§a -->
      <div class="settings-section">
        <h3>Auditoria de SeguranÃ§a</h3>
        <p class="section-desc">
          Verifique a saÃºde de suas senhas, incluindo senhas fracas, reutilizadas ou antigas.
        </p>
        <div style="margin-top: var(--space-3);">
          <button class="btn-secondary" onclick={() => currentTab.set("audit")}>
            Analisar SaÃºde do Cofre
          </button>
        </div>
      </div>

      <!-- ImportaÃ§Ã£o de Credenciais -->
      <div class="settings-section">
        <h3>ImportaÃ§Ã£o de Credenciais</h3>
        <p class="section-desc">
          Importe logins e senhas a partir de arquivos CSV exportados do Google Chrome, Bitwarden ou 1Password.
        </p>
        <div style="margin-top: var(--space-3);">
          <button class="btn-secondary" onclick={() => currentTab.set("csv_import")}>
            Importar de CSV
          </button>
        </div>
      </div>

      <!-- Backup -->
      <div class="settings-section">
        <h3>Backup</h3>
        <p class="section-desc">
          Exporte seus dados criptografados para guardar em local seguro.
          A restauraÃ§Ã£o substitui ou adiciona os dados no cofre atual.
        </p>

        <div class="backup-actions">
          <button class="btn-primary" onclick={() => (showBackupForm = !showBackupForm)}>
            {showBackupForm ? "Cancelar" : "Exportar Backup"}
          </button>

          <button class="btn-secondary" onclick={handleSelectImportFile} disabled={importing}>
            Selecionar Backup para Restaurar
          </button>
        </div>

        {#if showBackupForm}
          <div class="backup-form">
            <label class="field">
              <span class="field-label">Senha do Backup</span>
              <input
                type="password"
                bind:value={backupPassword}
                placeholder="MÃ­nimo 8 caracteres"
              />
            </label>
            <label class="field">
              <span class="field-label">Confirmar Senha</span>
              <input
                type="password"
                bind:value={backupPasswordConfirm}
                placeholder="Repita a senha"
              />
            </label>
            <button
              class="btn-primary"
              onclick={handleExportBackup}
              disabled={exporting}
            >
              {exporting ? "Exportandoâ€¦" : "Criar e Salvar Backup"}
            </button>
            <p class="field-hint">
              Guarde esta senha em local seguro. Sem ela nÃ£o Ã© possÃ­vel restaurar o backup.
            </p>
          </div>
        {/if}

        {#if importFileReady}
          <div class="backup-form">
            <p class="file-ready-msg">Arquivo de backup carregado.</p>
            <label class="field">
              <span class="field-label">Senha do Backup</span>
              <input
                type="password"
                bind:value={importPassword}
                placeholder="Senha usada ao exportar"
              />
            </label>
            <label class="setting-row">
              <span class="setting-label">Substituir dados existentes</span>
              <input type="checkbox" bind:checked={importReplace} />
            </label>
            <p class="field-hint">
              {#if importReplace}
                Todos os dados atuais serÃ£o removidos antes da restauraÃ§Ã£o.
              {:else}
                Os dados do backup serÃ£o adicionados aos existentes.
              {/if}
            </p>
            <button
              class="btn-danger"
              onclick={handleImportBackup}
              disabled={importing}
            >
              {importing ? "Restaurandoâ€¦" : "Restaurar Backup"}
            </button>
          </div>
        {/if}
      </div>

      <!-- SincronizaÃ§Ã£o Local -->
      <div class="settings-section">
        <h3>SincronizaÃ§Ã£o Local</h3>
        <p class="section-desc">
          Sincronize seus dados com o Lyvox Vault no celular ou outro computador de forma local e segura.
        </p>

        <div class="sync-methods">
          <!-- QR Code -->
          <div class="sync-method-card">
            <h4>ðŸ“± Via QR Code (Rede Local)</h4>
            <p class="field-hint">Fluxo principal. Conecte os dois dispositivos na mesma rede Wi-Fi e escaneie o cÃ³digo com o celular.</p>
            
            {#if syncServerRunning && qrCodeData}
              <div style="background: white; padding: 10px; display: inline-block; border-radius: 8px; margin: 10px 0;">
                <QRCode value={JSON.stringify(qrCodeData)} size={200} />
              </div>
              {#if syncing}
                <div style="margin-top: 10px; color: var(--color-accent); font-weight: bold;">
                  ðŸ”„ Importando/Enviando dados... NÃ£o feche o aplicativo!
                </div>
              {:else}
                <p class="field-hint">Escaneie o cÃ³digo com o aplicativo Android.</p>
                <button class="btn-secondary" onclick={stopSyncServer}>
                  Parar Servidor de SincronizaÃ§Ã£o
                </button>
              {/if}
            {:else}
              <button class="btn-primary" onclick={startSyncServer}>
                Gerar QR Code
              </button>
            {/if}
          </div>

          <!-- USB -->
          <div class="sync-method-card">
            <h4>ðŸ”Œ Via Cabo USB (AvanÃ§ado)</h4>
            <p class="field-hint">Requer modo desenvolvedor e depuraÃ§Ã£o USB (ADB) ativados no celular.</p>
            <button class="btn-secondary" onclick={() => showToast("Detectando dispositivos ADB... Nenhum encontrado.", "error")}>
              Procurar Dispositivos USB/ADB
            </button>
          </div>

          <!-- Fallback -->
          <div class="sync-method-card">
            <h4>ðŸ“ Via Arquivo (.vaultsync)</h4>
            <p class="field-hint">Fallback seguro. Exporte um pacote aqui e importe no outro dispositivo.</p>
            
            <div class="backup-actions" style="margin-top: 1rem;">
              <button class="btn-primary" onclick={() => (showSyncExportForm = !showSyncExportForm)}>
                {showSyncExportForm ? "Cancelar" : "Exportar Pacote"}
              </button>

              <button class="btn-secondary" onclick={handleSelectSyncFile} disabled={syncing}>
                Selecionar Pacote
              </button>
            </div>

            {#if showSyncExportForm}
              <div class="backup-form" style="margin-top: 1rem;">
                <label class="field">
                  <span class="field-label">Senha de SincronizaÃ§Ã£o</span>
                  <input
                    type="password"
                    bind:value={syncExportPassword}
                    placeholder="MÃ­nimo 8 caracteres"
                  />
                </label>
                <label class="field">
                  <span class="field-label">Confirmar Senha</span>
                  <input
                    type="password"
                    bind:value={syncExportPasswordConfirm}
                    placeholder="Repita a senha"
                  />
                </label>
                <button
                  class="btn-primary"
                  onclick={handleExportSync}
                  disabled={syncingExport}
                >
                  {syncingExport ? "Exportandoâ€¦" : "Criar e Salvar Pacote"}
                </button>
              </div>
            {/if}

            {#if syncFileReady}
              <div class="backup-form" style="margin-top: 1rem;">
                <p class="file-ready-msg">Pacote carregado.</p>
                <label class="field">
                  <span class="field-label">Senha de SincronizaÃ§Ã£o</span>
                  <input
                    type="password"
                    bind:value={syncImportPassword}
                    placeholder="Senha definida ao exportar"
                  />
                </label>
                <button
                  class="btn-danger"
                  onclick={handleImportSync}
                  disabled={syncing}
                >
                  {syncing ? "Sincronizandoâ€¦" : "Iniciar SincronizaÃ§Ã£o"}
                </button>
              </div>
            {/if}
          </div>
        </div>
      </div>

      <!-- RecuperaÃ§Ã£o -->
      <div class="settings-section">
        <h3>RecuperaÃ§Ã£o de Senha</h3>
        <div class="recovery-info">
          {#if recoveryStatus?.configured}
            <p class="status-configured">
              âœ… RecuperaÃ§Ã£o configurada ({recoveryStatus.attempts} tentativas realizadas)
            </p>
            <p class="field-hint">
              Para reconfigurar as perguntas, utilize a opÃ§Ã£o "Esqueci minha senha"
              na tela de desbloqueio e, apÃ³s redefinir a senha, configure novas perguntas.
            </p>
          {:else}
            <p class="status-not-configured">
              âš ï¸ RecuperaÃ§Ã£o nÃ£o configurada. Sem ela, nÃ£o serÃ¡ possÃ­vel recuperar
              o acesso se vocÃª esquecer a senha mestra.
            </p>
          {/if}
        </div>
      </div>

      <!-- Excluir Dados -->
      <div class="settings-section settings-section--danger">
        <h3>Excluir Dados</h3>
        <p class="section-desc">
          Remove permanentemente todas as entradas e notas do cofre.
          Esta aÃ§Ã£o nÃ£o pode ser desfeita.
        </p>

        <div class="clear-actions">
          <button
            class="btn-danger"
            onclick={() => (showClearForm = !showClearForm)}
          >
            {showClearForm ? "Cancelar" : "Excluir Todos os Dados"}
          </button>
        </div>

        {#if showClearForm}
          <div class="clear-form">
            <p class="clear-warning">
              âš ï¸ Confirme sua senha mestra duas vezes para excluir todos os dados.
            </p>
            <label class="field">
              <span class="field-label">Senha Mestra</span>
              <input
                type="password"
                bind:value={clearPassword}
                placeholder="Digite sua senha mestra"
              />
            </label>
            <label class="field">
              <span class="field-label">Confirmar Senha Mestra</span>
              <input
                type="password"
                bind:value={clearPasswordConfirm}
                placeholder="Confirme sua senha mestra"
              />
            </label>
            <button
              class="btn-danger"
              onclick={handleClearData}
              disabled={clearing}
            >
              {clearing ? "Excluindoâ€¦" : "Sim, excluir todos os dados"}
            </button>
          </div>
        {/if}
      </div>

      <!-- Biometria (Futuro) -->
      <div class="settings-section">
        <h3>Biometria (Android)</h3>
        <div class="biometry-info">
          <p class="field-hint">
            O desbloqueio por digital estarÃ¡ disponÃ­vel na versÃ£o Android.
            No desktop, o desbloqueio Ã© feito apenas com a senha mestra.
          </p>
          <div class="biometry-placeholder">
            <span class="biometry-icon">ðŸ”’</span>
            <span>Desbloqueio por digital</span>
            <span class="biometry-badge">Android (futuro)</span>
          </div>
          <button class="btn-secondary btn-sm" disabled>
            Remover acesso por digital
          </button>
          <p class="field-hint">
            DisponÃ­vel apenas na versÃ£o Android. Remove o vÃ­nculo biomÃ©trico
            do app sem afetar as digitais cadastradas no dispositivo.
            Exige senha mestra para confirmar.
          </p>
        </div>
      </div>

      <!-- Sobre -->
      <div class="settings-section">
        <h3>Sobre</h3>
        <div class="about-info">
          <p><strong>lyvox vault</strong> v1.0.2</p>
          <p>Gerenciador seguro de senhas local</p>
          <p class="about-security">
            Seus dados sÃ£o criptografados com AES-256-GCM e protegidos por senha mestra.
            Nenhum dado sai do seu computador.
          </p>
          <div style="margin-top: var(--space-4);">
            <button class="btn-secondary" onclick={handleCheckUpdate} disabled={checkingUpdate}>
              {#if checkingUpdate}
                <span class="spinner"></span> Buscando...
              {:else}
                <span>ðŸ”„ Buscar AtualizaÃ§Ãµes</span>
              {/if}
            </button>
          </div>
        </div>
      </div>
    </div>
  {/if}
</div>

<style>
  .settings-panel {
    flex: 1;
    padding: var(--space-8);
    overflow-y: auto;
    max-width: 640px;
    margin: 0 auto;
    width: 100%;
  }

  .panel-header {
    margin-bottom: var(--space-6);
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }

  .panel-header h2 {
    margin-bottom: 0;
  }

  .autosave-indicator {
    font-size: var(--font-size-xs);
    padding: 2px var(--space-2);
    border-radius: var(--radius-sm);
    font-weight: var(--font-weight-medium);
    transition: opacity 0.3s ease;
  }

  .autosave-saving {
    color: var(--color-text-tertiary);
    background: var(--color-surface-hover);
  }

  .autosave-done {
    color: var(--color-success);
    background: var(--color-success-muted);
  }

  .loading-text {
    color: var(--color-text-tertiary);
    font-size: var(--font-size-sm);
  }

  .settings-card {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-xl);
    padding: var(--space-6);
  }

  .settings-section {
    padding-bottom: var(--space-6);
    margin-bottom: var(--space-6);
    border-bottom: 1px solid var(--color-border);
  }

  .settings-section:last-of-type {
    border-bottom: none;
    margin-bottom: 0;
    padding-bottom: 0;
  }

  .settings-section--danger {
    border-bottom-color: var(--color-danger-muted);
  }

  .settings-section--danger h3 {
    color: var(--color-danger);
  }

  .settings-section h3 {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-secondary);
    margin-bottom: var(--space-4);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .section-desc {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
    margin-bottom: var(--space-4);
    line-height: var(--line-height-normal);
  }

  .setting-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--space-3) 0;
    gap: var(--space-4);
    cursor: pointer;
  }

  .setting-label {
    font-size: var(--font-size-sm);
    color: var(--color-text-primary);
  }

  .setting-control select {
    width: auto;
    min-width: 160px;
    padding: var(--space-1) var(--space-3);
    font-size: var(--font-size-sm);
  }

  .backup-actions {
    display: flex;
    gap: var(--space-3);
    margin-bottom: var(--space-4);
    flex-wrap: wrap;
  }

  .backup-form {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding: var(--space-4);
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    margin-top: var(--space-3);
  }

  .field {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
  }

  .field-label {
    font-size: var(--font-size-xs);
    font-weight: var(--font-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--color-text-tertiary);
  }

  .field-hint {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
    line-height: var(--line-height-normal);
  }

  .clear-actions {
    margin-bottom: var(--space-4);
  }

  .clear-form {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding: var(--space-4);
    background: var(--color-bg);
    border: 1px solid var(--color-danger);
    border-radius: var(--radius-md);
    margin-top: var(--space-3);
  }

  .clear-warning {
    font-size: var(--font-size-sm);
    color: var(--color-danger);
    font-weight: var(--font-weight-semibold);
    padding: var(--space-2) var(--space-3);
    background: var(--color-danger-muted);
    border-radius: var(--radius-md);
    text-align: center;
  }

  .file-ready-msg {
    font-size: var(--font-size-sm);
    color: var(--color-success);
    font-weight: var(--font-weight-medium);
    padding: var(--space-2);
    background: var(--color-success-muted);
    border-radius: var(--radius-sm);
    text-align: center;
  }

  .recovery-info {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .status-configured {
    font-size: var(--font-size-sm);
    color: var(--color-success);
    padding: var(--space-2) var(--space-3);
    background: var(--color-success-muted);
    border-radius: var(--radius-md);
  }

  .status-not-configured {
    font-size: var(--font-size-sm);
    color: var(--color-warning);
    padding: var(--space-2) var(--space-3);
    background: var(--color-warning-muted);
    border-radius: var(--radius-md);
  }

  .biometry-info {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .biometry-placeholder {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    padding: var(--space-3);
    border: 1px dashed var(--color-border);
    border-radius: var(--radius-md);
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    opacity: 0.7;
  }

  .biometry-badge {
    font-size: var(--font-size-xs);
    background: var(--color-accent-muted);
    color: var(--color-accent);
    padding: 2px var(--space-2);
    border-radius: var(--radius-sm);
    margin-left: auto;
  }

  .about-info {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .about-security {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
    padding: var(--space-3);
    background: var(--color-accent-subtle);
    border-radius: var(--radius-md);
    margin-top: var(--space-2);
  }
</style>

