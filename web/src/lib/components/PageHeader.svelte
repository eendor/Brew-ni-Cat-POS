<script>
  /** The page's title, one line on what it's for, and its actions on the right. */
  let { title, subtitle = '', actions, tabs } = $props();
</script>

<header class="page-head">
  {#if tabs}<div class="enter">{@render tabs()}</div>{/if}
  <div class="row">
    <div class="titles">
      <h1 class="enter">{title}</h1>
      {#if subtitle}<p class="sub enter-2">{subtitle}</p>{/if}
    </div>
    {#if actions}<div class="btn-row enter-3">{@render actions()}</div>{/if}
  </div>
</header>

<style>
  .page-head { display: flex; flex-direction: column; gap: var(--s-5); }
  .row { display: flex; align-items: end; justify-content: space-between; gap: var(--s-5); flex-wrap: wrap; }
  .titles { min-width: 0; max-width: 62ch; }
  h1 { font-size: var(--t-h1); font-weight: 700; letter-spacing: -.03em; }
  .sub { margin-top: var(--s-2); color: var(--ink-2); font-size: var(--t-body); }
</style>
