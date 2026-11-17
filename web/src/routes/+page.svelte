<script>
  import { ChevronLeft, ChevronRight, Printer } from '@lucide/svelte';
  import { db } from '$lib/data.svelte.js';
  import LoadError from '$lib/components/LoadError.svelte';
  import EmptyState from '$lib/components/EmptyState.svelte';
  import Sheet from '$lib/components/Sheet.svelte';
  import {
    STARTING_FLOAT, DAILY_TARGET, localDateKey, dateFromKey, shiftDateKey, formatPrice, receiptNumber, itemLabel,
    computeStats, dayReport, breakdownRange, foodBreakdown,
  } from '$lib/reports.js';

  const today = localDateKey(Date.now());
  let reportDate = $state(today);
  let period = $state('day');
  let printing = $state(false);

  const report = $derived(dayReport(db.orders, db.expenses, reportDate));
  const allTime = $derived(computeStats(db.orders));
  const recent = $derived(db.orders.slice(0, 8));
  const breakdown = $derived(foodBreakdown(db.orders, breakdownRange(period, reportDate), db));
  const errors = $derived(['orders', 'expenses'].filter((k) => db.errors[k]).map((k) => db.errors[k]));
  const isToday = $derived(reportDate === today);
  const dayLabel = $derived(dateFromKey(reportDate).toLocaleDateString('en-PH', { weekday: 'long', month: 'long', day: 'numeric' }));
  const pct = (n) => `${Math.round(n * 100)}%`;
  const time = (ts) => new Date(ts).toLocaleTimeString('en-PH', { hour: 'numeric', minute: '2-digit' });
  const ago = (ts) => (localDateKey(ts) === today ? time(ts) : new Date(ts).toLocaleDateString('en-PH', { month: 'short', day: 'numeric' }));

  function setDate(key) {
    reportDate = key > today ? today : key;
  }

  function printReport() {
    document.body.classList.add('printing');
    window.addEventListener('afterprint', () => document.body.classList.remove('printing'), { once: true });
    window.print();
  }
</script>

<LoadError {errors} />

<!-- The day: the figure that matters, set large. -->
<section class="day" aria-labelledby="day-title">
  <div class="day-bar enter">
    <div class="stepper">
      <button type="button" class="btn btn-icon" aria-label="Previous day" onclick={() => setDate(shiftDateKey(reportDate, -1))}><ChevronLeft aria-hidden="true" /></button>
      <h1 id="day-title" class="date-pick">
        <label>
          {isToday ? 'Today' : dayLabel}
          <span class="visually-hidden">, pick another day</span>
          <input type="date" max={today} value={reportDate} onchange={(e) => e.currentTarget.value && setDate(e.currentTarget.value)} />
        </label>
      </h1>
      <button type="button" class="btn btn-icon" aria-label="Next day" disabled={isToday} onclick={() => setDate(shiftDateKey(reportDate, 1))}><ChevronRight aria-hidden="true" /></button>
      {#if !isToday}<button type="button" class="btn btn-sm" onclick={() => (reportDate = today)}>Back to today</button>{/if}
    </div>
    <button type="button" class="btn btn-primary" onclick={() => (printing = true)}><Printer aria-hidden="true" /> Z-Reading</button>
  </div>

  {#if !db.loaded}
    <div class="skeleton" style="height: 96px; width: min(520px, 80%)"></div>
  {:else}
    <p class="figure num enter-2" aria-label="Net sales {formatPrice(report.netSales)}">{formatPrice(report.netSales)}</p>
    <p class="figure-line enter-2">
      net sales from {report.count} {report.count === 1 ? 'order' : 'orders'}{isToday ? ' so far' : ''}.
      <span class="muted">{pct(report.goalShare)} of the {formatPrice(DAILY_TARGET)} goal.</span>
    </p>
    <div class="goal enter-3" role="meter" aria-label="Daily goal" aria-valuemin="0" aria-valuemax="100" aria-valuenow={Math.round(report.goalShare * 100)}>
      <span style:transform={`scaleX(${report.goalShare})`}></span>
    </div>

    <dl class="facts enter-3">
      <div><dt class="label">Cash</dt><dd class="num">{formatPrice(report.cashSales)}</dd></div>
      <div><dt class="label">GCash</dt><dd class="num">{formatPrice(report.gcashSales)}</dd></div>
      <div><dt class="label">Expenses</dt><dd class="num">{formatPrice(report.expenseTotal)}</dd></div>
      <div><dt class="label">Profit</dt><dd class="num" class:danger-text={report.profit < 0}>{formatPrice(report.profit)}</dd></div>
    </dl>
  {/if}
</section>

<div class="cols">
  <section class="stack" aria-labelledby="sold-title">
    <div class="head">
      <h2 id="sold-title">What sold</h2>
      <div class="tabs" role="group" aria-label="Period">
        {#each [['day', isToday ? 'Today' : 'That day'], ['week', 'This week'], ['month', 'This month']] as [key, label]}
          <button type="button" aria-pressed={period === key} onclick={() => (period = key)}>{label}</button>
        {/each}
      </div>
    </div>
    {#if breakdown.groups.length === 0}
      <div class="panel"><EmptyState title="Nothing sold yet" text="Each item's count and sales show up here once orders come in." /></div>
    {:else}
      <div class="panel table-scroll">
        <table class="table">
          <thead><tr><th>Item</th><th class="end">Sold</th><th class="end">Sales</th></tr></thead>
          {#each breakdown.groups as g (g.category)}
            <tbody>
              <tr class="cat"><td colspan="2">{g.category}</td><td class="end">{formatPrice(g.total)}</td></tr>
              {#each g.rows as r (r.label)}
                <tr><td>{r.label}</td><td class="end">{r.quantity}</td><td class="end">{formatPrice(r.sales)}</td></tr>
              {/each}
            </tbody>
          {/each}
          <tfoot><tr><td colspan="2">Total</td><td class="end">{formatPrice(breakdown.total)}</td></tr></tfoot>
        </table>
      </div>
    {/if}
  </section>

  <aside class="stack-l side">
    <section class="stack" aria-labelledby="drawer-title">
      <h2 id="drawer-title" class="h3">Closing the drawer</h2>
      <div class="panel pad stack">
        <dl class="ledger">
          <dt>Cash sales</dt><dd>{formatPrice(report.cashSales)}</dd>
          <dt>Starting float</dt><dd>+{formatPrice(STARTING_FLOAT)}</dd>
          <dt>Expenses</dt><dd>−{formatPrice(report.expenseTotal)}</dd>
          <dt class="total">Should be in the drawer</dt><dd class="total">{formatPrice(report.drawer)}</dd>
        </dl>
        <p class="label">Counts every expense as paid from the drawer.</p>
      </div>

      {#if report.cashShare !== null}
        <div class="split" aria-label="Cash {pct(report.cashShare)}, GCash {pct(1 - report.cashShare)}">
          <div class="split-bar" aria-hidden="true"><span style:flex-grow={report.cashShare}></span><span style:flex-grow={1 - report.cashShare}></span></div>
          <div class="split-key label"><span><i class="k1"></i>Cash {pct(report.cashShare)}</span><span><i class="k2"></i>GCash {pct(1 - report.cashShare)}</span></div>
        </div>
      {/if}

      {#if report.expenses.length}
        <ul class="list panel">
          {#each report.expenses as e (e.id)}
            <li><span>{e.description}<span class="sub">{e.recorded_by || '—'} · {time(e.timestamp)}</span></span><span class="end">{formatPrice(e.amount)}</span></li>
          {/each}
        </ul>
      {/if}
    </section>

    <section class="stack" aria-labelledby="latest-title">
      <div class="head"><h2 id="latest-title" class="h3">Latest orders</h2><a class="link small" href="/orders">All orders</a></div>
      {#if db.loaded && recent.length === 0}
        <p class="muted">Orders show up here the moment a tablet syncs a sale.</p>
      {:else}
        <ul class="list panel">
          {#each recent as o (o.id)}
            <li class="order" class:void={o.is_voided}>
              <a href={`/orders?open=${o.id}`}>
                <span class="o-main">
                  <span><strong class="num">#{receiptNumber(o.id)}</strong> · {o.table_label || 'Take out'}</span>
                  <span class="sub clamp">{(o.order_items || []).map((i) => `${i.quantity}× ${itemLabel(i)}`).join(', ') || 'No items'}</span>
                </span>
                <span class="end"><span class="num">{formatPrice(o.total)}</span><span class="sub">{o.is_voided ? 'Voided' : ago(o.timestamp)}</span></span>
              </a>
            </li>
          {/each}
        </ul>
      {/if}
      <p class="label">All time: {formatPrice(allTime.netSales)} from {allTime.count.toLocaleString()} orders.</p>
    </section>
  </aside>
</div>

<Sheet bind:open={printing} title="Z-Reading" width="420px">
  <div class="ticket print-area">
    <p class="t-head">BREW NI CAT<br />Z-READING</p>
    <dl>
      <div><dt>DATE</dt><dd>{reportDate}</dd></div>
      <div><dt>TIME</dt><dd>{new Date().toLocaleTimeString('en-PH', { hour: '2-digit', minute: '2-digit' })}</dd></div>
      <div><dt>ORDERS</dt><dd>{report.count}</dd></div>
      <hr />
      <div><dt>GROSS SALES</dt><dd>{formatPrice(report.grossSales)}</dd></div>
      <div><dt>DISCOUNTS</dt><dd>{formatPrice(report.discounts)}</dd></div>
      <div><dt>NET REVENUE</dt><dd>{formatPrice(report.netSales)}</dd></div>
      <div><dt>OPERATING EXP</dt><dd>{formatPrice(report.expenseTotal)}</dd></div>
      <hr />
      <div><dt>CASH SALES</dt><dd>{formatPrice(report.cashSales)}</dd></div>
      <div><dt>GCASH SALES</dt><dd>{formatPrice(report.gcashSales)}</dd></div>
      <div><dt>STARTING FLOAT</dt><dd>{formatPrice(STARTING_FLOAT)}</dd></div>
      <div><dt>CASH DRAWER</dt><dd>{formatPrice(report.drawer)}</dd></div>
      <hr />
      <div class="t-strong"><dt>NET PROFIT</dt><dd>{formatPrice(report.profit)}</dd></div>
    </dl>
  </div>
  {#snippet footer()}
    <button type="button" class="btn" onclick={() => (printing = false)}>Close</button>
    <button type="button" class="btn btn-primary" onclick={printReport}><Printer aria-hidden="true" /> Print</button>
  {/snippet}
</Sheet>

<style>
  .day { display: flex; flex-direction: column; gap: var(--s-4); padding-bottom: var(--s-7); border-bottom: 1px solid var(--line); }
  .day-bar { display: flex; align-items: center; justify-content: space-between; gap: var(--s-4); flex-wrap: wrap; margin-bottom: var(--s-4); }
  .stepper { display: flex; align-items: center; gap: var(--s-2); flex-wrap: wrap; }
  .date-pick { position: relative; cursor: pointer; padding: 0 var(--s-2); font-size: var(--t-h3); font-weight: 650; letter-spacing: -.01em; }
  .date-pick label { cursor: pointer; }
  .date-pick input { position: absolute; inset: 0; opacity: 0; cursor: pointer; width: 100%; }
  .date-pick:has(input:focus-visible) { outline: 2px solid var(--action); outline-offset: 2px; border-radius: var(--r-sm); }
  .figure { font-size: var(--t-hero); font-weight: 700; letter-spacing: -.04em; line-height: .95; }
  .figure-line { font-size: var(--t-card); color: var(--ink); max-width: 62ch; }
  .goal { height: 4px; background: var(--g-alt); border-radius: 2px; overflow: hidden; margin-top: var(--s-2); }
  .goal span { display: block; height: 100%; background: var(--action); transform-origin: left; transition: transform 600ms var(--ease-out); }
  .facts { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-top: var(--s-5); }
  .facts div { padding: 0 var(--s-5); border-left: 1px solid var(--line); }
  .facts div:first-child { padding-left: 0; border-left: 0; }
  .facts dd { font-size: var(--t-h2); font-weight: 650; letter-spacing: -.02em; margin-top: var(--s-1); }
  @media (max-width: 760px) {
    .facts { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--s-5) 0; }
    .facts div:nth-child(3) { padding-left: 0; border-left: 0; }
    .facts dd { font-size: var(--t-h3); }
  }

  .cols { display: grid; grid-template-columns: minmax(0, 7fr) minmax(0, 5fr); gap: var(--s-7); align-items: start; }
  @media (max-width: 1023px) { .cols { grid-template-columns: minmax(0, 1fr); } }
  .h3 { font-size: var(--t-h3); }

  .split { display: flex; flex-direction: column; gap: var(--s-2); }
  .split-bar { display: flex; gap: 2px; height: 8px; border-radius: 4px; overflow: hidden; }
  .split-bar span:first-child { background: var(--action); }
  .split-bar span:last-child { background: var(--series-2); }
  .split-key { display: flex; gap: var(--s-5); }
  .split-key span { display: inline-flex; align-items: center; gap: var(--s-2); }
  .split-key i { width: 10px; height: 10px; border-radius: 3px; }
  .k1 { background: var(--action); }
  .k2 { background: var(--series-2); }

  .order { padding: 0 !important; }
  .order a { display: flex; justify-content: space-between; gap: var(--s-4); width: 100%; padding: var(--s-3) var(--s-5); text-decoration: none; transition: background-color 150ms var(--ease-out); }
  @media (hover: hover) and (pointer: fine) { .order a:hover { background: var(--hover-fill); } }
  .order.void .o-main, .order.void .num { color: var(--muted); text-decoration: line-through; }
  .o-main { display: flex; flex-direction: column; min-width: 0; }
  .order .end { display: flex; flex-direction: column; align-items: flex-end; }
  .clamp { display: -webkit-box; -webkit-line-clamp: 1; line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden; }

  .ticket { background: #fdfcf8; color: #1d2a21; font-family: var(--mono); font-size: 13px; padding: var(--s-5); border-radius: var(--r-sm); border: 1px solid var(--line); }
  .t-head { text-align: center; font-weight: 700; margin-bottom: var(--s-3); line-height: 1.4; }
  .ticket dl { display: flex; flex-direction: column; gap: var(--s-1); }
  .ticket dl div { display: flex; justify-content: space-between; gap: var(--s-3); }
  .ticket hr { border: 0; border-top: 1px dashed #8b8577; margin: 6px 0; width: 100%; }
  .t-strong { font-weight: 700; }
</style>
