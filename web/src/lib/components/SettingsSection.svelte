<script>
  /** One settings block: what it changes on the left, the form on the right, one Save at the bottom. */
  let { id, title, text = '', updated = '', error = '', saving = false, saveLabel = 'Save', onsave, children } = $props();
</script>

<section {id} class="block" aria-labelledby={`${id}-title`}>
  <div class="about">
    <h2 id={`${id}-title`}>{title}</h2>
    {#if text}<p class="muted small">{text}</p>{/if}
    {#if updated}<p class="label stamp">{updated}</p>{/if}
  </div>
  <form class="form panel pad stack" onsubmit={(e) => { e.preventDefault(); onsave(); }} novalidate>
    {@render children()}
    {#if error}<p class="notice notice-error" role="alert">{error}</p>{/if}
    <div class="foot"><button type="submit" class="btn btn-primary" disabled={saving}>{saving ? 'Saving…' : saveLabel}</button></div>
  </form>
</section>

<style>
  .block { display: grid; grid-template-columns: minmax(0, 4fr) minmax(0, 8fr); gap: var(--s-4) var(--s-7); padding: var(--s-7) 0; border-top: 1px solid var(--line); scroll-margin-top: calc(var(--nav) + var(--s-4)); }
  .block:first-child { border-top: 0; padding-top: 0; }
  .about { display: flex; flex-direction: column; gap: var(--s-2); }
  h2 { font-size: var(--t-h3); }
  .stamp { color: var(--muted); }
  .foot { display: flex; justify-content: flex-end; }
  @media (max-width: 1023px) { .block { grid-template-columns: minmax(0, 1fr); } }
</style>
