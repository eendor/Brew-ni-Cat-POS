<script>
  import { Download, X } from '@lucide/svelte';
  import { page } from '$app/state';
  import { goto } from '$app/navigation';
  import { db } from '$lib/data.svelte.js';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import LoadError from '$lib/components/LoadError.svelte';
  import EmptyState from '$lib/components/EmptyState.svelte';
  import OrderDetail from '$lib/components/OrderDetail.svelte';
  import { filterOrders, formatPrice, receiptNumber, itemLabel, ordersCsv, computeStats, localDateKey } from '$lib/reports.js';

  // Filters and the open order live in the URL, so a view survives a reload or a bookmark.
  const params = $derived(page.url.searchParams);
  const filters = $derived({ date: params.get('date') || '', device: params.get('device') || 'all', payment: params.get('payment') || 'all' });
  const openId = $derived(Number(params.get('open')) || null);

  const devices = $derived([...new Set(db.orders.map((o) => o.device_id).filter(Boolean))].sort());
  const shown = $derived(filterOrders(db.orders, filters));
  const totals = $derived(computeStats(shown));
  const selected = $derived(openId ? db.orders.find((o) => o.id === openId) : null);
  const filtered = $derived(Boolean(filters.date) || filters.device !== 'all' || filters.payment !== 'all');

  // Group the list by day so a long history scans quickly.
  const days = $derived.by(() => {
    const out = [];
    for (const o of shown) {
      const key = localDateKey(o.timestamp);
      if (out.at(-1)?.key !== key) out.push({ key, orders: [] });
      out.at(-1).orders.push(o);
    }
    return out;
  });

  const wideQuery = matchMedia('(min-width: 1024px)');
  let wide = $state(wideQuery.matches);
  $effect(() => {
    const on = () => (wide = wideQuery.matches);
    wideQuery.addEventListener('change', on);
    return () => wideQuery.removeEventListener('change', on);
  });

  let dialog;
  $effect(() => {
    if (!dialog) return;
    if (selected && !wide && !dialog.open) dialog.showModal();
    if ((!selected || wide) && dialog.open) dialog.close();
  });

  function setParams(changes) {
    const next = new URLSearchParams(page.url.searchParams);
    for (const [k, v] of Object.entries(changes)) {
      if (!v || v === 'all') next.delete(k);
      else next.set(k, v);
    }
    const q = next.toString();
    goto(q ? `?${q}` : page.url.pathname, { replaceState: true, keepFocus: true, noScroll: true });
  }

  function exportCsv() {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([ordersCsv(shown)], { type: 'text/csv;charset=utf-8' }));
    a.download = `cattastic_sales_report_${filters.date || 'all'}.csv`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }

  const dayName = (key) => {
    const today = localDateKey(Date.now());
    if (key === today) return 'Today';
    return new Date(key + 'T00:00').toLocaleDateString('en-PH', { weekday: 'short', month: 'short', day: 'numeric', year: key.slice(0, 4) === today.slice(0, 4) ? undefined : 'numeric' });
  };
  const time = (ts) => new Date(ts).toLocaleTimeString('en-PH', { hour: 'numeric', minute: '2-digit' });
</script>

<PageHeader title="Orders" subtitle="Every sale from every tablet. Pick one to fix a typo, a price or the payment, or to void it.">
  {#snippet actions()}
    <button type="button" class="btn" onclick={exportCsv} disabled={shown.length === 0}><Download aria-hidden="true" /> Export CSV</button>
  {/snippet}
</PageHeader>

<LoadError errors={db.errors.orders ? [db.errors.orders] : []} />

<div class="board" class:has-detail={selected && wide}>
  <section class="stack" aria-label="Order list">
    <div class="filters">
      <label class="field"><span class="label">Day</span><input class="input" type="date" value={filters.date} onchange={(e) => setParams({ date: e.currentTarget.value })} /></label>
      <label class="field">
        <span class="label">Tablet</span>
        <select class="select" value={filters.device} onchange={(e) => setParams({ device: e.currentTarget.value })}>
          <option value="all">All tablets</option>
          {#each devices as d}<option value={d}>Tablet {d.slice(0, 8)}</option>{/each}
        </select>
      </label>
      <div class="field">
        <span class="label" id="pay-filter">Paid with</span>
        <div class="tabs" role="group" aria-labelledby="pay-filter">
          {#each [['all', 'Any'], ['CASH', 'Cash'], ['GCASH', 'GCash']] as [key, label]}
            <button type="button" aria-pressed={filters.payment === key} onclick={() => setParams({ payment: key })}>{label}</button>
          {/each}
        </div>
      </div>
    </div>

    <p class="label" aria-live="polite">
      {shown.length.toLocaleString()} {shown.length === 1 ? 'order' : 'orders'}, {formatPrice(totals.netSales)} net (voided orders not counted).
      {#if filtered}<button type="button" class="clear" onclick={() => setParams({ date: '', device: '', payment: '' })}><X size={14} aria-hidden="true" /> Clear filters</button>{/if}
    </p>

    {#if !db.loaded}
      <div class="panel pad stack">{#each Array(6) as _}<div class="skeleton" style="height: 20px"></div>{/each}</div>
    {:else if shown.length === 0}
      <div class="panel">
        <EmptyState title={filtered ? 'No orders match' : 'No orders yet'} text={filtered ? 'Try another day, tablet or payment.' : 'Orders show up here the moment a tablet syncs a sale.'} />
      </div>
    {:else}
      <div class="days">
        {#each days as d (d.key)}
          <section aria-label={dayName(d.key)}>
            <h2 class="day-head label">{dayName(d.key)}</h2>
            <ul class="list panel">
              {#each d.orders as o (o.id)}
                <li class="o" class:void={o.is_voided} class:on={o.id === openId}>
                  <a href={`?${new URLSearchParams({ ...Object.fromEntries(params), open: o.id })}`} onclick={(e) => { e.preventDefault(); setParams({ open: String(o.id) }); }} aria-current={o.id === openId ? 'true' : undefined}>
                    <span class="o-time label num">{time(o.timestamp)}</span>
                    <span class="o-main">
                      <span><strong class="num">#{receiptNumber(o.id)}</strong> {o.table_label || 'Take out'}</span>
                      <span class="sub clamp">{(o.order_items || []).map((i) => `${i.quantity}× ${itemLabel(i)}`).join(', ') || 'No items'}</span>
                    </span>
                    <span class="end">
                      <span class="num amt">{formatPrice(o.total)}</span>
                      <span class="sub">{o.is_voided ? 'Voided' : o.payment_method === 'CASH' ? 'Cash' : 'GCash'} · {o.cashier_name || '—'}</span>
                    </span>
                  </a>
                </li>
              {/each}
            </ul>
          </section>
        {/each}
      </div>
    {/if}
  </section>

  {#if selected && wide}
    <aside class="side panel" aria-label="Order details">
      <OrderDetail order={selected} onclose={() => setParams({ open: '' })} />
    </aside>
  {/if}
</div>

<dialog class="sheet order-sheet" bind:this={dialog} onclose={() => openId && !wide && setParams({ open: '' })} aria-label="Order details">
  {#if selected && !wide}<OrderDetail order={selected} onclose={() => dialog.close()} />{/if}
</dialog>

<style>
  .board { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--s-6); align-items: start; }
  .board.has-detail { grid-template-columns: minmax(0, 5fr) minmax(0, 7fr); }
  .side { position: sticky; top: calc(var(--nav) + var(--s-5)); max-height: calc(100dvh - var(--nav) - var(--s-6)); overflow: hidden; display: flex; flex-direction: column; animation: rise 320ms var(--ease-out); }
  .filters { display: flex; flex-wrap: wrap; align-items: flex-end; gap: var(--s-4); }
  .filters .input, .filters .select { width: auto; min-width: 180px; }
  .clear { display: inline-flex; align-items: center; gap: var(--s-1); margin-left: var(--s-2); border: 0; background: none; padding: 0; color: var(--action-ink); font: inherit; font-weight: 600; cursor: pointer; }
  .days { display: flex; flex-direction: column; gap: var(--s-5); }
  .day-head { margin: 0 0 var(--s-2); font-weight: 600; color: var(--ink-2); }
  .o { padding: 0 !important; }
  .o a { display: grid; grid-template-columns: 64px minmax(0, 1fr) auto; gap: var(--s-4); align-items: start; width: 100%; padding: var(--s-3) var(--s-5); text-decoration: none; transition: background-color 150ms var(--ease-out); }
  @media (hover: hover) and (pointer: fine) { .o a:hover { background: var(--hover-fill); } }
  .o.on a { background: var(--action-soft); }
  .o-time { white-space: nowrap; padding-top: 2px; }
  .o-main { display: flex; flex-direction: column; min-width: 0; }
  .o .end { display: flex; flex-direction: column; align-items: flex-end; }
  .amt { font-weight: 600; }
  .o.void .o-main, .o.void .amt { color: var(--muted); text-decoration: line-through; }
  .clamp { display: -webkit-box; -webkit-line-clamp: 1; line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden; }
  .has-detail .o a { grid-template-columns: 56px minmax(0, 1fr) auto; }
  .order-sheet { --sheet-w: 640px; }
  .order-sheet :global(.detail) { max-height: inherit; }
  @media (max-width: 640px) {
    .o a { grid-template-columns: minmax(0, 1fr) auto; }
    .o-time { display: none; }
    .filters .field { flex: 1 1 100%; }
    .filters .input, .filters .select { width: 100%; }
  }
</style>
