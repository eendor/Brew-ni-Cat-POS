import { test } from 'node:test';
import assert from 'node:assert/strict';

import { catTreatsLast } from '../src/lib/category-order.js';

test('Cat Treats goes last and the others keep their order', () => {
  const list = [
    { id: 'cat_buldak', name: 'Buldak & Sedaap' },
    { id: 'cat_treats', name: 'Cat Treats' },
    { id: 'cat_bites', name: 'Cat-Tastic Bites' },
    { id: 'combos', name: 'Combos & Packages' },
  ];
  assert.deepEqual(
    catTreatsLast(list).map((c) => c.id),
    ['cat_buldak', 'cat_bites', 'combos', 'cat_treats'],
  );
});

test('matches a Cat Treats category added with another id', () => {
  const list = [{ id: 'cat_cat-treats', name: ' cat treats ' }, { id: 'combos', name: 'Combos' }];
  assert.deepEqual(catTreatsLast(list).map((c) => c.id), ['combos', 'cat_cat-treats']);
});
