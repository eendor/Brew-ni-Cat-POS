<script>
  import { X } from '@lucide/svelte';

  /**
   * A native modal <dialog>: focus stays inside, Escape closes it and the page behind is inert.
   * Clicking the backdrop or the close button closes it too.
   */
  let { open = $bindable(false), title, width = '560px', side = false, onclose = () => {}, children, footer, class: extra = '' } = $props();

  let dialog;

  $effect(() => {
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  });

  function handleClose() {
    if (open) open = false;
    onclose();
  }

  function backdrop(event) {
    if (event.target === dialog) dialog.close();
  }
</script>

<dialog
  bind:this={dialog}
  class="sheet {extra}"
  class:side
  style:--sheet-w={width}
  aria-labelledby="sheet-title"
  onclose={handleClose}
  onclick={backdrop}
>
  {#if open}
    <div class="sheet-inner">
      <header class="sheet-head">
        <h2 id="sheet-title">{title}</h2>
        <button type="button" class="btn btn-icon close" aria-label="Close" onclick={() => dialog.close()}>
          <X />
        </button>
      </header>
      <div class="sheet-body">
        {@render children()}
      </div>
      {#if footer}
        <footer class="sheet-foot">
          {@render footer()}
        </footer>
      {/if}
    </div>
  {/if}
</dialog>

<style>
  .close { color: var(--muted); margin: -4px 0; }
</style>
