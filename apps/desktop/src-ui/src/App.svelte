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

  const shellTabs: Array<{ id: ViewTab; label: string; icon: string }> = [
    { id: "vault", label: "Cofre", icon: "⌘" },
    { id: "media", label: "Mídia", icon: "◈" },
    { id: "notes", label: "Notas", icon: "✦" },
    { id: "generate", label: "Gerador", icon: "⚿" },
    { id: "settings", label: "Configurações", icon: "⚙" },
  ];

  isUnlocked.subscribe((v) => (unlocked = v));
  currentTab.subscribe((v) => (tab = v));
  toastMessage.subscribe((v) => (toast = v));
  themeChanged.subscribe((v) => (themeTick = v));

  async function applyTheme() {
    try {
      const settings = await invoke<{ theme: string }>("get_settings");
      applyThemeClass(settings.theme);
    } catch {
      applyThemeClass("dark");
    }
  }

  function applyThemeClass(theme: string) {
    const isLight =
      theme === "light" ||
      (theme === "system" && window.matchMedia("(prefers-color-scheme: light)").matches);
    document.documentElement.classList.toggle("light", isLight);
  }

  $effect(() => {
    applyTheme();
  });

  $effect(() => {
    if (themeTick > 0) applyTheme();
  });

  $effect(() => {
    const prefersLight = window.matchMedia("(prefers-color-scheme: light)");
    const prefersDark = window.matchMedia("(prefers-color-scheme: dark)");
    const handler = () => applyTheme();
    prefersLight.addEventListener("change", handler);
    prefersDark.addEventListener("change", handler);
    return () => {
      prefersLight.removeEventListener("change", handler);
      prefersDark.removeEventListener("change", handler);
    };
  });

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
          // keep UI responsive if backend is busy
        }
      }, 30000);

      return () => {
        if (lockTimer) clearInterval(lockTimer);
      };
    }
  });

  function handleActivity() {
    if (unlocked) invoke("record_activity").catch(() => {});
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

  function handleKeydown(e: KeyboardEvent) {
    const isMod = e.ctrlKey || e.metaKey;
    if (isMod && e.key === "l") {
      e.preventDefault();
      if (unlocked) handleLock();
    }
    if (isMod && e.key === "1") {
      e.preventDefault();
      if (unlocked) currentTab.set("vault");
    }
    if (isMod && e.key === "2") {
      e.preventDefault();
      if (unlocked) currentTab.set("media");
    }
    if (isMod && e.key === "3") {
      e.preventDefault();
      if (unlocked) currentTab.set("notes");
    }
    if (isMod && e.key === "4") {
      e.preventDefault();
      if (unlocked) currentTab.set("generate");
    }
    if (isMod && e.key === "5") {
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
      <aside class="sidebar">
        <div class="mac-controls"><span></span><span></span><span></span></div>
        <div class="brand">
          <span class="brand-mark">⌘</span>
          <div>
            <strong>Lyvox Vault</strong>
            <small>Cofre local premium</small>
          </div>
        </div>

        <nav class="side-nav">
          {#each shellTabs as item}
            <button class:active={tab === item.id} onclick={() => currentTab.set(item.id)}>
              <span>{item.icon}</span>
              {item.label}
            </button>
          {/each}
        </nav>

        <section class="security-card">
          <b>Segurança de ponta</b>
          <p>Argon2id, AES-256-GCM, backup local e zero cloud.</p>
        </section>

        <div class="profile-row">
          <span>LV</span>
          <div>
            <strong>Vault local</strong>
            <small>Modo privado ativo</small>
          </div>
          <button title="Bloquear cofre" onclick={handleLock}>⌘</button>
        </div>
      </aside>

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
    background: #070912;
  }

  .layout {
    display: flex;
    flex-direction: row;
    height: 100vh;
    overflow: hidden;
    background:
      radial-gradient(circle at 24% 8%, rgba(116, 72, 255, 0.16), transparent 32%),
      radial-gradient(circle at 80% 90%, rgba(48, 159, 255, 0.12), transparent 34%),
      #070912;
  }

  .sidebar {
    width: 270px;
    flex: 0 0 270px;
    display: flex;
    flex-direction: column;
    gap: var(--space-5);
    padding: var(--space-5);
    border-right: 1px solid rgba(255, 255, 255, 0.08);
    background: linear-gradient(180deg, rgba(16, 19, 34, 0.94), rgba(8, 10, 20, 0.98));
    box-shadow: 18px 0 60px rgba(0, 0, 0, 0.28);
  }

  .mac-controls {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .mac-controls span {
    width: 12px;
    height: 12px;
    border-radius: 50%;
  }

  .mac-controls span:nth-child(1) { background: #ff5f57; }
  .mac-controls span:nth-child(2) { background: #ffbd2e; }
  .mac-controls span:nth-child(3) { background: #28c840; }

  .brand {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    padding: var(--space-3) 0 var(--space-4);
  }

  .brand-mark {
    width: 46px;
    height: 46px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-weight: 800;
    background: linear-gradient(135deg, #8b5cf6, #06b6d4);
    box-shadow: 0 0 28px rgba(139, 92, 246, 0.35);
  }

  .brand strong,
  .profile-row strong {
    display: block;
    font-size: 1.05rem;
  }

  .brand small,
  .profile-row small,
  .security-card p {
    color: var(--color-text-secondary);
    font-size: var(--font-size-xs);
  }

  .side-nav {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .side-nav button {
    width: 100%;
    justify-content: flex-start;
    position: relative;
    padding: 12px 14px;
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    background: transparent;
    border: 1px solid transparent;
    border-radius: 12px;
  }

  .side-nav button:hover {
    color: var(--color-text-primary);
    background: rgba(255, 255, 255, 0.05);
  }

  .side-nav button.active {
    color: #fff;
    background: linear-gradient(90deg, rgba(124, 58, 237, 0.34), rgba(37, 99, 235, 0.12));
    border-color: rgba(168, 85, 247, 0.35);
    box-shadow: inset 4px 0 0 #a855f7;
  }

  .side-nav span {
    width: 24px;
    color: #a78bfa;
  }

  .security-card {
    margin-top: auto;
    padding: var(--space-4);
    border-radius: 16px;
    border: 1px solid rgba(255, 255, 255, 0.08);
    background:
      linear-gradient(135deg, rgba(124, 58, 237, 0.22), rgba(14, 165, 233, 0.08)),
      rgba(255, 255, 255, 0.04);
  }

  .profile-row {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    padding-top: var(--space-3);
    border-top: 1px solid rgba(255, 255, 255, 0.07);
  }

  .profile-row > span {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    display: grid;
    place-items: center;
    background: #1f2937;
    color: #bfdbfe;
    font-size: var(--font-size-xs);
    font-weight: 700;
  }

  .profile-row div {
    flex: 1;
    min-width: 0;
  }

  .profile-row button {
    width: 34px;
    height: 34px;
    padding: 0;
    color: #c4b5fd;
    background: rgba(255, 255, 255, 0.06);
  }

  .main-content {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    display: flex;
    padding: var(--space-5);
  }
</style>
