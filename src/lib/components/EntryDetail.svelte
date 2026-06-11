<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { open } from "@tauri-apps/plugin-shell";
  import { showToast } from "../stores/session";
  import type { VaultEntryDecrypted, Category } from "../types";

  interface Props {
    entry: VaultEntryDecrypted;
    onDeleted: () => void;
    onUpdated: () => void;
    categories: Category[];
  }

  let { entry, onDeleted, onUpdated, categories }: Props = $props();

  let editing = $state(false);
  let showPassword = $state(false);
  let copiedPassword = $state(false);
  let copiedLogin = $state(false);

  // Form state — inicializado vazio e sincronizado via $effect
  let editServiceName = $state("");
  let editLogin = $state("");
  let editPassword = $state("");
  let editUrl = $state("");
  let editNotes = $state("");
  let editCategoryId = $state<number | null>(null);

  // Sincroniza quando entry muda (seleção de entrada diferente)
  $effect(() => {
    editServiceName = entry.service_name;
    editLogin = entry.login;
    editPassword = entry.password;
    editUrl = entry.url;
    editNotes = entry.notes;
    editCategoryId = entry.category_id;
    editing = false;
    showPassword = false;
  });

  /** Normaliza uma URL: adiciona https:// se não tiver protocolo */
  function normalizeUrl(raw: string): string {
    const trimmed = raw.trim();
    if (!trimmed) return "";
    if (/^https?:\/\//i.test(trimmed)) return trimmed;
    return `https://${trimmed}`;
  }

  async function openLink() {
    const url = normalizeUrl(entry.url);
    if (!url) {
      showToast("Nenhuma URL cadastrada.", "error");
      return;
    }
    try {
      await open(url);
    } catch (e) {
      showToast("Erro ao abrir link: " + String(e), "error");
    }
  }

  async function copyPassword() {
    try {
      const { writeText, readText, clear } = await import("@tauri-apps/plugin-clipboard-manager");
      const copiedValue = entry.password;
      await writeText(copiedValue);
      copiedPassword = true;
      showToast("Senha copiada!", "success");

      // Limpa o clipboard após 30s, apenas se o conteúdo não tiver mudado
      setTimeout(async () => {
        copiedPassword = false;
        try {
          const currentText = await readText();
          if (currentText === copiedValue) {
            await clear();
          }
        } catch {
          // Se não conseguir ler, não limpa (segurança: não apagar conteúdo desconhecido)
        }
      }, 30000);
    } catch (e) {
      showToast(String(e), "error");
    }
  }

  async function copyLogin() {
    try {
      const { writeText, readText, clear } = await import("@tauri-apps/plugin-clipboard-manager");
      const copiedValue = entry.login;
      await writeText(copiedValue);
      copiedLogin = true;
      showToast("Login copiado!", "success");

      // Limpa o clipboard após 30s, apenas se o conteúdo não tiver mudado
      setTimeout(async () => {
        copiedLogin = false;
        try {
          const currentText = await readText();
          if (currentText === copiedValue) {
            await clear();
          }
        } catch {
          // Se não conseguir ler, não limpa (segurança: não apagar conteúdo desconhecido)
        }
      }, 30000);
    } catch (e) {
      showToast(String(e), "error");
    }
  }

  async function saveEdit() {
    try {
      await invoke("update_entry", {
        id: entry.id,
        serviceName: editServiceName,
        login: editLogin,
        password: editPassword,
        notes: editNotes || "",
        url: editUrl.trim(),
        categoryId: editCategoryId,
      });
      editing = false;
      onUpdated();
    } catch (e) {
      showToast(String(e), "error");
    }
  }

  async function deleteEntry() {
    try {
      const { confirm } = await import("@tauri-apps/plugin-dialog");
      const confirmed = await confirm("Tem certeza? Esta ação não pode ser desfeita.", {
        title: "Excluir Registro",
        kind: "warning",
      });
      if (!confirmed) return;

      await invoke("delete_entry", { id: entry.id });
      onDeleted();
    } catch (e) {
      showToast(String(e), "error");
    }
  }

  function getCategoryName(id: number | null): string {
    if (id === null) return "Sem categoria";
    return categories.find((c) => c.id === id)?.name ?? "Sem categoria";
  }
</script>

<div class="entry-detail animate-fade-in">
  {#if editing}
    <div class="detail-header">
      <h2>Editar Registro</h2>
    </div>
    <div class="edit-form">
      <label class="field">
        <span class="field-label">Serviço</span>
        <input type="text" bind:value={editServiceName} />
      </label>
      <label class="field">
        <span class="field-label">Login</span>
        <input type="text" bind:value={editLogin} />
      </label>
      <label class="field">
        <span class="field-label">Senha</span>
        <div class="field-row">
          <input type="text" bind:value={editPassword} />
          <button class="btn-secondary btn-sm" onclick={() => {}}>Gerar</button>
        </div>
      </label>
      <label class="field">
        <span class="field-label">URL</span>
        <input type="url" bind:value={editUrl} placeholder="https://exemplo.com" />
      </label>
      <label class="field">
        <span class="field-label">Categoria</span>
        <select bind:value={editCategoryId}>
          <option value={null}>Sem categoria</option>
          {#each categories as cat}
            <option value={cat.id}>{cat.name}</option>
          {/each}
        </select>
      </label>
      <label class="field">
        <span class="field-label">Observações</span>
        <textarea bind:value={editNotes} rows={3}></textarea>
      </label>
      <div class="edit-actions">
        <button class="btn-primary" onclick={saveEdit}>Salvar</button>
        <button class="btn-ghost" onclick={() => (editing = false)}>Cancelar</button>
      </div>
    </div>
  {:else}
    <div class="detail-header">
      <h2>{entry.service_name}</h2>
      <span class="category-badge" style="background: {categories.find(c => c.id === entry.category_id)?.color ?? '#6b7280'}">
        {getCategoryName(entry.category_id)}
      </span>
    </div>

    <div class="detail-body">
      <div class="detail-field">
          <span class="field-label">Login</span>
          <div class="field-value-row">
            <span>{entry.login}</span>
            <button class="btn-ghost btn-sm" onclick={copyLogin}>
              {copiedLogin ? "Copiado!" : "Copiar"}
            </button>
          </div>
      </div>

      <div class="detail-field">
        <span class="field-label">Senha</span>
        <div class="field-value-row">
          <span class="password-value">
            {showPassword ? entry.password : "•".repeat(entry.password.length || 16)}
          </span>
          <div class="field-actions">
            <button class="btn-ghost btn-sm" onclick={() => (showPassword = !showPassword)}>
              {showPassword ? "🙈" : "👁"}
            </button>
            <button class="btn-ghost btn-sm" onclick={copyPassword}>
              {copiedPassword ? "Copiado!" : "Copiar"}
            </button>
          </div>
        </div>
      </div>

      {#if entry.url}
        <div class="detail-field">
          <span class="field-label">URL</span>
          <div class="field-value-row">
            <span class="url-text">{entry.url}</span>
            <button class="btn-ghost btn-sm" onclick={openLink}>
              Abrir Link
            </button>
          </div>
        </div>
      {/if}

      {#if entry.notes}
        <div class="detail-field">
          <span class="field-label">Observações</span>
          <p class="notes-text">{entry.notes}</p>
        </div>
      {/if}

      <div class="detail-meta">
        <span class="meta-item">
          Criado: {new Date(entry.created_at + "Z").toLocaleDateString("pt-BR")}
        </span>
        <span class="meta-item">
          Atualizado: {new Date(entry.updated_at + "Z").toLocaleDateString("pt-BR")}
        </span>
      </div>
    </div>

    <div class="detail-actions">
      <button class="btn-primary" onclick={() => (editing = true)}>Editar</button>
      <button class="btn-danger" onclick={deleteEntry}>Excluir</button>
    </div>
  {/if}
</div>

<style>
  .entry-detail {
    padding: var(--space-6);
    max-width: 480px;
  }

  .detail-header {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    margin-bottom: var(--space-6);
    flex-wrap: wrap;
  }

  .detail-header h2 {
    font-size: var(--font-size-xl);
    word-break: break-word;
  }

  .category-badge {
    font-size: var(--font-size-xs);
    font-weight: var(--font-weight-medium);
    color: white;
    padding: 2px var(--space-2);
    border-radius: var(--radius-sm);
  }

  .detail-body {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .detail-field {
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

  .field-value-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-2);
    padding: var(--space-3);
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    font-size: var(--font-size-sm);
    min-height: 40px;
  }

  .field-value-row span:first-child {
    word-break: break-all;
    font-family: var(--font-mono);
    font-size: var(--font-size-sm);
  }

  .field-actions {
    display: flex;
    gap: var(--space-1);
    flex-shrink: 0;
  }

  .password-value {
    letter-spacing: 0.1em;
  }

  .url-text {
    color: var(--color-accent);
    text-decoration: none;
    font-size: var(--font-size-sm);
    word-break: break-all;
  }

  .url-text:hover {
    text-decoration: underline;
  }

  .notes-text {
    padding: var(--space-3);
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    font-size: var(--font-size-sm);
    white-space: pre-wrap;
    word-break: break-word;
    line-height: var(--line-height-normal);
  }

  .detail-meta {
    display: flex;
    gap: var(--space-4);
    margin-top: var(--space-2);
  }

  .meta-item {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
  }

  .detail-actions {
    display: flex;
    gap: var(--space-3);
    margin-top: var(--space-6);
    padding-top: var(--space-4);
    border-top: 1px solid var(--color-border);
  }

  /* ─── Edit form ────────────────────────── */
  .edit-form {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .field {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
  }

  .field-row {
    display: flex;
    gap: var(--space-2);
  }

  .field-row input {
    flex: 1;
  }

  .edit-actions {
    display: flex;
    gap: var(--space-3);
    margin-top: var(--space-2);
  }
</style>
