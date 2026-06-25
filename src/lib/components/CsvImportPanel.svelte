<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { currentTab, showToast } from "../stores/session";
  import type { VaultEntryDecrypted } from "../types";

  interface CsvEntry {
    serviceName: string;
    url: string;
    login: string;
    password: string;
    notes: string;
  }

  interface ImportResult {
    imported: number;
    skipped: number;
    errors: string[];
  }

  let step = $state(0); // 0=pick, 1=preview, 2=done
  let parsedEntries = $state<CsvEntry[]>([]);
  let importResult = $state<ImportResult | null>(null);
  let skipDuplicates = $state(true);
  let isImporting = $state(false);
  let selectedFileName = $state("");
  let fileInput: HTMLInputElement | undefined = $state(undefined);

  function parseLine(line: string): string[] {
    const result: string[] = [];
    let inQuote = false;
    let current = "";

    let i = 0;
    while (i < line.length) {
      const c = line[i];
      if (c === '"' && !inQuote) {
        inQuote = true;
      } else if (c === '"' && inQuote) {
        if (i + 1 < line.length && line[i + 1] === '"') {
          current += '"';
          i++; // escaped quote
        } else {
          inQuote = false;
        }
      } else if (c === ',' && !inQuote) {
        result.push(current);
        current = "";
      } else {
        current += c;
      }
      i++;
    }
    result.push(current);
    return result;
  }

  interface ColMapping {
    serviceCol: number;
    urlCol: number;
    loginCol: number;
    passwordCol: number;
    notesCol: number;
  }

  function detectMapping(header: string[]): ColMapping {
    const idx = (...keys: string[]): number => {
      for (const k of keys) {
        const index = header.findIndex(
          (h) => h === k || h.includes(k)
        );
        if (index >= 0) return index;
      }
      return -1;
    };

    const service = idx("name", "title", "service", "service_name");
    const url = idx("url", "login_uri", "website", "login_url");
    const login = idx("username", "login_username", "email", "login", "user");
    const password = idx("password", "login_password", "pass");
    const notes = idx("notes", "note", "comments", "extra");

    return {
      serviceCol: service >= 0 ? service : url >= 0 ? url : 0,
      urlCol: url >= 0 ? url : -1,
      loginCol: login >= 0 ? login : 2,
      passwordCol: password >= 0 ? password : 3,
      notesCol: notes,
    };
  }

  function handleFileChange(e: Event) {
    const target = e.target as HTMLInputElement;
    const file = target.files?.[0];
    if (!file) return;

    selectedFileName = file.name;
    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const text = event.target?.result as string;
        if (!text) {
          showToast("O arquivo está vazio.", "error");
          return;
        }

        const lines = text.split(/\r?\n/);
        if (lines.length === 0 || !lines[0].trim()) {
          showToast("O arquivo não possui cabeçalho.", "error");
          return;
        }

        const header = parseLine(lines[0]).map((h) => h.trim().toLowerCase());
        const mapping = detectMapping(header);

        const entries: CsvEntry[] = [];
        for (let i = 1; i < lines.length; i++) {
          const line = lines[i];
          if (!line.trim()) continue;

          const cols = parseLine(line);
          try {
            const serviceName = (cols[mapping.serviceCol] || "").trim();
            const entryUrl = mapping.urlCol >= 0 ? (cols[mapping.urlCol] || "").trim() : "";
            const login = (cols[mapping.loginCol] || "").trim();
            const password = (cols[mapping.passwordCol] || "").trim();
            const entryNotes = mapping.notesCol >= 0 ? (cols[mapping.notesCol] || "").trim() : "";

            if (serviceName || entryUrl) {
              entries.push({
                serviceName,
                url: entryUrl,
                login,
                password,
                notes: entryNotes,
              });
            }
          } catch {
            // ignore malformed lines
          }
        }

        if (entries.length === 0) {
          showToast("Nenhuma entrada válida encontrada no CSV.", "error");
        } else {
          parsedEntries = entries;
          step = 1;
        }
      } catch (ex) {
        showToast("Erro ao processar CSV: " + String(ex), "error");
      }
    };
    reader.readAsText(file);
  }

  function triggerFileSelect() {
    fileInput?.click();
  }

  async function handleImport() {
    isImporting = true;
    let imported = 0;
    let skipped = 0;
    const errors: string[] = [];

    try {
      const existing = await invoke<VaultEntryDecrypted[]>("list_entries");

      for (const e of parsedEntries) {
        try {
          if (
            skipDuplicates &&
            existing.some(
              (x) =>
                x.service_name.toLowerCase() === e.serviceName.toLowerCase() &&
                x.login.toLowerCase() === e.login.toLowerCase()
            )
          ) {
            skipped++;
            continue;
          }

          // Normalize URL
          let url = e.url.trim();
          if (url && !/^https?:\/\//i.test(url)) {
            url = `https://${url}`;
          }

          await invoke("create_entry", {
            serviceName: e.serviceName || url || "Importado",
            login: e.login,
            password: e.password,
            notes: e.notes || null,
            url: url,
            categoryId: null,
            isFavorite: false,
          });
          imported++;
        } catch (ex) {
          errors.push(`Erro em '${e.serviceName}': ${String(ex)}`);
        }
      }

      importResult = { imported, skipped, errors };
      step = 2;
    } catch (ex) {
      showToast("Erro na importação: " + String(ex), "error");
    } finally {
      isImporting = false;
    }
  }
</script>

<div class="csv-import-panel animate-fade-in">
  <div class="panel-header">
    <button class="btn-ghost btn-sm btn-back" onclick={() => currentTab.set("settings")}>
      &larr; Voltar para Configurações
    </button>
    <h2>Importar de Arquivo CSV</h2>
  </div>

  <div class="import-content">
    {#if step === 0}
      <!-- Step 0: Pick file -->
      <div class="step-card">
        <h3>Selecione o arquivo CSV</h3>
        <p class="section-desc">
          O arquivo é processado inteiramente em memória local no seu navegador. Nenhum dado é enviado para a nuvem.
        </p>

        <div class="formats-info">
          <h4>Formatos e colunas mapeados automaticamente:</h4>
          <ul>
            <li><strong>Google Chrome / Edge:</strong> name, url, username, password</li>
            <li><strong>Bitwarden:</strong> name, login_uri, login_username, login_password, notes</li>
            <li><strong>1Password:</strong> title, url, username, password, notes</li>
            <li><strong>Genérico:</strong> detecção automática baseada no cabeçalho</li>
          </ul>
        </div>

        <input
          type="file"
          accept=".csv"
          bind:this={fileInput}
          onchange={handleFileChange}
          style="display: none;"
        />

        <div class="actions-footer">
          <button class="btn-primary" onclick={triggerFileSelect}>
            Selecionar Arquivo CSV
          </button>
        </div>
      </div>
    {:else if step === 1}
      <!-- Step 1: Preview -->
      <div class="step-card">
        <div class="preview-header">
          <div class="file-info">
            <span class="file-name">{selectedFileName}</span>
            <span class="entries-count">{parsedEntries.length} credenciais encontradas</span>
          </div>
          <label class="setting-checkbox">
            <input type="checkbox" bind:checked={skipDuplicates} />
            <span>Ignorar registros com mesmo Serviço e Login já existentes</span>
          </label>
        </div>

        <div class="preview-list">
          {#each parsedEntries as entry}
            <div class="preview-row">
              <div class="row-icon">📄</div>
              <div class="row-info">
                <span class="row-name">{entry.serviceName || entry.url || "Sem nome"}</span>
                <span class="row-login">{entry.login || "Sem login"}</span>
              </div>
              {#if entry.password}
                <span class="row-badge">✓ Senha</span>
              {/if}
            </div>
          {/each}
        </div>

        <div class="actions-footer">
          <button class="btn-primary" onclick={handleImport} disabled={isImporting}>
            {isImporting ? "Importando..." : "Importar Agora"}
          </button>
          <button class="btn-ghost" onclick={() => { step = 0; parsedEntries = []; }} disabled={isImporting}>
            Cancelar
          </button>
        </div>
      </div>
    {:else if step === 2 && importResult}
      <!-- Step 2: Result -->
      <div class="step-card result-card">
        <span class="result-icon">🎉</span>
        <h3>Importação Concluída</h3>

        <div class="result-stats">
          <div class="stat-row">
            <span>Registros importados:</span>
            <strong>{importResult.imported}</strong>
          </div>
          <div class="stat-row">
            <span>Registros ignorados (duplicados):</span>
            <strong>{importResult.skipped}</strong>
          </div>
          <div class="stat-row">
            <span>Erros:</span>
            <strong style="color: var(--color-danger);">{importResult.errors.length}</strong>
          </div>
        </div>

        {#if importResult.errors.length > 0}
          <div class="errors-log">
            <h4>Registro de erros:</h4>
            <ul>
              {#each importResult.errors.slice(0, 5) as err}
                <li>{err}</li>
              {/each}
              {#if importResult.errors.length > 5}
                <li>e mais {importResult.errors.length - 5} erros...</li>
              {/if}
            </ul>
          </div>
        {/if}

        <div class="actions-footer">
          <button class="btn-primary" onclick={() => currentTab.set("vault")}>
            Ir para o Cofre
          </button>
          <button class="btn-ghost" onclick={() => { step = 0; parsedEntries = []; importResult = null; }}>
            Importar outro arquivo
          </button>
        </div>
      </div>
    {/if}
  </div>
</div>

<style>
  .csv-import-panel {
    flex: 1;
    padding: var(--space-8);
    overflow-y: auto;
    max-width: 720px;
    margin: 0 auto;
    width: 100%;
  }

  .panel-header {
    margin-bottom: var(--space-6);
  }

  .btn-back {
    margin-bottom: var(--space-3);
    padding-left: 0;
  }

  .import-content {
    display: flex;
    flex-direction: column;
    gap: var(--space-6);
  }

  .step-card {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-xl);
    padding: var(--space-6);
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .section-desc {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    line-height: var(--line-height-normal);
    margin: 0;
  }

  .formats-info {
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg);
    padding: var(--space-4);
  }

  .formats-info h4 {
    margin: 0 0 var(--space-2);
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-semibold);
  }

  .formats-info ul {
    margin: 0;
    padding-left: var(--space-4);
    font-size: var(--font-size-xs);
    color: var(--color-text-secondary);
    line-height: var(--line-height-normal);
  }

  .preview-header {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    border-bottom: 1px solid var(--color-border);
    padding-bottom: var(--space-4);
  }

  .file-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .file-name {
    font-size: var(--font-size-base);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
  }

  .entries-count {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
  }

  .setting-checkbox {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    cursor: pointer;
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
  }

  .setting-checkbox input {
    width: auto;
  }

  .preview-list {
    max-height: 250px;
    overflow-y: auto;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg);
    background: var(--color-bg);
  }

  .preview-row {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    padding: var(--space-3) var(--space-4);
    border-bottom: 1px solid var(--color-border);
  }

  .preview-row:last-child {
    border-bottom: none;
  }

  .row-icon {
    font-size: var(--font-size-lg);
    color: var(--color-text-tertiary);
  }

  .row-info {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-width: 0;
  }

  .row-name {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-medium);
    color: var(--color-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .row-login {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .row-badge {
    font-size: var(--font-size-xs);
    color: var(--color-success);
    background: var(--color-success-muted);
    padding: 2px var(--space-2);
    border-radius: var(--radius-sm);
    font-weight: var(--font-weight-medium);
  }

  .actions-footer {
    display: flex;
    gap: var(--space-3);
    border-top: 1px solid var(--color-border);
    padding-top: var(--space-4);
    margin-top: var(--space-2);
  }

  .result-card {
    align-items: center;
    text-align: center;
    padding: var(--space-8) var(--space-6);
  }

  .result-icon {
    font-size: var(--font-size-5xl);
    margin-bottom: var(--space-2);
  }

  .result-stats {
    width: 100%;
    max-width: 320px;
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg);
    padding: var(--space-4);
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .stat-row {
    display: flex;
    justify-content: space-between;
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
  }

  .errors-log {
    width: 100%;
    max-width: 480px;
    background: var(--color-danger-muted);
    border: 1px solid var(--color-danger);
    border-radius: var(--radius-lg);
    padding: var(--space-4);
    text-align: left;
  }

  .errors-log h4 {
    margin: 0 0 var(--space-2);
    font-size: var(--font-size-xs);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--color-danger);
  }

  .errors-log ul {
    margin: 0;
    padding-left: var(--space-4);
    font-size: var(--font-size-xs);
    color: var(--color-danger);
    line-height: var(--line-height-normal);
  }
</style>
