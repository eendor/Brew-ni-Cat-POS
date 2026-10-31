import test from 'node:test';
import assert from 'node:assert/strict';
import {
  validateSiteContentSection,
  findTakeoutBoxItem,
  getTakeoutBoxPesos,
  updateTakeoutBoxVariantsJson,
} from '../src/lib/site-content.ts';

test('validates hours section and rejects closing time before opening time', () => {
  const validHours = {
    days: {
      monday: { opens: '11:00', closes: '21:00' },
      tuesday: { opens: '11:00', closes: '21:00' },
      wednesday: { opens: '11:00', closes: '21:00' },
      thursday: { opens: '11:00', closes: '21:00' },
      friday: { opens: '11:00', closes: '21:00' },
      saturday: null,
      sunday: { opens: '13:00', closes: '21:00' },
    },
    note: 'Schedule updates are posted on our page.',
  };
  assert.equal(validateSiteContentSection('hours', validHours).ok, true);

  const invertedHours = {
    ...validHours,
    days: {
      ...validHours.days,
      monday: { opens: '21:00', closes: '11:00' },
    },
  };
  const rejected = validateSiteContentSection('hours', invertedHours);
  assert.equal(rejected.ok, false);
});

test('validates notice, payments, social, and riders sections', () => {
  assert.equal(
    validateSiteContentSection('notice', { text: '' }).ok,
    true,
  );
  assert.equal(
    validateSiteContentSection('notice', { text: ' leading space' }).ok,
    false,
  );

  assert.equal(
    validateSiteContentSection('payments', {
      methods: ['Cash', 'GCash'],
      qrCode: {
        src: '/images/payment/gcash-qr.svg',
        alt: 'GCash QR code',
      },
    }).ok,
    true,
  );
  assert.equal(
    validateSiteContentSection('payments', {
      methods: ['Cash', 'Cash'],
      qrCode: null,
    }).ok,
    false,
  );

  assert.equal(
    validateSiteContentSection('social', {
      facebook: 'https://www.facebook.com/example',
      facebookPhotos: '',
      tiktok: '',
    }).ok,
    true,
  );
  assert.equal(
    validateSiteContentSection('social', {
      facebook: 'http://insecure.example.com',
      facebookPhotos: '',
      tiktok: '',
    }).ok,
    false,
  );

  assert.equal(
    validateSiteContentSection('riders', {
      partners: [
        {
          name: 'Local Rider',
          href: 'https://www.facebook.com/rider',
          logo: '/images/riders/papas-delivery.jpg',
        },
      ],
    }).ok,
    true,
  );
});

test('finds and updates the Take-out Box item price in whole pesos', () => {
  const items = [
    {
      id: 'item_matcha',
      name: 'Matcha',
      variants_json: JSON.stringify([
        { id: '16oz', name: '16oz', basePrice: 69, priceByFlavor: {} },
      ]),
    },
    {
      id: 'item_takeout_box',
      name: 'Take-out Box',
      variants_json: JSON.stringify([
        { id: 'regular', name: 'Regular', basePrice: 10, priceByFlavor: {} },
      ]),
    },
  ];

  const found = findTakeoutBoxItem(items);
  assert.ok(found);
  assert.equal(found.id, 'item_takeout_box');
  assert.equal(getTakeoutBoxPesos(found), 10);

  const updatedJson = updateTakeoutBoxVariantsJson(found.variants_json, 15);
  assert.equal(
    getTakeoutBoxPesos({ ...found, variants_json: updatedJson }),
    15,
  );
});
