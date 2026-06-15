<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { open } from "@tauri-apps/plugin-dialog";
  import type { MediaItemDecrypted } from "../types";
  import { showToast, readOnlyMode as readOnlyStore } from "../stores/session";

  let mediaItems = $state<MediaItemDecrypted[]>([]);
  let isLoading = $state(true);
  let isImporting = $state(false);
  let readOnlyMode = $state(false);

  readOnlyStore.subscribe((v) => (readOnlyMode = v));

  async function loadMedia() {
    isLoading = true;
    try {
      mediaItems = await invoke<MediaItemDecrypted[]>("list_media");
    } catch (e) {
      showToast(`Erro ao carregar midias: ${e}`, "error");
    } finally {
      isLoading = false;
    }
  }

  async function handleImport() {
    if (readOnlyMode) {
      showToast("Modo somente leitura ativo. Importacao bloqueada.", "error");
      return;
    }

    try {
      const selected = await open({
        multiple: true,
        filters: [{
          name: "Image or Video",
          extensions: ["jpg", "jpeg", "png", "gif", "webp", "mp4", "mov", "webm"],
        }],
      });

      if (!selected) return;

      const files = Array.isArray(selected) ? selected : [selected];
      isImporting = true;

      for (const filePath of files) {
        await invoke("import_media_from_path", { filePath });
      }

      showToast("Midia importada com sucesso.", "success");
      await loadMedia();
    } catch (e) {
      showToast(`Erro ao importar: ${e}`, "error");
    } finally {
      isImporting = false;
    }
  }

  async function handleDelete(id: string) {
    if (readOnlyMode) {
      showToast("Modo somente leitura ativo. Exclusao bloqueada.", "error");
      return;
    }
    if (!confirm("Tem certeza que deseja excluir esta midia permanentemente?")) return;
    try {
      await invoke("delete_media", { id });
      showToast("Midia excluida.", "success");
      await loadMedia();
    } catch (e) {
      showToast(`Erro ao excluir: ${e}`, "error");
    }
  }

  function getThumbnail(item: MediaItemDecrypted) {
    return item.thumbnail_base64 ?? item.thumbnail_data ?? null;
  }

  $effect(() => {
    loadMedia();
    invoke<any>("get_settings")
      .then((cfg) => readOnlyStore.set(Boolean(cfg.read_only_mode)))
      .catch(() => {});
  });
</script>

<div class="media-container">
  <div class="header-actions">
    <h2>Cofre de Midias</h2>
    <button class="btn-primary" onclick={handleImport} disabled={isImporting || readOnlyMode}>
      {#if isImporting}Importando...{:else if readOnlyMode}Importacao bloqueada{:else}Importar Midia{/if}
    </button>
  </div>

  {#if isLoading}
    <div class="loading">Carregando...</div>
  {:else if mediaItems.length === 0}
    <div class="empty-state">
      <div class="empty-icon">&#128444;</div>
      <h3>Nenhuma midia no cofre</h3>
      <p>Suas fotos e videos seguros aparecerao aqui.</p>
    </div>
  {:else}
    <div class="media-grid">
      {#each mediaItems as item (item.id)}
        <div class="media-card">
          <div class="media-preview">
            {#if getThumbnail(item)}
              <img src="data:{item.mime_type};base64,{getThumbnail(item)}" alt={item.filename} />
            {:else if item.mime_type.startsWith("video/")}
              <div class="placeholder-icon">&#127909;</div>
            {:else}
              <div class="placeholder-icon">&#128444;</div>
            {/if}
          </div>
          <div class="media-info">
            <span class="media-name" title={item.filename}>{item.filename}</span>
            <button class="btn-ghost btn-small text-danger" onclick={() => handleDelete(item.id)} disabled={readOnlyMode} title="Excluir">
              &#128465;
            </button>
          </div>
        </div>
      {/each}
    </div>
  {/if}
</div>

<style>
  .media-container {
    padding: var(--space-6);
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow-y: auto;
  }

  .header-actions {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--space-6);
  }

  .loading,
  .empty-state {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: var(--color-text-tertiary);
  }

  .empty-icon {
    font-size: 48px;
    margin-bottom: var(--space-4);
    opacity: 0.5;
  }

  .media-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: var(--space-4);
  }

  .media-card {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  .media-preview {
    aspect-ratio: 1;
    background: var(--color-surface-hover);
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
  }

  .media-preview img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .placeholder-icon {
    font-size: 32px;
    opacity: 0.5;
  }

  .media-info {
    padding: var(--space-2) var(--space-3);
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--space-2);
  }

  .media-name {
    font-size: var(--font-size-xs);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .text-danger {
    color: var(--color-danger);
  }
</style>
