<script>
  import { Plus, Trash2, Pencil, X } from '@lucide/svelte';
  import { supabase } from '$lib/supabase.js';
  import { fetchOrders, run } from '$lib/data.svelte.js';
  import { notify } from '$lib/toast.svelte.js';
  import { formatPrice, receiptNumber, itemLabel, orderItemChanges } from '$lib/reports.js';

  /** One order: who, how it was paid and its items, all correctable, plus void. */
  let { order, onclose } = $props();

  let details = $state({ table_label: '', cashier_name: '', payment_method: 'CASH', payment_reference: '' });
  let working = $state(null); // null until "Edit items"
  let saving = $state(false);
  let error = $state('');

  // Reset the form whenever a different order is shown.
  let shownId = null;
  $effect(() => {
    if (order && order.id !== shownId) {
      shownId = order.id;
      details = {
        table_label: order.table_label || '',
        cashier_name: order.cashier_name || '',
        payment_method: order.payment_method || 'CASH',
        payment_reference: order.payment_reference || '',
      };
      working = null;
      error = '';
    }
  });

  const items = $derived(working ?? order?.order_items ?? []);
  const subtotal = $derived(items.reduce((s, i) => s + (i.quantity || 0) * (i.unit_price || 0), 0));
  const total = $derived(Math.max(0, subtotal - (order?.discount_deduction || 0)));
  const dirty = $derived(
    working !== null ||
      details.table_label.trim() !== (order.table_label || '') ||
      details.cashier_name.trim() !== (order.cashier_name || '') ||
      details.payment_method !== order.payment_method ||
      details.payment_reference.trim() !== (order.payment_reference || ''),
  );
  const when = $derived(new Date(order.timestamp).toLocaleString('en-PH', { dateStyle: 'medium', timeStyle: 'short' }));

  function addRow() {
    working.push({ id: 0, order_id: order.id, item_id: 'custom', item_name: '', variant_id: 'custom', variant_name: '', flavor: null, quantity: 1, unit_price: 0, total_price: 0 });
  }

  async function save() {
    saving = true;
    error = '';
    try {
      const updates = {
        table_label: details.table_label.trim() || null,
        cashier_name: details.cashier_name.trim() || null,
        payment_method: details.payment_method,
        payment_reference: details.payment_reference.trim() || null,
      };
      if (working !== null) {
        const change = orderItemChanges(order, working);
        if (change.deleteIds.length) await run(supabase.from('order_items').delete().in('id', change.deleteIds));
        for (const { id, ...row } of change.updates) await run(supabase.from('order_items').update(row).eq('id', id));
        if (change.inserts.length) await run(supabase.from('order_items').insert(change.inserts));
        updates.subtotal = change.subtotal;
        updates.total = change.total;
      }
      await run(supabase.from('orders').update(updates).eq('id', order.id));
      await fetchOrders();
      shownId = null; // show the saved values
      notify(`Saved order #${receiptNumber(order.id)}.`);
    } catch (e) {
      console.error('Could not save the order:', e);
      error = `Couldn't save: ${e.message || 'try again'}. Changes before the error were saved; check the items.`;
    } finally {
      saving = false;
    }
  }

  async function setVoided(id, voided) {
    await run(supabase.from('orders').update({ is_voided: voided }).eq('id', id));
    await fetchOrders();
  }

  async function toggleVoid() {
    const id = order.id;
    const next = !order.is_voided;
    try {
      await setVoided(id, next);
      notify(next ? `Voided #${receiptNumber(id)}. It no longer counts in sales.` : `#${receiptNumber(id)} counts in sales again.`, {
        undo: () => setVoided(id, !next).catch((e) => notify(`Couldn't undo: ${e.message}`, { tone: 'error' })),
      });
    } catch (e) {
      error = `Couldn't change the order: ${e.message || 'try again'}.`;
    }
  }
</script>

<article class="detail" aria-labelledby="order-title">
  <header class="d-head">
    <div>
      <h2 id="order-title">Order #{receiptNumber(order.id)}</h2>
      <p class="label">{when} · tablet {order.device_id?.slice(0, 8) || '—'}</p>
    </div>
    {#if order.is_voided}<span class="tag tag-danger">Voided</span>{/if}
    {#if onclose}<button type="button" class="btn btn-icon" aria-label="Close order" onclick={onclose}><X aria-hidden="true" /></button>{/if}
  </header>

  <div class="d-body">
    <div class="grid-2">
      <label class="field"><span class="label">Name or table</span><input class="input" bind:value={details.table_label} placeholder="Take out" maxlength="60" /></label>
      <label class="field"><span class="label">Cashier</span><input class="input" bind:value={details.cashier_name} maxlength="60" /></label>
      <div class="field">
        <span class="label" id="pay-label">Paid with</span>
        <div class="tabs" role="group" aria-labelledby="pay-label">
          <button type="button" aria-pressed={details.payment_method === 'CASH'} onclick={() => (details.payment_method = 'CASH')}>Cash</button>
          <button type="button" aria-pressed={details.payment_method === 'GCASH'} onclick={() => (details.payment_method = 'GCASH')}>GCash</button>
        </div>
      </div>
      {#if details.payment_method === 'GCASH'}
        <label class="field"><span class="label">GCash reference</span><input class="input num" bind:value={details.payment_reference} placeholder="Optional" maxlength="40" /></label>
      {/if}
    </div>

    <section class="stack">
      <div class="head">
        <h3>Items</h3>
        {#if working === null}
          <button type="button" class="btn btn-sm" onclick={() => (working = (order.order_items || []).map((i) => ({ ...i })))}><Pencil aria-hidden="true" /> Edit items</button>
        {:else}
          <button type="button" class="btn btn-sm btn-quiet" onclick={() => (working = null)}>Discard item changes</button>
        {/if}
      </div>

      {#if working === null}
        <ul class="list panel">
          {#each items as i (i.id)}
            <li><span>{i.quantity}× {itemLabel(i)}<span class="sub num">{formatPrice(i.unit_price)} each</span></span><span class="end">{formatPrice(i.quantity * i.unit_price)}</span></li>
          {:else}
            <li class="muted">No items on this order.</li>
          {/each}
        </ul>
      {:else}
        <div class="stack">
          {#each working as i, idx (idx)}
            <fieldset class="edit panel">
              <legend class="visually-hidden">Item {idx + 1}</legend>
              <label class="field name"><span class="label">Item</span><input class="input" bind:value={i.item_name} aria-invalid={!i.item_name.trim() ? 'true' : undefined} /></label>
              <label class="field"><span class="label">Size</span><input class="input" bind:value={i.variant_name} /></label>
              <label class="field"><span class="label">Flavor</span><input class="input" value={i.flavor || ''} oninput={(e) => (i.flavor = e.currentTarget.value || null)} /></label>
              <label class="field"><span class="label">Qty</span><input class="input" type="number" min="0" step="1" inputmode="numeric" value={i.quantity} oninput={(e) => (i.quantity = Math.max(0, parseInt(e.currentTarget.value || '0', 10) || 0))} /></label>
              <label class="field"><span class="label">Price each</span><input class="input" type="number" min="0" step="0.01" inputmode="decimal" value={i.unit_price} oninput={(e) => (i.unit_price = Math.max(0, parseFloat(e.currentTarget.value || '0') || 0))} /></label>
              <button type="button" class="btn btn-icon del" aria-label="Remove {i.item_name || 'this item'}" onclick={() => working.splice(idx, 1)}><Trash2 aria-hidden="true" /></button>
            </fieldset>
          {/each}
          <div><button type="button" class="btn btn-sm" onclick={addRow}><Plus aria-hidden="true" /> Add item</button></div>
          <p class="label">Rows with no name or a quantity of 0 are dropped when you save.</p>
        </div>
      {/if}
    </section>

    <dl class="ledger">
      <dt>Subtotal{working !== null ? ' (edited)' : ''}</dt><dd>{formatPrice(subtotal)}</dd>
      {#if order.discount_deduction > 0}<dt>Discount{order.discount_label ? ` · ${order.discount_label}` : ''}</dt><dd>−{formatPrice(order.discount_deduction)}</dd>{/if}
      <dt class="total">Total</dt><dd class="total">{formatPrice(total)}</dd>
    </dl>

    {#if error}<p class="notice notice-error" role="alert">{error}</p>{/if}
  </div>

  <footer class="d-foot">
    <button type="button" class="btn btn-danger" class:restore={order.is_voided} onclick={toggleVoid} disabled={saving}>{order.is_voided ? 'Unvoid' : 'Void order'}</button>
    <span class="label unsaved">{dirty ? 'Unsaved changes' : ''}</span>
    <button type="button" class="btn btn-primary" onclick={save} disabled={!dirty || saving}>{saving ? 'Saving…' : 'Save changes'}</button>
  </footer>
</article>

<style>
  .detail { display: flex; flex-direction: column; min-height: 0; height: 100%; }
  .d-head { display: flex; align-items: flex-start; gap: var(--s-3); padding: var(--s-5); border-bottom: 1px solid var(--line); }
  .d-head > div { flex: 1; min-width: 0; }
  .d-head h2 { font-size: var(--t-h3); }
  .d-body { padding: var(--s-5); display: flex; flex-direction: column; gap: var(--s-6); overflow-y: auto; flex: 1; }
  h3 { font-size: var(--t-card); }
  .edit { margin: 0; padding: var(--s-4); display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto; gap: var(--s-3); align-items: end; }
  .edit .name { grid-column: 1 / -1; }
  .edit .del { grid-row: 1; grid-column: 3; }
  .edit .name { grid-column: 1 / 3; }
  .d-foot { display: flex; align-items: center; gap: var(--s-3); padding: var(--s-4) var(--s-5); border-top: 1px solid var(--line); background: var(--surface); }
  .unsaved { flex: 1; text-align: right; }
  .restore { color: var(--ink); border-color: var(--line-strong); }
</style>
