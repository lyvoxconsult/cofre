<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { save, open } from "@tauri-apps/plugin-dialog";
  import { showToast, themeChanged } from "../stores/session";
  import type { AppSettings, RecoveryStatus } from "../types";

  let settings = $state<AppSettings>({
    auto_lock_minutes: 5,
    clipboard_clear_seconds: 30,
    theme: "dark",
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

  $effect(() => {
    loadSettings();
    loadRecoveryStatus();
  });

  async function loadSettings() {
    loading = true;
    try {
      settings = await invoke<AppSettings>("get_settings");
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

  async function saveSettings() {
    try {
      await invoke("update_settings", {
        autoLockMinutes: settings.auto_lock_minutes,
        clipboardClearSeconds: settings.clipboard_clear_seconds,
        theme: settings.theme,
      });
      showToast("Configurações salvas", "success");
      themeChanged.update((n) => n + 1);
    } catch (e) {
      showToast(String(e), "error");
    }
  }

  // ─── Backup ──────────────────────────────────

  async function handleExportBackup() {
    if (!backupPassword || backupPassword.length < 8) {
      showToast("A senha de backup deve ter no mínimo 8 caracteres.", "error");
      return;
    }
    if (backupPassword !== backupPasswordConfirm) {
      showToast("As senhas não coincidem.", "error");
      return;
    }

    exporting = true;
    try {
      const envelopeJson = await invoke<string>("export_backup", {
        backupPassword,
      });

      // Abre diálogo para salvar arquivo
      const filePath = await save({
        defaultPath: `lyvox-vault-backup-${new Date().toISOString().slice(0, 10)}.vault`,
        filters: [{ name: "lyvox vault Backup", extensions: ["vault"] }],
      });

      if (!filePath) {
        showToast("Exportação cancelada.", "info");
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

      // Lê o arquivo via comando Rust customizado
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
      showToast("A senha de backup deve ter no mínimo 8 caracteres.", "error");
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

  // ─── Excluir Dados ─────────────────────────────

  async function handleClearData() {
    if (!clearPassword || clearPassword.length < 8) {
      showToast("Digite sua senha mestra.", "error");
      return;
    }
    if (clearPassword !== clearPasswordConfirm) {
      showToast("As senhas não coincidem.", "error");
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
</script>

<div class="settings-panel animate-fade-in">
  <div class="panel-header">
    <h2>Configurações</h2>
  </div>

  {#if loading}
    <p class="loading-text">Carregando…</p>
  {:else}
    <div class="settings-card">
      <!-- Segurança -->
      <div class="settings-section">
        <h3>Segurança</h3>

        <label class="setting-row">
          <span class="setting-label">Bloqueio automático após</span>
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
          <span class="setting-label">Limpar clipboard após</span>
          <div class="setting-control">
            <select bind:value={settings.clipboard_clear_seconds}>
              <option value={10}>10 segundos</option>
              <option value={30}>30 segundos</option>
              <option value={60}>1 minuto</option>
              <option value={120}>2 minutos</option>
            </select>
          </div>
        </label>
      </div>

      <!-- Aparência -->
      <div class="settings-section">
        <h3>Aparência</h3>

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

      <!-- Backup -->
      <div class="settings-section">
        <h3>Backup</h3>
        <p class="section-desc">
          Exporte seus dados criptografados para guardar em local seguro.
          A restauração substitui ou adiciona os dados no cofre atual.
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
                placeholder="Mínimo 8 caracteres"
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
              {exporting ? "Exportando…" : "Criar e Salvar Backup"}
            </button>
            <p class="field-hint">
              Guarde esta senha em local seguro. Sem ela não é possível restaurar o backup.
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
                Todos os dados atuais serão removidos antes da restauração.
              {:else}
                Os dados do backup serão adicionados aos existentes.
              {/if}
            </p>
            <button
              class="btn-danger"
              onclick={handleImportBackup}
              disabled={importing}
            >
              {importing ? "Restaurando…" : "Restaurar Backup"}
            </button>
          </div>
        {/if}
      </div>

      <!-- Recuperação -->
      <div class="settings-section">
        <h3>Recuperação de Senha</h3>
        <div class="recovery-info">
          {#if recoveryStatus?.configured}
            <p class="status-configured">
              ✅ Recuperação configurada ({recoveryStatus.attempts} tentativas realizadas)
            </p>
            <p class="field-hint">
              Para reconfigurar as perguntas, utilize a opção "Esqueci minha senha"
              na tela de desbloqueio e, após redefinir a senha, configure novas perguntas.
            </p>
          {:else}
            <p class="status-not-configured">
              ⚠️ Recuperação não configurada. Sem ela, não será possível recuperar
              o acesso se você esquecer a senha mestra.
            </p>
          {/if}
        </div>
      </div>

      <!-- Excluir Dados -->
      <div class="settings-section settings-section--danger">
        <h3>Excluir Dados</h3>
        <p class="section-desc">
          Remove permanentemente todas as entradas e notas do cofre.
          Esta ação não pode ser desfeita.
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
              ⚠️ Confirme sua senha mestra duas vezes para excluir todos os dados.
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
              {clearing ? "Excluindo…" : "Sim, excluir todos os dados"}
            </button>
          </div>
        {/if}
      </div>

      <!-- Biometria (Futuro) -->
      <div class="settings-section">
        <h3>Biometria (Android)</h3>
        <div class="biometry-info">
          <p class="field-hint">
            O desbloqueio por digital estará disponível na versão Android.
            No desktop, o desbloqueio é feito apenas com a senha mestra.
          </p>
          <div class="biometry-placeholder">
            <span class="biometry-icon">🔒</span>
            <span>Desbloqueio por digital</span>
            <span class="biometry-badge">Android (futuro)</span>
          </div>
          <button class="btn-secondary btn-sm" disabled>
            Remover acesso por digital
          </button>
          <p class="field-hint">
            Disponível apenas na versão Android. Remove o vínculo biométrico
            do app sem afetar as digitais cadastradas no dispositivo.
            Exige senha mestra para confirmar.
          </p>
        </div>
      </div>

      <!-- Sobre -->
      <div class="settings-section">
        <h3>Sobre</h3>
        <div class="about-info">
          <p><strong>lyvox vault</strong> v0.1.0</p>
          <p>Gerenciador seguro de senhas local</p>
          <p class="about-security">
            Seus dados são criptografados com AES-256-GCM e protegidos por senha mestra.
            Nenhum dado sai do seu computador.
          </p>
        </div>
      </div>

      <div class="settings-actions">
        <button class="btn-primary" onclick={saveSettings}>Salvar Configurações</button>
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
  }

  .panel-header h2 {
    margin-bottom: var(--space-1);
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

  .settings-actions {
    margin-top: var(--space-6);
    padding-top: var(--space-4);
    border-top: 1px solid var(--color-border);
  }
</style>
