<script>
  import { db, fetchShop, saveShopClosure } from '$lib/data.svelte.js';
  import { notify } from '$lib/toast.svelte.js';
  import { SHOP_STATE_LABELS, MAX_NOTE, closedToday, openState } from '$lib/shop-closure.js';
  import Sheet from './Sheet.svelte';

  /**
   * The shop's open/closed switch in the top bar (Connect FR-111, FR-112), the same one the
   * tablets have. The shop is open by default and follows the opening hours; Closed today shuts
   * online orders on the website and Messenger until the end of the day. Hidden until the server
   * answers.
   */
  let now = $state(Date.now());
  let open = $state(false);
  let closed = $state(false);
  let note = $state('');
  let saving = $state(false);
  let error = $state('');

  const status = $derived(db.shop ? openState(db.shop, now) : null);
  const isClosedToday = $derived(status === 'closed-today');
  const changed = $derived(closed !== isClosedToday || (closed && note.trim() !== (db.shop?.note ?? '')));

  const EXPLAIN = {
    open: 'Open, and taking online orders on the website and Messenger.',
    'closed-today': 'Closed for today. The website and Messenger say so and take no online orders.',
    'outside-hours': 'Outside the opening hours, so the website already shows the shop as closed.',
  };

  // Re-check every minute so the label changes at opening and closing time, and pick up a
  // switch flipped on a tablet.
  $effect(() => {
    const tick = () => {
      now = Date.now();
      if (document.visibilityState === 'visible') fetchShop();
    };
    const timer = setInterval(tick, 60_000);
    document.addEventListener('visibilitychange', tick);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', tick);
    };
  });

  function start() {
    now = Date.now();
    closed = closedToday(db.shop, now);
    note = closed ? db.shop.note : '';
    error = '';
    open = true;
  }

  async function save(event) {
    event.preventDefault();
    saving = true;
    error = '';
    try {
      await saveShopClosure(closed, note);
      now = Date.now();
      open = false;
      notify(closed
        ? 'Closed for today. The website and Messenger say so within a minute. It opens again by itself tomorrow.'
        : 'The shop is open again. Online orders follow the opening hours.');
    } catch (e) {
      error = e?.message || "Couldn't save. Try again.";
    } finally {
      saving = false;
    }
  }
</script>

{#if status}
  <button
    type="button"
    class="pill {status}"
    onclick={start}
    aria-haspopup="dialog"
    aria-label={`Shop status: ${SHOP_STATE_LABELS[status]}. Change`}
    title="Open or close the shop for today"
  >
    <span class="pill-dot" aria-hidden="true"></span>{SHOP_STATE_LABELS[status]}
  </button>

  <Sheet bind:open title="Shop status" width="460px">
    <form id="shop-switch" class="stack" onsubmit={save} novalidate>
      <p class="now"><span class="tag {status === 'open' ? 'tag-ok' : status === 'closed-today' ? 'tag-danger' : ''}">{SHOP_STATE_LABELS[status]}</span> {EXPLAIN[status]}</p>
      <label class="row">
        <input type="checkbox" class="switch" bind:checked={closed} />
        <span><strong>Closed today</strong><span class="muted small">It opens again by itself tomorrow.</span></span>
      </label>
      {#if closed}
        <label class="field">
          <span class="label">Note for customers (optional)</span>
          <input class="input" type="text" maxlength={MAX_NOTE} bind:value={note} placeholder="e.g. Back tomorrow at 1 PM" />
          <span class="hint">Shown on the website beside "Closed today".</span>
        </label>
      {/if}
      {#if error}<p class="notice notice-error" role="alert">{error}</p>{/if}
    </form>
    {#snippet footer()}
      <button type="button" class="btn" onclick={() => (open = false)}>Cancel</button>
      <button type="submit" form="shop-switch" class="btn btn-primary" disabled={saving || !changed}>{saving ? 'Saving…' : 'Save'}</button>
    {/snippet}
  </Sheet>
{/if}

<style>
  .pill { display: inline-flex; align-items: center; gap: var(--s-2); min-height: 32px; padding: 0 var(--s-3); margin-right: var(--s-2); border: 1px solid var(--line-strong); border-radius: var(--r-btn); background: var(--surface); color: var(--ink); font: inherit; font-size: var(--t-label); font-weight: 600; white-space: nowrap; cursor: pointer; transition: background-color 150ms var(--ease-out); }
  @media (hover: hover) and (pointer: fine) { .pill:hover { background: var(--hover-fill); } }
  .pill:focus-visible { outline: 2px solid var(--action); outline-offset: 2px; }
  .pill-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--muted); }
  .pill.open { border-color: color-mix(in srgb, var(--action) 45%, transparent); color: var(--action-ink); }
  .pill.open .pill-dot { background: var(--action); }
  .pill.closed-today { border-color: var(--danger); background: var(--danger-soft); color: var(--danger); }
  .pill.closed-today .pill-dot { background: var(--danger); }
  .now { line-height: 1.5; }
  .now .tag { margin-right: var(--s-1); }
  .row { display: flex; align-items: center; gap: var(--s-4); cursor: pointer; }
  .row > span { display: flex; flex-direction: column; gap: 2px; }
  @media (max-width: 640px) {
    .pill { padding: 0 var(--s-2); margin-right: 0; }
  }
</style>
