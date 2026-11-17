<script>
  /** What's empty and the one thing to do about it. */
  let { title, text = '', action } = $props();
</script>

<div class="empty">
  <p class="title">{title}</p>
  {#if text}<p class="muted measure">{text}</p>{/if}
  {#if action}<div class="act">{@render action()}</div>{/if}
</div>

<style>
  .empty { padding: var(--s-7) var(--s-5); display: flex; flex-direction: column; gap: var(--s-2); }
  .title { font-weight: 650; font-size: var(--t-card); }
  .act { margin-top: var(--s-3); }
</style>
