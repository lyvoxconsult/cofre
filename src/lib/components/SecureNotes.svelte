<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { showToast } from "../stores/session";
  import type { SecureNoteDecrypted } from "../types";

  let notes = $state<SecureNoteDecrypted[]>([]);
  let loading = $state(false);
  let query = $state("");
  let selectedId = $state<string | null>(null);
  let showForm = $state(false);
  let editing = $state(false);

  let showDeleteConfirm = $state(false);
  let confirmPassword = $state("");
  let validatingDelete = $state(false);

  // Form state
  let formTitle = $state("");
  let formContent = $state("");
  let formCategory = $state("");

  let selectedNote = $derived(notes.find((n) => n.id === selectedId) ?? null);

  let filteredNotes = $derived.by(() => {
    if (!query) return notes;
    const q = query.toLowerCase();
    return notes.filter(
      (n) =>
        n.title.toLowerCase().includes(q) || n.category.toLowerCase().includes(q)
    );
  });

  async function loadNotes() {
    loading = true;
    try {
      notes = await invoke<SecureNoteDecrypted[]>("list_notes");
    } catch (e) {
      showToast(String(e), "error");
    } finally {
      loading = false;
    }
  }

  let readOnlyMode = $state(false);
  async function loadConfig() {
    try {
      const cfg = await invoke<any>("get_settings");
      readOnlyMode = cfg.read_only_mode;
    } catch {
      // ignore
    }
  }

  $effect(() => {
    loadNotes();
    loadConfig();
  });

  function selectNote(id: string) {
    selectedId = id;
    editing = false;
    showForm = false;
  }

  function openNewForm() {
    formTitle = "";
    formContent = "";
    formCategory = "";
    showForm = true;
    selectedId = null;
    editing = false;
  }

  function openEditForm() {
    if (!selectedNote) return;
    formTitle = selectedNote.title;
    formContent = selectedNote.content;
    formCategory = selectedNote.category;
    editing = true;
    showForm = true;
  }

  function cancelForm() {
    showForm = false;
    editing = false;
  }

  async function saveNote() {
    if (!formTitle.trim() && !formContent.trim()) {
      showToast("Título ou conteúdo é obrigatório.", "error");
      return;
    }

    try {
      if (editing && selectedId !== null) {
        await invoke("update_note", {
          id: selectedId,
          title: formTitle.trim(),
          content: formContent,
          category: formCategory.trim(),
        });
        showToast("Nota atualizada", "success");
      } else {
        await invoke("create_note", {
          title: formTitle.trim(),
          content: formContent,
          category: formCategory.trim(),
        });
        showToast("Nota criada", "success");
      }
      cancelForm();
      await loadNotes();
    } catch (e) {
      showToast(String(e), "error");
    }
  }

  function deleteNote() {
    showDeleteConfirm = true;
  }

  async function handleConfirmDelete() {
    if (!selectedNote) return;
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
      await invoke("delete_note", { id: selectedNote.id });
      showDeleteConfirm = false;
      confirmPassword = "";
      selectedId = null;
      showForm = false;
      editing = false;
      await loadNotes();
      showToast("Nota excluída", "info");
    } catch (e) {
      showToast(String(e), "error");
    } finally {
      validatingDelete = false;
    }
  }
</script>

<div class="notes-view">
  <!-- Sidebar de notas -->
  <aside class="notes-sidebar">
    <div class="sidebar-header">
      <h3>Notas</h3>
    </div>

    <div class="sidebar-search">
      <input
        type="text"
        placeholder="Buscar notas…"
        bind:value={query}
      />
    </div>

    <div class="notes-list">
      {#if loading}
        <div class="list-empty">Carregando…</div>
      {:else if filteredNotes.length === 0}
        <div class="list-empty">
          <p>{query ? "Nenhuma nota encontrada" : "Nenhuma nota ainda"}</p>
        </div>
      {:else}
        {#each filteredNotes as note (note.id)}
          <button
            class="note-row"
            class:selected={selectedId === note.id}
            onclick={() => selectNote(note.id)}
          >
            <div class="note-info">
              <span class="note-title">{note.title || "Sem título"}</span>
              {#if note.category}
                <span class="note-category">{note.category}</span>
              {/if}
            </div>
          </button>
        {/each}
      {/if}
    </div>

    <div class="sidebar-footer">
      <button class="btn-primary btn-sm" onclick={openNewForm} disabled={readOnlyMode}>
        + Nova Nota
      </button>
    </div>
  </aside>

  <!-- Painel de conteúdo -->
  <div class="notes-content">
    {#if showForm}
      <div class="note-form animate-scale-in">
        <h2>{editing ? "Editar Nota" : "Nova Nota"}</h2>

        <label class="field">
          <span class="field-label">Título</span>
          <input type="text" bind:value={formTitle} placeholder="Título da nota" />
        </label>

        <label class="field">
          <span class="field-label">Conteúdo</span>
          <textarea bind:value={formContent} rows={10} placeholder="Escreva sua nota aqui…"></textarea>
        </label>

        <label class="field">
          <span class="field-label">Categoria</span>
          <input type="text" bind:value={formCategory} placeholder="Ex: Ideias, Trabalho, Pessoal" />
        </label>

        <div class="form-actions">
          <button class="btn-primary" onclick={saveNote}>
            {editing ? "Salvar" : "Criar"}
          </button>
          <button class="btn-ghost" onclick={cancelForm}>Cancelar</button>
        </div>
      </div>
    {:else if selectedNote}
      <div class="note-detail animate-fade-in">
        <div class="detail-header">
          <h2>{selectedNote.title || "Sem título"}</h2>
          {#if selectedNote.category}
            <span class="category-tag">{selectedNote.category}</span>
          {/if}
        </div>

        {#if selectedNote.content}
          <div class="detail-content">
            {selectedNote.content}
          </div>
        {:else}
          <p class="empty-content">Nenhum conteúdo</p>
        {/if}

        <div class="detail-meta">
          <span>Criado: {new Date(selectedNote.created_at + "Z").toLocaleDateString("pt-BR")}</span>
          <span>Atualizado: {new Date(selectedNote.updated_at + "Z").toLocaleDateString("pt-BR")}</span>
        </div>

        <div class="detail-actions">
          <button class="btn-primary" onclick={openEditForm} disabled={readOnlyMode}>Editar</button>
          <button class="btn-danger" onclick={deleteNote} disabled={readOnlyMode}>Excluir</button>
        </div>
      </div>
    {:else}
      <div class="content-empty">
        <p>Selecione uma nota ou crie uma nova</p>
      </div>
    {/if}
  </div>

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
  .notes-view {
    display: flex;
    flex: 1;
    overflow: hidden;
  }

  /* ─── Sidebar ──────────────────────────── */
  .notes-sidebar {
    width: 260px;
    flex-shrink: 0;
    border-right: 1px solid var(--color-border);
    display: flex;
    flex-direction: column;
    background: var(--color-surface);
  }

  .sidebar-header {
    padding: var(--space-4) var(--space-4) var(--space-2);
  }

  .sidebar-header h3 {
    font-size: var(--font-size-xs);
    font-weight: var(--font-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--color-text-tertiary);
  }

  .sidebar-search {
    padding: 0 var(--space-3) var(--space-2);
  }

  .sidebar-search input {
    width: 100%;
    padding: var(--space-2) var(--space-3);
    font-size: var(--font-size-sm);
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
  }

  .notes-list {
    flex: 1;
    overflow-y: auto;
    padding: var(--space-1) var(--space-2);
  }

  .note-row {
    display: flex;
    align-items: center;
    width: 100%;
    padding: var(--space-3);
    border-radius: var(--radius-md);
    border: none;
    background: transparent;
    cursor: pointer;
    transition: background var(--transition-fast);
    text-align: left;
    color: var(--color-text-primary);
    margin-bottom: var(--space-1);
  }

  .note-row:hover {
    background: var(--color-surface-hover);
  }

  .note-row.selected {
    background: var(--color-accent-muted);
  }

  .note-info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .note-title {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-medium);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .note-category {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .sidebar-footer {
    padding: var(--space-3);
    border-top: 1px solid var(--color-border);
  }

  .sidebar-footer button {
    width: 100%;
  }

  .list-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: var(--space-8) var(--space-4);
    color: var(--color-text-tertiary);
    font-size: var(--font-size-sm);
  }

  /* ─── Painel de conteúdo ───────────────── */
  .notes-content {
    flex: 1;
    overflow-y: auto;
    padding: var(--space-6);
    background: var(--color-bg);
  }

  .content-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: var(--color-text-tertiary);
    font-size: var(--font-size-sm);
  }

  /* ─── Formulário ───────────────────────── */
  .note-form {
    max-width: 600px;
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .note-form h2 {
    font-size: var(--font-size-xl);
    margin-bottom: var(--space-2);
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

  .field textarea {
    min-height: 200px;
    resize: vertical;
    font-family: var(--font-mono);
    font-size: var(--font-size-sm);
    line-height: var(--line-height-normal);
  }

  .form-actions {
    display: flex;
    gap: var(--space-3);
    padding-top: var(--space-4);
    border-top: 1px solid var(--color-border);
  }

  /* ─── Detalhe ──────────────────────────── */
  .note-detail {
    max-width: 600px;
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

  .category-tag {
    font-size: var(--font-size-xs);
    font-weight: var(--font-weight-medium);
    background: var(--color-accent-muted);
    color: var(--color-accent);
    padding: 2px var(--space-2);
    border-radius: var(--radius-sm);
  }

  .detail-content {
    padding: var(--space-4);
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    font-size: var(--font-size-sm);
    white-space: pre-wrap;
    word-break: break-word;
    line-height: var(--line-height-normal);
    min-height: 120px;
  }

  .empty-content {
    color: var(--color-text-tertiary);
    font-size: var(--font-size-sm);
    padding: var(--space-8) 0;
  }

  .detail-meta {
    display: flex;
    gap: var(--space-4);
    margin-top: var(--space-4);
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
</style>
