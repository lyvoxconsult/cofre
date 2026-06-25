import { writable } from "svelte/store";

export const isUnlocked = writable<boolean>(false);
export const currentTab = writable<"vault" | "notes" | "generate" | "settings" | "audit" | "csv_import">("vault");
export const searchQuery = writable<string>("");
export const selectedEntryId = writable<string | null>(null);
export const toastMessage = writable<{ text: string; type: "success" | "error" | "info" } | null>(null);

/** Sinal para focar o campo de busca no cofre (incrementado a cada atalho) */
export const focusSearchSignal = writable<number>(0);

/** Sinal para aplicar tema imediatamente (SettingsPanel → App.svelte) */
export const themeChanged = writable<number>(0);

let toastTimer: ReturnType<typeof setTimeout> | null = null;

export function showToast(text: string, type: "success" | "error" | "info" = "info", duration = 3000) {
  toastMessage.set({ text, type });
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toastMessage.set(null);
  }, duration);
}
