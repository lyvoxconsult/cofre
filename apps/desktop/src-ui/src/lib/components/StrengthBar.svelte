<script lang="ts">
  let { entropyBits = 0, label = "" }: { entropyBits: number; label: string } = $props();

  let segments = $derived([
    { threshold: 40, class: "very-weak", active: entropyBits >= 0 },
    { threshold: 60, class: "weak", active: entropyBits >= 40 },
    { threshold: 80, class: "medium", active: entropyBits >= 60 },
    { threshold: 100, class: "strong", active: entropyBits >= 80 },
    { threshold: Infinity, class: "very-strong", active: entropyBits >= 100 },
  ]);

  let percent = $derived(Math.min(entropyBits / 1.28, 100));
</script>

<div class="strength-bar">
  <div class="bar-track">
    {#each segments as seg}
      <div class="bar-segment {seg.class}" class:active={seg.active}></div>
    {/each}
    <div class="bar-fill" style="width: {percent}%"></div>
  </div>
  <span class="strength-label {segments.find((s) => s.active)?.class ?? ''}">
    {label} &middot; {entropyBits.toFixed(0)} bits
  </span>
</div>

<style>
  .strength-bar {
    display: flex;
    align-items: center;
    gap: var(--space-3);
  }

  .bar-track {
    display: flex;
    gap: 3px;
    flex: 1;
    height: 6px;
    background: var(--color-border);
    border-radius: var(--radius-full);
    overflow: hidden;
    position: relative;
  }

  .bar-segment {
    flex: 1;
    background: var(--color-surface-elevated);
    transition: background var(--transition-normal);
  }

  .bar-fill {
    position: absolute;
    left: 0;
    top: 0;
    height: 100%;
    background: var(--color-accent);
    border-radius: var(--radius-full);
    transition: width 0.3s ease;
    opacity: 0.6;
  }

  .bar-segment.very-weak.active {
    background: var(--color-strength-very-weak);
  }
  .bar-segment.weak.active {
    background: var(--color-strength-weak);
  }
  .bar-segment.medium.active {
    background: var(--color-strength-medium);
  }
  .bar-segment.strong.active {
    background: var(--color-strength-strong);
  }
  .bar-segment.very-strong.active {
    background: var(--color-strength-very-strong);
  }

  .strength-label {
    font-size: var(--font-size-xs);
    font-weight: var(--font-weight-medium);
    white-space: nowrap;
    color: var(--color-text-tertiary);
  }

  .strength-label.very-weak {
    color: var(--color-strength-very-weak);
  }
  .strength-label.weak {
    color: var(--color-strength-weak);
  }
  .strength-label.medium {
    color: var(--color-strength-medium);
  }
  .strength-label.strong {
    color: var(--color-strength-strong);
  }
  .strength-label.very-strong {
    color: var(--color-strength-very-strong);
  }
</style>
