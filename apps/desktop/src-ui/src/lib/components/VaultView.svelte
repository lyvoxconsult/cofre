<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { showToast, searchQuery, selectedEntryId, focusSearchSignal, readOnlyMode as readOnlyStore } from "../stores/session";
  import type { VaultEntryDecrypted, Category } from "../types";
  import EntryDetail from "./EntryDetail.svelte";
  import EntryForm from "./EntryForm.svelte";

  let entries = $state<VaultEntryDecrypted[]>([]);
  let categories = $state<Category[]>([]);
  let loading = $state(false);
  let query = $state("");
  let selectedId = $state<string | null>(null);
  let showForm = $state(false);
  let filterCategory = $state<string | null>(null);
  let searchInput: HTMLInputElement | undefined = $state(undefined);
  let focusTick = $state(0);
  let privacyMode = $state(false);
  let readOnlyMode = $state(false);

  // Reatividade com stores
  searchQuery.subscribe((v) => (query = v));
  selectedEntryId.subscribe((v) => (selectedId = v));
  focusSearchSignal.subscribe((v) => (focusTick = v));
  readOnlyStore.subscribe((v) => (readOnlyMode = v));

  // Foca a busca quando o sinal dispara
  $effect(() => {
    if (focusTick > 0 && searchInput) {
      searchInput.focus();
      searchInput.select();
    }
  });

  let selectedEntry = $derived(entries.find((e) => e.id === selectedId) ?? null);

  let filteredEntries = $derived.by(() => {
    let result = entries;
    if (query) {
      const q = query.toLowerCase();
      result = result.filter(
        (e) =>
          e.service_name.toLowerCase().includes(q) ||
          e.login.toLowerCase().includes(q) ||
          e.url.toLowerCase().includes(q)
      );
    }
    if (filterCategory !== null) {
      result = result.filter((e) => e.category_id === filterCategory);
    }
    if (filterFavorites) {
      result = result.filter((e) => e.is_favorite);
    }
    return result;
  });

  async function loadEntries() {
    loading = true;
    try {
      entries = await invoke<VaultEntryDecrypted[]>("list_entries");
    } catch (e) {
      showToast(String(e), "error");
    } finally {
      loading = false;
    }
  }

  async function loadCategories() {
    try {
      categories = await invoke<Category[]>("list_categories");
    } catch {
      // fallback categories
      categories = [
        { id: "1", name: "Pessoal", color: "#6366f1", icon: "user", sort_order: 0 },
        { id: "2", name: "Trabalho", color: "#f59e0b", icon: "briefcase", sort_order: 1 },
        { id: "3", name: "Bancos", color: "#10b981", icon: "landmark", sort_order: 2 },
        { id: "4", name: "Outros", color: "#6b7280", icon: "folder", sort_order: 3 },
      ];
    }
  }

  async function loadConfig() {
    try {
      const cfg = await invoke<any>("get_settings");
      privacyMode = cfg.privacy_mode;
      readOnlyMode = cfg.read_only_mode;
      readOnlyStore.set(readOnlyMode);
    } catch {
      // ignore
    }
  }

  $effect(() => {
    loadEntries();
    loadCategories();
    loadConfig();
  });

  function handleSearchInput(e: Event) {
    const target = e.target as HTMLInputElement;
    query = target.value;
    searchQuery.set(query);
  }

  function selectEntry(id: string) {
    selectedId = id;
    selectedEntryId.set(id);
  }

  // Favoritos
  let filterFavorites = $state(false);

  function handleCreated() {
    showForm = false;
    loadEntries();
    showToast("Registro salvo com sucesso", "success");
  }

  function handleDeleted() {
    selectedId = null;
    selectedEntryId.set(null);
    loadEntries();
    showToast("Registro excluído", "info");
  }

  function handleUpdated() {
    loadEntries();
    showToast("Registro atualizado", "success");
  }

  function getCategoryColor(id: string | null): string {
    if (id === null) return "#6b7280";
    return categories.find((c) => c.id === id)?.color ?? "#6b7280";
  }

  function getCategoryName(id: string | null): string {
    if (id === null) return "Sem categoria";
    return categories.find((c) => c.id === id)?.name ?? "Desconhecida";
  }
</script>

<div class="vault-view">
  <!-- Sidebar de categorias -->
  <aside class="category-sidebar">
    <div class="sidebar-header">
      <h3>Categorias</h3>
    </div>
    <div class="category-list">
      <button
        class="category-item"
        class:active={filterCategory === null && !filterFavorites}
        onclick={() => { filterCategory = null; filterFavorites = false; }}
      >
        <span class="cat-dot" style="background: #6b7280"></span>
        <span>Todas</span>
        <span class="cat-count">{entries.length}</span>
      </button>
      <button
        class="category-item"
        class:active={filterFavorites}
        onclick={() => { filterCategory = null; filterFavorites = true; }}
      >
        <span class="cat-dot" style="background: #e11d48"></span>
        <span>⭐ Favoritos</span>
        <span class="cat-count">{entries.filter((e) => e.is_favorite).length}</span>
      </button>
      {#each categories as cat}
        <button
          class="category-item"
          class:active={filterCategory === cat.id && !filterFavorites}
          onclick={() => { filterCategory = cat.id; filterFavorites = false; }}
        >
          <span class="cat-dot" style="background: {cat.color}"></span>
          <span>{cat.name}</span>
          <span class="cat-count">{entries.filter((e) => e.category_id === cat.id).length}</span>
        </button>
      {/each}
    </div>
    <div class="sidebar-footer">
      <button class="btn-primary btn-sm" onclick={() => (showForm = true)} disabled={readOnlyMode}>
        + Novo Registro
      </button>
    </div>
  </aside>

  <!-- Lista de entradas -->
  <div class="vault-list-panel">
    <div class="list-header">
      <div class="search-box">
        <span class="search-icon">&#128269;</span>
        <input
          type="text"
          placeholder="Buscar por serviço ou login…"
          value={query}
          oninput={handleSearchInput}
          bind:this={searchInput}
        />
      </div>
    </div>

    <div class="list-body">
      {#if loading}
        <div class="list-empty">Carregando…</div>
      {:else if filteredEntries.length === 0}
        <div class="list-empty">
          <p>Nenhum registro encontrado</p>
          <button class="btn-secondary btn-sm" onclick={() => (showForm = true)} disabled={readOnlyMode}>
            Criar primeiro registro
          </button>
        </div>
      {:else}
        {#each filteredEntries as entry (entry.id)}
          <button
            class="entry-row"
            class:selected={selectedId === entry.id}
            onclick={() => selectEntry(entry.id)}
          >
            <span class="entry-dot" style="background: {getCategoryColor(entry.category_id)}"></span>
            <div class="entry-info">
              <span class="entry-name">{entry.service_name}</span>
              <span class="entry-login">{privacyMode ? "••••••••" : entry.login}</span>
            </div>
            <span class="entry-category-tag">{getCategoryName(entry.category_id)}</span>
          </button>
        {/each}
      {/if}
    </div>
  </div>

  <!-- Painel de detalhes / formulário -->
  <div class="vault-detail-panel">
    {#if showForm}
      <EntryForm
        onCancel={() => (showForm = false)}
        onCreated={handleCreated}
        {categories}
      />
    {:else if selectedEntry}
      <EntryDetail
        entry={selectedEntry}
        onDeleted={handleDeleted}
        onUpdated={handleUpdated}
        {categories}
        {readOnlyMode}
      />
    {:else}
      <div class="detail-empty">
        <p>Selecione um registro ou crie um novo</p>
      </div>
    {/if}
  </div>
</div>

<style>
  .vault-view {
    display: flex;
    flex: 1;
    overflow: hidden;
  }

  /* ─── Sidebar ──────────────────────────── */
  .category-sidebar {
    width: 200px;
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

  .category-list {
    flex: 1;
    overflow-y: auto;
    padding: var(--space-1) var(--space-2);
  }

  .category-item {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    width: 100%;
    padding: var(--space-2) var(--space-3);
    border-radius: var(--radius-md);
    font-size: var(--font-size-sm);
    background: transparent;
    color: var(--color-text-secondary);
    cursor: pointer;
    transition: all var(--transition-fast);
    border: none;
    text-align: left;
  }

  .category-item:hover {
    background: var(--color-surface-hover);
    color: var(--color-text-primary);
  }

  .category-item.active {
    background: var(--color-accent-muted);
    color: var(--color-accent);
  }

  .cat-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .cat-count {
    margin-left: auto;
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
  }

  .sidebar-footer {
    padding: var(--space-3) var(--space-3);
    border-top: 1px solid var(--color-border);
  }

  .sidebar-footer button {
    width: 100%;
  }

  /* ─── Lista ────────────────────────────── */
  .vault-list-panel {
    width: 320px;
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    border-right: 1px solid var(--color-border);
  }

  .list-header {
    padding: var(--space-3) var(--space-4);
    border-bottom: 1px solid var(--color-border);
  }

  .search-box {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    padding: 0 var(--space-3);
  }

  .search-box input {
    border: none;
    background: transparent;
    padding: var(--space-2) 0;
    font-size: var(--font-size-sm);
  }

  .search-box input:focus {
    box-shadow: none;
  }

  .search-icon {
    font-size: var(--font-size-sm);
    color: var(--color-text-tertiary);
  }

  .list-body {
    flex: 1;
    overflow-y: auto;
  }

  .entry-row {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    width: 100%;
    padding: var(--space-3) var(--space-4);
    border: none;
    border-bottom: 1px solid var(--color-border);
    background: transparent;
    cursor: pointer;
    transition: background var(--transition-fast);
    text-align: left;
    color: var(--color-text-primary);
  }

  .entry-row:hover {
    background: var(--color-surface-hover);
  }

  .entry-row.selected {
    background: var(--color-accent-muted);
  }

  .entry-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .entry-info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .entry-name {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-medium);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .entry-login {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .entry-category-tag {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
    background: var(--color-surface-elevated);
    padding: 2px var(--space-2);
    border-radius: var(--radius-sm);
    flex-shrink: 0;
  }

  .list-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--space-3);
    padding: var(--space-12) var(--space-4);
    color: var(--color-text-tertiary);
    font-size: var(--font-size-sm);
  }

  /* ─── Detalhe ──────────────────────────── */
  .vault-detail-panel {
    flex: 1;
    overflow-y: auto;
    background: var(--color-bg);
  }

  .detail-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: var(--color-text-tertiary);
    font-size: var(--font-size-sm);
  }
</style>
