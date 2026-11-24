<script>
  import '@fontsource-variable/inter';
  import '../app.css';
  import { page } from '$app/state';
  import { afterNavigate } from '$app/navigation';
  import { Menu, RefreshCw, LogOut, Sun, Moon, X, CircleAlert } from '@lucide/svelte';
  import { supabase } from '$lib/supabase.js';
  import { db, loadAll, refresh, startRealtime, resetData } from '$lib/data.svelte.js';
  import Toasts from '$lib/components/Toasts.svelte';
  import ShopSwitch from '$lib/components/ShopSwitch.svelte';

  let { children } = $props();

  // Six plain words. Stock lives under Menu: it's the ingredients the menu is made from.
  const NAV = [
    { href: '/', label: 'Sales', match: (p) => p === '/' },
    { href: '/orders', label: 'Orders', match: (p) => p.startsWith('/orders') },
    { href: '/expenses', label: 'Expenses', match: (p) => p.startsWith('/expenses') },
    { href: '/menu', label: 'Menu', match: (p) => p.startsWith('/menu') || p.startsWith('/stock') },
    { href: '/rewards', label: 'Rewards', match: (p) => p.startsWith('/rewards') },
    { href: '/settings', label: 'Website', match: (p) => p.startsWith('/settings') },
  ];

  let session = $state(null);
  let checking = $state(true);
  let email = $state('');
  let password = $state('');
  let signingIn = $state(false);
  let signInError = $state('');
  let theme = $state(document.documentElement.dataset.theme || 'light');
  let menu = $state();

  const current = $derived(NAV.find((n) => n.match(page.url.pathname)));

  $effect(() => {
    if (!supabase) {
      checking = false;
      return;
    }
    supabase.auth.getSession().then(({ data }) => {
      session = data.session;
      checking = false;
    });
    const { data } = supabase.auth.onAuthStateChange((_event, next) => {
      session = next;
    });
    return () => data.subscription.unsubscribe();
  });

  $effect(() => {
    if (session && !db.loaded) {
      loadAll();
      startRealtime();
    }
    if (!session && !checking) resetData();
  });

  afterNavigate(() => menu?.close());

  async function signIn(event) {
    event.preventDefault();
    signingIn = true;
    signInError = '';
    const { error } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
    signingIn = false;
    if (error) signInError = error.message === 'Invalid login credentials' ? 'Wrong email or password.' : error.message;
    else password = '';
  }

  function toggleTheme() {
    theme = theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem('bnc-theme', theme);
    } catch {
      // Private browsing: the choice just won't stick.
    }
  }
</script>

<svelte:head>
  <title>{current ? `${current.label} · Brew ni Cat POS` : 'Brew ni Cat POS'}</title>
</svelte:head>

{#if !supabase}
  <main class="gate wrap">
    <div class="gate-form">
      <CircleAlert size={28} class="danger-text" aria-hidden="true" />
      <h1>Supabase isn't set up for this deployment</h1>
      <p class="muted">Set NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY, then redeploy.</p>
    </div>
  </main>
{:else if checking}
  <main class="gate wrap" aria-busy="true"><p class="muted">Loading…</p></main>
{:else if !session}
  <main class="gate wrap">
    <div class="gate-type enter" aria-hidden="true">
      <img src="/brand-mark.png" alt="" width="64" height="64" />
      <p class="gate-name">Brew ni&nbsp;Cat</p>
      <p class="gate-line">Sales, orders and the menu from every tablet, in one place.</p>
    </div>
    <form class="gate-form enter-2" onsubmit={signIn}>
      <h1>Sign in</h1>
      <p class="muted">Use the store account. The tablets keep working while you're here.</p>
      <label class="field">
        <span class="label">Email</span>
        <input class="input" type="email" autocomplete="username" required bind:value={email} />
      </label>
      <label class="field">
        <span class="label">Password</span>
        <input class="input" type="password" autocomplete="current-password" required bind:value={password} aria-invalid={signInError ? 'true' : undefined} aria-describedby="sign-in-error" />
        <span class="hint error" id="sign-in-error" role="alert">{signInError}</span>
      </label>
      <button class="btn btn-primary btn-block" type="submit" disabled={signingIn}>{signingIn ? 'Signing in…' : 'Sign in'}</button>
    </form>
  </main>
{:else}
  <header class="nav">
    <div class="wrap nav-row">
      <a class="skip" href="#main">Skip to content</a>
      <a class="logo" href="/" aria-label="Brew ni Cat POS, sales">
        <img src="/brand-mark.png" alt="" width="32" height="32" />
        <span>Brew ni Cat</span>
      </a>
      <nav aria-label="Panel" class="words">
        {#each NAV as item (item.href)}
          <a href={item.href} aria-current={item.match(page.url.pathname) ? 'page' : undefined}>{item.label}</a>
        {/each}
      </nav>
      <div class="tools">
        <ShopSwitch />
        <span class="live" class:on={db.live} title={db.live ? 'Changes from the tablets appear as they happen.' : 'Not receiving live changes. Refresh to load the latest.'}>
          <span class="dot"></span><span class="live-word">{db.live ? 'Live' : 'Offline'}</span>
        </span>
        <button type="button" class="btn btn-icon" onclick={refresh} disabled={db.refreshing} aria-label="Refresh" title="Refresh">
          <RefreshCw class={db.refreshing ? 'spin' : ''} aria-hidden="true" />
        </button>
        <button type="button" class="btn btn-icon" onclick={toggleTheme} aria-label={theme === 'dark' ? 'Light appearance' : 'Dark appearance'} title={theme === 'dark' ? 'Light appearance' : 'Dark appearance'}>
          {#if theme === 'dark'}<Sun aria-hidden="true" />{:else}<Moon aria-hidden="true" />{/if}
        </button>
        <button type="button" class="btn btn-icon wide-only" onclick={() => supabase.auth.signOut()} aria-label="Sign out" title="Sign out">
          <LogOut aria-hidden="true" />
        </button>
        <button type="button" class="btn menu-btn" onclick={() => menu.showModal()} aria-haspopup="dialog">
          <Menu aria-hidden="true" /> Menu
        </button>
      </div>
    </div>
  </header>

  <dialog class="menu" bind:this={menu} aria-label="Panel sections">
    <div class="menu-top wrap">
      <span class="logo"><img src="/brand-mark.png" alt="" width="32" height="32" /><span>Brew ni Cat</span></span>
      <button type="button" class="btn btn-icon" onclick={() => menu.close()} aria-label="Close"><X aria-hidden="true" /></button>
    </div>
    <nav class="wrap" aria-label="Panel">
      {#each NAV as item (item.href)}
        <a href={item.href} aria-current={item.match(page.url.pathname) ? 'page' : undefined}>{item.label}</a>
      {/each}
      <button type="button" class="menu-out" onclick={() => supabase.auth.signOut()}>Sign out</button>
    </nav>
  </dialog>

  <main id="main" class="wrap main">
    {@render children()}
  </main>
{/if}

<Toasts />

<style>
  /* Sign in: the name set large beside a short form. */
  .gate { min-height: 100dvh; display: grid; grid-template-columns: minmax(0, 7fr) minmax(0, 5fr); gap: var(--s-8); align-items: center; padding-block: var(--s-7); }
  .gate-type img { border-radius: var(--r-md); margin-bottom: var(--s-5); }
  .gate-name { font-size: var(--t-hero); font-weight: 700; letter-spacing: -.035em; line-height: .95; }
  .gate-line { margin-top: var(--s-5); font-size: var(--t-card); color: var(--ink-2); max-width: 26ch; }
  .gate-form { display: flex; flex-direction: column; gap: var(--s-5); max-width: 400px; width: 100%; }
  .gate-form h1 { font-size: var(--t-h2); }
  @media (max-width: 860px) {
    .gate { grid-template-columns: minmax(0, 1fr); gap: var(--s-7); align-content: center; }
    .gate-name { font-size: clamp(44px, 12vw, 64px); }
    .gate-line { font-size: var(--t-body); }
  }

  /* The fixed bar: opaque, one hairline, the same at every scroll depth. */
  .nav { position: fixed; inset: 0 0 auto; z-index: 10; height: calc(var(--nav) + env(safe-area-inset-top)); padding-top: env(safe-area-inset-top); background: var(--g-main); border-bottom: 1px solid var(--line); }
  .nav-row { height: var(--nav); display: flex; align-items: center; gap: var(--s-6); }
  .skip { position: absolute; left: var(--gut); top: -100px; padding: var(--s-2) var(--s-4); border-radius: var(--r-sm); background: var(--ink); color: var(--g-main); z-index: 11; }
  .skip:focus { top: 12px; }
  .logo { display: flex; align-items: center; gap: var(--s-3); text-decoration: none; font-weight: 650; font-size: var(--t-card); letter-spacing: -.01em; white-space: nowrap; }
  .logo img { border-radius: var(--r-sm); }
  .words { display: flex; gap: var(--s-5); }
  .words a { position: relative; padding: var(--s-2) 0; font-size: 15px; font-weight: 500; color: var(--ink-2); text-decoration: none; white-space: nowrap; transition: color 150ms var(--ease-out); }
  .words a::after { content: ''; position: absolute; left: 0; right: 0; bottom: 2px; height: 1px; background: currentColor; transform: scaleX(0); transform-origin: right; transition: transform 150ms var(--ease-out); }
  @media (hover: hover) and (pointer: fine) { .words a:hover { color: var(--ink); } .words a:hover::after { transform: scaleX(1); transform-origin: left; } }
  .words a[aria-current='page'] { color: var(--ink); }
  .words a[aria-current='page']::after { height: 2px; bottom: -1px; transform: scaleX(1); background: var(--action); }
  .tools { margin-left: auto; display: flex; align-items: center; gap: var(--s-1); }
  .live { display: inline-flex; align-items: center; gap: var(--s-2); margin-right: var(--s-2); font-size: var(--t-label); color: var(--muted); }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--field-edge); }
  .live.on .dot { background: var(--action); }
  .menu-btn { display: none; }
  .main { padding-top: calc(var(--nav) + env(safe-area-inset-top) + var(--s-7)); padding-bottom: var(--s-9); display: flex; flex-direction: column; gap: var(--s-7); }

  /* Under 1024 the words move into a full-screen menu; the bar keeps the tools beside it. */
  @media (max-width: 1023px) {
    .words { display: none; }
    .menu-btn { display: inline-flex; padding: 0 var(--s-4); }
    .wide-only { display: none; }
  }
  @media (max-width: 640px) {
    .nav { --nav: 56px; }
    .live-word { display: none; }
    .logo span { display: none; }
    .main { padding-top: calc(56px + env(safe-area-inset-top) + var(--s-5)); gap: var(--s-6); }
  }

  .menu { position: fixed; inset: 0; width: 100vw; max-width: 100vw; height: 100dvh; max-height: 100dvh; margin: 0; padding: env(safe-area-inset-top) 0 0; border: 0; background: var(--g-main); color: var(--ink); }
  .menu[open] { animation: drop 200ms var(--ease-out); }
  @keyframes drop { from { opacity: 0; transform: translateY(-6px); } }
  .menu::backdrop { background: var(--g-main); }
  .menu-top { height: var(--nav); display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--line); }
  .menu nav { display: flex; flex-direction: column; padding-top: var(--s-3); }
  .menu nav a, .menu-out { display: flex; align-items: center; min-height: 56px; font-size: var(--t-h3); font-weight: 600; text-decoration: none; border: 0; border-bottom: 1px solid var(--line); background: none; text-align: left; padding: 0; cursor: pointer; }
  .menu nav a[aria-current='page'] { color: var(--action-ink); }
  .menu-out { color: var(--muted); font-weight: 500; border-bottom: 0; }
</style>
