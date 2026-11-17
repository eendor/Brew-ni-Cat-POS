<script>
  import { toasts, dismiss } from '$lib/toast.svelte.js';

  async function undo(t) {
    dismiss(t.id);
    await t.undo();
  }
</script>

<div class="toasts" role="status" aria-live="polite">
  {#each toasts as t (t.id)}
    <div class="toast" class:error={t.tone === 'error'}>
      <span class="msg">{t.message}</span>
      {#if t.undo}<button type="button" class="act" onclick={() => undo(t)}>Undo</button>{/if}
      <button type="button" class="act quiet" onclick={() => dismiss(t.id)}>OK</button>
    </div>
  {/each}
</div>

<style>
  .toasts { position: fixed; z-index: 20; left: 50%; bottom: max(var(--s-5), env(safe-area-inset-bottom)); transform: translateX(-50%); display: flex; flex-direction: column; gap: var(--s-2); width: min(520px, calc(100vw - 32px)); pointer-events: none; }
  .toast { pointer-events: auto; display: flex; align-items: center; gap: var(--s-1); min-height: 52px; padding: var(--s-1) var(--s-1) var(--s-1) var(--s-5); border-radius: var(--r-md); background: var(--ink); color: var(--g-main); animation: up 240ms var(--ease-out); }
  .toast.error { background: var(--danger); color: var(--surface); }
  .msg { flex: 1; font-size: var(--t-small); }
  .act { min-height: 44px; padding: 0 var(--s-4); border: 0; border-radius: var(--r-sm); background: transparent; color: inherit; font-weight: 650; cursor: pointer; }
  .act.quiet { font-weight: 500; opacity: .8; }
  @media (hover: hover) and (pointer: fine) { .act:hover { background: color-mix(in srgb, currentColor 14%, transparent); } }
  @keyframes up { from { opacity: 0; transform: translateY(12px); } }
</style>
