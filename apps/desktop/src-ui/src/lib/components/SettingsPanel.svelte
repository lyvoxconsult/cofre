<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { listen } from "@tauri-apps/api/event";
  import { save, open } from "@tauri-apps/plugin-dialog";
  import QRCode from "svelte-qrcode";
  import { showToast, themeChanged, currentTab, readOnlyMode } from "../stores/session";
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
  let query = $state("");

  let backupPassword = $state("");
  let backupPasswordConfirm = $state("");
  let showBackupForm = $state(false);
  let exporting = $state(false);
  let importPassword = $state("");
  let importing = $state(false);
  let importReplace = $state(true);
  let importFileContent = $state<string | null>(null);
  let importFileReady = $state(false);

  let showClearForm = $state(false);
  let clearPassword = $state("");
  let clearPasswordConfirm = $state("");
  let clearing = $state(false);
  let showReadOnlyUnlock = $state(false);
  let readOnlyUnlockPassword = $state("");
  let readOnlyUnlockError = $state("");
  let readOnlyUnlocking = $state(false);

  let showSyncExportForm = $state(false);
  let syncExportPassword = $state("");
  let syncExportPasswordConfirm = $state("");
  let syncingExport = $state(false);
  let syncImportPassword = $state("");
  let syncing = $state(false);
  let syncFileContent = $state<string | null>(null);
  let syncFileReady = $state(false);

  let qrCodeData = $state<any>(null);
  let syncServerRunning = $state(false);
  let autoSaving = $state(false);
  let autoSaved = $state(false);
  let settingsLoaded = $state(false);
  let autoSaveTimer: ReturnType<typeof setTimeout> | null = null;

  const autoLockOptions = [
    [0, "Nunca"],
    [1, "1 minuto"],
    [2, "2 minutos"],
    [5, "5 minutos"],
    [10, "10 minutos"],
    [15, "15 minutos"],
    [30, "30 minutos"],
    [60, "60 minutos"],
  ];

  const clipboardOptions = [
    [10, "10 segundos"],
    [30, "30 segundos"],
    [60, "1 minuto"],
    [120, "2 minutos"],
  ];

  function sectionVisible(...terms: string[]) {
    if (!query.trim()) return true;
    const q = query.trim().toLowerCase();
    return terms.join(" ").toLowerCase().includes(q);
  }

  function readOnlyBlocked(action = "Acao bloqueada em modo somente leitura.") {
    if (!settings.read_only_mode) return false;
    showToast(action, "error");
    return true;
  }

  function handleReadOnlyToggle(event: Event) {
    const target = event.currentTarget as HTMLInputElement;
    if (target.checked) {
      settings = { ...settings, read_only_mode: true };
      showToast("Modo somente leitura ativado.", "success");
      return;
    }

    target.checked = true;
    showReadOnlyUnlock = true;
    readOnlyUnlockPassword = "";
    readOnlyUnlockError = "";
  }

  async function disableReadOnlyMode() {
    if (!readOnlyUnlockPassword) {
      readOnlyUnlockError = "Digite a senha mestra.";
      return;
    }

    readOnlyUnlocking = true;
    try {
      const ok = await invoke<boolean>("verify_master_password", { password: readOnlyUnlockPassword });
      if (!ok) {
        readOnlyUnlockError = "Senha mestra incorreta.";
        return;
      }

      settings = { ...settings, read_only_mode: false };
      readOnlyMode.set(false);
      showReadOnlyUnlock = false;
      readOnlyUnlockPassword = "";
      readOnlyUnlockError = "";
      showToast("Modo somente leitura desativado.", "success");
    } catch (e) {
      readOnlyUnlockError = String(e);
    } finally {
      readOnlyUnlocking = false;
    }
  }

  function autoLockLabel(value: number) {
    return autoLockOptions.find(([v]) => v === value)?.[1] ?? `${value} minutos`;
  }

  function clipboardLabel(value: number) {
    return clipboardOptions.find(([v]) => v === value)?.[1] ?? `${value} segundos`;
  }

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
      unlistenStatus.then((f) => f());
      unlistenError.then((f) => f());
      unlistenStarted.then((f) => f());
    };
  });

  async function loadSettings() {
    loading = true;
    try {
      settings = await invoke<AppSettings>("get_settings");
      readOnlyMode.set(Boolean(settings.read_only_mode));
      setTimeout(() => {
        settingsLoaded = true;
      }, 50);
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
      recoveryStatus = null;
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
          theme,
          privacyMode: privacy,
          readOnlyMode: readOnly,
        });
        readOnlyMode.set(Boolean(readOnly));
        themeChanged.update((n) => n + 1);
        autoSaved = true;
        setTimeout(() => {
          autoSaved = false;
        }, 1600);
      } catch (e) {
        showToast("Erro ao salvar configuracao: " + String(e), "error");
      } finally {
        autoSaving = false;
      }
    }, 450);
  });

  async function startSyncServer() {
    if (readOnlyBlocked("Modo somente leitura ativo. Sync por QR bloqueado porque pode importar dados.")) return;
    try {
      qrCodeData = await invoke("start_sync_server");
      syncServerRunning = true;
      showToast("Servidor de sincronizacao iniciado.", "success");
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

  async function handleExportBackup() {
    if (!backupPassword || backupPassword.length < 8) {
      showToast("A senha de backup deve ter no minimo 8 caracteres.", "error");
      return;
    }
    if (backupPassword !== backupPasswordConfirm) {
      showToast("As senhas nao coincidem.", "error");
      return;
    }

    exporting = true;
    try {
      const envelopeJson = await invoke<string>("export_backup", { backupPassword });
      const filePath = await save({
        defaultPath: `lyvox-vault-backup-${new Date().toISOString().slice(0, 10)}.vault`,
        filters: [{ name: "Lyvox Vault Backup", extensions: ["vault"] }],
      });
      if (!filePath) {
        showToast("Exportacao cancelada.", "info");
        return;
      }
      await invoke("write_backup_file", { path: filePath, content: envelopeJson });
      showToast("Backup exportado com sucesso.", "success");
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
    if (readOnlyBlocked("Modo somente leitura ativo. Restauracao bloqueada.")) return;
    try {
      const filePath = await open({
        multiple: false,
        filters: [{ name: "Lyvox Vault Backup", extensions: ["vault"] }],
      });
      if (!filePath) return;
      importFileContent = await invoke<string>("read_backup_file", { path: String(filePath) });
      importFileReady = true;
      showToast("Arquivo de backup carregado.", "info");
    } catch (e) {
      showToast("Erro ao ler arquivo: " + String(e), "error");
    }
  }

  async function handleImportBackup() {
    if (readOnlyBlocked("Modo somente leitura ativo. Restauracao bloqueada.")) return;
    if (!importPassword || importPassword.length < 8) {
      showToast("A senha de backup deve ter no minimo 8 caracteres.", "error");
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

  async function handleClearData() {
    if (readOnlyBlocked("Modo somente leitura ativo. Exclusao bloqueada.")) return;
    if (!clearPassword || clearPassword.length < 8) {
      showToast("Digite sua senha mestra.", "error");
      return;
    }
    if (clearPassword !== clearPasswordConfirm) {
      showToast("As senhas nao coincidem.", "error");
      return;
    }

    clearing = true;
    try {
      const result = await invoke<string>("clear_all_vault_data", { password: clearPassword });
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

  async function handleExportSync() {
    if (!syncExportPassword || syncExportPassword.length < 8) {
      showToast("A senha de sincronizacao deve ter no minimo 8 caracteres.", "error");
      return;
    }
    if (syncExportPassword !== syncExportPasswordConfirm) {
      showToast("As senhas nao coincidem.", "error");
      return;
    }

    syncingExport = true;
    try {
      const syncJson = await invoke<string>("export_sync_package", { syncPassword: syncExportPassword });
      const filePath = await save({
        defaultPath: `lyvox-vault-sync-${new Date().toISOString().slice(0, 10)}.vaultsync`,
        filters: [{ name: "Lyvox Vault Sync Package", extensions: ["vaultsync"] }],
      });
      if (!filePath) {
        showToast("Sincronizacao cancelada.", "info");
        return;
      }
      await invoke("write_backup_file", { path: filePath, content: syncJson });
      showToast("Pacote de sincronizacao exportado com sucesso.", "success");
      syncExportPassword = "";
      syncExportPasswordConfirm = "";
      showSyncExportForm = false;
    } catch (e) {
      showToast("Erro ao exportar sync: " + String(e), "error");
    } finally {
      syncingExport = false;
    }
  }

  async function handleSelectSyncFile() {
    if (readOnlyBlocked("Modo somente leitura ativo. Importacao de sync bloqueada.")) return;
    try {
      const filePath = await open({
        multiple: false,
        filters: [{ name: "Lyvox Vault Sync Package", extensions: ["vaultsync"] }],
      });
      if (!filePath) return;
      syncFileContent = await invoke<string>("read_backup_file", { path: String(filePath) });
      syncFileReady = true;
      showToast("Pacote de sincronizacao carregado.", "info");
    } catch (e) {
      showToast("Erro ao ler pacote: " + String(e), "error");
    }
  }

  async function handleImportSync() {
    if (readOnlyBlocked("Modo somente leitura ativo. Importacao de sync bloqueada.")) return;
    if (!syncImportPassword || syncImportPassword.length < 8) {
      showToast("Digite a senha de sincronizacao.", "error");
      return;
    }
    if (!syncFileContent) {
      showToast("Selecione um pacote de sincronizacao primeiro.", "error");
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
      showToast("Erro na sincronizacao: " + String(e), "error");
    } finally {
      syncing = false;
    }
  }

  function handleCheckUpdate() {
    showToast("Atualizacoes externas desativadas. Use instaladores locais gerados pelo projeto.", "info");
  }
</script>

<div class="settings-panel animate-fade-in">
  <div class="settings-shell">
    <header class="settings-header">
      <div>
        <h2>Configuracoes</h2>
        <p>Personalize sua experiencia e gerencie a seguranca do Lyvox Vault.</p>
      </div>
      <div class="header-tools">
        <label class="search-settings">
          <span>&#128269;</span>
          <input bind:value={query} type="search" placeholder="Buscar configuracoes..." />
        </label>
        {#if autoSaving}
          <span class="autosave autosave-saving">Salvando...</span>
        {:else if autoSaved}
          <span class="autosave autosave-done">Salvo</span>
        {/if}
      </div>
    </header>

    {#if loading}
      <div class="loading-card">Carregando configuracoes...</div>
    {:else}
      <div class="settings-grid">
        {#if sectionVisible("seguranca bloqueio clipboard privacidade somente leitura")}
          <section class="settings-card span-4">
            <h3><span>&#128737;</span> Seguranca</h3>
            <label class="setting-row">
              <span>
                <b>Bloqueio automatico apos</b>
                <small>{autoLockLabel(settings.auto_lock_minutes)}</small>
              </span>
              <select bind:value={settings.auto_lock_minutes}>
                {#each autoLockOptions as [value, label]}
                  <option value={value}>{label}</option>
                {/each}
              </select>
            </label>
            <label class="setting-row">
              <span>
                <b>Limpar clipboard apos</b>
                <small>{clipboardLabel(settings.clipboard_clear_seconds)}</small>
              </span>
              <select bind:value={settings.clipboard_clear_seconds}>
                {#each clipboardOptions as [value, label]}
                  <option value={value}>{label}</option>
                {/each}
              </select>
            </label>
            <label class="setting-row">
              <span>
                <b>Modo Privacidade</b>
                <small>Oculta logins na listagem</small>
              </span>
              <input class="switch" type="checkbox" bind:checked={settings.privacy_mode} />
            </label>
            <label class="setting-row">
              <span>
                <b>Modo Somente Leitura</b>
                <small>Bloqueia edicoes, exclusoes e importacoes</small>
              </span>
              <input class="switch" type="checkbox" checked={settings.read_only_mode} onchange={handleReadOnlyToggle} />
            </label>
          </section>
        {/if}

        {#if sectionVisible("aparencia tema escuro claro sistema")}
          <section class="settings-card span-4">
            <h3><span>&#127912;</span> Aparencia</h3>
            <label class="setting-row">
              <span>
                <b>Tema</b>
                <small>{settings.theme === "dark" ? "Escuro" : settings.theme === "light" ? "Claro" : "Sistema"}</small>
              </span>
              <select bind:value={settings.theme}>
                <option value="dark">Escuro</option>
                <option value="light">Claro</option>
                <option value="system">Sistema</option>
              </select>
            </label>
          </section>
        {/if}

        <section class="quick-stack span-4">
          {#if sectionVisible("auditoria senhas seguranca saude")}
            <button class="action-card" onclick={() => currentTab.set("audit")}>
              <span class="action-icon">&#128737;</span>
              <span>
                <b>Auditoria de Seguranca</b>
                <small>Verifica senhas fracas, antigas ou reutilizadas.</small>
              </span>
              <i>&rsaquo;</i>
            </button>
          {/if}
          {#if sectionVisible("importacao csv credenciais chrome bitwarden 1password")}
            <button class="action-card" onclick={() => currentTab.set("csv_import")} disabled={settings.read_only_mode}>
              <span class="action-icon">&#8682;</span>
              <span>
                <b>Importacao de Credenciais</b>
                <small>{settings.read_only_mode ? "Bloqueada pelo modo somente leitura." : "Importe CSV do Chrome, Bitwarden ou 1Password."}</small>
              </span>
              <i>&rsaquo;</i>
            </button>
          {/if}
          {#if sectionVisible("backup exportar restaurar")}
            <button class="action-card" onclick={() => (showBackupForm = !showBackupForm)}>
              <span class="action-icon">&#128190;</span>
              <span>
                <b>Backup</b>
                <small>Exporte dados criptografados ou restaure arquivo local.</small>
              </span>
              <i>&rsaquo;</i>
            </button>
          {/if}
        </section>

        {#if sectionVisible("backup exportar restaurar vault")}
          <section class="settings-card span-12">
            <div class="section-title-row">
              <h3><span>&#128190;</span> Backup</h3>
              <div class="inline-actions">
                <button class="btn-secondary btn-sm" onclick={() => (showBackupForm = !showBackupForm)}>
                  {showBackupForm ? "Cancelar exportacao" : "Exportar backup"}
                </button>
                <button class="btn-secondary btn-sm" onclick={handleSelectImportFile} disabled={importing || settings.read_only_mode}>
                  {settings.read_only_mode ? "Restauracao bloqueada" : "Selecionar backup"}
                </button>
              </div>
            </div>

            {#if showBackupForm}
              <div class="form-grid">
                <label class="field">
                  <span>Senha do backup</span>
                  <input type="password" bind:value={backupPassword} placeholder="Minimo 8 caracteres" />
                </label>
                <label class="field">
                  <span>Confirmar senha</span>
                  <input type="password" bind:value={backupPasswordConfirm} placeholder="Repita a senha" />
                </label>
                <button class="btn-primary" onclick={handleExportBackup} disabled={exporting}>
                  {exporting ? "Exportando..." : "Criar e salvar backup"}
                </button>
              </div>
            {/if}

            {#if importFileReady}
              <div class="form-grid danger-form">
                <p class="file-ready">Arquivo de backup carregado.</p>
                <label class="field">
                  <span>Senha do backup</span>
                  <input type="password" bind:value={importPassword} placeholder="Senha usada ao exportar" />
                </label>
                <label class="check-row">
                  <input type="checkbox" bind:checked={importReplace} />
                  <span>Substituir dados existentes</span>
                </label>
                <button class="btn-danger" onclick={handleImportBackup} disabled={importing || settings.read_only_mode}>
                  {importing ? "Restaurando..." : "Restaurar backup"}
                </button>
              </div>
            {/if}
          </section>
        {/if}

        {#if sectionVisible("sync sincronizacao local qr usb arquivo vaultsync")}
          <section class="settings-card span-12">
            <h3><span>&#8644;</span> Sincronizacao Local</h3>
            <p class="section-desc">Sincronize seu cofre entre celular e computador na rede local, com pacote criptografado.</p>
            <div class="sync-grid">
              <div class="sync-card">
                <span class="action-icon">&#128241;</span>
                <b>Via QR Code (Rede Local)</b>
                <small>Conecte os dispositivos na mesma rede Wi-Fi e escaneie o codigo.</small>
                {#if syncServerRunning && qrCodeData}
                  <div class="qr-wrap">
                    <QRCode value={JSON.stringify(qrCodeData)} size={168} />
                  </div>
                  <button class="btn-secondary btn-sm" onclick={stopSyncServer}>Parar servidor</button>
                {:else}
                  <button class="btn-primary btn-sm" onclick={startSyncServer} disabled={settings.read_only_mode}>
                    {settings.read_only_mode ? "Bloqueado" : "Gerar QR Code"}
                  </button>
                {/if}
              </div>

              <div class="sync-card sync-card-disabled">
                <span class="action-icon">&#128268;</span>
                <b>Via Cabo USB (Avancado)</b>
                <small>Sem comando ADB exposto no Desktop atual. Nao ha acao ativa nesta tela.</small>
                <span class="status-pill">Indisponivel</span>
              </div>

              <div class="sync-card">
                <span class="action-icon">&#128193;</span>
                <b>Via Arquivo (.vaultsync)</b>
                <small>Exporte pacote criptografado e importe no outro dispositivo.</small>
                <div class="inline-actions">
                  <button class="btn-primary btn-sm" onclick={() => (showSyncExportForm = !showSyncExportForm)}>
                    {showSyncExportForm ? "Cancelar" : "Exportar pacote"}
                  </button>
                  <button class="btn-secondary btn-sm" onclick={handleSelectSyncFile} disabled={syncing || settings.read_only_mode}>
                    {settings.read_only_mode ? "Import bloqueado" : "Selecionar pacote"}
                  </button>
                </div>
              </div>
            </div>

            {#if showSyncExportForm}
              <div class="form-grid">
                <label class="field">
                  <span>Senha de sincronizacao</span>
                  <input type="password" bind:value={syncExportPassword} placeholder="Minimo 8 caracteres" />
                </label>
                <label class="field">
                  <span>Confirmar senha</span>
                  <input type="password" bind:value={syncExportPasswordConfirm} placeholder="Repita a senha" />
                </label>
                <button class="btn-primary" onclick={handleExportSync} disabled={syncingExport}>
                  {syncingExport ? "Exportando..." : "Criar e salvar pacote"}
                </button>
              </div>
            {/if}

            {#if syncFileReady}
              <div class="form-grid danger-form">
                <p class="file-ready">Pacote de sync carregado.</p>
                <label class="field">
                  <span>Senha de sincronizacao</span>
                  <input type="password" bind:value={syncImportPassword} placeholder="Senha definida ao exportar" />
                </label>
                <button class="btn-danger" onclick={handleImportSync} disabled={syncing || settings.read_only_mode}>
                  {syncing ? "Sincronizando..." : "Importar pacote"}
                </button>
              </div>
            {/if}
          </section>
        {/if}

        {#if sectionVisible("recuperacao senha perguntas acesso")}
          <button class="banner-card warning span-12" onclick={() => showToast("Configure recuperacao pela tela de desbloqueio apos redefinicao da senha.", "info")}>
            <span>&#9888;</span>
            <span>
              <b>Recuperacao de Senha</b>
              <small>
                {#if recoveryStatus?.configured}
                  Recuperacao configurada. Tentativas registradas: {recoveryStatus.attempts}.
                {:else}
                  Recuperacao nao configurada. Sem ela, nao sera possivel recuperar acesso se esquecer a senha mestra.
                {/if}
              </small>
            </span>
            <i>&rsaquo;</i>
          </button>
        {/if}

        {#if sectionVisible("zona perigo excluir dados limpar")}
          <section class="settings-card danger span-12">
            <div class="section-title-row">
              <h3><span>&#128465;</span> Zona de Perigo</h3>
              <button class="btn-danger btn-sm" onclick={() => (showClearForm = !showClearForm)} disabled={settings.read_only_mode}>
                {settings.read_only_mode ? "Exclusao bloqueada" : showClearForm ? "Cancelar" : "Excluir dados"}
              </button>
            </div>
            <p class="section-desc">Remove permanentemente todas as entradas e notas do cofre. Esta acao nao pode ser desfeita.</p>
            {#if showClearForm}
              <div class="form-grid danger-form">
                <label class="field">
                  <span>Senha mestra</span>
                  <input type="password" bind:value={clearPassword} placeholder="Digite sua senha mestra" />
                </label>
                <label class="field">
                  <span>Confirmar senha mestra</span>
                  <input type="password" bind:value={clearPasswordConfirm} placeholder="Confirme sua senha mestra" />
                </label>
                <button class="btn-danger" onclick={handleClearData} disabled={clearing || settings.read_only_mode}>
                  {clearing ? "Excluindo..." : "Confirmar exclusao total"}
                </button>
              </div>
            {/if}
          </section>
        {/if}

        {#if sectionVisible("biometria android digital")}
          <section class="settings-card span-12">
            <h3><span>&#128274;</span> Biometria Android</h3>
            <label class="setting-row disabled-row">
              <span>
                <b>Desbloqueio por digital</b>
                <small>Disponivel apenas no Android. Desktop usa senha mestra.</small>
              </span>
              <input class="switch" type="checkbox" disabled />
            </label>
          </section>
        {/if}

        {#if sectionVisible("sobre versao local criptografia atualizacao")}
          <section class="settings-card span-12 about-card">
            <h3><span>&#9432;</span> Sobre</h3>
            <div>
              <b>Lyvox Vault Desktop</b>
              <small>v1.0.3 - Cofre local com Argon2id e AES-256-GCM.</small>
            </div>
            <button class="btn-secondary btn-sm" onclick={handleCheckUpdate}>Politica de atualizacao</button>
          </section>
        {/if}
      </div>
    {/if}
  </div>

  {#if showReadOnlyUnlock}
    <div class="modal-backdrop" role="presentation">
      <div class="modal-card">
        <h3>Desativar somente leitura</h3>
        <p>Digite a senha mestra para permitir edicoes, exclusoes e importacoes novamente.</p>
        {#if readOnlyUnlockError}
          <p class="modal-error">{readOnlyUnlockError}</p>
        {/if}
        <label class="field">
          <span>Senha mestra</span>
          <input type="password" bind:value={readOnlyUnlockPassword} placeholder="Digite sua senha mestra" />
        </label>
        <div class="modal-actions">
          <button class="btn-secondary" onclick={() => (showReadOnlyUnlock = false)} disabled={readOnlyUnlocking}>Cancelar</button>
          <button class="btn-primary" onclick={disableReadOnlyMode} disabled={readOnlyUnlocking}>
            {readOnlyUnlocking ? "Validando..." : "Desativar"}
          </button>
        </div>
      </div>
    </div>
  {/if}
</div>

<style>
  .settings-panel {
    flex: 1;
    min-width: 0;
    overflow-y: auto;
    padding: 26px 30px;
  }

  .settings-shell {
    max-width: 1180px;
    margin: 0 auto;
  }

  .settings-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--space-6);
    margin-bottom: var(--space-5);
  }

  .settings-header h2 {
    font-size: 1.9rem;
    margin-bottom: 4px;
  }

  .settings-header p,
  .section-desc,
  .settings-card small,
  .action-card small,
  .sync-card small,
  .banner-card small,
  .about-card small {
    color: var(--color-text-secondary);
    font-size: var(--font-size-sm);
    line-height: 1.45;
  }

  .header-tools {
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }

  .search-settings {
    width: min(300px, 36vw);
    height: 40px;
    display: flex;
    align-items: center;
    gap: var(--space-2);
    padding: 0 var(--space-3);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: var(--radius-lg);
    background: rgba(255, 255, 255, 0.035);
  }

  .search-settings input {
    border: 0;
    background: transparent;
    padding: 0;
    font-size: var(--font-size-sm);
  }

  .search-settings input:focus {
    box-shadow: none;
  }

  .autosave {
    white-space: nowrap;
    font-size: var(--font-size-xs);
    border-radius: 999px;
    padding: 4px 9px;
  }

  .autosave-saving {
    color: var(--color-text-secondary);
    background: rgba(255, 255, 255, 0.06);
  }

  .autosave-done {
    color: var(--color-success);
    background: var(--color-success-muted);
  }

  .loading-card,
  .settings-card,
  .action-card,
  .banner-card {
    border: 1px solid rgba(255, 255, 255, 0.1);
    background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.055), rgba(255, 255, 255, 0.025)),
      rgba(11, 14, 24, 0.78);
    box-shadow: 0 14px 34px rgba(0, 0, 0, 0.18), inset 0 1px 0 rgba(255, 255, 255, 0.04);
    border-radius: 12px;
  }

  .settings-grid {
    display: grid;
    grid-template-columns: repeat(12, minmax(0, 1fr));
    gap: var(--space-4);
    padding-bottom: var(--space-8);
  }

  .span-4 { grid-column: span 4; }
  .span-12 { grid-column: span 12; }

  .settings-card {
    padding: var(--space-4);
  }

  .settings-card h3 {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-bottom: var(--space-3);
    color: #a78bfa;
    font-size: var(--font-size-xs);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .setting-row {
    min-height: 56px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-4);
    padding: var(--space-3);
    border: 1px solid rgba(255, 255, 255, 0.075);
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.035);
    margin-top: var(--space-2);
  }

  .setting-row span,
  .action-card span:nth-child(2),
  .banner-card span:nth-child(2) {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 2px;
  }

  .setting-row b,
  .action-card b,
  .sync-card b,
  .banner-card b {
    color: var(--color-text-primary);
    font-size: var(--font-size-sm);
  }

  .setting-row select {
    width: 150px;
    flex: 0 0 auto;
    font-size: var(--font-size-sm);
    background: rgba(10, 10, 11, 0.72);
  }

  .switch {
    width: 38px;
    height: 22px;
    flex: 0 0 auto;
    accent-color: var(--color-accent);
    cursor: pointer;
  }

  .quick-stack {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .action-card,
  .banner-card {
    width: 100%;
    min-height: 84px;
    padding: var(--space-4);
    color: var(--color-text-primary);
    text-align: left;
    justify-content: flex-start;
  }

  .action-card:hover:not(:disabled),
  .banner-card:hover {
    border-color: rgba(139, 92, 246, 0.38);
    background:
      linear-gradient(180deg, rgba(139, 92, 246, 0.13), rgba(255, 255, 255, 0.035)),
      rgba(11, 14, 24, 0.84);
  }

  .action-icon {
    width: 42px;
    height: 42px;
    display: grid;
    place-items: center;
    flex: 0 0 auto;
    color: #a855f7;
    background: rgba(139, 92, 246, 0.16);
    border-radius: 10px;
  }

  .action-card i,
  .banner-card i {
    margin-left: auto;
    color: var(--color-text-secondary);
    font-style: normal;
    font-size: 1.45rem;
  }

  .section-title-row {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--space-4);
    margin-bottom: var(--space-3);
  }

  .inline-actions,
  .form-grid {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    flex-wrap: wrap;
  }

  .form-grid {
    align-items: flex-end;
    margin-top: var(--space-3);
    padding: var(--space-4);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 10px;
    background: rgba(0, 0, 0, 0.18);
  }

  .field {
    min-width: 220px;
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .field span,
  .check-row span {
    color: var(--color-text-secondary);
    font-size: var(--font-size-xs);
    font-weight: var(--font-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .check-row {
    display: flex;
    align-items: center;
    gap: var(--space-2);
  }

  .check-row input {
    width: auto;
  }

  .danger-form {
    border-color: rgba(239, 68, 68, 0.28);
    background: rgba(239, 68, 68, 0.06);
  }

  .file-ready {
    width: 100%;
    color: var(--color-success);
    font-size: var(--font-size-sm);
  }

  .sync-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--space-3);
    margin-top: var(--space-3);
  }

  .sync-card {
    min-height: 122px;
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    padding: var(--space-4);
    border: 1px solid rgba(255, 255, 255, 0.085);
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.035);
  }

  .sync-card .inline-actions {
    margin-top: auto;
  }

  .sync-card-disabled {
    opacity: 0.68;
  }

  .status-pill {
    width: fit-content;
    margin-top: auto;
    color: var(--color-text-secondary);
    font-size: var(--font-size-xs);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 999px;
    padding: 3px 9px;
  }

  .qr-wrap {
    width: fit-content;
    padding: var(--space-2);
    background: white;
    border-radius: 10px;
  }

  .banner-card {
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }

  .banner-card.warning {
    border-color: rgba(245, 158, 11, 0.28);
    background: linear-gradient(90deg, rgba(245, 158, 11, 0.12), rgba(255, 255, 255, 0.035));
  }

  .danger {
    border-color: rgba(239, 68, 68, 0.32);
    background: linear-gradient(90deg, rgba(239, 68, 68, 0.12), rgba(255, 255, 255, 0.03));
  }

  .danger h3 {
    color: var(--color-danger);
  }

  .disabled-row {
    opacity: 0.74;
  }

  .about-card {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-4);
  }

  .about-card div {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .modal-backdrop {
    position: fixed;
    inset: 0;
    z-index: 40;
    display: grid;
    place-items: center;
    padding: var(--space-4);
    background: rgba(3, 6, 18, 0.72);
    backdrop-filter: blur(14px);
  }

  .modal-card {
    width: min(420px, 100%);
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding: var(--space-5);
    border: 1px solid rgba(139, 92, 246, 0.28);
    border-radius: 14px;
    background:
      linear-gradient(180deg, rgba(139, 92, 246, 0.14), rgba(255, 255, 255, 0.035)),
      #090d18;
    box-shadow: 0 24px 72px rgba(0, 0, 0, 0.46);
  }

  .modal-card h3 {
    margin: 0;
    color: var(--color-text-primary);
  }

  .modal-card p {
    margin: 0;
    color: var(--color-text-secondary);
    font-size: var(--font-size-sm);
    line-height: 1.5;
  }

  .modal-error {
    color: var(--color-danger) !important;
  }

  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: var(--space-2);
  }

  @media (max-width: 1100px) {
    .span-4 {
      grid-column: span 6;
    }

    .quick-stack {
      grid-column: span 12;
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .sync-grid {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 760px) {
    .settings-panel {
      padding: var(--space-4);
    }

    .settings-header,
    .header-tools,
    .section-title-row,
    .about-card {
      flex-direction: column;
      align-items: stretch;
    }

    .search-settings {
      width: 100%;
    }

    .span-4,
    .quick-stack,
    .span-12 {
      grid-column: span 12;
    }

    .quick-stack {
      display: flex;
    }

    .setting-row {
      align-items: stretch;
      flex-direction: column;
    }

    .setting-row select {
      width: 100%;
    }
  }
</style>
