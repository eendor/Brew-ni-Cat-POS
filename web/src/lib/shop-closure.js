import { WEEK_DAYS, isText } from './site-content.js';

/**
 * The owner's "Closed today" switch (Connect FR-111, FR-112), with the same rules as the tablets
 * (ShopClosure.kt). It is the `closure` row of `site_content`. The shop is open by default and
 * follows the saved hours. The switch only counts on the Philippine day it was turned on, so it
 * opens again by itself the next day.
 */
export const MAX_NOTE = 300;
export const SHOP_SECTIONS = ['closure', 'hours'];

export const SHOP_STATE_LABELS = {
  open: 'Open',
  'closed-today': 'Closed today',
  'outside-hours': 'Outside hours',
};

// The Philippines has one time zone, UTC+8, and no daylight saving.
const SHOP_OFFSET_MS = 8 * 60 * 60 * 1000;
const TIME = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;

const toMs = (now) => (now instanceof Date ? now.getTime() : Number(now));

/** The shop's own date (YYYY-MM-DD), weekday and time (HH:MM) for an instant. */
export function shopClock(now) {
  const iso = new Date(toMs(now) + SHOP_OFFSET_MS).toISOString();
  const weekday = new Date(toMs(now) + SHOP_OFFSET_MS).getUTCDay(); // 0 is Sunday
  return { date: iso.slice(0, 10), day: WEEK_DAYS[(weekday + 6) % 7], time: iso.slice(11, 16) };
}

/**
 * `{ days: { monday: { opens: '11:00', closes: '21:00' }, sunday: null, … } }` as a day map, or
 * null when the hours can't be read. Without the hours the shop is never shown as closed.
 */
export function parseHours(content) {
  const days = content?.days;
  if (typeof days !== 'object' || days === null) return null;
  const hours = {};
  for (const day of WEEK_DAYS) {
    if (!Object.prototype.hasOwnProperty.call(days, day)) return null;
    const value = days[day];
    if (value === null) {
      hours[day] = null;
      continue;
    }
    if (!TIME.test(value?.opens ?? '') || !TIME.test(value?.closes ?? '')) return null;
    hours[day] = { opens: value.opens, closes: value.closes };
  }
  return hours;
}

/** The `closure` and `hours` rows together, or null when there is no usable `closure` row. */
export function parseShopState(rows) {
  const bySection = Object.fromEntries((rows ?? []).map((row) => [row?.section, row]));
  const closure = bySection.closure;
  if (typeof closure?.content?.closed !== 'boolean') return null;
  const changedAt = Date.parse(closure.updated_at ?? '');
  return {
    closed: closure.content.closed,
    note: typeof closure.content.note === 'string' ? closure.content.note : '',
    changedAt: Number.isNaN(changedAt) ? null : changedAt,
    hours: parseHours(bySection.hours?.content),
  };
}

/** Whether Closed today still applies: it lasts only for the day it was turned on. */
export function closedToday(state, now = Date.now()) {
  if (!state?.closed) return false;
  if (state.changedAt === null || state.changedAt === undefined) return true;
  return shopClock(state.changedAt).date === shopClock(now).date;
}

/** 'open', 'closed-today' or 'outside-hours', the same answer the website and the tablets give. */
export function openState(state, now = Date.now()) {
  if (closedToday(state, now)) return 'closed-today';
  if (!state?.hours) return 'open';
  const clock = shopClock(now);
  const today = state.hours[clock.day];
  if (!today) return 'outside-hours';
  return clock.time >= today.opens && clock.time < today.closes ? 'open' : 'outside-hours';
}

/** The note as the server accepts it: one line, no spaces at the ends, at most 300 characters. */
export function cleanNote(text) {
  const oneLine = String(text ?? '').replace(/[\u0000-\u001f\u007f]+/g, ' ').trim();
  return Array.from(oneLine).slice(0, MAX_NOTE).join('').trim();
}

/** The `closure` row's content for a save; the note is cleared when the shop opens again. */
export function closureContent(closed, note = '') {
  const content = { closed: Boolean(closed), note: closed ? cleanNote(note) : '' };
  if (!isText(content.note, MAX_NOTE, { allowEmpty: true })) {
    throw new Error('The note has characters the website can’t show. Try plain text.');
  }
  return content;
}
