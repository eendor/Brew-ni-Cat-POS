<script>
  import { onMount } from 'svelte';
  import { Plus, Trash2, CircleCheck, CircleAlert, TriangleAlert } from '@lucide/svelte';
  import { supabase } from '$lib/supabase.js';
  import { notify } from '$lib/toast.svelte.js';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import EmptyState from '$lib/components/EmptyState.svelte';
  import Sheet from '$lib/components/Sheet.svelte';
  import {
    CHANCE_OUT_OF, MAX_NAME, MAX_DESCRIPTION, chanceLabel, codeCheckMessage, draftFromReward,
    emptyDraft, normalizeCode, totalPrizeChance, validateRewardDraft,
  } from '$lib/rewards.js';

  let rewards = $state([]);
  let loading = $state(true);
  let loadError = $state('');

  let draft = $state(null);
  let editorOpen = $state(false);
  let saving = $state(false);
  let formError = $state('');
  let confirmDelete = $state(false);

  let code = $state('');
  let checking = $state(false);
  let checked = $state(null);

  const prizeTotal = $derived(totalPrizeChance(rewards));
  const canCheck = $derived(normalizeCode(code).length >= 4 && !checking);
  const canRedeem = $derived(!checking && checked?.ok && checked.status === 'valid');

  async function load() {
    loading = true;
    loadError = '';
    const { data, error } = await supabase
      .from('shop_rewards')
      .select('id, kind, name, description, points_cost, win_chance, stock, daily_limit, active, sort_order, updated_at')
      .order('kind')
      .order('sort_order');
    if (error) loadError = error.message || "Couldn't load the rewards.";
    else rewards = data ?? [];
    loading = false;
  }
  onMount(load);

  function startNew(kind) {
    const next = rewards.filter((r) => r.kind === kind).reduce((max, r) => Math.max(max, r.sort_order), 0) + 10;
    draft = emptyDraft(kind, next);
    formError = '';
    confirmDelete = false;
    editorOpen = true;
  }

  function startEdit(r) {
    draft = draftFromReward(r);
    formError = '';
    confirmDelete = false;
    editorOpen = true;
  }

  async function save(event) {
    event.preventDefault();
    const result = validateRewardDraft($state.snapshot(draft), rewards.map((r) => r.id));
    if (!result.ok) {
      formError = result.error;
      return;
    }
    saving = true;
    formError = '';
    const row = result.value;
    const { error } = draft.id
      ? await supabase.from('shop_rewards').update(row).eq('id', row.id)
      : await supabase.from('shop_rewards').insert(row);
    saving = false;
    if (error) {
      formError = error.message || "Couldn't save.";
      return;
    }
    notify(`Saved ${row.name}. The app shows it right away.`);
    editorOpen = false;
    await load();
  }

  async function toggle(r) {
    const { error } = await supabase.from('shop_rewards').update({ active: !r.active }).eq('id', r.id);
    if (error) notify(error.message, { tone: 'error' });
    else notify(r.active ? `${r.name} is hidden from customers.` : `${r.name} is on.`);
    await load();
  }

  async function remove() {
    saving = true;
    const { error } = await supabase.from('shop_rewards').delete().eq('id', draft.id);
    saving = false;
    if (error) {
      formError = error.message;
      return;
    }
    notify(`Deleted ${draft.name}.`);
    editorOpen = false;
    await load();
  }

  async function checkCode(redeem) {
    if (normalizeCode(code).length < 4) return;
    checking = true;
    const { data, error } = await supabase.rpc('verify_reward_code', { p_code: code.trim(), p_redeem: redeem });
    checked = error ? { ok: false, error: 'network' } : data;
    checking = false;
  }

  function meta(r) {
    const parts = [r.kind === 'reward' ? `${r.points_cost} ${r.points_cost === 1 ? 'coin' : 'coins'}` : `${r.win_chance} in ${CHANCE_OUT_OF} · ${chanceLabel(r.win_chance ?? 1)}`];
    if (r.stock !== null) parts.push(`${r.stock} left`);
    if (r.daily_limit !== null) parts.push(`${r.daily_limit} a day`);
    return parts.join(' · ');
  }
</script>

<PageHeader title="Rewards" subtitle="What customers can trade coins for in the app, and what the game can give away." />

<section class="counter" aria-labelledby="code-title">
  <div class="counter-text">
    <h2 id="code-title">Check a code</h2>
    <p class="muted">Type the code from the customer's phone. Check it, then mark it used when you hand the item over.</p>
  </div>
  <form class="code-form" onsubmit={(e) => { e.preventDefault(); checkCode(false); }}>
    <label class="visually-hidden" for="code">Reward code</label>
    <input id="code" class="input code" bind:value={code} oninput={() => (checked = null)} placeholder="RWD-AB12CD34" autocapitalize="characters" autocomplete="off" spellcheck="false" maxlength="20" />
    <button type="submit" class="btn" disabled={!canCheck}>{checking ? 'Checking…' : 'Check'}</button>
    <button type="button" class="btn btn-primary" disabled={!canRedeem} onclick={() => checkCode(true)}>Mark used</button>
  </form>
  {#if checked}
    <p class="result" class:ok={checked.ok} role="status">
      {#if checked.ok}<CircleCheck aria-hidden="true" />{:else}<CircleAlert aria-hidden="true" />{/if}
      {codeCheckMessage(checked)}
    </p>
  {/if}
</section>

{#if loadError}
  <p class="notice notice-error" role="alert">{loadError} <button type="button" class="link" onclick={load}>Try again</button></p>
{/if}

{#snippet column(kind, title, blurb)}
  {@const rows = rewards.filter((r) => r.kind === kind)}
  <section class="col stack" aria-labelledby={`h-${kind}`}>
    <div class="head">
      <div>
        <h2 id={`h-${kind}`}>{title}</h2>
        <p class="small muted">{blurb}</p>
      </div>
      <button type="button" class="btn btn-sm" onclick={() => startNew(kind)}><Plus aria-hidden="true" /> Add</button>
    </div>
    {#if kind === 'prize' && prizeTotal > CHANCE_OUT_OF}
      <p class="notice notice-warn"><TriangleAlert aria-hidden="true" /> The prizes that are on add up to {prizeTotal} in {CHANCE_OUT_OF}, so every play wins and the later prizes come up less often than set. Lower some chances.</p>
    {/if}
    {#if loading}
      <div class="panel pad stack">{#each Array(3) as _}<div class="skeleton" style="height: 24px"></div>{/each}</div>
    {:else if rows.length === 0}
      <div class="panel"><EmptyState title="None yet" text={kind === 'reward' ? 'Add something customers can buy with coins.' : 'Add a prize the game can hand out.'} /></div>
    {:else}
      <ul class="list panel">
        {#each rows as r (r.id)}
          <li class:off={!r.active}>
            <button type="button" class="row-main" onclick={() => startEdit(r)}>
              <span class="nm">{r.name}</span>
              <span class="sub">{meta(r)}</span>
            </button>
            <label class="on">
              <input type="checkbox" class="switch" checked={r.active} onchange={() => toggle(r)} />
              <span class="visually-hidden">Show {r.name} to customers</span>
            </label>
          </li>
        {/each}
      </ul>
    {/if}
  </section>
{/snippet}

<div class="cols">
  {@render column('reward', 'Rewards for coins', 'Customers spend coins on these.')}
  {@render column('prize', 'Game prizes', `Chances are out of ${CHANCE_OUT_OF} plays.`)}
</div>

<Sheet bind:open={editorOpen} side width="520px" title={draft ? `${draft.id ? 'Edit' : 'New'} ${draft.kind === 'reward' ? 'reward' : 'game prize'}` : ''}>
  {#if draft}
    <form id="reward-form" class="stack" onsubmit={save}>
      <label class="field"><span class="label">Name</span><input class="input" bind:value={draft.name} maxlength={MAX_NAME} /></label>
      <label class="field"><span class="label">Description (optional)</span><textarea class="textarea" rows="2" bind:value={draft.description} maxlength={MAX_DESCRIPTION}></textarea></label>
      {#if draft.kind === 'reward'}
        <label class="field"><span class="label">Coins it costs</span><input class="input" inputmode="numeric" bind:value={draft.coins} /></label>
      {:else}
        <label class="field">
          <span class="label">Chance, out of {CHANCE_OUT_OF} plays</span>
          <input class="input" inputmode="numeric" bind:value={draft.chance} aria-describedby="chance-hint" />
          <span class="hint" id="chance-hint">{Number(draft.chance) > 0 ? chanceLabel(Number(draft.chance)) : 'For example, 20 means about 1 in 50 plays.'}</span>
        </label>
      {/if}
      <div class="grid-2">
        <label class="field"><span class="label">Stock left</span><input class="input" inputmode="numeric" bind:value={draft.stock} placeholder="No limit" /></label>
        <label class="field"><span class="label">Most per day</span><input class="input" inputmode="numeric" bind:value={draft.dailyLimit} placeholder="No limit" /></label>
      </div>
      <label class="check-row"><input type="checkbox" class="switch" bind:checked={draft.active} /> <span>On, so customers can see it</span></label>
      {#if formError}<p class="notice notice-error" role="alert">{formError}</p>{/if}
      {#if confirmDelete}
        <div class="panel pad stack confirm" role="alertdialog" aria-labelledby="del-title">
          <h3 id="del-title">Delete {draft.name}?</h3>
          <p class="small">Codes already given stay valid. To hide it for now, turn it off instead.</p>
          <div class="btn-row">
            <button type="button" class="btn" onclick={() => (confirmDelete = false)}>Keep it</button>
            <button type="button" class="btn btn-danger" disabled={saving} onclick={remove}>Delete</button>
          </div>
        </div>
      {/if}
    </form>
  {/if}
  {#snippet footer()}
    {#if draft?.id && !confirmDelete}
      <button type="button" class="btn btn-quiet danger-text" onclick={() => (confirmDelete = true)}><Trash2 aria-hidden="true" /> Delete</button>
      <span class="spacer"></span>
    {/if}
    <button type="button" class="btn" onclick={() => (editorOpen = false)}>Cancel</button>
    <button type="submit" form="reward-form" class="btn btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
  {/snippet}
</Sheet>

<style>
  h2 { font-size: var(--t-h3); }
  .counter { display: grid; grid-template-columns: minmax(0, 5fr) minmax(0, 7fr); gap: var(--s-4) var(--s-7); align-items: center; padding: 0 0 var(--s-6); border-bottom: 1px solid var(--line); margin-bottom: var(--s-6); }
  .counter-text { display: flex; flex-direction: column; gap: var(--s-2); }
  .code-form { display: flex; gap: var(--s-2); }
  .code { flex: 1; min-width: 0; height: 56px; font-family: ui-monospace, 'SF Mono', Menlo, monospace; font-size: var(--t-h3); letter-spacing: .08em; text-transform: uppercase; }
  .code-form .btn { height: 56px; }
  .result { grid-column: 2; display: flex; gap: var(--s-2); align-items: flex-start; margin: 0; color: var(--danger); font-weight: 500; }
  .result.ok { color: var(--action-ink); }
  .cols { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--s-7); align-items: start; }
  .row-main { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: flex-start; border: 0; background: none; padding: var(--s-2) 0; text-align: left; cursor: pointer; color: inherit; }
  .nm { font-weight: 600; }
  li.off .nm { color: var(--muted); text-decoration: line-through; }
  .on { display: inline-flex; align-items: center; cursor: pointer; padding: var(--s-2); }
  .check-row { display: flex; gap: var(--s-3); align-items: center; cursor: pointer; }
  .confirm { border-color: var(--danger); }
  .confirm h3 { font-size: var(--t-card); }
  @media (max-width: 1023px) {
    .counter { grid-template-columns: minmax(0, 1fr); }
    .result { grid-column: 1; }
    .cols { grid-template-columns: minmax(0, 1fr); }
  }
  @media (max-width: 640px) {
    .code-form { flex-wrap: wrap; }
    .code { flex-basis: 100%; }
    .code-form .btn { flex: 1; }
  }
</style>
