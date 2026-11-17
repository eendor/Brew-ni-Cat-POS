<script>
  import { Trash2 } from '@lucide/svelte';
  import { supabase } from '$lib/supabase.js';
  import { db, fetchExpenses, run } from '$lib/data.svelte.js';
  import { notify } from '$lib/toast.svelte.js';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import LoadError from '$lib/components/LoadError.svelte';
  import EmptyState from '$lib/components/EmptyState.svelte';
  import { formatPrice, localDateKey } from '$lib/reports.js';

  const today = localDateKey(Date.now());
  const todays = $derived(db.expenses.filter((e) => localDateKey(e.timestamp) === today));
  const todayTotal = $derived(todays.reduce((s, e) => s + e.amount, 0));
  const allTotal = $derived(db.expenses.reduce((s, e) => s + e.amount, 0));
  const people = $derived(new Set(db.expenses.map((e) => e.recorded_by).filter(Boolean)).size);

  const days = $derived.by(() => {
    const out = [];
    for (const e of db.expenses) {
      const key = localDateKey(e.timestamp);
      if (out.at(-1)?.key !== key) out.push({ key, items: [], total: 0 });
      out.at(-1).items.push(e);
      out.at(-1).total += e.amount;
    }
    return out;
  });

  const dayName = (key) =>
    key === today ? 'Today' : new Date(key + 'T00:00').toLocaleDateString('en-PH', { weekday: 'long', month: 'long', day: 'numeric' });
  const time = (ts) => new Date(ts).toLocaleTimeString('en-PH', { hour: 'numeric', minute: '2-digit' });

  // Deleting sets is_deleted rather than removing the row: a tablet that still has the expense
  // would upload it again, and the tombstone tells it the expense is gone.
  async function setDeleted(id, deleted) {
    await run(supabase.from('expenses').update({ is_deleted: deleted }).eq('id', id));
    await fetchExpenses();
  }

  async function remove(e) {
    try {
      await setDeleted(e.id, true);
      notify(`Deleted "${e.description}" from every tablet.`, {
        undo: () => setDeleted(e.id, false).catch((err) => notify(`Couldn't undo: ${err.message}`, { tone: 'error' })),
      });
    } catch (err) {
      notify(`Couldn't delete the expense: ${err.message || 'try again'}.`, { tone: 'error' });
    }
  }
</script>

<PageHeader title="Expenses" subtitle="What the cashiers paid out of the drawer, recorded on the tablets." />

<LoadError errors={db.errors.expenses ? [db.errors.expenses] : []} />

<div class="layout">
  <aside class="summary">
    <div>
      <p class="label">Today</p>
      <p class="big num">{formatPrice(todayTotal)}</p>
      <p class="muted">{todays.length} {todays.length === 1 ? 'expense' : 'expenses'}</p>
    </div>
    <dl class="ledger">
      <dt>All time</dt><dd>{formatPrice(allTotal)}</dd>
      <dt>Expenses recorded</dt><dd>{db.expenses.length}</dd>
      <dt>Recorded by</dt><dd>{people} {people === 1 ? 'person' : 'people'}</dd>
    </dl>
    <p class="label measure">Expenses are added on the tablets. Deleting one here removes it from every tablet.</p>
  </aside>

  <section class="stack-l" aria-label="Expenses by day">
    {#if !db.loaded}
      <div class="panel pad stack">{#each Array(5) as _}<div class="skeleton" style="height: 20px"></div>{/each}</div>
    {:else if days.length === 0}
      <div class="panel"><EmptyState title="No expenses yet" text="When a cashier records an expense on a tablet, it shows up here." /></div>
    {:else}
      {#each days as d (d.key)}
        <section class="stack" aria-labelledby={`d-${d.key}`}>
          <div class="head"><h2 id={`d-${d.key}`} class="dh">{dayName(d.key)}</h2><p class="num muted">{formatPrice(d.total)}</p></div>
          <ul class="list panel">
            {#each d.items as e (e.id)}
              <li>
                <span class="what">{e.description}<span class="sub">{e.recorded_by || '—'} · {time(e.timestamp)}</span></span>
                <span class="end amt">{formatPrice(e.amount)}</span>
                <button type="button" class="btn btn-icon btn-sm del" aria-label="Delete {e.description}" title="Delete" onclick={() => remove(e)}><Trash2 aria-hidden="true" /></button>
              </li>
            {/each}
          </ul>
        </section>
      {/each}
    {/if}
  </section>
</div>

<style>
  .layout { display: grid; grid-template-columns: minmax(0, 4fr) minmax(0, 8fr); gap: var(--s-7); align-items: start; }
  .summary { position: sticky; top: calc(var(--nav) + var(--s-6)); display: flex; flex-direction: column; gap: var(--s-6); }
  .big { font-size: var(--t-stat); font-weight: 700; letter-spacing: -.03em; line-height: 1.1; margin: var(--s-1) 0; }
  .dh { font-size: var(--t-card); }
  .what { flex: 1; min-width: 0; }
  .amt { font-weight: 600; }
  .del { color: var(--muted); }
  @media (hover: hover) and (pointer: fine) { .del:hover:not(:disabled) { color: var(--danger); } }
  @media (max-width: 1023px) {
    .layout { grid-template-columns: minmax(0, 1fr); }
    .summary { position: static; }
  }
</style>
