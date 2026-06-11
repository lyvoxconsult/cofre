/// Limpa o clipboard após um delay configurável
export function scheduleClipboardClear(delayMs: number): void {
  if (delayMs <= 0) return;

  setTimeout(async () => {
    try {
      // Tenta limpar via API Tauri primeiro
      const { writeText } = await import("@tauri-apps/plugin-clipboard-manager");
      await writeText("");
    } catch {
      try {
        // Fallback para Web API
        await navigator.clipboard.writeText("");
      } catch {
        // Clipboard não disponível — ignora
      }
    }
  }, delayMs);
}
