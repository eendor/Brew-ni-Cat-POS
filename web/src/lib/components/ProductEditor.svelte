<script>
  import { Plus, Trash2 } from '@lucide/svelte';
  import Sheet from './Sheet.svelte';
  import ProductIconPicker from './ProductIconPicker.svelte';
  import { supabase } from '$lib/supabase.js';
  import { db, fetchItems, fetchCategories, run } from '$lib/data.svelte.js';
  import { notify } from '$lib/toast.svelte.js';
  import { normalizeProductIconKey, resolveDefaultProductIconKey, stripIconColumnIfUnsupported } from '$lib/product-icons.js';
  import { parseFlavors, slugId, normalizeVariants, parseVariants } from '$lib/reports.js';

  /** Add or edit one menu item: name, category, icon, flavors and sizes with prices. */
  let { open = $bindable(false), item = null } = $props();

  let name = $state('');
  let categoryId = $state('');
  let icon = $state(null);
  let flavorsText = $state('');
  let variants = $state([]);
  let perFlavor = $state(false);
  let addingCategory = $state(false);
  let newCategory = $state('');
  let saving = $state(false);
  let error = $state('');
  let tried = $state(false);

  const flavors = $derived(parseFlavors(flavorsText));
  const defaultIcon = $derived(resolveDefaultProductIconKey(item?.id || '', categoryId));

  let openedFor = undefined;
  $effect(() => {
    if (!open) {
      openedFor = undefined;
      return;
    }
    if (openedFor === (item?.id ?? null)) return;
    openedFor = item?.id ?? null;
    error = '';
    tried = false;
    addingCategory = false;
    if (item) {
      name = item.name;
      categoryId = item.category_id;
      icon = normalizeProductIconKey(item.icon);
      flavorsText = (item.flavors || '').split('|').filter(Boolean).join(', ');
      const parsed = parseVariants(item.variants_json);
      variants = parsed.length ? parsed : [{ id: 'regular', name: 'Regular', basePrice: 0, priceByFlavor: {} }];
      perFlavor = parsed.some((v) => Object.keys(v.priceByFlavor || {}).length > 0);
    } else {
      name = '';
      categoryId = db.categories[0]?.id || '';
      icon = null;
      flavorsText = '';
      variants = [{ id: 'regular', name: 'Regular', basePrice: 0, priceByFlavor: {} }];
      perFlavor = false;
    }
  });

  function renameSize(v, value) {
    v.name = value;
    v.id = value.toLowerCase().replace(/[^a-z0-9]/g, '_') || v.id;
  }

  async function addCategory() {
    const label = newCategory.trim();
    if (!label) return;
    const existing = db.categories.find((c) => c.name.toLowerCase() === label.toLowerCase());
    if (existing) {
      categoryId = existing.id;
    } else {
      const id = slugId('cat_', label);
      try {
        await run(supabase.from('categories').insert({ id, name: label }));
        await fetchCategories();
        categoryId = id;
      } catch (e) {
        error = `Couldn't add the category: ${e.message || 'try again'}.`;
        return;
      }
    }
    newCategory = '';
    addingCategory = false;
  }

  async function save() {
    tried = true;
    if (!name.trim() || !categoryId) return;
    saving = true;
    error = '';
    const payload = {
      id: item?.id || `${slugId('item_', name)}_${Date.now().toString().slice(-4)}`,
      category_id: categoryId,
      name: name.trim(),
      flavors: flavors.join('|'),
      variants_json: JSON.stringify(normalizeVariants($state.snapshot(variants), flavors, perFlavor)),
      is_available: item ? item.is_available : true,
      icon: normalizeProductIconKey(icon),
    };
    let { error: err } = await supabase.from('items').upsert(payload);
    if (err) {
      const retry = stripIconColumnIfUnsupported(payload, err);
      if (retry.shouldRetry) ({ error: err } = await supabase.from('items').upsert(retry.fallbackPayload));
    }
    saving = false;
    if (err) {
      error = `Couldn't save: ${err.message}`;
      return;
    }
    await fetchItems();
    notify(item ? `Saved ${payload.name}.` : `Added ${payload.name} to the menu.`);
    open = false;
  }
</script>

<Sheet bind:open side width="600px" title={item ? `Edit ${item.name}` : 'New item'}>
  <label class="field">
    <span class="label">Name</span>
    <input class="input" bind:value={name} maxlength="80" aria-invalid={tried && !name.trim() ? 'true' : undefined} aria-describedby="name-hint" />
    {#if tried && !name.trim()}<span class="hint error" id="name-hint">Give the item a name.</span>{/if}
  </label>

  <div class="field">
    <label class="label" for="item-category">Category</label>
    {#if addingCategory}
      <div class="inline">
        <input class="input" bind:value={newCategory} placeholder="e.g. Milk tea" maxlength="40" onkeydown={(e) => e.key === 'Enter' && (e.preventDefault(), addCategory())} />
        <button type="button" class="btn" onclick={addCategory} disabled={!newCategory.trim()}>Add</button>
        <button type="button" class="btn btn-quiet" onclick={() => (addingCategory = false)}>Cancel</button>
      </div>
    {:else}
      <div class="inline">
        <select id="item-category" class="select" bind:value={categoryId}>
          {#each db.categories as c (c.id)}<option value={c.id}>{c.name}</option>{/each}
        </select>
        <button type="button" class="btn" onclick={() => (addingCategory = true)}><Plus aria-hidden="true" /> New</button>
      </div>
    {/if}
  </div>

  <ProductIconPicker bind:value={icon} defaultIconKey={defaultIcon} />

  <label class="field">
    <span class="label">Flavors</span>
    <input class="input" bind:value={flavorsText} placeholder="Matcha, Oreo, Taro" />
    <span class="hint">Separate them with commas. Leave empty if there's only one.</span>
  </label>

  <section class="stack">
    <div class="head">
      <h3>Sizes and prices</h3>
      {#if flavors.length > 0}
        <label class="perflavor"><input type="checkbox" class="switch" bind:checked={perFlavor} /> <span class="small">Price each flavor</span></label>
      {/if}
    </div>
    {#each variants as v, i (i)}
      <fieldset class="size panel">
        <legend class="visually-hidden">Size {i + 1}</legend>
        <div class="size-top">
          <label class="field grow"><span class="label">Size</span><input class="input" value={v.name} oninput={(e) => renameSize(v, e.currentTarget.value)} /></label>
          {#if !(perFlavor && flavors.length)}
            <label class="field price"><span class="label">Price (₱)</span><input class="input" type="number" min="0" step="1" inputmode="decimal" value={v.basePrice} oninput={(e) => (v.basePrice = parseFloat(e.currentTarget.value) || 0)} /></label>
          {/if}
          <button type="button" class="btn btn-icon" aria-label="Remove {v.name || 'this size'}" disabled={variants.length === 1} onclick={() => variants.splice(i, 1)}><Trash2 aria-hidden="true" /></button>
        </div>
        {#if perFlavor && flavors.length}
          <div class="flavor-prices">
            {#each flavors as f (f)}
              <label class="field"><span class="label">{f} (₱)</span><input class="input" type="number" min="0" step="1" inputmode="decimal" value={v.priceByFlavor?.[f] || 0} oninput={(e) => (v.priceByFlavor = { ...(v.priceByFlavor || {}), [f]: parseFloat(e.currentTarget.value) || 0 })} /></label>
            {/each}
          </div>
        {/if}
      </fieldset>
    {/each}
    <div><button type="button" class="btn btn-sm" onclick={() => variants.push({ id: 'size_' + Date.now().toString().slice(-4), name: '', basePrice: 0, priceByFlavor: {} })}><Plus aria-hidden="true" /> Add size</button></div>
  </section>

  {#if error}<p class="notice notice-error" role="alert">{error}</p>{/if}

  {#snippet footer()}
    <button type="button" class="btn" onclick={() => (open = false)}>Cancel</button>
    <button type="button" class="btn btn-primary" onclick={save} disabled={saving}>{saving ? 'Saving…' : item ? 'Save changes' : 'Add to menu'}</button>
  {/snippet}
</Sheet>

<style>
  h3 { font-size: var(--t-card); }
  .inline { display: flex; gap: var(--s-2); }
  .inline .input, .inline .select { flex: 1; }
  .perflavor { display: inline-flex; align-items: center; gap: var(--s-2); cursor: pointer; }
  .size { margin: 0; padding: var(--s-4); display: flex; flex-direction: column; gap: var(--s-3); }
  .size-top { display: flex; gap: var(--s-3); align-items: flex-end; }
  .grow { flex: 1; }
  .price { width: 140px; }
  .flavor-prices { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: var(--s-3); }
</style>
