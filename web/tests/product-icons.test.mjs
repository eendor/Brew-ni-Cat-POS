import test from 'node:test';
import assert from 'node:assert/strict';
import {
  PRODUCT_ICON_OPTIONS,
  PRODUCT_ICON_KEY_REGEX,
  isValidProductIconKey,
  normalizeProductIconKey,
  getProductIconLabel,
  resolveDefaultProductIconKey,
  resolveEffectiveProductIconKey,
  stripIconColumnIfUnsupported,
} from '../src/lib/product-icons.ts';

test('PRODUCT_ICON_OPTIONS exposes 30 unique DB-valid icon keys matching Android POS', () => {
  assert.equal(PRODUCT_ICON_OPTIONS.length, 30);
  const seenKeys = new Set();
  const seenLabels = new Set();

  for (const option of PRODUCT_ICON_OPTIONS) {
    assert.equal(PRODUCT_ICON_KEY_REGEX.test(option.key), true, `Invalid key: ${option.key}`);
    assert.equal(seenKeys.has(option.key), false, `Duplicate key: ${option.key}`);
    assert.equal(seenLabels.has(option.label), false, `Duplicate label: ${option.label}`);
    seenKeys.add(option.key);
    seenLabels.add(option.label);
    assert.equal(isValidProductIconKey(option.key), true);
    assert.equal(getProductIconLabel(option.key), option.label);
  }
});

test('normalizeProductIconKey trims valid keys and rejects invalid or blank values', () => {
  assert.equal(normalizeProductIconKey('  leaf  '), 'leaf');
  assert.equal(normalizeProductIconKey('buldak'), 'buldak');
  assert.equal(normalizeProductIconKey(''), null);
  assert.equal(normalizeProductIconKey('   '), null);
  assert.equal(normalizeProductIconKey(null), null);
  assert.equal(normalizeProductIconKey(undefined), null);
  assert.equal(normalizeProductIconKey('UPPERCASE_KEY'), null);
  assert.equal(normalizeProductIconKey('unknown_icon_key'), null);
});

test('resolveEffectiveProductIconKey prefers custom icon and falls back to item/category defaults', () => {
  assert.equal(
    resolveEffectiveProductIconKey({ id: 'drink_matcha', category_id: 'cat_drinks', icon: 'cherry' }),
    'cherry'
  );
  assert.equal(
    resolveEffectiveProductIconKey({ id: 'drink_matcha', category_id: 'cat_drinks', icon: null }),
    'leaf'
  );
  assert.equal(
    resolveEffectiveProductIconKey({ id: 'buldak_carbo', category_id: 'cat_buldak', icon: '' }),
    'buldak'
  );
  assert.equal(
    resolveEffectiveProductIconKey({ id: 'bite_takeout_box', category_id: 'cat_takeout', icon: undefined }),
    'package'
  );
  assert.equal(
    resolveDefaultProductIconKey('custom_new_item', 'cat_drinks'),
    'coffee'
  );
});

test('stripIconColumnIfUnsupported strips icon when PostgREST reports missing icon column', () => {
  const payload = {
    id: 'drink_matcha',
    category_id: 'cat_drinks',
    name: 'Matcha',
    flavors: 'Vanilla',
    variants_json: '[]',
    is_available: true,
    icon: 'leaf',
  };

  const missingColRetry = stripIconColumnIfUnsupported(payload, {
    code: 'PGRST204',
    message: "Could not find the 'icon' column of 'items' in the schema cache",
  });
  assert.equal(missingColRetry.shouldRetry, true);
  assert.equal('icon' in missingColRetry.fallbackPayload, false);
  assert.equal(missingColRetry.fallbackPayload.id, 'drink_matcha');

  const unrelatedError = stripIconColumnIfUnsupported(payload, {
    code: '42501',
    message: 'new row violates row-level security policy',
  });
  assert.equal(unrelatedError.shouldRetry, false);
});
