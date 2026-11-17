<script>
  import { supabase } from '$lib/supabase.js';
  import { db, fetchInventory, run } from '$lib/data.svelte.js';
  import { notify } from '$lib/toast.svelte.js';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import MenuTabs from '$lib/components/MenuTabs.svelte';
  import LoadError from '$lib/components/LoadError.svelte';
  import EmptyState from '$lib/components/EmptyState.svelte';
  import Sheet from '$lib/components/Sheet.svelte';

  let adjusting = $state(null);
  let open = $state(false);
  let delta = $state('');
  let saving = $state(false);
  let error = $state('');

  const isLow = (i) => i.current_stock <= i.reorder_threshold;
  const low = $derived(db.inventory.filter(isLow));
  const fine = $derived(db.inventory.filter((i) => !isLow(i)));
  const usedBy = (inv) =>
    db.recipes
      .filter((r) => r.inventory_item_id === inv.id)
      .map((r) => db.items.find((m) => m.id === r.menu_item_id)?.name)
      .filter(Boolean);
  // The bar fills to three times the reorder level, so "low" sits at the first third.
  const level = (i) => Math.max(0, Math.min(1, i.current_stock / Math.max(1, i.reorder_threshold * 3)));
  const amount = (n) => Number(n).toLocaleString('en-US', { maximumFractionDigits: 2 });

  const parsed = $derived(delta.trim() === '' ? NaN : Number(delta));
  const after = $derived(adjusting && Number.isFinite(parsed) ? adjusting.current_stock + parsed : null);

  function startAdjust(inv) {
    adjusting = inv;
    delta = '';
    error = '';
    open = true;
  }

  async function save(event) {
    event.preventDefault();
    if (after === null) return;
    saving = true;
    error = '';
    try {
      await run(supabase.from('inventory').update({ current_stock: after }).eq('id', adjusting.id));
      await fetchInventory();
      notify(`${adjusting.item_name} is now ${amount(after)} ${adjusting.unit}.`);
      open = false;
    } catch (e) {
      error = `Couldn't save: ${e.message || 'try again'}.`;
    } finally {
      saving = false;
    }
  }
</script>

<PageHeader title="Stock" subtitle="Ingredients go down by themselves as the tablets sell. Adjust them here after a delivery or a count.">
  {#snippet tabs()}<MenuTabs />{/snippet}
</PageHeader>

<LoadError errors={db.errors.inventory ? [db.errors.inventory] : []} />

{#snippet rows(list)}
  <ul class="list panel">
    {#each list as inv (inv.id)}
      {@const used = usedBy(inv)}
      <li class="inv">
        <span class="inv-name">
          <span class="nm">{inv.item_name}</span>
          <span class="sub">{used.length ? `Used in ${used.slice(0, 3).join(', ')}${used.length > 3 ? ` and ${used.length - 3} more` : ''}` : 'Not used in any item yet'}</span>
        </span>
        <span class="inv-level">
          <span class="bar" class:low={isLow(inv)} aria-hidden="true"><span style:transform={`scaleX(${level(inv)})`}></span></span>
          <span class="small num"><strong>{amount(inv.current_stock)}</strong> {inv.unit} <span class="muted">· reorder at {amount(inv.reorder_threshold)}</span></span>
        </span>
        <button type="button" class="btn btn-sm" onclick={() => startAdjust(inv)}>Adjust<span class="visually-hidden"> {inv.item_name}</span></button>
      </li>
    {/each}
  </ul>
{/snippet}

{#if !db.loaded}
  <div class="panel pad stack">{#each Array(6) as _}<div class="skeleton" style="height: 24px"></div>{/each}</div>
{:else if db.inventory.length === 0}
  <div class="panel"><EmptyState title="No ingredients tracked" text="When ingredients are added to the inventory, their stock shows up here." /></div>
{:else}
  {#if low.length}
    <section class="stack" aria-labelledby="low-title">
      <div class="head"><h2 id="low-title">Running low</h2><p class="label">{low.length} at or under the reorder level</p></div>
      {@render rows(low)}
    </section>
  {/if}
  <section class="stack" aria-labelledby="ok-title">
    <div class="head"><h2 id="ok-title">{low.length ? 'Everything else' : 'All ingredients'}</h2><p class="label">{fine.length} with enough stock</p></div>
    {#if fine.length}{@render rows(fine)}{:else}<p class="muted">Every ingredient is running low.</p>{/if}
  </section>
{/if}

<Sheet bind:open title={adjusting ? `Adjust ${adjusting.item_name}` : 'Adjust stock'} width="440px">
  {#if adjusting}
    <form id="adjust-form" class="stack" onsubmit={save}>
      <p>There {adjusting.current_stock === 1 ? 'is' : 'are'} <strong class="num">{amount(adjusting.current_stock)} {adjusting.unit}</strong> now.</p>
      <label class="field">
        <span class="label">Add or remove ({adjusting.unit})</span>
        <input class="input" type="number" step="any" inputmode="decimal" bind:value={delta} placeholder="50, or -10 to remove" aria-describedby="after" />
        <span class="hint" id="after">{after === null ? 'Use a minus sign to take stock away.' : `It will be ${amount(after)} ${adjusting.unit}.`}</span>
      </label>
      {#if after !== null && after < 0}<p class="notice notice-warn">That takes the stock below zero.</p>{/if}
      {#if error}<p class="notice notice-error" role="alert">{error}</p>{/if}
    </form>
  {/if}
  {#snippet footer()}
    <button type="button" class="btn" onclick={() => (open = false)}>Cancel</button>
    <button type="submit" form="adjust-form" class="btn btn-primary" disabled={after === null || parsed === 0 || saving}>{saving ? 'Saving…' : 'Save'}</button>
  {/snippet}
</Sheet>

<style>
  h2 { font-size: var(--t-h3); }
  .inv { display: grid !important; grid-template-columns: minmax(0, 5fr) minmax(0, 5fr) auto; align-items: center; }
  .inv-name { display: flex; flex-direction: column; min-width: 0; }
  .nm { font-weight: 600; }
  .inv-level { display: flex; flex-direction: column; gap: var(--s-2); }
  .bar { height: 6px; border-radius: 3px; background: var(--g-alt); overflow: hidden; max-width: 280px; }
  .bar span { display: block; height: 100%; background: var(--action); transform-origin: left; transition: transform 400ms var(--ease-out); }
  .bar.low span { background: var(--series-2); }
  @media (max-width: 640px) {
    .inv { grid-template-columns: minmax(0, 1fr) auto; }
    .inv-level { grid-column: 1; grid-row: 2; }
    .inv .btn { grid-row: 1 / 3; grid-column: 2; }
  }
</style>
