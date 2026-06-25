<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { isUnlocked, currentTab, toastMessage, showToast, focusSearchSignal, themeChanged } from "./lib/stores/session";
  import UnlockScreen from "./lib/components/UnlockScreen.svelte";
  import VaultView from "./lib/components/VaultView.svelte";
  import SecureNotes from "./lib/components/SecureNotes.svelte";
  import GeneratePanel from "./lib/components/GeneratePanel.svelte";
  import SettingsPanel from "./lib/components/SettingsPanel.svelte";
  import AuditPanel from "./lib/components/AuditPanel.svelte";
  import CsvImportPanel from "./lib/components/CsvImportPanel.svelte";
  import MediaVault from "./lib/components/MediaVault.svelte";
  import Toast from "./lib/components/Toast.svelte";
  import type { ViewTab } from "./lib/types";

  let unlocked = $state(false);
  let tab = $state<ViewTab>("vault");
  let toast = $state<{ text: string; type: "success" | "error" | "info" } | null>(null);
  let lockTimer: ReturnType<typeof setInterval> | null = null;
  let themeTick = $state(0);

  // Reativa stores
  isUnlocked.subscribe((v) => (unlocked = v));
  currentTab.subscribe((v) => (tab = v));
  toastMessage.subscribe((v) => (toast = v));
  themeChanged.subscribe((v) => (themeTick = v));

  // Aplica o tema lendo da config do backend
  async function applyTheme() {
    try {
      const settings = await invoke<{ theme: string }>("get_settings");
      applyThemeClass(settings.theme);
    } catch {
      // fallback: mantém o atual
    }
  }

  function applyThemeClass(theme: string) {
    const isLight =
      theme === "light" ||
      (theme === "system" && window.matchMedia("(prefers-color-scheme: light)").matches);
    document.documentElement.classList.toggle("light", isLight);
  }

  // Aplica tema na inicialização (antes do unlock, para a UnlockScreen refletir o tema)
  $effect(() => {
    applyTheme();
  });

  // Reaplica quando themeChanged é disparado (settings salvou)
  $effect(() => {
    if (themeTick > 0) {
      applyTheme();
    }
  });

  // Ouve mudanças no sistema quando tema é "system"
  let prefersLight: MediaQueryList | null = null;
  let prefersDark: MediaQueryList | null = null;
  $effect(() => {
    prefersLight = window.matchMedia("(prefers-color-scheme: light)");
    prefersDark = window.matchMedia("(prefers-color-scheme: dark)");
    const handler = () => applyTheme();
    prefersLight.addEventListener("change", handler);
    prefersDark.addEventListener("change", handler);
    return () => {
      prefersLight?.removeEventListener("change", handler);
      prefersDark?.removeEventListener("change", handler);
    };
  });

  // Auto-lock check a cada 30s
  $effect(() => {
    if (unlocked) {
      lockTimer = setInterval(async () => {
        try {
          const locked = await invoke<boolean>("check_auto_lock");
          if (locked) {
            isUnlocked.set(false);
            showToast("Cofre bloqueado por inatividade", "info");
          }
        } catch {
          // ignora
        }
      }, 30000);

      return () => {
        if (lockTimer) clearInterval(lockTimer);
      };
    }
  });

  // Registra atividade em cliques
  function handleActivity() {
    if (unlocked) {
      invoke("record_activity").catch(() => {});
    }
  }

  async function handleLock() {
    try {
      await invoke("lock_vault");
      isUnlocked.set(false);
      showToast("Cofre bloqueado", "info");
    } catch (e) {
      showToast(String(e), "error");
    }
  }

  // Atalhos de teclado
  function handleKeydown(e: KeyboardEvent) {
    const isMod = e.ctrlKey || e.metaKey;

    if (isMod && e.key === "l") {
      e.preventDefault();
      if (unlocked) handleLock();
    }

    // Ctrl+1 = Cofre, Ctrl+2 = Notas, Ctrl+3 = Gerador, Ctrl+4 = Config
    if (isMod && e.key === "1") {
      e.preventDefault();
      if (unlocked) currentTab.set("vault");
    }
    if (isMod && e.key === "2") {
      e.preventDefault();
      if (unlocked) currentTab.set("notes");
    }
    if (isMod && e.key === "3") {
      e.preventDefault();
      if (unlocked) currentTab.set("generate");
    }
    if (isMod && e.key === "4") {
      e.preventDefault();
      if (unlocked) currentTab.set("settings");
    }
    if (isMod && e.key === "f") {
      e.preventDefault();
      if (unlocked) {
        currentTab.set("vault");
        focusSearchSignal.update((n) => n + 1);
      }
    }
  }
</script>

<svelte:window onclick={handleActivity} onkeydown={handleKeydown} />

<div class="app">
  {#if !unlocked}
    <UnlockScreen />
  {:else}
    <div class="layout">
      <header class="header">
        <div class="header-left">
          <h1 class="logo">
            <span class="logo-icon">&#9670;</span>
            lyvox vault
          </h1>
        </div>

        <nav class="nav-tabs">
          {#each ["vault", "media", "notes", "generate", "settings"] as viewTab}
            <button
              class="nav-tab"
              class:active={tab === viewTab}
              onclick={() => currentTab.set(viewTab as ViewTab)}
            >
              {viewTab === "vault" ? "Cofre" : viewTab === "media" ? "Mídias" : viewTab === "notes" ? "Notas" : viewTab === "generate" ? "Gerador" : "Config"}
            </button>
          {/each}
        </nav>

        <div class="header-right">
          <button class="btn-ghost btn-icon" title="Bloquear cofre" onclick={handleLock}>
            &#128274;
          </button>
        </div>
      </header>

      <main class="main-content">
        {#if tab === "vault"}
          <VaultView />
        {:else if tab === "media"}
          <MediaVault />
        {:else if tab === "notes"}
          <SecureNotes />
        {:else if tab === "generate"}
          <GeneratePanel />
        {:else if tab === "settings"}
          <SettingsPanel />
        {:else if tab === "audit"}
          <AuditPanel />
        {:else if tab === "csv_import"}
          <CsvImportPanel />
        {/if}
      </main>
    </div>
  {/if}

  {#if toast}
    <Toast {toast} />
  {/if}
</div>

<style>
  .app {
    height: 100vh;
    display: flex;
    flex-direction: column;
    background: var(--color-bg);
  }

  .layout {
    display: flex;
    flex-direction: column;
    height: 100vh;
    overflow: hidden;
  }

  .header {
    display: flex;
    align-items: center;
    padding: 0 var(--space-6);
    height: 52px;
    border-bottom: 1px solid var(--color-border);
    background: var(--color-surface);
    flex-shrink: 0;
    gap: var(--space-4);
  }

  .header-left {
    flex-shrink: 0;
  }

  .logo {
    font-size: var(--font-size-lg);
    font-weight: var(--font-weight-semibold);
    display: flex;
    align-items: center;
    gap: var(--space-2);
    letter-spacing: -0.02em;
  }

  .logo-icon {
    color: var(--color-accent);
    font-size: 0.8em;
  }

  .nav-tabs {
    display: flex;
    gap: var(--space-1);
    margin-left: var(--space-8);
    flex: 1;
  }

  .nav-tab {
    padding: var(--space-2) var(--space-4);
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-medium);
    color: var(--color-text-tertiary);
    background: transparent;
    border: none;
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all var(--transition-fast);
  }

  .nav-tab:hover {
    color: var(--color-text-primary);
    background: var(--color-surface-hover);
  }

  .nav-tab.active {
    color: var(--color-accent);
    background: var(--color-accent-subtle);
  }

  .header-right {
    display: flex;
    gap: var(--space-2);
    align-items: center;
  }

  .main-content {
    flex: 1;
    overflow: hidden;
    display: flex;
  }
</style>
