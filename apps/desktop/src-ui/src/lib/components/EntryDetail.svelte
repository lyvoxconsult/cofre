<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { open } from "@tauri-apps/plugin-shell";
  import { showToast } from "../stores/session";
  import type { VaultEntryDecrypted, Category, AttachmentDecrypted } from "../types";

  interface Props {
    entry: VaultEntryDecrypted;
    onDeleted: () => void;
    onUpdated: () => void;
    categories: Category[];
    readOnlyMode: boolean;
  }

  let { entry, onDeleted, onUpdated, categories, readOnlyMode }: Props = $props();

  let editing = $state(false);
  let showPassword = $state(false);
  let copiedPassword = $state(false);
  let copiedLogin = $state(false);

  let showDeleteConfirm = $state(false);
  let confirmPassword = $state("");
  let validatingDelete = $state(false);

  // Form state — inicializado vazio e sincronizado via $effect
  let editServiceName = $state("");
  let editLogin = $state("");
  let editPassword = $state("");
  let editUrl = $state("");
  let editNotes = $state("");
  let editCategoryId = $state<string | null>(null);
  let editIsFavorite = $state(false);

  // Sincroniza quando entry muda (seleção de entrada diferente)
  $effect(() => {
    editServiceName = entry.service_name;
    editLogin = entry.login;
    editPassword = entry.password;
    editUrl = entry.url;
    editNotes = entry.notes;
    editCategoryId = entry.category_id;
    editIsFavorite = entry.is_favorite;
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
        isFavorite: editIsFavorite,
      });
      editing = false;
      onUpdated();
    } catch (e) {
      showToast(String(e), "error");
    }
  }

  async function toggleFavorite() {
    try {
      await invoke("update_entry", {
        id: entry.id,
        serviceName: entry.service_name,
        login: entry.login,
        password: entry.password,
        notes: entry.notes || "",
        url: entry.url,
        categoryId: entry.category_id,
        isFavorite: !entry.is_favorite,
      });
      onUpdated();
    } catch (e) {
      showToast(String(e), "error");
    }
  }

  function deleteEntry() {
    showDeleteConfirm = true;
  }

  async function handleConfirmDelete() {
    if (!confirmPassword) {
      showToast("Por favor, digite a senha mestra.", "error");
      return;
    }
    validatingDelete = true;
    try {
      const isCorrect = await invoke<boolean>("verify_master_password", { password: confirmPassword });
      if (!isCorrect) {
        showToast("Senha mestra incorreta.", "error");
        validatingDelete = false;
        return;
      }
      await invoke("delete_entry", { id: entry.id });
      showDeleteConfirm = false;
      confirmPassword = "";
      onDeleted();
    } catch (e) {
      showToast(String(e), "error");
    } finally {
      validatingDelete = false;
    }
  }

  function getCategoryName(id: string | null): string {
    if (id === null) return "Sem categoria";
    return categories.find((c) => c.id === id)?.name ?? "Sem categoria";
  }

  // Lógica de Anexos
  let attachments = $state<AttachmentDecrypted[]>([]);
  let uploading = $state(false);

  async function loadAttachments() {
    try {
      attachments = await invoke<AttachmentDecrypted[]>("list_attachments", { entryId: entry.id });
    } catch (e) {
      console.error("Erro ao listar anexos:", e);
    }
  }

  async function handleFileSelect(event: Event) {
    const target = event.target as HTMLInputElement;
    if (!target.files || target.files.length === 0) return;
    const file = target.files[0];
    
    // Limite de 15MB
    if (file.size > 15 * 1024 * 1024) {
      showToast("O arquivo é muito grande. Limite de 15MB.", "error");
      return;
    }

    uploading = true;
    const reader = new FileReader();
    reader.onload = async () => {
      try {
        const base64String = (reader.result as string).split(',')[1];
        await invoke("create_attachment", {
          entryId: entry.id,
          filename: file.name,
          mimeType: file.type || "application/octet-stream",
          fileBytesBase64: base64String,
        });
        showToast("Anexo adicionado com sucesso!", "success");
        await loadAttachments();
      } catch (e) {
        showToast("Erro ao adicionar anexo: " + String(e), "error");
      } finally {
        uploading = false;
      }
    };
    reader.onerror = () => {
      showToast("Erro ao ler arquivo local.", "error");
      uploading = false;
    };
    reader.readAsDataURL(file);
    target.value = "";
  }

  async function downloadAttachment(att: AttachmentDecrypted) {
    try {
      const base64Data = await invoke<string>("get_attachment_data", { id: att.id });
      const binaryString = window.atob(base64Data);
      const len = binaryString.length;
      const bytes = new Uint8Array(len);
      for (let i = 0; i < len; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      const blob = new Blob([bytes], { type: att.mime_type });
      const url = URL.createObjectURL(blob);
      
      const a = document.createElement("a");
      a.href = url;
      a.download = att.filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      showToast("Anexo baixado com sucesso!", "success");
    } catch (e) {
      showToast("Erro ao baixar anexo: " + String(e), "error");
    }
  }

  async function deleteAttachment(id: string) {
    if (!confirm("Tem certeza que deseja excluir este anexo?")) return;
    try {
      await invoke("delete_attachment", { id });
      showToast("Anexo excluído com sucesso!", "success");
      await loadAttachments();
    } catch (e) {
      showToast("Erro ao excluir anexo: " + String(e), "error");
    }
  }

  $effect(() => {
    if (entry?.id) {
      loadAttachments();
    }
  });
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
      <label class="field-checkbox" style="display: flex; align-items: center; gap: var(--space-2); cursor: pointer; padding: var(--space-1) 0;">
        <input type="checkbox" bind:checked={editIsFavorite} style="width: auto;" />
        <span style="font-size: var(--font-size-sm); color: var(--color-text-primary);">Favorito</span>
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
    <div class="detail-header" style="display: flex; align-items: center; gap: var(--space-2); width: 100%;">
      <h2 style="margin: 0; flex-shrink: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{entry.service_name}</h2>
      <button class="btn-ghost" onclick={toggleFavorite} disabled={readOnlyMode} style="font-size: 20px; border: none; cursor: pointer; background: transparent; padding: 4px; color: #fbbf24; display: flex; align-items: center; justify-content: center; line-height: 1;" title={entry.is_favorite ? "Remover dos favoritos" : "Adicionar aos favoritos"}>
        {entry.is_favorite ? "★" : "☆"}
      </button>
      <span class="category-badge" style="background: {categories.find(c => c.id === entry.category_id)?.color ?? '#6b7280'}; font-size: var(--font-size-xs); font-weight: var(--font-weight-medium); color: white; padding: 2px var(--space-2); border-radius: var(--radius-sm); margin-left: auto; flex-shrink: 0;">
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

      <div class="detail-field">
        <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-1);">
          <span class="field-label">Anexos e Mídia</span>
          {#if !readOnlyMode}
            <button class="btn-secondary btn-sm" onclick={() => document.getElementById('attachment-file-input')?.click()} disabled={uploading}>
              {uploading ? "Enviando..." : "+ Adicionar"}
            </button>
          {/if}
        </div>
        <input type="file" id="attachment-file-input" style="display: none;" onchange={handleFileSelect} />
        
        <div class="attachments-list">
          {#if attachments.length === 0}
            <span class="no-attachments">Nenhum anexo encontrado.</span>
          {:else}
            {#each attachments as att}
              <div class="attachment-item">
                <div class="attachment-info" onclick={() => downloadAttachment(att)} onkeydown={(e) => e.key === 'Enter' && downloadAttachment(att)} role="button" tabindex="0" style="cursor: pointer; flex: 1; min-width: 0;">
                  <span class="attachment-icon">📎</span>
                  <div class="attachment-text" style="display: flex; flex-direction: column; min-width: 0;">
                    <span class="attachment-name" title={att.filename}>{att.filename}</span>
                    <span class="attachment-size">{(att.file_size / 1024).toFixed(1)} KB</span>
                  </div>
                </div>
                {#if !readOnlyMode}
                  <button class="btn-danger-icon" onclick={() => deleteAttachment(att.id)} title="Excluir anexo">✕</button>
                {/if}
              </div>
            {/each}
          {/if}
        </div>
      </div>

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
      <button class="btn-primary" onclick={() => (editing = true)} disabled={readOnlyMode}>Editar</button>
      <button class="btn-danger" onclick={deleteEntry} disabled={readOnlyMode}>Excluir</button>
    </div>
  {/if}

  {#if showDeleteConfirm}
    <div class="modal-overlay">
      <div class="modal-content animate-scale-in">
        <h3 style="margin-top: 0; color: var(--color-danger);">Confirmar Exclusão</h3>
        <p style="font-size: var(--font-size-sm); color: var(--color-text-secondary); line-height: var(--line-height-normal);">
          Esta ação é irreversível. Por favor, confirme digitando sua senha mestra.
        </p>
        <label class="field" style="display: flex; flex-direction: column; gap: var(--space-1); width: 100%;">
          <span class="field-label" style="font-size: var(--font-size-xs); font-weight: var(--font-weight-semibold); text-transform: uppercase; color: var(--color-text-tertiary);">Senha Mestra</span>
          <input type="password" bind:value={confirmPassword} placeholder="Digite sua senha mestra" style="width: 100%;" />
        </label>
        <div class="modal-actions" style="margin-top: var(--space-4); display: flex; gap: var(--space-2); justify-content: flex-end;">
          <button class="btn-danger" onclick={handleConfirmDelete} disabled={validatingDelete}>
            {validatingDelete ? "Excluindo..." : "Confirmar Exclusão"}
          </button>
          <button class="btn-secondary" onclick={() => { showDeleteConfirm = false; confirmPassword = ""; }}>
            Cancelar
          </button>
        </div>
      </div>
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

  /* ─── Estilos de Anexos ─────────────────── */
  .attachments-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    margin-top: var(--space-2);
  }

  .attachment-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-2);
    padding: var(--space-2) var(--space-3);
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
  }

  .attachment-info {
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }

  .attachment-icon {
    font-size: 18px;
    color: var(--color-text-tertiary);
  }

  .attachment-name {
    font-size: var(--font-size-sm);
    color: var(--color-text-primary);
    font-weight: var(--font-weight-medium);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .attachment-name:hover {
    color: var(--color-accent);
    text-decoration: underline;
  }

  .attachment-size {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
  }

  .no-attachments {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
    font-style: italic;
    padding: var(--space-2) 0;
  }

  .btn-danger-icon {
    background: transparent;
    border: none;
    color: var(--color-danger);
    font-size: 14px;
    cursor: pointer;
    padding: 4px var(--space-2);
    border-radius: var(--radius-sm);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 0.2s;
  }

  .btn-danger-icon:hover {
    background: rgba(239, 68, 68, 0.1);
  }
</style>
