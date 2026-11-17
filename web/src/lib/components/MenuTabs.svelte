<script>
  import { page } from '$app/state';
  import { UtensilsCrossed, Package } from '@lucide/svelte';
</script>

<nav class="tabs" aria-label="Menu sections">
  <a href="/menu" aria-current={page.url.pathname.startsWith('/menu') ? 'page' : undefined}><UtensilsCrossed aria-hidden="true" /> Items</a>
  <a href="/stock" aria-current={page.url.pathname.startsWith('/stock') ? 'page' : undefined}><Package aria-hidden="true" /> Stock</a>
</nav>
