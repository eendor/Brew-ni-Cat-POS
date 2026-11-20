<script>
  import { Plus, Search, Trash2, TriangleAlert, Settings2 } from '@lucide/svelte';
  import { supabase } from '$lib/supabase.js';
  import { db, fetchItems, fetchCategories, run } from '$lib/data.svelte.js';
  import { notify } from '$lib/toast.svelte.js';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import MenuTabs from '$lib/components/MenuTabs.svelte';
  import LoadError from '$lib/components/LoadError.svelte';
  import EmptyState from '$lib/components/EmptyState.svelte';
  import ProductIcon from '$lib/components/ProductIcon.svelte';
  import ProductEditor from '$lib/components/ProductEditor.svelte';
  import Sheet from '$lib/components/Sheet.svelte';
  import { resolveEffectiveProductIconKey } from '$lib/product-icons.js';
  import { formatPrice, startingPrice, isItemLowStock, parseVariants, slugId } from '$lib/reports.js';

  let query = $state('');
  let editorOpen = $state(false);
  let editing = $state(null);
  let categoriesOpen = $state(false);
  let newCategory = $state('');
  let deleting = $state(null); // the category being deleted
  let confirmText = $state('');
  let busy = $state(false);
  let dealOpen = $state(false);
  let dealItem = $state(null);
  let dealPercent = $state(undefined);
  let dealActive = $state(false);
  let dealBusy = $state(false);
  let dealError = $state('');
  let deals = $state({});
  const validDeal = $derived(Number.isInteger(dealPercent) && dealPercent >= 1 && dealPercent <= 99);

  async function loadDeals() {
    try {
      const rows = await run(supabase.from('menu_deals').select('item_id, percent_off, active'));
      deals = Object.fromEntries((rows ?? []).map((row) => [row.item_id, row]));
      return true;
    } catch {
      return false;
    }
  }

  $effect(() => { void loadDeals(); });

  async function editDeal(item) {
    dealItem = item;
    dealOpen = true;
    dealBusy = true;
    dealError = '';
    try {
      const loaded = await loadDeals();
      if (dealItem?.id !== item.id || !dealOpen) return;
      if (!loaded) {
        dealError = "Deals aren't available. Try again once checkout deals are enabled.";
        return;
      }
      dealPercent = deals[item.id]?.percent_off;
      dealActive = deals[item.id]?.active ?? false;
    } finally {
      dealBusy = false;
    }
  }

  async function saveDeal(event) {
    event.preventDefault();
    if (!dealItem || !validDeal || dealBusy || dealError) return;
    const item = dealItem;
    dealBusy = true;
    try {
      await run(supabase.from('menu_deals').upsert({
        item_id: item.id, percent_off: dealPercent, active: dealActive,
      }, { onConflict: 'item_id' }));
      await loadDeals();
      dealOpen = false;
      notify(`Saved the deal for ${item.name}.`);
    } catch {
      notify("Couldn't save the deal. Try again.", { tone: 'error' });
    } finally {
      dealBusy = false;
    }
  }

  const q = $derived(query.trim().toLowerCase());
  const sections = $derived.by(() => {
    const match = (i) => !q || i.name.toLowerCase().includes(q) || (i.flavors || '').toLowerCase().includes(q);
    const known = new Set(db.categories.map((c) => c.id));
    const list = db.categories.map((c) => ({ id: c.id, name: c.name, items: db.items.filter((i) => i.category_id === c.id && match(i)) }));
    const loose = db.items.filter((i) => !known.has(i.category_id) && match(i));
    if (loose.length) list.push({ id: 'other', name: 'No category', items: loose });
    return list;
  });
  const shownCount = $derived(sections.reduce((n, s) => n + s.items.length, 0));
  const errors = $derived(['items', 'categories'].filter((k) => db.errors[k]).map((k) => db.errors[k]));

  function edit(item) {
    editing = item;
    editorOpen = true;
  }

  function isOnlineDealItem(item) {
    const key = item.name.toLowerCase().replace(/[^a-z]/g, '');
    return !['takeoutbox', 'cattreats', 'bottledwater'].includes(key);
  }

  function sizesLine(item) {
    const sizes = parseVariants(item.variants_json).map((v) => v.name).filter(Boolean);
    return sizes.length > 1 ? sizes.join(' · ') : '';
  }

  async function deleteItem(item) {
    const saved = $state.snapshot(item);
    try {
      await run(supabase.from('items').delete().eq('id', item.id));
      await fetchItems();
      notify(`Removed ${item.name} from the menu.`, {
        undo: async () => {
          try {
            await run(supabase.from('items').upsert(saved));
            await fetchItems();
          } catch (e) {
            notify(`Couldn't bring it back: ${e.message}`, { tone: 'error' });
          }
        },
      });
    } catch (e) {
      notify(`Couldn't remove ${item.name}: ${e.message || 'try again'}.`, { tone: 'error' });
    }
  }

  async function addCategory(event) {
    event.preventDefault();
    const name = newCategory.trim();
    if (!name) return;
    if (db.categories.some((c) => c.name.toLowerCase() === name.toLowerCase())) {
      notify(`There's already a category called ${name}.`, { tone: 'error' });
      return;
    }
    busy = true;
    try {
      await run(supabase.from('categories').insert({ id: slugId('cat_', name), name }));
      await fetchCategories();
      newCategory = '';
    } catch (e) {
      notify(`Couldn't add the category: ${e.message || 'try again'}.`, { tone: 'error' });
    } finally {
      busy = false;
    }
  }

  async function deleteCategory() {
    busy = true;
    try {
      await run(supabase.from('categories').delete().eq('id', deleting.id));
      await Promise.all([fetchCategories(), fetchItems()]);
      notify(`Deleted ${deleting.name} and its items.`);
      deleting = null;
    } catch (e) {
      notify(`Couldn't delete the category: ${e.message || 'try again'}.`, { tone: 'error' });
    } finally {
      busy = false;
    }
  }
</script>

<PageHeader title="Menu" subtitle="What the tablets sell, by category. Changes reach every tablet.">
  {#snippet tabs()}<MenuTabs />{/snippet}
  {#snippet actions()}
    <button type="button" class="btn" onclick={() => (categoriesOpen = true)}><Settings2 aria-hidden="true" /> Categories</button>
    <button type="button" class="btn btn-primary" onclick={() => edit(null)} disabled={db.categories.length === 0}><Plus aria-hidden="true" /> New item</button>
  {/snippet}
</PageHeader>

<LoadError {errors} />

<div class="catalog">
  <aside class="index">
    <label class="search">
      <Search size={18} aria-hidden="true" />
      <span class="visually-hidden">Search the menu</span>
      <input class="input" type="search" placeholder="Search items or flavors" bind:value={query} />
    </label>
    <nav aria-label="Categories">
      <ul>
        {#each sections as s (s.id)}
          <li><a href={`#cat-${s.id}`}><span>{s.name}</span><span class="label num">{s.items.length}</span></a></li>
        {/each}
      </ul>
    </nav>
  </aside>

  <div class="sections">
    {#if !db.loaded}
      <div class="panel pad stack">{#each Array(6) as _}<div class="skeleton" style="height: 24px"></div>{/each}</div>
    {:else if db.categories.length === 0}
      <div class="panel">
        <EmptyState title="No categories yet" text="Items live in categories, such as Coffee or Snacks. Add one first.">
          {#snippet action()}<button type="button" class="btn btn-primary" onclick={() => (categoriesOpen = true)}>Add a category</button>{/snippet}
        </EmptyState>
      </div>
    {:else if q && shownCount === 0}
      <div class="panel"><EmptyState title={`Nothing matches "${query.trim()}"`} text="Check the spelling, or search for a flavor instead." /></div>
    {:else}
      {#each sections as s (s.id)}
        {#if !q || s.items.length}
          <section id={`cat-${s.id}`} class="cat" aria-labelledby={`h-${s.id}`}>
            <div class="head"><h2 id={`h-${s.id}`}>{s.name}</h2><p class="label">{s.items.length} {s.items.length === 1 ? 'item' : 'items'}</p></div>
            {#if s.items.length === 0}
              <p class="muted empty-cat">No items in {s.name} yet.</p>
            {:else}
              <ul class="items">
                {#each s.items as item (item.id)}
                  {@const low = isItemLowStock(item.id, db.recipes, db.inventory)}
                  {@const sizes = sizesLine(item)}
                  <li class="it" class:off={!item.is_available}>
                    <button type="button" class="it-main" onclick={() => edit(item)} aria-label="Edit {item.name}">
                      <span class="ico"><ProductIcon iconKey={resolveEffectiveProductIconKey(item)} size={22} /></span>
                      <span class="it-text">
                        <span class="it-name">{item.name}</span>
                        {#if item.flavors}<span class="sub">{item.flavors.split('|').join(', ')}</span>{/if}
                        {#if sizes}<span class="sub">{sizes}</span>{/if}
                      </span>
                      <span class="it-end">
                        <span class="num">from {formatPrice(startingPrice(item.variants_json))}</span>
                        <span class="tags">
                          {#if !item.is_available}<span class="tag">Unavailable</span>{/if}
                          {#if deals[item.id]?.active}<span class="tag">{deals[item.id].percent_off}% off</span>{/if}
                          {#if low}<span class="tag tag-warn"><TriangleAlert aria-hidden="true" /> Low stock</span>{/if}
                        </span>
                      </span>
                    </button>
                    {#if isOnlineDealItem(item)}
                      <button type="button" class="btn btn-sm" aria-label="Edit deal for {item.name}" disabled={dealBusy} onclick={() => editDeal(item)}>Deal</button>
                    {/if}
                    <button type="button" class="btn btn-icon btn-sm del" aria-label="Remove {item.name}" title="Remove" onclick={() => deleteItem(item)}><Trash2 aria-hidden="true" /></button>
                  </li>
                {/each}
              </ul>
            {/if}
          </section>
        {/if}
      {/each}
    {/if}
  </div>
</div>

<ProductEditor bind:open={editorOpen} item={editing} />

<Sheet bind:open={dealOpen} title={dealItem ? `Deal for ${dealItem.name}` : 'Menu deal'} width="440px">
  <form class="stack" onsubmit={saveDeal}>
    <p class="muted">The deal reduces the item price before extras. Customers can apply coins to the discounted subtotal.</p>
    {#if dealError}
      <p role="alert">{dealError}</p>
    {:else}
      <label class="field"><span class="label">Percent off</span><input class="input" type="number" min="1" max="99" step="1" required bind:value={dealPercent} disabled={dealBusy} /></label>
      <label><input type="checkbox" bind:checked={dealActive} disabled={dealBusy} /> Active on Connect checkout</label>
    {/if}
    <button type="submit" class="btn btn-primary" disabled={!validDeal || dealBusy || !!dealError}>Save deal</button>
  </form>
</Sheet>

<Sheet bind:open={categoriesOpen} title="Categories" width="480px" onclose={() => (deleting = null)}>
  <form class="add" onsubmit={addCategory}>
    <label class="field grow"><span class="label">New category</span><input class="input" bind:value={newCategory} placeholder="e.g. Milk tea" maxlength="40" /></label>
    <button type="submit" class="btn btn-primary" disabled={!newCategory.trim() || busy}>Add</button>
  </form>
  <ul class="list panel">
    {#each db.categories as c (c.id)}
      {@const count = db.items.filter((i) => i.category_id === c.id).length}
      <li>
        <span>{c.name}<span class="sub">{count} {count === 1 ? 'item' : 'items'}</span></span>
        <button type="button" class="btn btn-sm btn-danger" onclick={() => { deleting = c; confirmText = ''; }}>Delete</button>
      </li>
    {:else}
      <li class="muted">No categories yet.</li>
    {/each}
  </ul>
  {#if deleting}
    {@const count = db.items.filter((i) => i.category_id === deleting.id).length}
    <div class="confirm panel pad stack" role="alertdialog" aria-labelledby="del-title">
      <h3 id="del-title">Delete {deleting.name}?</h3>
      <p>This also deletes its {count} {count === 1 ? 'item' : 'items'} from every tablet, and it can't be undone.</p>
      <label class="field"><span class="label">Type {deleting.name} to confirm</span><input class="input" bind:value={confirmText} autocomplete="off" /></label>
      <div class="btn-row">
        <button type="button" class="btn" onclick={() => (deleting = null)}>Keep it</button>
        <button type="button" class="btn btn-danger" disabled={confirmText.trim().toLowerCase() !== deleting.name.toLowerCase() || busy} onclick={deleteCategory}>Delete category and items</button>
      </div>
    </div>
  {/if}
</Sheet>

<style>
  .catalog { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 9fr); gap: var(--s-7); align-items: start; }
  .index { position: sticky; top: calc(var(--nav) + var(--s-6)); display: flex; flex-direction: column; gap: var(--s-4); }
  .search { position: relative; display: block; }
  .search :global(svg) { position: absolute; left: 14px; top: 13px; color: var(--muted); pointer-events: none; }
  .search .input { padding-left: 42px; }
  .index ul { list-style: none; margin: 0; padding: 0; }
  .index a { display: flex; justify-content: space-between; gap: var(--s-3); min-height: 40px; align-items: center; padding: 0 var(--s-3); border-radius: var(--r-sm); text-decoration: none; color: var(--ink-2); transition: background-color 150ms var(--ease-out), color 150ms var(--ease-out); }
  @media (hover: hover) and (pointer: fine) { .index a:hover { background: var(--hover-fill); color: var(--ink); } }

  .sections { display: flex; flex-direction: column; gap: var(--s-7); }
  .cat { scroll-margin-top: calc(var(--nav) + var(--s-5)); display: flex; flex-direction: column; gap: var(--s-4); }
  .cat h2 { font-size: var(--t-h3); }
  .empty-cat { padding: var(--s-4) 0; }
  .items { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 340px), 1fr)); gap: var(--s-3); }
  .it { position: relative; display: flex; background: var(--surface); border: 1px solid var(--line); border-radius: var(--r-md); transition: transform 180ms var(--ease-out), border-color 180ms var(--ease-out); }
  @media (hover: hover) and (pointer: fine) { .it:hover { transform: translateY(-2px); border-color: var(--line-strong); } }
  .it-main { flex: 1; min-width: 0; display: grid; grid-template-columns: 44px minmax(0, 1fr) auto; gap: var(--s-3); align-items: start; padding: var(--s-4); padding-right: 48px; border: 0; background: none; text-align: left; cursor: pointer; border-radius: inherit; }
  .ico { width: 44px; height: 44px; border-radius: var(--r-sm); background: var(--g-alt); display: grid; place-items: center; color: var(--action-ink); }
  .it-text { display: flex; flex-direction: column; min-width: 0; }
  .it-name { font-weight: 600; font-size: var(--t-body); }
  .it-text .sub { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .it-end { display: flex; flex-direction: column; align-items: flex-end; gap: var(--s-1); font-size: var(--t-small); white-space: nowrap; }
  .tags { display: flex; gap: var(--s-1); }
  .it.off .it-name, .it.off .ico { opacity: .55; }
  .del { position: absolute; top: var(--s-2); right: var(--s-2); color: var(--muted); }
  @media (hover: hover) and (pointer: fine) { .del:hover:not(:disabled) { color: var(--danger); } }

  .add { display: flex; gap: var(--s-3); align-items: flex-end; }
  .grow { flex: 1; }
  .confirm { border-color: var(--danger); }
  .confirm h3 { font-size: var(--t-card); }

  @media (max-width: 1023px) {
    .catalog { grid-template-columns: minmax(0, 1fr); gap: var(--s-5); }
    .index { position: static; }
    .index nav ul { display: flex; gap: var(--s-2); overflow-x: auto; scrollbar-width: none; }
    .index a { white-space: nowrap; background: var(--g-alt); border-radius: var(--r-btn); padding: 0 var(--s-4); gap: var(--s-2); }
  }
</style>
