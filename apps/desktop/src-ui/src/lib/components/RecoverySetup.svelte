<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { showToast } from "../stores/session";

  const QUESTIONS = [
    "Qual o nome do seu primeiro animal de estimação?",
    "Qual o nome da sua cidade natal?",
    "Qual o nome do seu melhor amigo de infância?",
    "Qual o nome do seu professor favorito?",
    "Qual o seu prato favorito?",
    "Qual o nome da sua primeira escola?",
    "Qual o sobrenome de solteira da sua mãe?",
    "Qual o nome do seu livro favorito?",
    "Em que ano você se formou no ensino médio?",
    "Qual o modelo do seu primeiro carro?",
  ];

  interface Props {
    onComplete: () => void;
    onSkip: () => void;
  }

  let { onComplete, onSkip }: Props = $props();

  let step = $state<"select" | "answer">("select");
  let selectedIndexes = $state<number[]>([]);
  let answers = $state<string[]>(["", "", ""]);
  let saving = $state(false);
  let error = $state("");

  function toggleQuestion(index: number) {
    if (selectedIndexes.includes(index)) {
      selectedIndexes = selectedIndexes.filter((i) => i !== index);
    } else if (selectedIndexes.length < 3) {
      selectedIndexes = [...selectedIndexes, index];
    }
  }

  function startAnswering() {
    if (selectedIndexes.length !== 3) {
      error = "Selecione exatamente 3 perguntas.";
      return;
    }
    error = "";
    step = "answer";
  }

  async function handleSave() {
    for (let i = 0; i < 3; i++) {
      if (!answers[i].trim()) {
        error = `Responda à pergunta ${i + 1}.`;
        return;
      }
    }

    saving = true;
    error = "";
    try {
      await invoke("setup_recovery", {
        questions: selectedIndexes.map((idx, i) => ({
          question_index: idx,
          answer: answers[i],
        })),
      });
      showToast("Perguntas de recuperação configuradas!", "success");
      onComplete();
    } catch (e) {
      error = String(e);
    } finally {
      saving = false;
    }
  }
</script>

<div class="recovery-setup animate-fade-in">
  <div class="setup-card">
    <h2>Proteção Extra</h2>
    <p class="setup-desc">
      Configure perguntas de segurança para recuperar seu cofre caso esqueça a senha mestra.
      Você pode pular esta etapa, mas não será possível recuperar o acesso.
    </p>
    <div class="security-notice">
      <strong>⚠️ Importante:</strong> Respostas fáceis de adivinhar (como datas conhecidas, nomes comuns ou informações públicas) reduzem a segurança da recuperação.
      Escolha respostas que só você saiba.
    </div>

    {#if step === "select"}
      <div class="question-list">
        <p class="step-label">Selecione 3 perguntas:</p>
        {#each QUESTIONS as question, i}
          <button
            class="question-option"
            class:selected={selectedIndexes.includes(i)}
            class:disabled={!selectedIndexes.includes(i) && selectedIndexes.length >= 3}
            onclick={() => toggleQuestion(i)}
          >
            <span class="q-number">{i + 1}</span>
            <span class="q-text">{question}</span>
            {#if selectedIndexes.includes(i)}
              <span class="q-check">✓</span>
            {/if}
          </button>
        {/each}
      </div>

      <div class="setup-actions">
        <button class="btn-primary" onclick={startAnswering} disabled={selectedIndexes.length !== 3}>
          Continuar
        </button>
        <button class="btn-ghost" onclick={onSkip}>Pular</button>
      </div>
    {:else}
      <div class="answer-list">
        <p class="step-label">Responda às perguntas selecionadas:</p>
        {#each selectedIndexes as qIndex, i}
          <label class="answer-field">
            <span class="field-label">Pergunta {i + 1}: {QUESTIONS[qIndex]}</span>
            <input
              type="text"
              bind:value={answers[i]}
              placeholder="Sua resposta"
              disabled={saving}
            />
          </label>
        {/each}
      </div>

      {#if error}
        <p class="error-msg">{error}</p>
      {/if}

      <div class="setup-actions">
        <button class="btn-primary" onclick={handleSave} disabled={saving}>
          {saving ? "Salvando…" : "Salvar Respostas"}
        </button>
        <button class="btn-ghost" onclick={onSkip} disabled={saving}>Pular</button>
      </div>
    {/if}
  </div>
</div>

<style>
  .recovery-setup {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: var(--space-4);
    min-height: 60vh;
  }

  .setup-card {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-xl);
    padding: var(--space-8) var(--space-6);
    width: 100%;
    max-width: 520px;
    box-shadow: var(--shadow-lg);
  }

  .setup-card h2 {
    font-size: var(--font-size-xl);
    margin-bottom: var(--space-2);
  }

  .setup-desc {
    color: var(--color-text-tertiary);
    font-size: var(--font-size-sm);
    margin-bottom: var(--space-4);
    line-height: var(--line-height-normal);
  }

  .security-notice {
    font-size: var(--font-size-xs);
    color: var(--color-warning);
    background: var(--color-warning-muted);
    border: 1px solid var(--color-warning);
    border-radius: var(--radius-md);
    padding: var(--space-3);
    margin-bottom: var(--space-6);
    line-height: var(--line-height-normal);
  }

  .step-label {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-semibold);
    margin-bottom: var(--space-3);
    color: var(--color-text-secondary);
  }

  .question-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    margin-bottom: var(--space-6);
  }

  .question-option {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    width: 100%;
    padding: var(--space-3);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    background: var(--color-bg);
    cursor: pointer;
    transition: all var(--transition-fast);
    text-align: left;
    color: var(--color-text-primary);
  }

  .question-option:hover:not(.disabled) {
    border-color: var(--color-accent);
    background: var(--color-accent-subtle);
  }

  .question-option.selected {
    border-color: var(--color-accent);
    background: var(--color-accent-muted);
  }

  .question-option.disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  .q-number {
    font-size: var(--font-size-xs);
    font-weight: var(--font-weight-bold);
    color: var(--color-text-tertiary);
    width: 20px;
    text-align: center;
  }

  .q-text {
    flex: 1;
    font-size: var(--font-size-sm);
  }

  .q-check {
    color: var(--color-accent);
    font-weight: var(--font-weight-bold);
  }

  .answer-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
    margin-bottom: var(--space-6);
  }

  .answer-field {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
  }

  .field-label {
    font-size: var(--font-size-xs);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-tertiary);
    margin-bottom: var(--space-1);
  }

  .setup-actions {
    display: flex;
    gap: var(--space-3);
  }

  .error-msg {
    color: var(--color-danger);
    font-size: var(--font-size-sm);
    margin-bottom: var(--space-3);
    padding: var(--space-2) var(--space-3);
    background: var(--color-danger-muted);
    border-radius: var(--radius-md);
  }
</style>
