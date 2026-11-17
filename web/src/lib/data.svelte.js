import { supabase } from './supabase.js';

/**
 * Everything the panel shows, loaded once after sign-in and kept current by Supabase
 * Realtime, so a sale, a stock change or a menu edit on any tablet shows up here without a
 * refresh. Each table keeps its own error so one failure doesn't blank the whole panel.
 */
export const db = $state({
  orders: [],
  categories: [],
  items: [],
  inventory: [],
  recipes: [],
  expenses: [],
  loaded: false,
  refreshing: false,
  live: false,
  errors: {},
});

async function load(key, query) {
  if (!supabase) return;
  const { data, error } = await query;
  if (error) {
    console.error(`Could not load ${key}:`, error);
    db.errors[key] = error.message || 'Could not load.';
    return;
  }
  delete db.errors[key];
  db[key] = data || [];
}

export const fetchOrders = () =>
  load('orders', supabase.from('orders').select('*, order_items(*)').order('timestamp', { ascending: false }));
export const fetchCategories = () => load('categories', supabase.from('categories').select('*').order('name'));
export const fetchItems = () => load('items', supabase.from('items').select('*').order('name'));
export const fetchInventory = () => load('inventory', supabase.from('inventory').select('*').order('item_name'));
export const fetchRecipes = () => load('recipes', supabase.from('recipe_mappings').select('*'));
// Deleted expenses are tombstoned (is_deleted) rather than removed; see deleteExpense.
export const fetchExpenses = () =>
  load('expenses', supabase.from('expenses').select('*').eq('is_deleted', false).order('timestamp', { ascending: false }));

export async function loadAll() {
  await Promise.all([fetchOrders(), fetchCategories(), fetchItems(), fetchInventory(), fetchRecipes(), fetchExpenses()]);
  db.loaded = true;
}

export async function refresh() {
  db.refreshing = true;
  try {
    await loadAll();
  } finally {
    db.refreshing = false;
  }
}

let channel = null;

export function startRealtime() {
  if (!supabase || channel) return;
  const on = (table, fn) => (ch) => ch.on('postgres_changes', { event: '*', schema: 'public', table }, () => fn());
  channel = [
    on('orders', fetchOrders),
    on('order_items', fetchOrders),
    on('inventory', fetchInventory),
    on('items', fetchItems),
    on('categories', fetchCategories),
    on('expenses', fetchExpenses),
  ].reduce((ch, add) => add(ch), supabase.channel('realtime-orders'));
  channel.subscribe((status) => {
    db.live = status === 'SUBSCRIBED';
  });
}

export function stopRealtime() {
  if (channel) supabase.removeChannel(channel);
  channel = null;
  db.live = false;
}

export function resetData() {
  stopRealtime();
  Object.assign(db, { orders: [], categories: [], items: [], inventory: [], recipes: [], expenses: [], loaded: false, errors: {} });
}

/** Throws the Supabase error so callers can show it. */
export async function run(query) {
  const { data, error } = await query;
  if (error) throw error;
  return data;
}
