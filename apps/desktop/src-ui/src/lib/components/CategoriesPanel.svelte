<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { showToast, readOnlyMode } from "../stores/session";
  import type { Category } from "../types";

  let categories = $state<Category[]>([]);
  let loading = $state(true);

  // Form States
  let showForm = $state(false);
  let editingCategory = $state<Category | null>(null);
  let name = $state("");
  let selectedColor = $state("#6366f1");
  let selectedIcon = $state("folder");
  let saving = $state(false);

  // Delete Confirm States
  let categoryToDelete = $state<Category | null>(null);

  const colors = [
    "#6366f1", // Indigo
    "#f59e0b", // Dourado / Âmbar
    "#10b981", // Verde
    "#ef4444", // Vermelho
    "#8b5cf6", // Roxo
    "#d97706", // Cobre
    "#ec4899", // Rosa
    "#6b7280", // Cinza
  ];

  const icons = [
    { key: "folder", label: "📁 Pasta" },
    { key: "user", label: "👤 Usuário" },
    { key: "briefcase", label: "💼 Trabalho" },
    { key: "landmark", label: "🏛️ Bancos" },
    { key: "key", label: "🔑 Chave" },
    { key: "star", label: "⭐ Estrela" },
    { key: "heart", label: "❤️ Favorito" },
    { key: "tag", label: "🏷️ Tag" },
  ];

  async function loadCategories() {
    loading = true;
    try {
      categories = await invoke<Category[]>("list_categories");
    } catch (e) {
      showToast("Erro ao carregar categorias: " + String(e), "error");
    } finally {
      loading = false;
    }
  }

  function getIconEmoji(iconName: string) {
    switch (iconName) {
      case "folder": return "📁";
      case "user": return "👤";
      case "briefcase": return "💼";
      case "landmark": return "🏛️";
      case "key": return "🔑";
      case "star": return "⭐";
      case "heart": return "❤️";
      case "tag": return "🏷️";
      default: return "📁";
    }
  }

  async function handleSave() {
    if (!name.trim()) {
      showToast("O nome da categoria é obrigatório.", "error");
      return;
    }

    if (categories.some((c) => c.name.toLowerCase() === name.trim().toLowerCase() && c.id !== editingCategory?.id)) {
      showToast("Já existe uma categoria com este nome.", "error");
      return;
    }

    saving = true;
    try {
      if (editingCategory) {
        await invoke("update_category", {
          id: editingCategory.id,
          name: name.trim(),
          color: selectedColor,
          icon: selectedIcon,
        });
        showToast("Categoria atualizada com sucesso.", "success");
      } else {
        await invoke("create_category", {
          name: name.trim(),
          color: selectedColor,
          icon: selectedIcon,
        });
        showToast("Categoria criada com sucesso.", "success");
      }
      showForm = false;
      editingCategory = null;
      name = "";
      selectedColor = "#6366f1";
      selectedIcon = "folder";
      await loadCategories();
    } catch (e) {
      showToast("Erro ao salvar categoria: " + String(e), "error");
    } finally {
      saving = false;
    }
  }

  async function handleDelete() {
    if (!categoryToDelete) return;
    if ($readOnlyMode) {
      showToast("Ação bloqueada no modo somente leitura.", "error");
      return;
    }

    if (categoryToDelete.id === "1" || categoryToDelete.id === "2" || categoryToDelete.id === "3" || categoryToDelete.id === "4") {
      showToast("Não é possível excluir categorias padrão do sistema.", "error");
      categoryToDelete = null;
      return;
    }

    try {
      await invoke("delete_category", { id: categoryToDelete.id });
      showToast("Categoria excluída com sucesso.", "success");
      categoryToDelete = null;
      await loadCategories();
    } catch (e) {
      showToast("Erro ao excluir categoria: " + String(e), "error");
    }
  }

  function openEdit(cat: Category) {
    editingCategory = cat;
    name = cat.name;
    selectedColor = cat.color;
    selectedIcon = cat.icon;
    showForm = true;
  }

  function openCreate() {
    editingCategory = null;
    name = "";
    selectedColor = "#6366f1";
    selectedIcon = "folder";
    showForm = true;
  }

  $effect(() => {
    loadCategories();
  });
</script>

<div class="categories-panel animate-fade-in">
  <div class="panel-shell">
    <header class="panel-header">
      <div>
        <h2>Categorias</h2>
        <p>Gerencie suas categorias personalizadas para organizar suas credenciais e notas.</p>
      </div>
      <div>
        {#if !$readOnlyMode}
          <button class="btn-primary" onclick={openCreate}>+ Nova Categoria</button>
        {/if}
      </div>
    </header>

    {#if loading}
      <div class="loading-card">Carregando categorias...</div>
    {:else}
      <div class="categories-list">
        {#each categories as cat}
          <div class="category-card" style="border-left: 4px solid {cat.color}">
            <div class="category-info">
              <span class="category-icon" style="background: {cat.color}22; color: {cat.color}">
                {getIconEmoji(cat.icon)}
              </span>
              <div>
                <strong>{cat.name}</strong>
                {#if cat.id === "1" || cat.id === "2" || cat.id === "3" || cat.id === "4"}
                  <small>Padrão do sistema</small>
                {:else}
                  <small>Personalizada</small>
                {/if}
              </div>
            </div>
            <div class="category-actions">
              {#if !$readOnlyMode && cat.id !== "1" && cat.id !== "2" && cat.id !== "3" && cat.id !== "4"}
                <button class="btn-icon" title="Editar" onclick={() => openEdit(cat)}>✏️</button>
                <button class="btn-icon btn-delete" title="Excluir" onclick={() => (categoryToDelete = cat)}>🗑️</button>
              {/if}
            </div>
          </div>
        {/each}
      </div>
    {/if}
  </div>

  {#if showForm}
    <div class="modal-backdrop" role="presentation">
      <div class="modal-card">
        <h3>{editingCategory ? "Editar Categoria" : "Nova Categoria"}</h3>
        <p>Preencha os campos para salvar a categoria.</p>
        
        <div class="form-group">
          <label class="field">
            <span>Nome da categoria</span>
            <input type="text" bind:value={name} placeholder="Ex: Redes Sociais" />
          </label>
        </div>

        <div class="form-group">
          <span class="group-label">Selecione a cor</span>
          <div class="color-grid">
            {#each colors as colorHex}
              <button 
                class="color-dot" 
                style="background: {colorHex}" 
                class:selected={selectedColor === colorHex}
                onclick={() => (selectedColor = colorHex)}
                aria-label="Selecionar cor {colorHex}"
              ></button>
            {/each}
          </div>
        </div>

        <div class="form-group">
          <span class="group-label">Selecione o ícone</span>
          <div class="icon-grid">
            {#each icons as iconItem}
              <button 
                class="icon-option" 
                class:selected={selectedIcon === iconItem.key}
                onclick={() => (selectedIcon = iconItem.key)}
              >
                {iconItem.label}
              </button>
            {/each}
          </div>
        </div>

        <div class="modal-actions">
          <button class="btn-secondary" onclick={() => (showForm = false)} disabled={saving}>Cancelar</button>
          <button class="btn-primary" onclick={handleSave} disabled={saving}>
            {saving ? "Salvando..." : "Salvar"}
          </button>
        </div>
      </div>
    </div>
  {/if}

  {#if categoryToDelete}
    <div class="modal-backdrop" role="presentation">
      <div class="modal-card danger-modal">
        <h3>Excluir Categoria?</h3>
        <p>Tem certeza de que deseja excluir a categoria '<strong>{categoryToDelete.name}</strong>'? Qualquer senha que utilize esta categoria ficará classificada como "Sem categoria".</p>
        <div class="modal-actions">
          <button class="btn-secondary" onclick={() => (categoryToDelete = null)}>Cancelar</button>
          <button class="btn-danger" onclick={handleDelete}>Excluir categoria</button>
        </div>
      </div>
    </div>
  {/if}
</div>

<style>
  .categories-panel {
    flex: 1;
    min-width: 0;
    overflow-y: auto;
    padding: 26px 30px;
  }

  .panel-shell {
    max-width: 900px;
    margin: 0 auto;
  }

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-6);
    margin-bottom: var(--space-5);
  }

  .panel-header h2 {
    font-size: 1.9rem;
    margin-bottom: 4px;
  }

  .panel-header p {
    color: var(--color-text-secondary);
    font-size: var(--font-size-sm);
  }

  .categories-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin-top: 20px;
  }

  .category-card {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px var(--space-4);
    background: rgba(19, 24, 40, 0.7);
    border: 1px solid rgba(255, 255, 255, 0.06);
    border-radius: 12px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
    transition: transform 0.2s, background 0.2s;
  }

  .category-card:hover {
    background: rgba(19, 24, 40, 0.85);
    transform: translateY(-2px);
  }

  .category-info {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .category-icon {
    width: 40px;
    height: 40px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1.2rem;
  }

  .category-info strong {
    display: block;
    font-size: 1.05rem;
    color: white;
  }

  .category-info small {
    font-size: var(--font-size-xs);
    color: var(--color-text-secondary);
  }

  .category-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .btn-icon {
    background: transparent;
    border: none;
    cursor: pointer;
    font-size: 1.2rem;
    padding: 6px;
    border-radius: 8px;
    transition: background 0.2s;
  }

  .btn-icon:hover {
    background: rgba(255, 255, 255, 0.08);
  }

  .btn-icon.btn-delete:hover {
    background: rgba(239, 68, 68, 0.15);
  }

  /* Form / Modal styles */
  .form-group {
    margin-top: 18px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .group-label {
    font-size: var(--font-size-sm);
    color: var(--color-text-primary);
    font-weight: 600;
  }

  .color-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }

  .color-dot {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    border: 2px solid transparent;
    cursor: pointer;
    transition: border-color 0.2s, transform 0.2s;
  }

  .color-dot:hover {
    transform: scale(1.1);
  }

  .color-dot.selected {
    border-color: white;
    box-shadow: 0 0 10px rgba(255, 255, 255, 0.5);
  }

  .icon-grid {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .icon-option {
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.1);
    color: white;
    padding: 8px 12px;
    border-radius: 8px;
    cursor: pointer;
    font-size: var(--font-size-sm);
    transition: background 0.2s, border-color 0.2s;
  }

  .icon-option:hover {
    background: rgba(255, 255, 255, 0.1);
  }

  .icon-option.selected {
    background: rgba(139, 92, 246, 0.2);
    border-color: #8b5cf6;
    color: #c4b5fd;
  }
</style>
