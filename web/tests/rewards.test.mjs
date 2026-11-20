import test from 'node:test';
import assert from 'node:assert/strict';
import {
  validateRewardDraft,
  emptyDraft,
  draftFromReward,
  rewardIdFor,
  totalPrizeChance,
  chanceLabel,
  normalizeCode,
  codeCheckMessage,
} from '../src/lib/rewards.js';

test('a new reward needs a name and 1 to 10000 coins; blank stock and limit mean no limit', () => {
  const draft = { ...emptyDraft('reward', 120), name: '  Free   Latte! ', coins: '80' };
  const checked = validateRewardDraft(draft, ['reward-free-latte']);
  assert.equal(checked.ok, true);
  assert.deepEqual(checked.value, {
    id: 'reward-free-latte-2',
    kind: 'reward',
    name: 'Free Latte!',
    description: '',
    points_cost: 80,
    win_chance: null,
    stock: null,
    daily_limit: null,
    active: true,
    sort_order: 120,
  });
  assert.equal(validateRewardDraft({ ...draft, name: ' ' }).ok, false);
  for (const coins of ['0', '10001', '2.5', 'abc', '']) {
    assert.equal(validateRewardDraft({ ...draft, coins }).ok, false, coins);
  }
});

test('a prize has a chance out of 1000 and never a cost; stock and daily limit are whole numbers', () => {
  const prize = { ...emptyDraft('prize', 10), name: 'Cookie', chance: '25', stock: '5', dailyLimit: '2', coins: '99' };
  const checked = validateRewardDraft(prize);
  assert.equal(checked.ok, true);
  assert.equal(checked.value.points_cost, null);
  assert.equal(checked.value.win_chance, 25);
  assert.equal(checked.value.stock, 5);
  assert.equal(checked.value.daily_limit, 2);
  assert.equal(validateRewardDraft({ ...prize, chance: '1001' }).ok, false);
  assert.equal(validateRewardDraft({ ...prize, stock: '-1' }).ok, false);
  assert.equal(validateRewardDraft({ ...prize, dailyLimit: '0' }).ok, false);
  assert.equal(validateRewardDraft({ ...prize, stock: '0' }).ok, true, 'stock 0 means sold out');
});

test('editing keeps the id, and the draft round-trips the row', () => {
  const row = { id: 'reward-kiwi-soda', kind: 'reward', name: 'Kiwi soda', description: 'Cold.', points_cost: 40, win_chance: null, stock: null, daily_limit: 3, active: false, sort_order: 10 };
  const checked = validateRewardDraft(draftFromReward(row));
  assert.deepEqual(checked, { ok: true, value: row });
  assert.equal(rewardIdFor('prize', '¡Ñandú Bar!'), 'prize-nandu-bar');
});

test('prize chance totals and wording', () => {
  const rows = [
    { kind: 'prize', active: true, win_chance: 600 },
    { kind: 'prize', active: false, win_chance: 900 },
    { kind: 'prize', active: true, win_chance: 500 },
    { kind: 'reward', active: true, win_chance: null },
  ];
  assert.equal(totalPrizeChance(rows), 1100);
  assert.equal(chanceLabel(1000), 'Every play');
  assert.equal(chanceLabel(20), 'About 1 in 50 plays');
});

test('codes are matched without case, spaces or dashes, and each answer reads plainly', () => {
  assert.equal(normalizeCode(' rwd-ab12 cd34 '), 'RWDAB12CD34');
  assert.match(
    codeCheckMessage({ ok: true, status: 'valid', code: 'RWD-AB12CD34', rewardName: 'Kiwi soda', source: 'points', expiresAt: '2026-12-17T04:00:00Z', redeemedAt: null }),
    /^Valid: Kiwi soda \(reward\), good until Dec 17, 2026/,
  );
  assert.equal(
    codeCheckMessage({ ok: true, status: 'redeemed', code: 'PRZ-AB12CD34', rewardName: 'Cookie', source: 'mini-game', expiresAt: '', redeemedAt: '' }),
    'Done. PRZ-AB12CD34 (Cookie) is now used. Give the game prize.',
  );
  assert.match(codeCheckMessage({ ok: false, error: 'already_redeemed', redeemedAt: '2026-11-17T04:00:00Z' }), /^Already used on Nov 17, 2026/);
  assert.match(codeCheckMessage({ ok: false, error: 'expired', expiresAt: '2026-11-01T04:00:00Z' }), /^Expired on Nov 1, 2026/);
  assert.equal(codeCheckMessage({ ok: false, error: 'unknown_code' }), 'No such code. Check the letters with the customer.');
  assert.match(codeCheckMessage({ ok: false, error: 'forbidden' }), /store account/);
});
