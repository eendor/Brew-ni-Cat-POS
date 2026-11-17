/**
 * Pure sales, menu and stock maths for the panel. Kept free of Svelte and Supabase so
 * tests/reports.test.mjs can check it with plain Node.
 */

/** Cash the drawer starts each day with, matching the tablet's Z-Reading. */
export const STARTING_FLOAT = 1500;
/** The tablet's daily sales goal. */
export const DAILY_TARGET = 10000;

const pad2 = (n) => String(n).padStart(2, '0');

/** YYYY-MM-DD in the browser's local time zone. */
export function localDateKey(ts) {
  const d = new Date(ts);
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/** A local Date at midnight for a YYYY-MM-DD key. */
export function dateFromKey(key) {
  const [y, m, d] = String(key).split('-').map(Number);
  return new Date(y || 1970, (m || 1) - 1, d || 1);
}

/** Moves a date key by whole days. */
export function shiftDateKey(key, days) {
  const d = dateFromKey(key);
  d.setDate(d.getDate() + days);
  return localDateKey(d.getTime());
}

export function formatPrice(value) {
  return '₱' + Number(value || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** The 4-digit receipt number the tablets print. Ids from the cloud carry a device prefix. */
export function receiptNumber(id) {
  const shown = id >= 1000000000 ? id % 1000000000 : id;
  return String(shown).padStart(4, '0');
}

/** Totals for non-voided orders. */
export function computeStats(orders) {
  let grossSales = 0, discounts = 0, netSales = 0, cashSales = 0, gcashSales = 0, count = 0;
  for (const o of orders) {
    if (o.is_voided) continue;
    grossSales += o.subtotal;
    discounts += o.discount_deduction;
    netSales += o.total;
    count++;
    if (o.payment_method === 'CASH') cashSales += o.total;
    else gcashSales += o.total;
  }
  return { grossSales, discounts, netSales, cashSales, gcashSales, count };
}

/** The end-of-day report for one local date, as the tablet's Z-Reading shows it. */
export function dayReport(orders, expenses, dateKey) {
  const stats = computeStats(orders.filter((o) => localDateKey(o.timestamp) === dateKey));
  const dayExpenses = expenses
    .filter((e) => localDateKey(e.timestamp) === dateKey)
    .sort((a, b) => b.timestamp - a.timestamp);
  const expenseTotal = dayExpenses.reduce((sum, e) => sum + e.amount, 0);
  const collected = stats.cashSales + stats.gcashSales;
  return {
    ...stats,
    expenses: dayExpenses,
    expenseTotal,
    profit: stats.netSales - expenseTotal,
    drawer: stats.cashSales + STARTING_FLOAT - expenseTotal,
    cashShare: collected > 0 ? stats.cashSales / collected : null,
    goalShare: Math.min(1, stats.netSales / DAILY_TARGET),
  };
}

/**
 * Orders matching the audit-log filters. The date is compared in local time, the same day
 * the shop counts; the old panel compared UTC dates, which moved orders taken before 8 AM
 * in the Philippines onto the previous day.
 */
export function filterOrders(orders, { date = '', device = 'all', payment = 'all' } = {}) {
  return orders.filter(
    (o) =>
      (!date || localDateKey(o.timestamp) === date) &&
      (device === 'all' || o.device_id === device) &&
      (payment === 'all' || o.payment_method === payment),
  );
}

/** "Shrimp Whisker" from a flavor stored as "Flavor: Shrimp Whisker". */
export function flavorLabel(flavor) {
  if (!flavor) return '';
  return flavor.includes(': ') ? flavor.split(': ').pop() : flavor;
}

/** "Takoyaki · 4pcs · Shrimp Whisker". The size is left out when it repeats the name. */
export function itemLabel(item) {
  const parts = [item.item_name];
  if (item.variant_name && item.variant_name.toLowerCase() !== item.item_name.toLowerCase()) {
    parts.push(item.variant_name);
  }
  if (item.flavor) parts.push(flavorLabel(item.flavor));
  return parts.join(' · ');
}

/** Start and end (ms) of the food-totals window: one picked day, this week from Monday, or this month. */
export function breakdownRange(period, dayKey, now = new Date()) {
  if (period === 'day') {
    const start = dateFromKey(dayKey);
    const end = new Date(start);
    end.setHours(23, 59, 59, 999);
    return { start: start.getTime(), end: end.getTime() };
  }
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999).getTime();
  if (period === 'week') {
    const sinceMonday = (now.getDay() + 6) % 7;
    return { start: new Date(now.getFullYear(), now.getMonth(), now.getDate() - sinceMonday).getTime(), end };
  }
  return { start: new Date(now.getFullYear(), now.getMonth(), 1).getTime(), end };
}

/**
 * Sales per distinct product (item + size + flavor) inside a window, grouped by category,
 * mirroring the Android app's Food & Drink Totals. Voided orders are skipped.
 */
export function foodBreakdown(orders, range, { items = [], categories = [] } = {}) {
  const categoryOf = (itemId) => {
    const catId = items.find((m) => m.id === itemId)?.category_id;
    return (catId && categories.find((c) => c.id === catId)?.name) || 'Other';
  };
  const rows = new Map();
  for (const o of orders) {
    if (o.is_voided || o.timestamp < range.start || o.timestamp > range.end) continue;
    for (const it of o.order_items || []) {
      const key = `${it.item_name}|${it.variant_name || ''}|${it.flavor || ''}`;
      const category = categoryOf(it.item_id);
      const row = rows.get(key) || { label: itemLabel(it), category, quantity: 0, sales: 0 };
      row.quantity += it.quantity;
      row.sales += it.total_price;
      if (row.category === 'Other' && category !== 'Other') row.category = category;
      rows.set(key, row);
    }
  }
  const groups = new Map();
  for (const row of rows.values()) {
    if (!groups.has(row.category)) groups.set(row.category, []);
    groups.get(row.category).push(row);
  }
  const grouped = [...groups.entries()]
    .map(([category, list]) => ({
      category,
      rows: list.sort((a, b) => b.sales - a.sales),
      total: list.reduce((s, r) => s + r.sales, 0),
    }))
    .sort((a, b) => b.total - a.total);
  return { groups: grouped, total: grouped.reduce((s, g) => s + g.total, 0) };
}

const csvCell = (value) => {
  const text = value === null || value === undefined ? '' : String(value);
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
};

/** The audit-log export, same columns as before. */
export function ordersCsv(orders) {
  const header = 'Order ID,Receipt Number,Device ID,Timestamp,Date,Subtotal,Discount Deduction,Discount Label,Total,Payment Method,Payment Reference,Cashier,Name';
  const lines = orders.map((o) =>
    [
      o.id,
      receiptNumber(o.id),
      o.device_id,
      o.timestamp,
      new Date(o.timestamp).toLocaleString(),
      o.subtotal,
      o.discount_deduction,
      o.discount_label,
      o.total,
      o.payment_method,
      o.payment_reference,
      o.cashier_name,
      o.table_label,
    ]
      .map(csvCell)
      .join(','),
  );
  return [header, ...lines].join('\n') + '\n';
}

/**
 * What saving an edited order changes: line items to delete, update and insert, and the new
 * totals. Rows with no name or a quantity of 0 are dropped. New rows get ids after the
 * order's existing ones, the scheme the tablets use (order id × 1000 + n).
 */
export function orderItemChanges(order, working) {
  const original = order.order_items || [];
  const keep = working.filter((i) => i.quantity > 0 && i.item_name.trim() !== '');
  const keepIds = new Set(keep.filter((i) => i.id > 0).map((i) => i.id));
  const deleteIds = original.filter((i) => !keepIds.has(i.id)).map((i) => i.id);
  const updates = keep
    .filter((i) => i.id > 0)
    .map((i) => ({
      id: i.id,
      item_name: i.item_name,
      variant_name: i.variant_name,
      flavor: i.flavor || null,
      quantity: i.quantity,
      unit_price: i.unit_price,
      total_price: i.quantity * i.unit_price,
    }));
  const baseId = Math.max(order.id * 1000, ...original.map((i) => i.id));
  const inserts = keep
    .filter((i) => !(i.id > 0))
    .map((i, idx) => ({
      id: baseId + 1 + idx,
      order_id: order.id,
      item_id: i.item_id || 'custom',
      item_name: i.item_name,
      variant_id: i.variant_id || 'custom',
      variant_name: i.variant_name || '',
      flavor: i.flavor || null,
      quantity: i.quantity,
      unit_price: i.unit_price,
      total_price: i.quantity * i.unit_price,
    }));
  const subtotal = keep.reduce((s, i) => s + i.quantity * i.unit_price, 0);
  return { deleteIds, updates, inserts, subtotal, total: Math.max(0, subtotal - (order.discount_deduction || 0)) };
}

/** "Matcha, Oreo" typed in the editor → ["Matcha", "Oreo"]. */
export function parseFlavors(text) {
  return String(text || '').split(',').map((f) => f.trim()).filter(Boolean);
}

/** An id from a name: slugId('cat_', 'Milk Tea') → "cat_milk_tea". */
export function slugId(prefix, name) {
  return prefix + String(name).trim().toLowerCase().replace(/[^a-z0-9]/g, '_');
}

/**
 * Sizes as saved: per-flavor pricing keeps priceByFlavor (only for current flavors) with a
 * base price of 0; flat pricing keeps the base price and an empty priceByFlavor.
 */
export function normalizeVariants(variants, flavors, perFlavor) {
  return variants.map((v) => {
    if (perFlavor && flavors.length > 0) {
      const priceByFlavor = {};
      for (const f of flavors) priceByFlavor[f] = v.priceByFlavor?.[f] || 0;
      return { ...v, basePrice: 0, priceByFlavor };
    }
    return { ...v, priceByFlavor: {} };
  });
}

export function parseVariants(variantsJson) {
  try {
    const parsed = JSON.parse(variantsJson);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/** The lowest price of any size and flavor, or 0. */
export function startingPrice(variantsJson) {
  const prices = parseVariants(variantsJson).flatMap((v) => {
    const byFlavor = Object.values(v.priceByFlavor || {}).filter((p) => p > 0);
    if (byFlavor.length > 0) return byFlavor;
    return v.basePrice > 0 ? [v.basePrice] : [];
  });
  return prices.length > 0 ? Math.min(...prices) : 0;
}

/** True when any ingredient a menu item uses is at or below its reorder level. */
export function isItemLowStock(itemId, recipes, inventory) {
  return recipes
    .filter((r) => r.menu_item_id === itemId)
    .some((r) => {
      const inv = inventory.find((i) => i.id === r.inventory_item_id);
      return Boolean(inv) && inv.current_stock <= inv.reorder_threshold;
    });
}
