<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { showToast } from "../stores/session";
  import type { Category } from "../types";

  interface Props {
    onCancel: () => void;
    onCreated: () => void;
    categories: Category[];
  }

  let { onCancel, onCreated, categories }: Props = $props();

  let serviceName = $state("");
  let login = $state("");
  let password = $state("");
  let url = $state("");
  let notes = $state("");
  let categoryId = $state<string | null>(null);
  let isFavorite = $state(false);
  let saving = $state(false);

  async function handleCreate() {
    if (!serviceName.trim()) {
      showToast("Nome do serviço é obrigatório.", "error");
      return;
    }
    if (!login.trim()) {
      showToast("Login é obrigatório.", "error");
      return;
    }
    if (!password) {
      showToast("Senha é obrigatória.", "error");
      return;
    }

    saving = true;
    try {
      await invoke("create_entry", {
        serviceName: serviceName.trim(),
        login: login.trim(),
        password,
        notes: notes.trim() || null,
        url: url.trim(),
        categoryId,
        isFavorite,
      });
      onCreated();
    } catch (e) {
      showToast(String(e), "error");
    } finally {
      saving = false;
    }
  }

  async function generatePassword() {
    try {
      const result = await invoke<{ password: string }>("generate_password", {
        config: {
          length: 24,
          use_uppercase: true,
          use_lowercase: true,
          use_numbers: true,
          use_special: true,
          special_chars: "!@#$%^&*()-_=+[]{}|;:,.<>?",
          exclude_ambiguous: false,
        },
      });
      password = result.password;
    } catch (e) {
      showToast(String(e), "error");
    }
  }
</script>

<div class="entry-form animate-scale-in">
  <div class="form-header">
    <h2>Novo Registro</h2>
  </div>

  <div class="form-body">
    <label class="field">
      <span class="field-label">Nome do Serviço *</span>
      <input type="text" bind:value={serviceName} placeholder="Ex: GitHub, Google, Banco X" />
    </label>

    <label class="field">
      <span class="field-label">Login / E-mail *</span>
      <input type="text" bind:value={login} placeholder="usuario@email.com" />
    </label>

    <label class="field">
      <span class="field-label">Senha *</span>
      <div class="field-row">
        <input type="password" bind:value={password} placeholder="Senha" />
        <button class="btn-secondary btn-sm" onclick={generatePassword}>Gerar</button>
      </div>
    </label>

    <label class="field">
      <span class="field-label">URL</span>
      <input type="url" bind:value={url} placeholder="https://exemplo.com" />
    </label>

    <label class="field">
      <span class="field-label">Categoria</span>
      <select bind:value={categoryId}>
        <option value={null}>Sem categoria</option>
        {#each categories as cat}
          <option value={cat.id}>{cat.name}</option>
        {/each}
      </select>
    </label>

    <label class="field-checkbox" style="display: flex; align-items: center; gap: var(--space-2); cursor: pointer; padding: var(--space-1) 0;">
      <input type="checkbox" bind:checked={isFavorite} style="width: auto;" />
      <span style="font-size: var(--font-size-sm); color: var(--color-text-primary);">Adicionar aos Favoritos</span>
    </label>

    <label class="field">
      <span class="field-label">Observações</span>
      <textarea bind:value={notes} rows={3} placeholder="Informações adicionais…"></textarea>
    </label>
  </div>

  <div class="form-actions">
    <button class="btn-primary" onclick={handleCreate} disabled={saving}>
      {saving ? "Salvando…" : "Salvar"}
    </button>
    <button class="btn-ghost" onclick={onCancel}>Cancelar</button>
  </div>
</div>

<style>
  .entry-form {
    padding: var(--space-6);
    max-width: 480px;
  }

  .form-header {
    margin-bottom: var(--space-6);
  }

  .form-header h2 {
    font-size: var(--font-size-xl);
  }

  .form-body {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
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

  .field-row {
    display: flex;
    gap: var(--space-2);
  }

  .field-row input {
    flex: 1;
  }

  .form-actions {
    display: flex;
    gap: var(--space-3);
    margin-top: var(--space-6);
    padding-top: var(--space-4);
    border-top: 1px solid var(--color-border);
  }
</style>
