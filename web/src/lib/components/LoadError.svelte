<script>
  import { CircleAlert } from '@lucide/svelte';
  import { refresh } from '$lib/data.svelte.js';

  /** Shown at the top of a page when a table it needs didn't load. */
  let { errors = [] } = $props();
</script>

{#if errors.length}
  <div class="notice notice-error" role="alert">
    <CircleAlert aria-hidden="true" />
    <p>Part of this page didn't load ({errors.join(' ')}), so what you see may be out of date. <button type="button" class="again" onclick={refresh}>Try again</button></p>
  </div>
{/if}

<style>
  .again { border: 0; background: none; padding: 0; color: inherit; font-weight: 650; text-decoration: underline; cursor: pointer; }
</style>
