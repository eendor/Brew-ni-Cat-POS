import test from 'node:test';
import assert from 'node:assert/strict';
import {
  computeStats, dayReport, filterOrders, localDateKey, shiftDateKey, receiptNumber, itemLabel,
  breakdownRange, foodBreakdown, ordersCsv, orderItemChanges, normalizeVariants, startingPrice,
  isItemLowStock, slugId, parseFlavors, STARTING_FLOAT,
} from '../src/lib/reports.js';

const at = (y, m, d, h = 12) => new Date(y, m - 1, d, h).getTime();
const order = (over) => ({
  id: 1, device_id: 'tab-a', timestamp: at(2026, 11, 17), subtotal: 100, discount_deduction: 0,
  discount_label: '', total: 100, payment_method: 'CASH', is_voided: false, order_items: [], ...over,
});

test('stats skip voided orders and split cash from GCash', () => {
  const s = computeStats([
    order({ total: 100, subtotal: 120, discount_deduction: 20 }),
    order({ total: 50, subtotal: 50, payment_method: 'GCASH' }),
    order({ total: 999, subtotal: 999, is_voided: true }),
  ]);
  assert.deepEqual(s, { grossSales: 170, discounts: 20, netSales: 150, cashSales: 100, gcashSales: 50, count: 2 });
});

test('the day report subtracts that day\'s expenses from profit and the drawer', () => {
  const r = dayReport(
    [order({ total: 300, subtotal: 300 }), order({ timestamp: at(2026, 11, 16), total: 80, subtotal: 80 })],
    [{ id: 'e1', timestamp: at(2026, 11, 17, 9), amount: 40 }, { id: 'e2', timestamp: at(2026, 11, 16), amount: 5 }],
    '2026-11-17',
  );
  assert.equal(r.netSales, 300);
  assert.equal(r.expenseTotal, 40);
  assert.equal(r.profit, 260);
  assert.equal(r.drawer, 300 + STARTING_FLOAT - 40);
  assert.equal(r.cashShare, 1);
  assert.equal(r.expenses.length, 1);
});

test('a day with no sales has no payment split rather than a made-up 50/50', () => {
  assert.equal(dayReport([], [], '2026-11-17').cashShare, null);
});

test('audit filters compare the local date, so an early-morning order stays on its day', () => {
  const early = order({ timestamp: at(2026, 11, 17, 1) });
  assert.equal(filterOrders([early], { date: '2026-11-17' }).length, 1);
  assert.equal(filterOrders([early], { date: '2026-11-16' }).length, 0);
  assert.equal(filterOrders([early, order({ payment_method: 'GCASH' })], { payment: 'GCASH' }).length, 1);
  assert.equal(filterOrders([early], { device: 'tab-b' }).length, 0);
});

test('date keys and receipt numbers', () => {
  assert.equal(localDateKey(at(2026, 1, 5)), '2026-01-05');
  assert.equal(shiftDateKey('2026-03-01', -1), '2026-02-28');
  assert.equal(receiptNumber(7), '0007');
  assert.equal(receiptNumber(3000000042), '0042');
});

test('item labels drop a size that repeats the name and the "Flavor:" prefix', () => {
  assert.equal(itemLabel({ item_name: 'Takoyaki', variant_name: '4pcs', flavor: 'Flavor: Shrimp' }), 'Takoyaki · 4pcs · Shrimp');
  assert.equal(itemLabel({ item_name: 'Soda', variant_name: 'soda', flavor: null }), 'Soda');
});

test('the week starts on Monday and the month on the 1st', () => {
  const wed = new Date(2026, 10, 18, 15);
  assert.equal(breakdownRange('week', '', wed).start, new Date(2026, 10, 16).getTime());
  assert.equal(breakdownRange('month', '', wed).start, new Date(2026, 10, 1).getTime());
  const day = breakdownRange('day', '2026-11-10');
  assert.equal(day.start, new Date(2026, 10, 10).getTime());
  assert.equal(day.end, new Date(2026, 10, 10, 23, 59, 59, 999).getTime());
});

test('food totals group each product under its category, biggest first', () => {
  const line = (over) => ({ item_id: 'tako', item_name: 'Takoyaki', variant_name: '4pcs', flavor: null, quantity: 1, total_price: 40, ...over });
  const { groups, total } = foodBreakdown(
    [
      order({ order_items: [line(), line({ quantity: 2, total_price: 80 })] }),
      order({ order_items: [line({ item_id: 'soda', item_name: 'Soda', variant_name: 'Can', total_price: 30 })] }),
      order({ is_voided: true, order_items: [line({ total_price: 1000 })] }),
    ],
    { start: at(2026, 11, 17, 0), end: at(2026, 11, 17, 23) },
    { items: [{ id: 'tako', category_id: 'bites' }, { id: 'soda', category_id: 'drinks' }], categories: [{ id: 'bites', name: 'Bites' }, { id: 'drinks', name: 'Drinks' }] },
  );
  assert.equal(total, 150);
  assert.deepEqual(groups.map((g) => [g.category, g.total]), [['Bites', 120], ['Drinks', 30]]);
  assert.equal(groups[0].rows[0].quantity, 3);
});

test('the CSV quotes text with commas or quotes', () => {
  const csv = ordersCsv([order({ discount_label: 'Senior, PWD', table_label: 'Table "3"', cashier_name: null })]);
  const row = csv.trim().split('\n')[1];
  assert.match(row, /"Senior, PWD"/);
  assert.match(row, /"Table ""3"""$/);
});

test('saving edited items deletes removed rows, updates kept ones and numbers new ones', () => {
  const o = order({ id: 5, discount_deduction: 10, order_items: [
    { id: 5001, item_name: 'A', quantity: 1, unit_price: 50 },
    { id: 5002, item_name: 'B', quantity: 1, unit_price: 20 },
  ] });
  const c = orderItemChanges(o, [
    { id: 5001, item_name: 'A', variant_name: '', flavor: '', quantity: 2, unit_price: 50 },
    { id: 0, item_name: 'C', variant_name: '', flavor: null, quantity: 1, unit_price: 15 },
    { id: 0, item_name: '  ', quantity: 1, unit_price: 99 },
  ]);
  assert.deepEqual(c.deleteIds, ['5002']);
  assert.equal(c.updates[0].total_price, 100);
  assert.equal(c.inserts[0].id, '5003');
  assert.equal(c.subtotal, 115);
  assert.equal(c.total, 105);
});

test('variants keep one kind of price', () => {
  const v = [{ id: 'r', name: 'Regular', basePrice: 50, priceByFlavor: { Old: 9, Shrimp: 60 } }];
  assert.deepEqual(normalizeVariants(v, ['Shrimp', 'Veggie'], true)[0], { id: 'r', name: 'Regular', basePrice: 0, priceByFlavor: { Shrimp: 60, Veggie: 0 } });
  assert.deepEqual(normalizeVariants(v, ['Shrimp'], false)[0].priceByFlavor, {});
  assert.equal(startingPrice(JSON.stringify([{ basePrice: 0, priceByFlavor: { A: 40, B: 60 } }, { basePrice: 35 }])), 35);
  assert.equal(startingPrice('not json'), 0);
});

test('low stock, ids and flavors', () => {
  const recipes = [{ menu_item_id: 'tea', inventory_item_id: 'milk' }];
  assert.equal(isItemLowStock('tea', recipes, [{ id: 'milk', current_stock: 2, reorder_threshold: 2 }]), true);
  assert.equal(isItemLowStock('tea', recipes, [{ id: 'milk', current_stock: 3, reorder_threshold: 2 }]), false);
  assert.equal(isItemLowStock('soda', recipes, []), false);
  assert.equal(slugId('cat_', ' Milk Tea '), 'cat_milk_tea');
  assert.deepEqual(parseFlavors('Matcha, , Oreo '), ['Matcha', 'Oreo']);
});

test('item edits keep exact ids past the safe integer range', () => {
  const o = order({ id: 797423000003023, order_items: [
    { id: 797423000003023000, key: '797423000003023000', item_name: 'A', quantity: 1, unit_price: 10 },
    { id: 797423000003023000, key: '797423000003023001', item_name: 'B', quantity: 1, unit_price: 20 },
  ] });
  const c = orderItemChanges(o, [
    { ...o.order_items[1], quantity: 2 },
    { id: 0, item_name: 'Box', quantity: 1, unit_price: 10 },
  ]);
  assert.deepEqual(c.deleteIds, ['797423000003023000']);
  assert.equal(c.updates[0].id, '797423000003023001');
  assert.equal(c.inserts[0].id, '797423000003023002');
  assert.equal(c.subtotal, 50);
});
