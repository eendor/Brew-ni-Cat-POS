import test from 'node:test';
import assert from 'node:assert/strict';
import {
  shopClock,
  parseHours,
  parseShopState,
  closedToday,
  openState,
  cleanNote,
  closureContent,
  MAX_NOTE,
} from '../src/lib/shop-closure.js';

const everyDay = { opens: '11:00', closes: '21:00' };
const hoursContent = {
  days: {
    monday: null,
    tuesday: everyDay,
    wednesday: everyDay,
    thursday: everyDay,
    friday: everyDay,
    saturday: everyDay,
    sunday: everyDay,
  },
  note: '',
};
// Wednesday, 25 November 2026, in Philippine time (UTC+8).
const at = (time) => Date.parse(`2026-11-25T${time}:00+08:00`);

const rows = (closure, updatedAt = '2026-11-25T02:00:00+00:00') => [
  { section: 'closure', content: closure, updated_at: updatedAt },
  { section: 'hours', content: hoursContent, updated_at: '2026-11-01T00:00:00+00:00' },
];

test('reads the clock in Philippine time, not the browser time zone', () => {
  // 23:30 UTC on Monday is already 07:30 on Tuesday in Manila.
  assert.deepEqual(shopClock(Date.parse('2026-11-23T23:30:00Z')), { date: '2026-11-24', day: 'tuesday', time: '07:30' });
  assert.deepEqual(shopClock(new Date('2026-11-29T15:59:00Z')), { date: '2026-11-29', day: 'sunday', time: '23:59' });
});

test('parses the hours and refuses hours it cannot read', () => {
  assert.deepEqual(parseHours(hoursContent).tuesday, everyDay);
  assert.equal(parseHours(hoursContent).monday, null);
  assert.equal(parseHours({ days: { monday: everyDay } }), null);
  assert.equal(parseHours({ days: { ...hoursContent.days, friday: { opens: '9 AM', closes: '21:00' } } }), null);
  assert.equal(parseHours(undefined), null);
});

test('parses the closure and hours rows together', () => {
  const state = parseShopState(rows({ closed: true, note: 'Back tomorrow at 1 PM' }));
  assert.equal(state.closed, true);
  assert.equal(state.note, 'Back tomorrow at 1 PM');
  assert.equal(state.changedAt, Date.parse('2026-11-25T02:00:00Z'));
  assert.deepEqual(state.hours.wednesday, everyDay);
  assert.equal(parseShopState([{ section: 'hours', content: hoursContent }]), null);
  assert.equal(parseShopState([{ section: 'closure', content: { note: '' } }]), null);
  assert.equal(parseShopState(null), null);
});

test('Closed today only counts on the Philippine day it was turned on', () => {
  // Turned on at 10:00 PM on Wednesday, Manila time (14:00 UTC).
  const state = parseShopState(rows({ closed: true, note: '' }, '2026-11-25T14:00:00Z'));
  assert.equal(closedToday(state, at('23:59')), true);
  assert.equal(closedToday(state, Date.parse('2026-11-26T00:00:00+08:00')), false);
  assert.equal(closedToday({ closed: true, changedAt: null, hours: null }, at('12:00')), true);
  assert.equal(closedToday({ closed: false, changedAt: at('12:00'), hours: null }, at('12:00')), false);
});

test('works out open, closed today and outside hours like the tablets', () => {
  const open = parseShopState(rows({ closed: false, note: '' }));
  assert.equal(openState(open, at('10:59')), 'outside-hours');
  assert.equal(openState(open, at('11:00')), 'open');
  assert.equal(openState(open, at('20:59')), 'open');
  assert.equal(openState(open, at('21:00')), 'outside-hours');
  // Monday is a closed day.
  assert.equal(openState(open, Date.parse('2026-11-23T15:00:00+08:00')), 'outside-hours');
  const closed = parseShopState(rows({ closed: true, note: '' }));
  assert.equal(openState(closed, at('15:00')), 'closed-today');
  // Yesterday's switch no longer counts.
  assert.equal(openState(closed, Date.parse('2026-11-26T15:00:00+08:00')), 'open');
  // Without readable hours the shop isn't called closed.
  assert.equal(openState({ ...open, hours: null }, at('03:00')), 'open');
});

test('cleans the note the way the server accepts it', () => {
  assert.equal(cleanNote('  Back\ntomorrow\tat 1 PM  '), 'Back tomorrow at 1 PM');
  assert.equal(Array.from(cleanNote('🐱'.repeat(400))).length, MAX_NOTE);
  assert.deepEqual(closureContent(true, ' Out of milk '), { closed: true, note: 'Out of milk' });
  assert.deepEqual(closureContent(false, 'ignored'), { closed: false, note: '' });
});
