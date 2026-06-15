<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { showToast, currentTab } from "../stores/session";
  import type { PasswordGenResult, PasswordGenConfig } from "../types";
  import StrengthBar from "./StrengthBar.svelte";

  let length = $state(24);
  let useUppercase = $state(true);
  let useLowercase = $state(true);
  let useNumbers = $state(true);
  let useSpecial = $state(true);
  let specialChars = $state("!@#$%^&*()-_=+[]{}|;:,.<>?");
  let excludeAmbiguous = $state(false);

  let result = $state<PasswordGenResult | null>(null);
  let generating = $state(false);
  let copied = $state(false);

  async function generate() {
    generating = true;
    try {
      result = await invoke<PasswordGenResult>("generate_password", {
        config: {
          length,
          use_uppercase: useUppercase,
          use_lowercase: useLowercase,
          use_numbers: useNumbers,
          use_special: useSpecial,
          special_chars: specialChars,
          exclude_ambiguous: excludeAmbiguous,
        },
      });

      // Salva configurações atuais como defaults
      await invoke("update_password_gen_defaults", {
        length,
        use_uppercase: useUppercase,
        use_lowercase: useLowercase,
        use_numbers: useNumbers,
        use_special: useSpecial,
        special_chars: specialChars,
        exclude_ambiguous: excludeAmbiguous,
      }).catch(() => {});
    } catch (e) {
      showToast(String(e), "error");
    } finally {
      generating = false;
    }
  }

  async function copyPassword() {
    if (!result) return;
    try {
      const { writeText, readText, clear } = await import("@tauri-apps/plugin-clipboard-manager");
      const copiedValue = result.password;
      await writeText(copiedValue);
      copied = true;
      showToast("Senha copiada!", "success");

      // Auto-limpeza do clipboard após 30s (só se conteúdo não mudou)
      setTimeout(async () => {
        copied = false;
        try {
          const currentText = await readText();
          if (currentText === copiedValue) {
            await clear();
          }
        } catch {
          // Não limpa se não conseguir ler (segurança)
        }
      }, 30000);
    } catch (e) {
      showToast(String(e), "error");
    }
  }

  function saveToVault() {
    if (!result) return;
    currentTab.set("vault");
  }
</script>

<div class="generate-panel animate-fade-in">
  <div class="panel-header">
    <h2>Gerador de Senhas</h2>
    <p class="panel-desc">Crie senhas fortes e aleatórias com configuracões personalizadas.</p>
  </div>

  <div class="generator-card">
    <!-- Result -->
    <div class="result-area">
      {#if result}
        <div class="password-display">
          <code class="password-text">{result.password}</code>
        </div>
        <StrengthBar entropyBits={result.entropy_bits} label={result.strength_label} />
        <div class="result-actions">
          <button class="btn-primary" onclick={copyPassword}>
            {copied ? "Copiado!" : "Copiar Senha"}
          </button>
          <button class="btn-secondary" onclick={saveToVault}>
            Salvar no Cofre
          </button>
        </div>
      {:else}
        <div class="password-display password-display--empty">
          <p class="placeholder-text">Configure e clique em "Gerar"</p>
        </div>
      {/if}
    </div>

    <!-- Controls -->
    <div class="controls">
      <div class="control-group">
        <label class="control-label" for="gen-length">
          <span>Tamanho: {length}</span>
        </label>
        <input
          type="range"
          id="gen-length"
          min="4"
          max="128"
          bind:value={length}
          class="slider"
        />
        <div class="range-labels">
          <span>4</span>
          <span>128</span>
        </div>
      </div>

      <div class="control-group checkboxes">
        <label class="checkbox-row">
          <input type="checkbox" bind:checked={useUppercase} />
          <span>A-Z (maiúsculas)</span>
        </label>
        <label class="checkbox-row">
          <input type="checkbox" bind:checked={useLowercase} />
          <span>a-z (minúsculas)</span>
        </label>
        <label class="checkbox-row">
          <input type="checkbox" bind:checked={useNumbers} />
          <span>0-9 (números)</span>
        </label>
        <label class="checkbox-row">
          <input type="checkbox" bind:checked={useSpecial} />
          <span>Caracteres especiais</span>
        </label>
        {#if useSpecial}
          <div class="special-chars-input">
            <input type="text" bind:value={specialChars} placeholder="Caracteres especiais" />
          </div>
        {/if}
        <label class="checkbox-row">
          <input type="checkbox" bind:checked={excludeAmbiguous} />
          <span>Excluir ambíguos (il1Lo0O)</span>
        </label>
      </div>

      <button class="btn-primary generate-btn" onclick={generate} disabled={generating}>
        {generating ? "Gerando…" : "Gerar Senha"}
      </button>
    </div>
  </div>
</div>

<style>
  .generate-panel {
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

  .panel-desc {
    color: var(--color-text-tertiary);
    font-size: var(--font-size-sm);
  }

  .generator-card {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-xl);
    padding: var(--space-6);
    display: flex;
    flex-direction: column;
    gap: var(--space-6);
  }

  .result-area {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .password-display {
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg);
    padding: var(--space-4) var(--space-5);
    min-height: 56px;
    display: flex;
    align-items: center;
  }

  .password-display--empty {
    justify-content: center;
  }

  .password-text {
    font-family: var(--font-mono);
    font-size: var(--font-size-lg);
    word-break: break-all;
    letter-spacing: 0.05em;
    user-select: all;
  }

  .placeholder-text {
    color: var(--color-text-placeholder);
    font-size: var(--font-size-sm);
  }

  .result-actions {
    display: flex;
    gap: var(--space-3);
  }

  .result-actions button {
    flex: 1;
  }

  .controls {
    display: flex;
    flex-direction: column;
    gap: var(--space-5);
  }

  .control-group {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .control-label {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-medium);
    color: var(--color-text-secondary);
  }

  .slider {
    -webkit-appearance: none;
    appearance: none;
    height: 6px;
    background: var(--color-border);
    border-radius: var(--radius-full);
    outline: none;
    border: none;
    box-shadow: none;
    padding: 0;
  }

  .slider::-webkit-slider-thumb {
    -webkit-appearance: none;
    width: 18px;
    height: 18px;
    background: var(--color-accent);
    border-radius: 50%;
    cursor: pointer;
    transition: transform var(--transition-fast);
  }

  .slider::-webkit-slider-thumb:hover {
    transform: scale(1.15);
  }

  .range-labels {
    display: flex;
    justify-content: space-between;
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
  }

  .checkboxes {
    gap: var(--space-1);
  }

  .checkbox-row {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    padding: var(--space-2) var(--space-3);
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: background var(--transition-fast);
    font-size: var(--font-size-sm);
  }

  .checkbox-row:hover {
    background: var(--color-surface-hover);
  }

  .checkbox-row input[type="checkbox"] {
    width: 16px;
    height: 16px;
    accent-color: var(--color-accent);
    flex-shrink: 0;
  }

  .special-chars-input {
    padding-left: var(--space-8);
  }

  .generate-btn {
    width: 100%;
    padding: var(--space-3);
    font-size: var(--font-size-base);
    font-weight: var(--font-weight-semibold);
  }
</style>
