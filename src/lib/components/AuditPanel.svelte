<script lang="ts">
  import { invoke } from "@tauri-apps/api/core";
  import { currentTab, showToast } from "../stores/session";
  import type { VaultEntryDecrypted } from "../types";

  type IssueType = "MISSING" | "REUSED" | "WEAK" | "OLD";

  interface AuditIssue {
    entryId: string;
    serviceName: string;
    login: string;
    issueType: IssueType;
    detail: string;
  }

  let entries = $state<VaultEntryDecrypted[]>([]);
  let loading = $state(true);
  let scannedCount = $state(0);
  let issues = $state<AuditIssue[]>([]);

  $effect(() => {
    runAudit();
  });

  function evaluateStrength(password: string): number {
    let score = 0;
    if (password.length >= 20) score += 30;
    else if (password.length >= 16) score += 25;
    else if (password.length >= 12) score += 20;
    else if (password.length >= 8) score += 10;

    if (/[A-Z]/.test(password)) score += 15;
    if (/[a-z]/.test(password)) score += 15;
    if (/[0-9]/.test(password)) score += 15;
    if (/[^A-Za-z0-9]/.test(password)) score += 20;

    const unique = new Set(password).size;
    score += Math.min(unique * 5, 20);

    return Math.min(Math.max(score, 0), 100);
  }

  async function runAudit() {
    loading = true;
    try {
      entries = await invoke<VaultEntryDecrypted[]>("list_entries");
      scannedCount = entries.length;

      const found: AuditIssue[] = [];
      const passwordToEntries = new Map<string, VaultEntryDecrypted[]>();

      for (const entry of entries) {
        const password = entry.password;

        // Missing
        if (!password) {
          found.push({
            entryId: entry.id,
            serviceName: entry.service_name,
            login: entry.login,
            issueType: "MISSING",
            detail: "Nenhuma senha cadastrada",
          });
          continue;
        }

        // Group for reuse check
        if (!passwordToEntries.has(password)) {
          passwordToEntries.set(password, []);
        }
        passwordToEntries.get(password)!.push(entry);

        // Weak password
        const strength = evaluateStrength(password);
        if (strength < 40) {
          let reason = "";
          if (password.length < 8) {
            reason = `Senha muito curta (${password.length} caracteres)`;
          } else if (/^\d+$/.test(password)) {
            reason = "Apenas números — muito previsível";
          } else if (/^[a-zA-Z]+$/.test(password)) {
            reason = "Apenas letras — sem números ou símbolos";
          } else if (!/[A-Z]/.test(password) && !/\d/.test(password)) {
            reason = "Sem letras maiúsculas ou números";
          } else {
            reason = `Senha fraca (pontuação: ${strength}/100)`;
          }

          found.push({
            entryId: entry.id,
            serviceName: entry.service_name,
            login: entry.login,
            issueType: "WEAK",
            detail: reason,
          });
        }

        // Old password (> 90 days)
        try {
          const updated = new Date(entry.updated_at + "Z");
          const daysSince = Math.floor((Date.now() - updated.getTime()) / (1000 * 60 * 60 * 24));
          if (daysSince >= 90) {
            found.push({
              entryId: entry.id,
              serviceName: entry.service_name,
              login: entry.login,
              issueType: "OLD",
              detail: `Não atualizada há ${daysSince} dias`,
            });
          }
        } catch {
          // ignore date parse errors
        }
      }

      // Reused check
      for (const [pwd, group] of passwordToEntries.entries()) {
        if (group.length > 1) {
          const names = group.map((e) => e.service_name).join(", ");
          for (const e of group) {
            // Avoid duplicate reuse check
            if (!found.some((x) => x.entryId === e.id && x.issueType === "REUSED")) {
              found.push({
                entryId: e.id,
                serviceName: e.service_name,
                login: e.login,
                issueType: "REUSED",
                detail: `Senha reutilizada em: ${names}`,
              });
            }
          }
        }
      }

      // Sort: MISSING > REUSED > WEAK > OLD
      const order: Record<IssueType, number> = { MISSING: 0, REUSED: 1, WEAK: 2, OLD: 3 };
      issues = found.sort((a, b) => order[a.issueType] - order[b.issueType]);
    } catch (e) {
      showToast(String(e), "error");
    } finally {
      loading = false;
    }
  }

  let healthyCount = $derived(scannedCount - issues.filter(i => i.issueType !== "OLD").length); // Old passwords don't fully degrade health score as much, let's include all non-missing/reused/weak
  let healthPct = $derived(scannedCount > 0 ? Math.round((healthyCount / scannedCount) * 100) : 100);

  let healthColor = $derived.by(() => {
    if (healthPct >= 90) return "var(--color-success)";
    if (healthPct >= 70) return "var(--color-warning)";
    return "var(--color-danger)";
  });

  const issueTitles: Record<IssueType, string> = {
    MISSING: "⛔ Senhas Ausentes",
    REUSED: "⚠️ Senhas Reutilizadas",
    WEAK: "🔴 Senhas Fracas",
    OLD: "🕐 Senhas Antigas (mais de 90 dias)",
  };

  const issueLabelColors: Record<IssueType, string> = {
    MISSING: "var(--color-danger)",
    REUSED: "var(--color-warning)",
    WEAK: "var(--color-danger)",
    OLD: "var(--color-text-tertiary)",
  };

  const issueIcons: Record<IssueType, string> = {
    MISSING: "🔒",
    REUSED: "📋",
    WEAK: "⚠️",
    OLD: "🕒",
  };
</script>

<div class="audit-panel animate-fade-in">
  <div class="panel-header">
    <button class="btn-ghost btn-sm btn-back" onclick={() => currentTab.set("settings")}>
      &larr; Voltar para Configurações
    </button>
    <h2>Auditoria de Segurança</h2>
  </div>

  {#if loading}
    <p class="loading-text">Analisando senhas do cofre...</p>
  {:else}
    <div class="audit-content">
      <!-- Health Score Card -->
      <div class="summary-card">
        <div class="summary-info">
          <div class="summary-text">
            <h3>Saúde do cofre</h3>
            <p>{scannedCount} senhas analisadas • {issues.length} problemas encontrados</p>
            <div class="progress-bar-wrapper">
              <div class="progress-bar" style="background: var(--color-border);">
                <div class="progress-fill" style="width: {healthPct}%; background: {healthColor};"></div>
              </div>
              <span class="health-label" style="color: {healthColor};">{healthPct}% saudável</span>
            </div>
          </div>
          <div class="summary-badge" style="color: {healthColor};">
            {healthPct}%
          </div>
        </div>
      </div>

      {#if issues.length === 0}
        <div class="audit-empty animate-scale-in">
          <span class="success-icon">🏆</span>
          <h3>Nenhum problema encontrado!</h3>
          <p>Todas as suas senhas estão com boa saúde e não há reuso ou vulnerabilidades.</p>
        </div>
      {:else}
        <!-- List issues grouped by type -->
        <div class="issues-list">
          {#each ["MISSING", "REUSED", "WEAK", "OLD"] as type}
            {@const typeIssues = issues.filter((i) => i.issueType === type)}
            {#if typeIssues.length > 0}
              <div class="issue-group">
                <h4 style="color: {issueLabelColors[type as IssueType]}">
                  {issueTitles[type as IssueType]} ({typeIssues.length})
                </h4>
                <div class="issue-cards">
                  {#each typeIssues as issue}
                    <div class="issue-card">
                      <div class="issue-icon-wrapper" style="background: {issueLabelColors[type as IssueType]}15; color: {issueLabelColors[type as IssueType]};">
                        {issueIcons[type as IssueType]}
                      </div>
                      <div class="issue-details">
                        <span class="issue-service">{issue.serviceName}</span>
                        {#if issue.login}
                          <span class="issue-login">{issue.login}</span>
                        {/if}
                        <span class="issue-detail-text" style="color: {issueLabelColors[type as IssueType]};">
                          {issue.detail}
                        </span>
                      </div>
                    </div>
                  {/each}
                </div>
              </div>
            {/if}
          {/each}
        </div>
      {/if}
    </div>
  {/if}
</div>

<style>
  .audit-panel {
    flex: 1;
    padding: var(--space-8);
    overflow-y: auto;
    max-width: 720px;
    margin: 0 auto;
    width: 100%;
  }

  .panel-header {
    margin-bottom: var(--space-6);
  }

  .btn-back {
    margin-bottom: var(--space-3);
    padding-left: 0;
  }

  .loading-text {
    color: var(--color-text-tertiary);
    font-size: var(--font-size-sm);
  }

  .audit-content {
    display: flex;
    flex-direction: column;
    gap: var(--space-6);
  }

  .summary-card {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-xl);
    padding: var(--space-6);
  }

  .summary-info {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--space-6);
  }

  .summary-text {
    flex: 1;
  }

  .summary-text h3 {
    margin: 0 0 var(--space-1);
    font-size: var(--font-size-lg);
  }

  .summary-text p {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
    margin: 0 0 var(--space-4);
  }

  .progress-bar-wrapper {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
  }

  .progress-bar {
    height: 8px;
    border-radius: var(--radius-full);
    overflow: hidden;
    width: 100%;
  }

  .progress-fill {
    height: 100%;
    border-radius: var(--radius-full);
    transition: width var(--transition-normal);
  }

  .health-label {
    font-size: var(--font-size-xs);
    font-weight: var(--font-weight-semibold);
  }

  .summary-badge {
    font-size: var(--font-size-4xl);
    font-weight: var(--font-weight-bold);
    letter-spacing: -0.02em;
    flex-shrink: 0;
  }

  .audit-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: var(--space-12) var(--space-6);
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-xl);
    text-align: center;
    gap: var(--space-2);
  }

  .success-icon {
    font-size: var(--font-size-5xl);
    margin-bottom: var(--space-2);
  }

  .audit-empty h3 {
    color: var(--color-success);
    margin: 0;
  }

  .audit-empty p {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    max-width: 400px;
    margin: 0;
    line-height: var(--line-height-normal);
  }

  .issues-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-6);
  }

  .issue-group {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .issue-group h4 {
    margin: 0;
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-semibold);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .issue-cards {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .issue-card {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg);
    padding: var(--space-4);
    display: flex;
    align-items: center;
    gap: var(--space-4);
  }

  .issue-icon-wrapper {
    width: 40px;
    height: 40px;
    border-radius: var(--radius-md);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: var(--font-size-lg);
    flex-shrink: 0;
  }

  .issue-details {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
    flex: 1;
  }

  .issue-service {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
  }

  .issue-login {
    font-size: var(--font-size-xs);
    color: var(--color-text-tertiary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .issue-detail-text {
    font-size: var(--font-size-xs);
    margin-top: 2px;
  }
</style>
