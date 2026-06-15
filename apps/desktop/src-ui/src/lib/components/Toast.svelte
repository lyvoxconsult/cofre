<script lang="ts">
  interface Props {
    toast: { text: string; type: "success" | "error" | "info" };
  }

  let { toast }: Props = $props();
</script>

<div class="toast toast-{toast.type} animate-slide-up">
  <span class="toast-icon">
    {toast.type === "success" ? "✓" : toast.type === "error" ? "✕" : "ℹ"}
  </span>
  <span>{toast.text}</span>
</div>

<style>
  .toast {
    position: fixed;
    bottom: var(--space-6);
    right: var(--space-6);
    display: flex;
    align-items: center;
    gap: var(--space-3);
    padding: var(--space-3) var(--space-4);
    border-radius: var(--radius-lg);
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-medium);
    box-shadow: var(--shadow-lg);
    z-index: 100;
    max-width: 400px;
    backdrop-filter: blur(8px);
  }

  .toast-success {
    background: var(--color-success-muted);
    color: var(--color-success);
    border: 1px solid var(--color-success);
  }

  .toast-error {
    background: var(--color-danger-muted);
    color: var(--color-danger);
    border: 1px solid var(--color-danger);
  }

  .toast-info {
    background: var(--color-accent-muted);
    color: var(--color-accent);
    border: 1px solid var(--color-accent);
  }

  .toast-icon {
    font-size: var(--font-size-lg);
    flex-shrink: 0;
  }
</style>
