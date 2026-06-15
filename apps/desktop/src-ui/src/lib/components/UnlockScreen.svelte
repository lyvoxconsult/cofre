<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { isUnlocked, showToast } from "../stores/session";
  import RecoverySetup from "./RecoverySetup.svelte";
  import type { RecoveryStatus, QuestionWithOptions } from "../types";

  type Mode = "loading" | "create" | "setup_recovery" | "unlock" | "recovery" | "recovery_reset";

  let mode = $state<Mode>("loading");
  let password = $state("");
  let confirmPassword = $state("");
  let error = $state("");
  let unlocking = $state(false);

  // Recovery state
  let recoveryStatus = $state<RecoveryStatus | null>(null);
  let questions = $state<QuestionWithOptions[]>([]);
  let selectedOptions = $state<number[]>([-1, -1, -1]);
  let newPassword = $state("");
  let newConfirmPassword = $state("");
  let blockTimer = $state<ReturnType<typeof setInterval> | null>(null);
  let blockRemaining = $state(0);

  // Verifica estado inicial
  $effect(() => {
    checkInitialState();
    return () => {
      if (blockTimer) clearInterval(blockTimer);
    };
  });

  async function checkInitialState() {
    try {
      const firstRun = await invoke<boolean>("is_first_run");
      if (firstRun) {
        mode = "create";
      } else {
        mode = "unlock";
        // Verifica status da recuperação
        try {
          recoveryStatus = await invoke<RecoveryStatus>("get_recovery_status");
          if (recoveryStatus.blocked && recoveryStatus.blocked_remaining_secs) {
            startBlockTimer(recoveryStatus.blocked_remaining_secs);
          }
        } catch {
          // Recovery não configurado — ok
        }
      }
    } catch (e) {
      error = String(e);
      mode = "unlock";
    }
  }

  function startBlockTimer(secs: number) {
    blockRemaining = secs;
    blockTimer = setInterval(() => {
      blockRemaining--;
      if (blockRemaining <= 0) {
        if (blockTimer) clearInterval(blockTimer);
        blockRemaining = 0;
      }
    }, 1000);
  }

  // ─── Validação ────────────────────────────

  function validatePassword(): string | null {
    if (password.length < 8) {
      return "A senha mestra deve ter no mínimo 8 caracteres.";
    }
    if (password !== confirmPassword) {
      return "As senhas não coincidem.";
    }
    return null;
  }

  // ─── Desbloqueio normal / Primeira execução ──

  async function handleUnlock() {
    if (!password) {
      error = "Digite a senha mestra.";
      return;
    }

    if (mode === "create") {
      const validationError = validatePassword();
      if (validationError) {
        error = validationError;
        return;
      }
    }

    unlocking = true;
    error = "";

    try {
      await invoke("unlock_vault", { password });

      if (mode === "create") {
        // Primeira execução: configurar recovery depois
        mode = "setup_recovery";
        password = "";
        confirmPassword = "";
      } else {
        isUnlocked.set(true);
        showToast("Cofre desbloqueado", "success");
      }
    } catch (e) {
      error = String(e);
      password = "";
      confirmPassword = "";
    } finally {
      unlocking = false;
    }
  }

  function handleRecoverySetupComplete() {
    isUnlocked.set(true);
    showToast("Cofre criado com sucesso!", "success");
  }

  function handleRecoverySetupSkip() {
    isUnlocked.set(true);
    showToast("Cofre criado! Você pode configurar a recuperação depois em Config.", "info");
  }

  // ─── Fluxo de Recuperação ─────────────────

  async function startRecovery() {
    error = "";
    try {
      const status = await invoke<RecoveryStatus>("get_recovery_status");
      recoveryStatus = status;
      if (status.blocked) {
        return;
      }
      questions = await invoke<QuestionWithOptions[]>("get_recovery_questions");
      selectedOptions = [-1, -1, -1];
      mode = "recovery";
    } catch (e) {
      error = String(e);
    }
  }

  function selectOption(questionIdx: number, optionIdx: number) {
    const newOpts = [...selectedOptions];
    newOpts[questionIdx] = optionIdx;
    selectedOptions = newOpts;
  }

  async function handleVerifyAnswers() {
    // Verifica se todas as 3 foram selecionadas
    if (selectedOptions.some((o) => o === -1)) {
      error = "Selecione uma resposta para cada pergunta.";
      return;
    }

    unlocking = true;
    error = "";

    try {
      // Pega as respostas selecionadas
      const answers = questions.map((q, i) => q.options[selectedOptions[i]]);
      await invoke("verify_recovery_answers", { answers });
      showToast("Respostas corretas! Cofre desbloqueado.", "success");

      // Agora pode resetar a senha
      password = "";
      confirmPassword = "";
      mode = "recovery_reset";
    } catch (e) {
      error = String(e);
      // Atualiza status para verificar bloqueio
      try {
        recoveryStatus = await invoke<RecoveryStatus>("get_recovery_status");
        if (recoveryStatus.blocked && recoveryStatus.blocked_remaining_secs) {
          startBlockTimer(recoveryStatus.blocked_remaining_secs);
        }
      } catch {}
    } finally {
      unlocking = false;
    }
  }

  async function handleResetPassword() {
    if (newPassword.length < 8) {
      error = "A nova senha deve ter no mínimo 8 caracteres.";
      return;
    }
    if (newPassword !== newConfirmPassword) {
      error = "As senhas não coincidem.";
      return;
    }

    unlocking = true;
    error = "";

    try {
      await invoke("reset_master_password", {
        newPassword,
        newAnswers: null, // Mantém recovery existente
      });
      isUnlocked.set(true);
      showToast("Senha redefinida com sucesso!", "success");
    } catch (e) {
      error = String(e);
    } finally {
      unlocking = false;
    }
  }

  function cancelRecovery() {
    mode = "unlock";
    error = "";
    selectedOptions = [-1, -1, -1];
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.key === "Enter") {
      if (mode === "unlock" || mode === "create") handleUnlock();
      else if (mode === "recovery_reset") handleResetPassword();
    }
  }
</script>

<div class="unlock-screen">
  {#if mode === "loading"}
    <div class="unlock-card animate-fade-in">
      <div class="unlock-icon">&#9670;</div>
      <h1 class="unlock-title">lyvox vault</h1>
      <p class="unlock-subtitle">Carregando…</p>
    </div>

  {:else if mode === "setup_recovery"}
    <RecoverySetup
      onComplete={handleRecoverySetupComplete}
      onSkip={handleRecoverySetupSkip}
    />

  {:else if mode === "create"}
    <!-- Primeira execução -->
    <div class="unlock-card animate-scale-in">
      <div class="unlock-icon">&#9670;</div>
      <h1 class="unlock-title">Bem-vindo ao lyvox vault</h1>
      <p class="unlock-subtitle">Crie sua senha mestra para proteger seu cofre</p>

      <div class="unlock-form">
        <input
          type="password"
          placeholder="Senha Mestra (mín. 8 caracteres)"
          bind:value={password}
          onkeydown={handleKeydown}
          disabled={unlocking}
          autocomplete="new-password"
        />
        <input
          type="password"
          placeholder="Confirme a Senha Mestra"
          bind:value={confirmPassword}
          onkeydown={handleKeydown}
          disabled={unlocking}
          autocomplete="new-password"
        />

        {#if error}
          <p class="error-msg">{error}</p>
        {/if}

        <button class="btn-primary unlock-btn" onclick={handleUnlock} disabled={unlocking}>
          {unlocking ? "Criando Cofre…" : "Criar Cofre"}
        </button>
      </div>
      <p class="unlock-hint">Guarde sua senha mestra em um local seguro.</p>
    </div>

  {:else if mode === "recovery"}
    <!-- Responder perguntas de recuperação -->
    <div class="unlock-card animate-scale-in recovery-card">
      <div class="unlock-icon">&#9670;</div>
      <h1 class="unlock-title">Recuperar Acesso</h1>
      <p class="unlock-subtitle">Responda às perguntas de segurança</p>

      <div class="recovery-questions">
        {#each questions as q, qi}
          <div class="recovery-question-block">
            <p class="question-text">{qi + 1}. {q.question}</p>
            <div class="options-list">
              {#each q.options as option, oi}
                <button
                  class="option-btn"
                  class:selected={selectedOptions[qi] === oi}
                  onclick={() => selectOption(qi, oi)}
                >
                  {option}
                </button>
              {/each}
            </div>
          </div>
        {/each}
      </div>

      {#if error}
        <p class="error-msg">{error}</p>
      {/if}

      {#if recoveryStatus?.blocked && blockRemaining > 0}
        <p class="block-msg">Bloqueado. Aguarde {blockRemaining}s.</p>
      {/if}

      <div class="recovery-actions">
        <button
          class="btn-primary unlock-btn"
          onclick={handleVerifyAnswers}
          disabled={unlocking || (recoveryStatus?.blocked ?? false)}
        >
          {unlocking ? "Verificando…" : "Verificar Respostas"}
        </button>
        <button class="btn-ghost" onclick={cancelRecovery}>Voltar</button>
      </div>
    </div>

  {:else if mode === "recovery_reset"}
    <!-- Redefinir senha após recuperação -->
    <div class="unlock-card animate-scale-in">
      <div class="unlock-icon">&#9670;</div>
      <h1 class="unlock-title">Redefinir Senha Mestra</h1>
      <p class="unlock-subtitle">Escolha uma nova senha para seu cofre</p>

      <div class="unlock-form">
        <input
          type="password"
          placeholder="Nova Senha Mestra (mín. 8 caracteres)"
          bind:value={newPassword}
          onkeydown={handleKeydown}
          disabled={unlocking}
          autocomplete="new-password"
        />
        <input
          type="password"
          placeholder="Confirme a Nova Senha"
          bind:value={newConfirmPassword}
          onkeydown={handleKeydown}
          disabled={unlocking}
          autocomplete="new-password"
        />

        {#if error}
          <p class="error-msg">{error}</p>
        {/if}

        <button class="btn-primary unlock-btn" onclick={handleResetPassword} disabled={unlocking}>
          {unlocking ? "Redefinindo…" : "Redefinir Senha"}
        </button>
      </div>
    </div>

  {:else}
    <!-- Login normal -->
    <div class="unlock-card animate-scale-in">
      <div class="unlock-icon">&#9670;</div>
      <h1 class="unlock-title">lyvox vault</h1>
      <p class="unlock-subtitle">Gerenciador seguro de senhas</p>

      <div class="unlock-form">
        <input
          type="password"
          placeholder="Senha Mestra"
          bind:value={password}
          onkeydown={handleKeydown}
          disabled={unlocking}
          autocomplete="current-password"
        />

        {#if error}
          <p class="error-msg">{error}</p>
        {/if}

        <button class="btn-primary unlock-btn" onclick={handleUnlock} disabled={unlocking}>
          {unlocking ? "Desbloqueando…" : "Desbloquear Cofre"}
        </button>

        {#if recoveryStatus?.configured}
          <button
            class="btn-ghost recovery-link"
            onclick={startRecovery}
            disabled={unlocking}
          >
            Esqueci minha senha
          </button>
        {/if}
      </div>
    </div>
  {/if}
</div>

<style>
  .unlock-screen {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100vh;
    background: var(--color-bg);
    padding: var(--space-4);
    overflow-y: auto;
  }

  .unlock-card {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-xl);
    padding: var(--space-12) var(--space-10);
    width: 100%;
    max-width: 440px;
    text-align: center;
    box-shadow: var(--shadow-xl);
  }

  .recovery-card {
    max-width: 520px;
    padding: var(--space-8) var(--space-6);
  }

  .unlock-icon {
    font-size: 3rem;
    color: var(--color-accent);
    margin-bottom: var(--space-4);
    line-height: 1;
  }

  .unlock-title {
    font-size: var(--font-size-2xl);
    font-weight: var(--font-weight-bold);
    margin-bottom: var(--space-2);
    letter-spacing: -0.03em;
  }

  .unlock-subtitle {
    color: var(--color-text-tertiary);
    font-size: var(--font-size-sm);
    margin-bottom: var(--space-8);
  }

  .unlock-form {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .unlock-btn {
    width: 100%;
    padding: var(--space-3);
    font-size: var(--font-size-base);
    font-weight: var(--font-weight-semibold);
  }

  .recovery-link {
    font-size: var(--font-size-sm);
    margin-top: var(--space-2);
    color: var(--color-accent);
  }

  .error-msg {
    color: var(--color-danger);
    font-size: var(--font-size-sm);
    text-align: left;
    padding: var(--space-2) var(--space-3);
    background: var(--color-danger-muted);
    border-radius: var(--radius-md);
  }

  .block-msg {
    color: var(--color-warning);
    font-size: var(--font-size-sm);
    padding: var(--space-2);
    background: var(--color-warning-muted);
    border-radius: var(--radius-md);
  }

  .unlock-hint {
    color: var(--color-text-tertiary);
    font-size: var(--font-size-xs);
    margin-top: var(--space-6);
  }

  /* ─── Recovery questions ────────────────── */
  .recovery-questions {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
    margin-bottom: var(--space-4);
    text-align: left;
  }

  .recovery-question-block {
    padding: var(--space-3);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    background: var(--color-bg);
  }

  .question-text {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-medium);
    margin-bottom: var(--space-2);
  }

  .options-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
  }

  .option-btn {
    width: 100%;
    padding: var(--space-2) var(--space-3);
    font-size: var(--font-size-sm);
    text-align: left;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    cursor: pointer;
    color: var(--color-text-primary);
    transition: all var(--transition-fast);
  }

  .option-btn:hover {
    border-color: var(--color-accent);
    background: var(--color-accent-subtle);
  }

  .option-btn.selected {
    border-color: var(--color-accent);
    background: var(--color-accent-muted);
    font-weight: var(--font-weight-semibold);
  }

  .recovery-actions {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    margin-top: var(--space-4);
  }
</style>
