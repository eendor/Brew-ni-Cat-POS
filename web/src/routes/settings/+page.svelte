<script>
  import { onMount } from 'svelte';
  import { Plus, Trash2, RefreshCw } from '@lucide/svelte';
  import { supabase } from '$lib/supabase.js';
  import { db, fetchItems } from '$lib/data.svelte.js';
  import { notify } from '$lib/toast.svelte.js';
  import PageHeader from '$lib/components/PageHeader.svelte';
  import SettingsSection from '$lib/components/SettingsSection.svelte';
  import {
    SITE_CONTENT_SECTIONS, WEEK_DAYS, WEEK_DAY_LABELS, validateSiteContentSection,
    findTakeoutBoxItem, getTakeoutBoxPesos, updateTakeoutBoxVariantsJson,
  } from '$lib/site-content.js';

  const INDEX = [
    ['notice', 'Banner'], ['hours', 'Hours'], ['contact', 'Contact'], ['takeout', 'Take-out box'],
    ['address', 'Address'], ['payments', 'Payments'], ['social', 'Social links'], ['riders', 'Delivery partners'],
    ['about', 'About page'], ['gallery', 'Gallery'],
  ];

  let content = $state({});
  let updated = $state({});
  let loading = $state(true);
  let loadError = $state('');
  let saving = $state('');
  let errors = $state({});

  const takeoutItem = $derived(findTakeoutBoxItem(db.items));
  const takeoutPesos = $derived(getTakeoutBoxPesos(takeoutItem));
  let fee = $state('');
  $effect(() => {
    if (takeoutPesos !== null) fee = String(takeoutPesos);
  });

  async function load() {
    loading = true;
    loadError = '';
    const { data, error } = await supabase.from('site_content').select('section, content, updated_at');
    if (error) {
      loadError = error.message || "Couldn't load the website content.";
    } else {
      const next = {};
      const stamps = {};
      for (const row of data ?? []) {
        if (!SITE_CONTENT_SECTIONS.includes(row.section)) continue;
        next[row.section] = row.content;
        if (row.updated_at) stamps[row.section] = row.updated_at;
      }
      content = next;
      updated = stamps;
      errors = {};
    }
    loading = false;
  }
  onMount(load);

  function stamp(key) {
    const iso = updated[key];
    if (!iso) return 'Not saved yet';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return 'Saved';
    return `Updated ${d.toLocaleDateString([], { month: 'short', day: 'numeric' })} at ${d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}`;
  }

  async function save(section) {
    const checked = validateSiteContentSection(section, $state.snapshot(content[section]));
    if (!checked.ok) {
      errors[section] = checked.error;
      return;
    }
    saving = section;
    errors[section] = '';
    const { data, error } = await supabase.from('site_content').update({ content: checked.value }).eq('section', section).select('updated_at').single();
    saving = '';
    if (error) {
      errors[section] = error.message || "Couldn't save.";
      return;
    }
    if (data?.updated_at) updated[section] = data.updated_at;
    notify('Saved and recorded in the audit log. Live on the website within 60 seconds.');
  }

  async function saveFee() {
    if (!takeoutItem) {
      errors.takeout = "There's no Take-out Box item on the menu.";
      return;
    }
    const pesos = Number(fee);
    if (!Number.isFinite(pesos) || pesos <= 0 || Math.round(pesos) !== pesos) {
      errors.takeout = 'Use a whole peso amount above ₱0.';
      return;
    }
    saving = 'takeout';
    errors.takeout = '';
    const { error } = await supabase.from('items').update({ variants_json: updateTakeoutBoxVariantsJson(takeoutItem.variants_json, pesos) }).eq('id', takeoutItem.id);
    saving = '';
    if (error) {
      errors.takeout = error.message || "Couldn't save the fee.";
      return;
    }
    await fetchItems();
    notify(`The take-out box is now ₱${pesos}.`);
  }

  const oneLine = (v) => v.replace(/[\r\n]+/g, ' ');
</script>

<PageHeader title="Website" subtitle="What the public website says about the shop. Saved changes show up within a minute.">
  {#snippet actions()}
    <button type="button" class="btn" onclick={load} disabled={loading}><RefreshCw aria-hidden="true" /> Reload</button>
  {/snippet}
</PageHeader>

{#if loadError}
  <p class="notice notice-error" role="alert">{loadError} <button type="button" class="link" onclick={load}>Try again</button></p>
{/if}

<div class="settings">
  <nav class="index" aria-label="Website sections">
    <ul>
      {#each INDEX as [id, label] (id)}<li><a href={`#${id}`}>{label}</a></li>{/each}
    </ul>
  </nav>

  <div class="blocks">
    {#if loading}
      <div class="panel pad stack">{#each Array(8) as _}<div class="skeleton" style="height: 24px"></div>{/each}</div>
    {:else}
      {#if content.notice}
        <SettingsSection id="notice" title="Banner" text="A line shown above every page. Leave it empty to hide the banner." updated={stamp('notice')} error={errors.notice} saving={saving === 'notice'} onsave={() => save('notice')}>
          <label class="field"><span class="label">Banner text</span><textarea class="textarea" rows="2" maxlength="300" value={content.notice.text} oninput={(e) => (content.notice.text = oneLine(e.currentTarget.value))} placeholder="Holiday hours this Friday: 1 PM to 8 PM"></textarea></label>
        </SettingsSection>
      {/if}

      {#if content.hours}
        <SettingsSection id="hours" title="Hours" text="Turn a day off to show it as closed." updated={stamp('hours')} error={errors.hours} saving={saving === 'hours'} onsave={() => save('hours')}>
          <ul class="days">
            {#each WEEK_DAYS as day (day)}
              {@const h = content.hours.days[day]}
              <li>
                <label class="day"><input type="checkbox" class="switch" checked={h !== null} onchange={(e) => (content.hours.days[day] = e.currentTarget.checked ? { opens: '11:00', closes: '21:00' } : null)} /> <span>{WEEK_DAY_LABELS[day]}</span></label>
                {#if h}
                  <span class="times">
                    <input class="input" type="time" bind:value={h.opens} aria-label="{WEEK_DAY_LABELS[day]} opens" />
                    <span class="muted">to</span>
                    <input class="input" type="time" bind:value={h.closes} aria-label="{WEEK_DAY_LABELS[day]} closes" />
                  </span>
                {:else}
                  <span class="muted">Closed</span>
                {/if}
              </li>
            {/each}
          </ul>
          <label class="field"><span class="label">Note under the hours</span><input class="input" maxlength="300" bind:value={content.hours.note} /></label>
        </SettingsSection>
      {/if}

      {#if content.contact}
        <SettingsSection id="contact" title="Contact" updated={stamp('contact')} error={errors.contact} saving={saving === 'contact'} onsave={() => save('contact')}>
          <div class="grid-2">
            <label class="field"><span class="label">Phone</span><input class="input" type="tel" maxlength="20" bind:value={content.contact.phone} /></label>
            <label class="field"><span class="label">Email</span><input class="input" type="email" maxlength="254" bind:value={content.contact.email} /></label>
          </div>
        </SettingsSection>
      {/if}

      <SettingsSection id="takeout" title="Take-out box" text="The website reads this from the Take-out Box item on the menu, so the tablets and the website always charge the same." error={errors.takeout} saving={saving === 'takeout'} saveLabel="Save fee" onsave={saveFee}>
        {#if takeoutItem}
          <label class="field narrow"><span class="label">Fee (₱)</span><input class="input" type="number" min="1" step="1" inputmode="numeric" bind:value={fee} /></label>
        {:else}
          <p class="muted">There's no Take-out Box item on the menu yet. Add one under Menu first.</p>
        {/if}
      </SettingsSection>

      {#if content.address}
        <SettingsSection id="address" title="Address" text="Shown in the footer and on the contact page." updated={stamp('address')} error={errors.address} saving={saving === 'address'} onsave={() => save('address')}>
          <label class="field"><span class="label">Street</span><input class="input" maxlength="80" bind:value={content.address.street} /></label>
          <div class="grid-2">
            <label class="field"><span class="label">Barangay or area</span><input class="input" maxlength="80" bind:value={content.address.area} /></label>
            <label class="field"><span class="label">Town</span><input class="input" maxlength="80" bind:value={content.address.town} /></label>
            <label class="field"><span class="label">Province</span><input class="input" maxlength="80" bind:value={content.address.province} /></label>
            <label class="field"><span class="label">Postal code</span><input class="input" inputmode="numeric" maxlength="4" bind:value={content.address.postalCode} /></label>
            <label class="field"><span class="label">Country</span><input class="input" maxlength="80" bind:value={content.address.country} /></label>
            <label class="field"><span class="label">Landmark (optional)</span><input class="input" maxlength="160" bind:value={content.address.landmark} /></label>
          </div>
          <label class="field"><span class="label">Google Maps link</span><input class="input" type="url" maxlength="500" bind:value={content.address.mapUrl} placeholder="https://maps.app.goo.gl/…" /></label>
        </SettingsSection>
      {/if}

      {#if content.payments}
        <SettingsSection id="payments" title="Payments" text="The ways to pay, listed at checkout. One to six." updated={stamp('payments')} error={errors.payments} saving={saving === 'payments'} onsave={() => save('payments')}>
          <ul class="rows">
            {#each content.payments.methods as _, i (i)}
              <li>
                <input class="input" maxlength="30" bind:value={content.payments.methods[i]} aria-label="Payment method {i + 1}" />
                <button type="button" class="btn btn-icon" aria-label="Remove payment method {i + 1}" disabled={content.payments.methods.length <= 1} onclick={() => content.payments.methods.splice(i, 1)}><Trash2 aria-hidden="true" /></button>
              </li>
            {/each}
          </ul>
          {#if content.payments.methods.length < 6}<div><button type="button" class="btn btn-sm" onclick={() => content.payments.methods.push('')}><Plus aria-hidden="true" /> Add a method</button></div>{/if}
          <label class="day"><input type="checkbox" class="switch" checked={content.payments.qrCode !== null} onchange={(e) => (content.payments.qrCode = e.currentTarget.checked ? { src: '/images/payment/gcash-qr.svg', alt: 'Brew ni Cat GCash and InstaPay QR code' } : null)} /> <span>Show the GCash QR code at pickup checkout</span></label>
          {#if content.payments.qrCode}
            <div class="grid-2">
              <label class="field"><span class="label">QR image path</span><input class="input" maxlength="200" bind:value={content.payments.qrCode.src} placeholder="/images/…" /></label>
              <label class="field"><span class="label">QR description</span><input class="input" maxlength="300" bind:value={content.payments.qrCode.alt} /></label>
            </div>
          {/if}
        </SettingsSection>
      {/if}

      {#if content.social}
        <SettingsSection id="social" title="Social links" text="Leave one empty to hide it." updated={stamp('social')} error={errors.social} saving={saving === 'social'} onsave={() => save('social')}>
          <label class="field"><span class="label">Facebook page</span><input class="input" type="url" maxlength="500" bind:value={content.social.facebook} placeholder="https://…" /></label>
          <label class="field"><span class="label">Facebook photos</span><input class="input" type="url" maxlength="500" bind:value={content.social.facebookPhotos} placeholder="https://…" /></label>
          <label class="field"><span class="label">TikTok</span><input class="input" type="url" maxlength="500" bind:value={content.social.tiktok} placeholder="https://…" /></label>
        </SettingsSection>
      {/if}

      {#if content.riders}
        <SettingsSection id="riders" title="Delivery partners" text="Independent riders customers can book. Up to eight." updated={stamp('riders')} error={errors.riders} saving={saving === 'riders'} onsave={() => save('riders')}>
          {#each content.riders.partners as p, i (i)}
            <fieldset class="group">
              <legend class="label">Partner {i + 1}</legend>
              <div class="grid-3">
                <label class="field"><span class="label">Name</span><input class="input" maxlength="80" bind:value={p.name} /></label>
                <label class="field"><span class="label">Link</span><input class="input" type="url" maxlength="500" bind:value={p.href} placeholder="https://facebook.com/…" /></label>
                <label class="field"><span class="label">Logo path</span><input class="input" maxlength="200" bind:value={p.logo} placeholder="/images/riders/…" /></label>
              </div>
              <button type="button" class="btn btn-sm btn-quiet danger-text" onclick={() => content.riders.partners.splice(i, 1)}><Trash2 aria-hidden="true" /> Remove {p.name || 'partner'}</button>
            </fieldset>
          {:else}
            <p class="muted">No delivery partners listed.</p>
          {/each}
          {#if content.riders.partners.length < 8}<div><button type="button" class="btn btn-sm" onclick={() => content.riders.partners.push({ name: '', href: 'https://www.facebook.com/', logo: '/images/riders/papas-delivery.jpg' })}><Plus aria-hidden="true" /> Add a partner</button></div>{/if}
        </SettingsSection>
      {/if}

      {#if content.about}
        <SettingsSection id="about" title="About page" text="The home page copy, the story and the cat house rules." updated={stamp('about')} error={errors.about} saving={saving === 'about'} onsave={() => save('about')}>
          <div class="grid-2">
            <label class="field"><span class="label">Opening date</span><input class="input" type="date" bind:value={content.about.openedOn} /></label>
            <label class="field"><span class="label">Menu summary line</span><input class="input" maxlength="80" bind:value={content.about.menuSummary} /></label>
            <label class="field"><span class="label">Hero title</span><input class="input" maxlength="80" bind:value={content.about.heroTitle} /></label>
            <label class="field"><span class="label">Home section title</span><input class="input" maxlength="80" bind:value={content.about.homeTitle} /></label>
          </div>
          {#each [['heroText', 'Hero text'], ['homeText', 'Home section text'], ['intro', 'About intro'], ['footerText', 'Footer text']] as [key, label] (key)}
            <label class="field"><span class="label">{label}</span><textarea class="textarea" rows="2" maxlength="300" value={content.about[key]} oninput={(e) => (content.about[key] = oneLine(e.currentTarget.value))}></textarea></label>
          {/each}
          <label class="field"><span class="label">Story heading</span><input class="input" maxlength="80" bind:value={content.about.storyTitle} /></label>
          <fieldset class="group">
            <legend class="label">Story, one to six paragraphs</legend>
            {#each content.about.story as _, i (i)}
              <div class="rows-item">
                <textarea class="textarea" rows="3" maxlength="1000" bind:value={content.about.story[i]} aria-label="Paragraph {i + 1}"></textarea>
                <button type="button" class="btn btn-icon" aria-label="Remove paragraph {i + 1}" disabled={content.about.story.length <= 1} onclick={() => content.about.story.splice(i, 1)}><Trash2 aria-hidden="true" /></button>
              </div>
            {/each}
            {#if content.about.story.length < 6}<div><button type="button" class="btn btn-sm" onclick={() => content.about.story.push('')}><Plus aria-hidden="true" /> Add a paragraph</button></div>{/if}
          </fieldset>
          <fieldset class="group">
            <legend class="label">Cat house rules, up to six</legend>
            {#each content.about.catRules as rule, i (i)}
              <div class="rows-item">
                <div class="stack grow">
                  <input class="input" maxlength="80" bind:value={rule.title} aria-label="Rule {i + 1} title" placeholder="Title" />
                  <textarea class="textarea" rows="2" maxlength="300" value={rule.text} oninput={(e) => (rule.text = oneLine(e.currentTarget.value))} aria-label="Rule {i + 1} text"></textarea>
                </div>
                <button type="button" class="btn btn-icon" aria-label="Remove rule {i + 1}" onclick={() => content.about.catRules.splice(i, 1)}><Trash2 aria-hidden="true" /></button>
              </div>
            {/each}
            {#if content.about.catRules.length < 6}<div><button type="button" class="btn btn-sm" onclick={() => content.about.catRules.push({ title: '', text: '' })}><Plus aria-hidden="true" /> Add a rule</button></div>{/if}
          </fieldset>
        </SettingsSection>
      {/if}

      {#if content.gallery}
        <SettingsSection id="gallery" title="Gallery" text={`${content.gallery.photos.length} of 80 photos. Keep the count divisible by 6 so the 2- and 3-column grids end flush.`} updated={stamp('gallery')} error={errors.gallery} saving={saving === 'gallery'} saveLabel="Save gallery" onsave={() => save('gallery')}>
          {#if content.gallery.photos.length < 80}
            <div><button type="button" class="btn btn-sm" onclick={() => content.gallery.photos.unshift({ src: '/images/shop/photo_148.jpg', alt: 'Brew ni Cat café moment' })}><Plus aria-hidden="true" /> Add a photo at the top</button></div>
          {/if}
          <ol class="photos">
            {#each content.gallery.photos as photo, i (i)}
              <li>
                <span class="label num pos">{i + 1}</span>
                <div class="stack grow">
                  <input class="input" maxlength="200" bind:value={photo.src} aria-label="Photo {i + 1} path" />
                  <input class="input" maxlength="300" bind:value={photo.alt} aria-label="Photo {i + 1} description" placeholder="What's in the photo" />
                  <div class="flags">
                    {#each [['featured', 'Featured'], ['people', 'People']] as [flag, label] (flag)}
                      <label class="day small"><input type="checkbox" class="check" checked={Boolean(photo[flag])} onchange={(e) => { if (e.currentTarget.checked) photo[flag] = true; else delete photo[flag]; }} /> {label}</label>
                    {/each}
                  </div>
                </div>
                <button type="button" class="btn btn-icon" aria-label="Remove photo {i + 1}" disabled={content.gallery.photos.length <= 1} onclick={() => content.gallery.photos.splice(i, 1)}><Trash2 aria-hidden="true" /></button>
              </li>
            {/each}
          </ol>
        </SettingsSection>
      {/if}
    {/if}
  </div>
</div>

<style>
  .settings { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 10fr); gap: var(--s-7); align-items: start; }
  .index { position: sticky; top: calc(var(--nav) + var(--s-6)); }
  .index ul { list-style: none; margin: 0; padding: 0; border-left: 1px solid var(--line); }
  .index a { display: flex; align-items: center; min-height: 36px; padding: 0 var(--s-4); margin-left: -1px; border-left: 2px solid transparent; text-decoration: none; color: var(--muted); font-size: var(--t-small); transition: color 150ms var(--ease-out), border-color 150ms var(--ease-out); }
  @media (hover: hover) and (pointer: fine) { .index a:hover { color: var(--ink); border-left-color: var(--line-strong); } }
  .blocks { min-width: 0; }
  .days, .rows, .photos { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
  .days li { display: flex; align-items: center; justify-content: space-between; gap: var(--s-4); min-height: 56px; border-bottom: 1px solid var(--line); }
  .days li:last-child { border-bottom: 0; }
  .day { display: inline-flex; align-items: center; gap: var(--s-3); cursor: pointer; }
  .times { display: inline-flex; align-items: center; gap: var(--s-2); }
  .times .input { width: 132px; }
  .rows { gap: var(--s-2); }
  .rows li, .rows-item { display: flex; gap: var(--s-2); align-items: flex-start; }
  .rows .input, .rows-item .textarea { flex: 1; }
  .grow { flex: 1; min-width: 0; }
  .narrow { max-width: 200px; }
  .group { border: 0; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--s-3); }
  .group + .group { padding-top: var(--s-4); border-top: 1px solid var(--line); }
  .group > .btn { align-self: flex-start; }
  .grid-3 { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--s-3); }
  .photos { gap: 0; max-height: 640px; overflow-y: auto; border-top: 1px solid var(--line); }
  .photos li { display: flex; gap: var(--s-3); align-items: flex-start; padding: var(--s-4) 0; border-bottom: 1px solid var(--line); }
  .pos { width: 28px; padding-top: 12px; color: var(--muted); }
  .flags { display: flex; gap: var(--s-5); }
  @media (max-width: 1023px) {
    .settings { grid-template-columns: minmax(0, 1fr); gap: var(--s-5); }
    .index { position: static; }
    .index ul { display: flex; gap: var(--s-2); overflow-x: auto; border: 0; scrollbar-width: none; }
    .index a { white-space: nowrap; border: 0; margin: 0; background: var(--g-alt); border-radius: var(--r-btn); color: var(--ink-2); }
  }
  @media (max-width: 640px) {
    .grid-3 { grid-template-columns: minmax(0, 1fr); }
    .days li { flex-wrap: wrap; padding: var(--s-2) 0; }
    .times .input { width: 116px; }
  }
</style>
