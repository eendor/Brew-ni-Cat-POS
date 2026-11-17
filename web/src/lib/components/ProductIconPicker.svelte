<script>
  import { Sparkles } from '@lucide/svelte';
  import ProductIcon from './ProductIcon.svelte';
  import { PRODUCT_ICON_OPTIONS, getProductIconLabel, normalizeProductIconKey } from '$lib/product-icons.js';

  /** Pick one of the 30 icons, or Auto (the default for the item's id and category). */
  let { value = $bindable(null), defaultIconKey } = $props();
  const selected = $derived(normalizeProductIconKey(value));
  const autoLabel = $derived(getProductIconLabel(defaultIconKey));
</script>

<fieldset class="picker" data-testid="product-icon-picker">
  <legend class="label">Icon</legend>
  <div class="grid" role="radiogroup">
    <label class="opt" class:on={selected === null}>
      <input class="visually-hidden" type="radio" name="product-icon" checked={selected === null} onchange={() => (value = null)} />
      <Sparkles size={18} aria-hidden="true" />
      <span>Auto{autoLabel ? ` (${autoLabel})` : ''}</span>
    </label>
    {#each PRODUCT_ICON_OPTIONS as opt (opt.key)}
      <label class="opt" class:on={selected === opt.key} title={opt.label}>
        <input class="visually-hidden" type="radio" name="product-icon" checked={selected === opt.key} onchange={() => (value = opt.key)} />
        <ProductIcon iconKey={opt.key} size={18} />
        <span>{opt.label}</span>
      </label>
    {/each}
  </div>
</fieldset>

<style>
  .picker { border: 0; margin: 0; padding: 0; min-width: 0; }
  legend.label { padding: 0; margin-bottom: var(--s-2); }
  .grid {
    display: grid; grid-template-columns: repeat(auto-fill, minmax(9.5rem, 1fr)); gap: 4px;
    max-height: 13.5rem; overflow-y: auto; padding: 4px;
    border-radius: var(--r-sm); background: var(--surface);
    border: 1px solid var(--field-edge);
  }
  .opt {
    display: flex; align-items: center; gap: 8px; min-height: 40px; padding: 0 10px;
    border-radius: 6px; cursor: pointer; font-size: var(--t-small); color: var(--ink);
    transition: background-color 150ms var(--ease-out);
  }
  .opt span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .opt :global(svg) { flex: none; color: var(--muted); }
  @media (hover: hover) and (pointer: fine) { .opt:hover { background: var(--hover-fill); } }
  .opt.on { background: var(--ink); color: var(--g-main); font-weight: 600; }
  .opt.on :global(svg) { color: var(--g-main); }
  .opt:has(input:focus-visible) { outline: 2px solid var(--action); outline-offset: -2px; }
</style>
