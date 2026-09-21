'use client';

import React, { useState, useEffect } from 'react';
import { supabase } from '@/lib/supabase';
import {
  TrendingUp,
  Receipt,
  FileText,
  Package,
  Layers,
  Search,
  Download,
  Plus,
  Edit2,
  Trash2,
  RefreshCw,
  CheckCircle,
  Clock,
  Sparkles,
  ShoppingBag,
  DollarSign,
  Coffee,
  Smartphone,
  ChevronDown,
  ChevronUp,
  Cookie,
  AlertCircle,
  Printer,
  Calendar,
  User,
  Users,
  Utensils,
  Menu,
  ChevronRight,
  Info,
  Flame
} from 'lucide-react';

// Interfaces mapping the database tables
interface OrderItem {
  id: number;
  order_id: number;
  item_id: string;
  item_name: string;
  variant_id: string;
  variant_name: string;
  flavor: string | null;
  quantity: number;
  unit_price: number;
  total_price: number;
}

interface Order {
  id: number;
  device_id: string;
  timestamp: number;
  subtotal: number;
  discount_deduction: number;
  discount_label: string;
  total: number;
  payment_method: string;
  payment_reference: string | null;
  cashier_id: string | null;
  cashier_name: string | null;
  table_label: string | null;
  is_served: boolean;
  is_voided?: boolean;
  created_at: string;
  order_items?: OrderItem[];
}

interface Category {
  id: string;
  name: string;
  created_at?: string;
}

interface MenuItem {
  id: string;
  category_id: string;
  name: string;
  flavors: string;
  variants_json: string; // JSON string containing Variant[]
  is_available: boolean;
  created_at?: string;
}

interface Variant {
  id: string;
  name: string;
  basePrice: number;
  priceByFlavor: Record<string, number>;
}

interface InventoryItem {
  id: string;
  item_name: string;
  unit: string;
  current_stock: number;
  reorder_threshold: number;
  created_at?: string;
}

interface RecipeMapping {
  id: string;
  menu_item_id: string;
  size_variant_name: string | null;
  inventory_item_id: string;
  deduction_quantity: number;
  created_at?: string;
}

interface Expense {
  id: string;
  timestamp: number;
  description: string;
  amount: number;
  recorded_by: string | null;
  device_id?: string | null;
  is_deleted?: boolean;
  created_at?: string;
}

// The database only answers to the signed-in store account (row-level security),
// so the dashboard is gated behind a login. The session persists in this browser.
export default function Page() {
  const [session, setSession] = useState<any>(null);
  const [authReady, setAuthReady] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [authError, setAuthError] = useState<string | null>(null);
  const [signingIn, setSigningIn] = useState(false);

  useEffect(() => {
    if (!supabase) {
      setAuthReady(true);
      return;
    }
    supabase.auth.getSession().then(({ data }: any) => {
      setSession(data.session);
      setAuthReady(true);
    });
    const { data: sub } = supabase.auth.onAuthStateChange((_event: string, s: any) => {
      setSession(s);
    });
    return () => sub.subscription.unsubscribe();
  }, []);

  const handleSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!supabase) return;
    setSigningIn(true);
    setAuthError(null);
    const { error } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
    setSigningIn(false);
    if (error) {
      setAuthError(error.message === 'Invalid login credentials' ? 'Wrong email or password.' : error.message);
    }
  };

  if (!authReady) {
    return (
      <div className="min-h-screen bg-[#060607] flex items-center justify-center">
        <RefreshCw className="w-6 h-6 text-slate-600 animate-spin" />
      </div>
    );
  }

  if (!session) {
    return (
      <div className="min-h-screen bg-[#060607] flex items-center justify-center p-6">
        <form
          onSubmit={handleSignIn}
          className="w-full max-w-sm bg-white/[0.02] border border-white/10 rounded-3xl p-8 flex flex-col gap-5 backdrop-blur-xl"
        >
          <div className="flex flex-col gap-1">
            <h1 className="text-xl font-black text-slate-100">Brew ni Cat POS</h1>
            <p className="text-xs text-slate-500">Sign in with the store account to open the dashboard.</p>
          </div>
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="Email"
            className="bg-black/40 border border-white/10 rounded-2xl px-4 py-3 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-emerald-500/50"
          />
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Password"
            className="bg-black/40 border border-white/10 rounded-2xl px-4 py-3 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-emerald-500/50"
          />
          {authError && <p className="text-xs text-red-400">{authError}</p>}
          <button
            type="submit"
            disabled={signingIn}
            className="bg-emerald-500/15 hover:bg-emerald-500/25 border border-emerald-500/30 rounded-2xl px-4 py-3 text-sm font-bold text-emerald-300 transition-colors disabled:opacity-50"
          >
            {signingIn ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    );
  }

  return <Dashboard />;
}

function Dashboard() {
  const [activeTab, setActiveTab] = useState<'dashboard' | 'history' | 'expenses' | 'menu' | 'inventory'>('dashboard');
  const [orders, setOrders] = useState<Order[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [recipes, setRecipes] = useState<RecipeMapping[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // Filter States
  const [filterDate, setFilterDate] = useState<string>(''); // YYYY-MM-DD
  // Which day's End-of-Day (Z-Reading) report to show on the dashboard. Defaults to today, but
  // the owner counts the drawer the next morning, so they can scroll back to any past day here —
  // the online POS kept no per-day report before this.
  const [reportDate, setReportDate] = useState<string>(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  });
  // Per-food breakdown window: 'week' (Mon-start) or 'month' (1st of month).
  const [breakdownPeriod, setBreakdownPeriod] = useState<'week' | 'month'>('week');
  const [filterDeviceId, setFilterDeviceId] = useState<string>('all');
  const [filterPayment, setFilterPayment] = useState<string>('all');
  const [expandedOrders, setExpandedOrders] = useState<Record<number, boolean>>({});
  const [editTableLabels, setEditTableLabels] = useState<Record<number, string>>({});
  const [editCashierNames, setEditCashierNames] = useState<Record<number, string>>({});
  const [editPaymentMethods, setEditPaymentMethods] = useState<Record<number, string>>({});
  const [editReferenceIds, setEditReferenceIds] = useState<Record<number, string>>({});
  // Working copy of an order's line items while the owner edits them in the Audit Log.
  // Keyed by order id; absent until first edited (falls back to the order's own items).
  const [editItems, setEditItems] = useState<Record<number, OrderItem[]>>({});

  // Menu Category Filter State (matches app category filtering)
  const [selectedCategoryId, setSelectedCategoryId] = useState<string>('all');

  // Menu Editor Modal States
  const [showItemModal, setShowItemModal] = useState(false);
  const [editingItem, setEditingItem] = useState<MenuItem | null>(null);
  const [itemName, setItemName] = useState('');
  const [itemCategory, setItemCategory] = useState('');
  const [itemFlavors, setItemFlavors] = useState('');
  const [itemVariants, setItemVariants] = useState<Variant[]>([
    { id: 'regular', name: 'Regular', basePrice: 0, priceByFlavor: {} }
  ]);
  // When true, each size is priced per flavor (priceByFlavor) instead of one flat basePrice —
  // e.g. Takoyaki, where 4pcs costs ₱40 for Veggie but ₱60 for Shrimp.
  const [perFlavorPricing, setPerFlavorPricing] = useState(false);

  // Category Modal States
  const [showCategoryModal, setShowCategoryModal] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');

  // Inventory Modal States
  const [showInventoryModal, setShowInventoryModal] = useState(false);
  const [editingInventory, setEditingInventory] = useState<InventoryItem | null>(null);
  const [stockAdjustment, setStockAdjustment] = useState('');

  // Simulated Printer Modal State
  const [showPrintModal, setShowPrintModal] = useState(false);
  const [printSummary, setPrintSummary] = useState<any>(null);

  useEffect(() => {
    if (!supabase) {
      setLoading(false);
      return;
    }
    fetchData();

    // Subscribe to realtime updates on orders AND catalog/stock so the dashboard stays live
    // without a manual refresh (stock, menu and category edits from any device).
    const channel = supabase
      .channel('realtime-orders')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'orders' }, () => {
        fetchOrdersOnly();
      })
      .on('postgres_changes', { event: '*', schema: 'public', table: 'order_items' }, () => {
        fetchOrdersOnly();
      })
      .on('postgres_changes', { event: '*', schema: 'public', table: 'inventory' }, () => {
        fetchInventory();
      })
      .on('postgres_changes', { event: '*', schema: 'public', table: 'items' }, () => {
        fetchMenuItems();
      })
      .on('postgres_changes', { event: '*', schema: 'public', table: 'categories' }, () => {
        fetchCategories();
      })
      .on('postgres_changes', { event: '*', schema: 'public', table: 'expenses' }, () => {
        fetchExpenses();
      })
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
    // Mount-only on purpose: the fetchers are stable for the lifetime of the page, and listing
    // them here would tear down and re-open the realtime channel on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchData = async () => {
    if (!supabase) return;
    setLoading(true);
    try {
      await Promise.all([
        fetchOrdersOnly(),
        fetchCategories(),
        fetchMenuItems(),
        fetchInventory(),
        fetchRecipes(),
        fetchExpenses()
      ]);
    } catch (e) {
      console.error('Error fetching dashboard data:', e);
    } finally {
      setLoading(false);
    }
  };

  const fetchOrdersOnly = async () => {
    if (!supabase) return;
    try {
      const { data, error } = await supabase
        .from('orders')
        .select('*, order_items(*)')
        .order('timestamp', { ascending: false });

      if (error) throw error;
      setOrders(data || []);
    } catch (e) {
      console.error('Error fetching orders:', e);
    }
  };

  const fetchCategories = async () => {
    if (!supabase) return;
    try {
      const { data, error } = await supabase.from('categories').select('*').order('name');
      if (error) throw error;
      setCategories(data || []);
    } catch (e) {
      console.error('Error fetching categories:', e);
      setCategories([]);
    }
  };

  const fetchMenuItems = async () => {
    if (!supabase) return;
    try {
      const { data, error } = await supabase.from('items').select('*').order('name');
      if (error) throw error;
      setMenuItems(data || []);
    } catch (e) {
      console.error('Error fetching menu items:', e);
      setMenuItems([]);
    }
  };

  const fetchInventory = async () => {
    if (!supabase) return;
    try {
      const { data, error } = await supabase.from('inventory').select('*').order('item_name');
      if (error) throw error;
      setInventory(data || []);
    } catch (e) {
      console.error('Error fetching inventory:', e);
      setInventory([]);
    }
  };

  const fetchRecipes = async () => {
    if (!supabase) return;
    try {
      const { data, error } = await supabase.from('recipe_mappings').select('*');
      if (error) throw error;
      setRecipes(data || []);
    } catch (e) {
      console.error('Error fetching recipe mappings:', e);
      setRecipes([]);
    }
  };

  const fetchExpenses = async () => {
    if (!supabase) return;
    try {
      const { data, error } = await supabase
        .from('expenses')
        .select('*')
        .eq('is_deleted', false)
        .order('timestamp', { ascending: false });
      if (error) throw error;
      setExpenses(data || []);
    } catch (e) {
      console.error('Error fetching expenses:', e);
      setExpenses([]);
    }
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    await fetchData();
    setRefreshing(false);
  };

  // Tombstone rather than hard-delete: the tablets re-upload their local copy of
  // every expense on each sync, so a deleted row would come straight back. The
  // is_deleted flag survives that upsert and also tells the app to drop its copy.
  const handleDeleteExpense = async (expense: Expense) => {
    if (!supabase) return;
    if (!confirm(`Delete expense "${expense.description}" (-₱${expense.amount.toFixed(2)})? This removes it from every device.`)) return;
    try {
      const { error } = await supabase.from('expenses').update({ is_deleted: true }).eq('id', expense.id);
      if (error) throw error;
      setExpenses(prev => prev.filter(e => e.id !== expense.id));
    } catch (e) {
      console.error('Error deleting expense:', e);
      alert('Could not delete the expense. Try again.');
    }
  };

  const toggleOrderExpand = (id: number) => {
    setExpandedOrders(prev => ({ ...prev, [id]: !prev[id] }));
  };

  // Z-Reading Calculations for Today (or selected date)
  const getFilteredOrders = () => {
    return orders.filter(order => {
      const orderDate = new Date(order.timestamp).toISOString().split('T')[0];
      const matchDate = filterDate ? orderDate === filterDate : true;
      const matchDevice = filterDeviceId !== 'all' ? order.device_id === filterDeviceId : true;
      const matchPayment = filterPayment !== 'all' ? order.payment_method === filterPayment : true;
      return matchDate && matchDevice && matchPayment;
    });
  };

  const calculateZReading = () => {
    const filtered = getFilteredOrders();
    let grossSales = 0;
    let discounts = 0;
    let netSales = 0;
    let cashSales = 0;
    let gcashSales = 0;
    let nonVoidedCount = 0;

    filtered.forEach(order => {
      if (order.is_voided) return; // Skip voided orders in financials
      grossSales += order.subtotal;
      discounts += order.discount_deduction;
      netSales += order.total;
      nonVoidedCount++;
      if (order.payment_method === 'CASH') {
        cashSales += order.total;
      } else {
        gcashSales += order.total;
      }
    });

    return { grossSales, discounts, netSales, cashSales, gcashSales, count: nonVoidedCount };
  };

  const uniqueDevices = Array.from(new Set(orders.map(o => o.device_id)));
  const zReading = calculateZReading();

  // The Live Dashboard is scoped to TODAY (local date), independent of the Audit Log's
  // date filter. allTimeStats is kept so the manager can also see the running total.
  const localDateKey = (ts: number) => {
    const d = new Date(ts);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  };
  const computeStats = (list: typeof orders) => {
    let grossSales = 0, discounts = 0, netSales = 0, cashSales = 0, gcashSales = 0, count = 0;
    list.forEach(o => {
      if (o.is_voided) return;
      grossSales += o.subtotal;
      discounts += o.discount_deduction;
      netSales += o.total;
      count++;
      if (o.payment_method === 'CASH') cashSales += o.total; else gcashSales += o.total;
    });
    return { grossSales, discounts, netSales, cashSales, gcashSales, count };
  };
  const todayKey = localDateKey(Date.now());
  // The End-of-Day report follows the picked reportDate (defaults to today) so the owner can pull
  // up a previous day's takings the morning after — matching the tablet's Z-Reading, which they
  // couldn't see online before.
  const reportOrders = orders.filter(o => localDateKey(o.timestamp) === reportDate);
  const todayStats = computeStats(reportOrders);
  const allTimeStats = computeStats(orders);
  const STARTING_FLOAT = 1500.0;
  const isReportToday = reportDate === todayKey;
  const reportDateLabel = (() => {
    const [y, m, d] = reportDate.split('-').map(Number);
    return new Date(y, (m || 1) - 1, d || 1).toLocaleDateString(undefined, {
      weekday: 'short', year: 'numeric', month: 'short', day: 'numeric'
    });
  })();

  // Operating expenses recorded on the cashier terminals, scoped to the report date (local).
  // Treated as cash drawer outflows: they reduce the estimated drawer balance and net profit.
  const todayExpenseList = expenses
    .filter(e => localDateKey(e.timestamp) === reportDate)
    .sort((a, b) => b.timestamp - a.timestamp);
  const todayExpenses = todayExpenseList.reduce((sum, e) => sum + e.amount, 0);

  // --- Per-food sales breakdown (mirrors the Android app's Food & Drink Totals) ---
  // Each food/drink is totaled on its own, grouped by category, for the selected week or month —
  // separate from the shop-wide takings so the owner can see per-item profitability.
  const breakdownRange = (() => {
    const now = new Date();
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999).getTime();
    let start: number;
    if (breakdownPeriod === 'week') {
      const day = now.getDay(); // 0=Sun..6=Sat
      const diffToMonday = (day + 6) % 7; // days since Monday
      const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - diffToMonday, 0, 0, 0, 0);
      start = monday.getTime();
    } else {
      start = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0, 0).getTime();
    }
    return { start, end };
  })();

  const categoryNameById = (id: string) => categories.find(c => c.id === id)?.name || 'Other';
  const menuItemCategory = (itemId: string) => menuItems.find(m => m.id === itemId)?.category_id;

  // Aggregate active (non-voided) order lines within the window, keyed by item name.
  const foodBreakdown = (() => {
    const map = new Map<string, { itemName: string; categoryName: string; quantity: number; sales: number }>();
    orders.forEach(o => {
      if (o.is_voided) return;
      if (o.timestamp < breakdownRange.start || o.timestamp > breakdownRange.end) return;
      (o.order_items || []).forEach(it => {
        const key = it.item_name;
        const catId = menuItemCategory(it.item_id);
        const categoryName = catId ? categoryNameById(catId) : 'Other';
        const prev = map.get(key) || { itemName: it.item_name, categoryName, quantity: 0, sales: 0 };
        prev.quantity += it.quantity;
        prev.sales += it.total_price;
        if (prev.categoryName === 'Other' && categoryName !== 'Other') prev.categoryName = categoryName;
        map.set(key, prev);
      });
    });
    return Array.from(map.values());
  })();

  const foodBreakdownGrouped = (() => {
    const byCat = new Map<string, typeof foodBreakdown>();
    foodBreakdown.forEach(row => {
      const arr = byCat.get(row.categoryName) || [];
      arr.push(row);
      byCat.set(row.categoryName, arr);
    });
    return Array.from(byCat.entries())
      .map(([categoryName, rows]) => ({
        categoryName,
        rows: rows.sort((a, b) => b.sales - a.sales),
        total: rows.reduce((s, r) => s + r.sales, 0)
      }))
      .sort((a, b) => b.total - a.total);
  })();
  const foodBreakdownTotal = foodBreakdown.reduce((s, r) => s + r.sales, 0);
  const breakdownRangeLabel = (() => {
    const fmt = (t: number) => new Date(t).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
    return `${fmt(breakdownRange.start)} – ${fmt(breakdownRange.end)}`;
  })();
  // All-time expense aggregates for the dedicated Expense Log tab.
  const allExpensesTotal = expenses.reduce((sum, e) => sum + e.amount, 0);
  const uniqueRecorders = new Set(expenses.map(e => e.recorded_by).filter(Boolean)).size;

  // Export CSV of Orders
  const exportCSV = () => {
    const filtered = getFilteredOrders();
    let csvContent = 'data:text/csv;charset=utf-8,';
    csvContent += 'Order ID,Receipt Number,Device ID,Timestamp,Date,Subtotal,Discount Deduction,Discount Label,Total,Payment Method,Payment Reference,Cashier,Name,Served\n';

    filtered.forEach(o => {
      const displayId = o.id >= 1000000000 ? o.id % 1000000000 : o.id;
      const receiptNo = String(displayId).padStart(4, '0');
      const dateStr = new Date(o.timestamp).toLocaleString();
      const row = [
        o.id,
        receiptNo,
        o.device_id,
        o.timestamp,
        `"${dateStr}"`,
        o.subtotal,
        o.discount_deduction,
        `"${o.discount_label}"`,
        o.total,
        o.payment_method,
        `"${o.payment_reference || ''}"`,
        `"${o.cashier_name || ''}"`,
        `"${o.table_label || ''}"`,
        o.is_served ? 'TRUE' : 'FALSE'
      ].join(',');
      csvContent += row + '\n';
    });

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `cattastic_sales_report_${filterDate || 'all'}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // Serve live order toggle
  const toggleOrderServed = async (orderId: number, currentStatus: boolean) => {
    try {
      const { error } = await supabase
        .from('orders')
        .update({ is_served: !currentStatus })
        .eq('id', orderId);
      if (error) throw error;
      fetchOrdersOnly();
    } catch (e) {
      console.error(e);
    }
  };

  // The order's line items as currently being edited (working copy), or its saved items.
  const getOrderItems = (o: Order): OrderItem[] =>
    editItems[o.id] !== undefined ? editItems[o.id] : (o.order_items || []);

  // Apply a mutation to an order's working item list, seeding from saved items on first touch.
  const mutateItems = (orderId: number, fn: (items: OrderItem[]) => OrderItem[]) => {
    setEditItems(prev => {
      const base = prev[orderId] !== undefined
        ? prev[orderId]
        : (orders.find(o => o.id === orderId)?.order_items || []);
      return { ...prev, [orderId]: fn(base.map(i => ({ ...i }))) };
    });
  };

  const updateItemField = (orderId: number, idx: number, field: 'item_name' | 'variant_name' | 'flavor' | 'quantity' | 'unit_price', value: string) => {
    mutateItems(orderId, items => {
      const copy = items.map(i => ({ ...i }));
      const it = copy[idx];
      if (field === 'quantity') it.quantity = Math.max(0, parseInt(value || '0', 10) || 0);
      else if (field === 'unit_price') it.unit_price = Math.max(0, parseFloat(value || '0') || 0);
      else if (field === 'flavor') it.flavor = value || null;
      else (it as any)[field] = value;
      it.total_price = it.quantity * it.unit_price;
      return copy;
    });
  };

  const removeItemRow = (orderId: number, idx: number) =>
    mutateItems(orderId, items => items.filter((_, i) => i !== idx));

  const addItemRow = (orderId: number) =>
    mutateItems(orderId, items => [
      ...items,
      { id: 0, order_id: orderId, item_id: 'custom', item_name: '', variant_id: 'custom', variant_name: '', flavor: null, quantity: 1, unit_price: 0, total_price: 0 },
    ]);

  // Enter / leave item-edit mode for an order. Items are read-only until "Edit Items".
  const startEditItems = (o: Order) =>
    setEditItems(prev => ({ ...prev, [o.id]: (o.order_items || []).map(i => ({ ...i })) }));

  const cancelEditItems = (orderId: number) =>
    setEditItems(prev => { const n = { ...prev }; delete n[orderId]; return n; });

  // Subtotal of an order's working items (used for the live preview and on save).
  const workingSubtotal = (o: Order) =>
    getOrderItems(o).reduce((s, i) => s + i.quantity * i.unit_price, 0);

  // Update order details (and line items, if edited) from the Audit Log.
  const handleUpdateOrderDetails = async (orderId: number) => {
    if (!supabase) return;
    try {
      const table = editTableLabels[orderId] !== undefined ? editTableLabels[orderId].trim() : undefined;
      const cashier = editCashierNames[orderId] !== undefined ? editCashierNames[orderId].trim() : undefined;
      const payment = editPaymentMethods[orderId] !== undefined ? editPaymentMethods[orderId] : undefined;
      const ref = editReferenceIds[orderId] !== undefined ? editReferenceIds[orderId].trim() : undefined;

      const updates: any = {};
      if (table !== undefined) updates.table_label = table || null;
      if (cashier !== undefined) updates.cashier_name = cashier || null;
      if (payment !== undefined) updates.payment_method = payment;
      if (ref !== undefined) updates.payment_reference = ref || null;

      // Persist line-item edits (qty / price / add / remove) and recalc totals.
      const working = editItems[orderId];
      if (working !== undefined) {
        const order = orders.find(o => o.id === orderId);
        const original = order?.order_items || [];
        const keep = working.filter(i => i.quantity > 0 && i.item_name.trim() !== '');

        const keepIds = new Set(keep.filter(i => i.id > 0).map(i => i.id));
        const deletedIds = original.filter(i => !keepIds.has(i.id)).map(i => i.id);
        if (deletedIds.length) {
          const { error } = await supabase.from('order_items').delete().in('id', deletedIds);
          if (error) throw error;
        }

        for (const i of keep.filter(i => i.id > 0)) {
          const { error } = await supabase.from('order_items').update({
            item_name: i.item_name, variant_name: i.variant_name, flavor: i.flavor,
            quantity: i.quantity, unit_price: i.unit_price, total_price: i.quantity * i.unit_price,
          }).eq('id', i.id);
          if (error) throw error;
        }

        const newOnes = keep.filter(i => !(i.id > 0));
        if (newOnes.length) {
          const baseId = Math.max(orderId * 1000, ...original.map(i => i.id));
          const rows = newOnes.map((i, idx) => ({
            id: baseId + 1 + idx, order_id: orderId, item_id: i.item_id || 'custom',
            item_name: i.item_name, variant_id: i.variant_id || 'custom', variant_name: i.variant_name || '',
            flavor: i.flavor || null, quantity: i.quantity, unit_price: i.unit_price, total_price: i.quantity * i.unit_price,
          }));
          const { error } = await supabase.from('order_items').insert(rows);
          if (error) throw error;
        }

        const newSubtotal = keep.reduce((s, i) => s + i.quantity * i.unit_price, 0);
        updates.subtotal = newSubtotal;
        updates.total = Math.max(0, newSubtotal - (order?.discount_deduction || 0));
      }

      const { error } = await supabase
        .from('orders')
        .update(updates)
        .eq('id', orderId);

      if (error) throw error;
      setEditItems(prev => { const n = { ...prev }; delete n[orderId]; return n; });
      await fetchOrdersOnly();
      alert('Order updated successfully!');
    } catch (e) {
      console.error('Error updating order details:', e);
      alert('Failed to update order. Please try again.');
    }
  };

  // Toggle order void status
  const toggleOrderVoid = async (orderId: number, currentVoidStatus: boolean) => {
    if (!supabase) return;
    try {
      const { error } = await supabase
        .from('orders')
        .update({ is_voided: !currentVoidStatus })
        .eq('id', orderId);

      if (error) throw error;
      await fetchOrdersOnly();
    } catch (e) {
      console.error('Error toggling void status:', e);
    }
  };

  // Delete Category
  const handleDeleteCategory = async (catId: string) => {
    if (!confirm('Are you sure? This will delete all items under this category.')) return;
    await supabase.from('categories').delete().eq('id', catId);
    fetchData();
  };

  // Add Category
  const handleAddCategory = async () => {
    if (!newCategoryName.trim()) return;
    const catId = 'cat_' + newCategoryName.toLowerCase().replace(/[^a-z0-9]/g, '_');
    await supabase.from('categories').insert({ id: catId, name: newCategoryName.trim() });
    setNewCategoryName('');
    setShowCategoryModal(false);
    fetchData();
  };

  // Save Item (New or Edit)
  const handleSaveItem = async () => {
    if (!itemName.trim() || !itemCategory) return;
    const itemId = editingItem?.id || 'item_' + itemName.toLowerCase().replace(/[^a-z0-9]/g, '_') + '_' + Date.now().toString().slice(-4);

    const flavorsList = itemFlavors.split(',').map(f => f.trim()).filter(Boolean);
    // Normalise variants to the chosen pricing mode so the two price fields never disagree:
    // per-flavor keeps priceByFlavor (pruned to current flavors) with basePrice 0; flat keeps
    // basePrice with an empty priceByFlavor.
    const normalizedVariants = itemVariants.map(v => {
      if (perFlavorPricing && flavorsList.length > 0) {
        const priceByFlavor: Record<string, number> = {};
        flavorsList.forEach(f => { priceByFlavor[f] = v.priceByFlavor?.[f] || 0; });
        return { ...v, basePrice: 0, priceByFlavor };
      }
      return { ...v, priceByFlavor: {} };
    });

    const payload = {
      id: itemId,
      category_id: itemCategory,
      name: itemName.trim(),
      flavors: flavorsList.join('|'),
      variants_json: JSON.stringify(normalizedVariants),
      is_available: editingItem ? editingItem.is_available : true
    };

    const { error } = await supabase.from('items').upsert(payload);
    if (error) {
      console.error(error);
      alert('Error saving item: ' + error.message);
    } else {
      setShowItemModal(false);
      setEditingItem(null);
      fetchData();
    }
  };

  const handleEditItemClick = (item: MenuItem) => {
    setEditingItem(item);
    setItemName(item.name);
    setItemCategory(item.category_id);
    setItemFlavors(item.flavors.split('|').join(', '));
    try {
      const parsed = JSON.parse(item.variants_json) as Variant[];
      setItemVariants(parsed);
      // Auto-detect per-flavor pricing: any size that prices itself by flavor.
      setPerFlavorPricing(parsed.some(v => Object.keys(v.priceByFlavor || {}).length > 0));
    } catch (_) {
      setItemVariants([{ id: 'regular', name: 'Regular', basePrice: 0, priceByFlavor: {} }]);
      setPerFlavorPricing(false);
    }
    setShowItemModal(true);
  };

  const handleDeleteItem = async (itemId: string) => {
    if (!confirm('Are you sure you want to delete this menu item?')) return;
    await supabase.from('items').delete().eq('id', itemId);
    fetchData();
  };

  // Save Stock Adjustment
  const handleSaveStock = async () => {
    if (!editingInventory || !stockAdjustment) return;
    const delta = parseFloat(stockAdjustment);
    if (isNaN(delta)) return;

    const newStock = editingInventory.current_stock + delta;
    await supabase.from('inventory').update({ current_stock: newStock }).eq('id', editingInventory.id);
    
    setShowInventoryModal(false);
    setEditingInventory(null);
    setStockAdjustment('');
    fetchData();
  };

  const formatPrice = (val: number) => {
    return '₱' + val.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  // Get Lucide Icon matching category ID
  const getCategoryIcon = (catId: string) => {
    switch (catId) {
      case 'cat_drinks':
        return <Coffee className="w-5 h-5 text-emerald-400" />;
      case 'combos':
        return <Utensils className="w-5 h-5 text-purple-400" />;
      case 'cat_buldak':
        return <Flame className="w-5 h-5 text-orange-400" />;
      default:
        return <Cookie className="w-5 h-5 text-amber-400" />;
    }
  };

  // Effective prices for a size variant: per-flavor prices when present, else the flat base.
  const variantPrices = (v: Variant): number[] => {
    const flavorPrices = Object.values(v.priceByFlavor || {}).filter(p => p > 0);
    if (flavorPrices.length > 0) return flavorPrices;
    return v.basePrice > 0 ? [v.basePrice] : [];
  };

  // Calculate Starting Price for Menu Item Card (lowest of any size/flavor combination).
  const getStartingPrice = (variantsJson: string) => {
    try {
      const vars = JSON.parse(variantsJson) as Variant[];
      const prices = vars.flatMap(variantPrices);
      if (prices.length > 0) return Math.min(...prices);
    } catch (_) {}
    return 0;
  };

  // Check if Menu Item is Low Stock based on recipes & inventory levels
  const checkIsItemLowStock = (itemId: string) => {
    const itemRecipes = recipes.filter(r => r.menu_item_id === itemId);
    if (itemRecipes.length === 0) return false;
    return itemRecipes.some(r => {
      const inv = inventory.find(i => i.id === r.inventory_item_id);
      return inv && inv.current_stock <= inv.reorder_threshold;
    });
  };

  // Print simulated Z-Reading
  const handlePrintZReading = () => {
    setPrintSummary({
      title: "End of Day Z-Reading Summary",
      grossSales: todayStats.grossSales,
      discounts: todayStats.discounts,
      netRevenue: todayStats.netSales,
      cashSales: todayStats.cashSales,
      gcashSales: todayStats.gcashSales,
      startingFloat: STARTING_FLOAT,
      expenses: todayExpenses,
      drawerBalance: todayStats.cashSales + STARTING_FLOAT - todayExpenses,
      profits: todayStats.netSales - todayExpenses,
      orderCount: todayStats.count,
      timestamp: Date.now()
    });
    setShowPrintModal(true);
  };

  if (!supabase) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-950 p-6 text-slate-100 font-sans">
        <div className="relative w-full max-w-xl bg-slate-900/40 border border-slate-800 rounded-3xl p-8 backdrop-blur-xl shadow-2xl flex flex-col gap-6 overflow-hidden">
          <div className="absolute -right-16 -top-16 w-48 h-48 bg-emerald-500/10 rounded-full blur-3xl"></div>
          <div className="absolute -left-16 -bottom-16 w-48 h-48 bg-purple-500/10 rounded-full blur-3xl"></div>

          <div className="flex items-center gap-4">
            <div className="p-3 bg-amber-500/10 border border-amber-500/30 rounded-2xl text-amber-400">
              <Sparkles className="w-6 h-6 animate-pulse" />
            </div>
            <div>
              <h1 className="text-xl font-bold tracking-tight">Supabase Configuration Required</h1>
              <p className="text-sm text-slate-400">POS Web Manager Dashboard</p>
            </div>
          </div>

          <div className="bg-slate-950/60 border border-slate-800/80 rounded-2xl p-5 flex flex-col gap-4">
            <p className="text-sm text-slate-300 leading-relaxed">
              To connect this dashboard to your POS live database, please add the following environment variables to your deployment environment (e.g., Vercel Project Settings):
            </p>

            <div className="flex flex-col gap-3 font-mono text-xs">
              <div className="bg-slate-900 border border-slate-800 p-3.5 rounded-xl flex flex-col gap-1 select-all">
                <span className="text-slate-500"># Supabase Project URL</span>
                <span className="text-emerald-400">NEXT_PUBLIC_SUPABASE_URL</span>
              </div>
              <div className="bg-slate-900 border border-slate-800 p-3.5 rounded-xl flex flex-col gap-1 select-all">
                <span className="text-slate-500"># Supabase Anon/Public Key</span>
                <span className="text-emerald-400">NEXT_PUBLIC_SUPABASE_ANON_KEY</span>
              </div>
            </div>
          </div>

          <div className="flex flex-col gap-2.5 text-xs text-slate-400">
            <p className="font-semibold text-slate-300">How to configure on Vercel:</p>
            <ol className="list-decimal pl-4 flex flex-col gap-1.5 leading-relaxed">
              <li>Go to your Vercel Project Dashboard.</li>
              <li>Navigate to <strong>Settings</strong> &gt; <strong>Environment Variables</strong>.</li>
              <li>Add the two variables above using the credentials from your Supabase Project Settings.</li>
              <li>Redeploy or trigger a new build on Vercel.</li>
            </ol>
          </div>
        </div>
      </div>
    );
  }

  // Percentage splits for Z-reading progress bars (today-scoped)
  const totalCollected = todayStats.cashSales + todayStats.gcashSales;
  const cashPct = totalCollected > 0 ? (todayStats.cashSales / totalCollected) * 100 : 50;
  const targetSales = 10000.0;
  const goalProgressPct = Math.min(100, (todayStats.netSales / targetSales) * 100);

  return (
    <div className="flex h-screen bg-[#050507] text-slate-100 font-sans overflow-hidden relative">
      
      {/* Background Ambient Glows (Matches app AdaptiveAmbientGlows) */}
      <div className="absolute top-[-10%] left-[-10%] w-[500px] h-[500px] rounded-full bg-emerald-500/5 blur-[120px] pointer-events-none z-0"></div>
      <div className="absolute bottom-[-10%] right-[-10%] w-[600px] h-[600px] rounded-full bg-purple-500/5 blur-[150px] pointer-events-none z-0"></div>
      <div className="absolute top-[40%] left-[30%] w-[400px] h-[400px] rounded-full bg-purple-600/3 blur-[110px] pointer-events-none z-0"></div>

      {/* Sidebar Navigation (hidden on mobile — replaced by the bottom tab bar) */}
      <aside className="hidden md:flex w-64 bg-[#0c0c0e]/80 backdrop-blur-xl border-r border-white/5 flex-col justify-between p-6 z-10">
        <div className="flex flex-col gap-8">
          
          {/* Logo & Brand Wordmark matching app exactly */}
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-emerald-500/10 border border-emerald-500/20 rounded-2xl shadow-[0_0_15px_rgba(16,185,129,0.1)]">
              <Coffee className="w-6 h-6 text-emerald-400" />
            </div>
            <div>
              <h1 className="text-xl font-black tracking-tight select-none">
                <span className="text-slate-100">Brew </span>
                <span className="text-purple-400">ni </span>
                <span className="text-emerald-400">Cat</span>
              </h1>
              <p className="text-[10px] text-slate-400 uppercase tracking-widest font-bold mt-0.5">POS Manager Panel</p>
            </div>
          </div>

          {/* Navigation Links with Glassmorphic Active States */}
          <nav className="flex flex-col gap-2">
            <button
              onClick={() => setActiveTab('dashboard')}
              className={`flex items-center gap-3.5 px-4.5 py-3 rounded-2xl text-sm font-medium transition-all ${
                activeTab === 'dashboard'
                  ? 'bg-white/[0.04] text-emerald-400 border border-white/5 shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]'
                  : 'text-slate-400 hover:bg-white/[0.02] hover:text-slate-200 border border-transparent'
              }`}
            >
              <TrendingUp className="w-4 h-4" />
              Live Dashboard
            </button>
            <button
              onClick={() => setActiveTab('history')}
              className={`flex items-center gap-3.5 px-4.5 py-3 rounded-2xl text-sm font-medium transition-all ${
                activeTab === 'history'
                  ? 'bg-white/[0.04] text-emerald-400 border border-white/5 shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]'
                  : 'text-slate-400 hover:bg-white/[0.02] hover:text-slate-200 border border-transparent'
              }`}
            >
              <Receipt className="w-4 h-4" />
              Order Audit Log
            </button>
            <button
              onClick={() => setActiveTab('expenses')}
              className={`flex items-center gap-3.5 px-4.5 py-3 rounded-2xl text-sm font-medium transition-all ${
                activeTab === 'expenses'
                  ? 'bg-white/[0.04] text-emerald-400 border border-white/5 shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]'
                  : 'text-slate-400 hover:bg-white/[0.02] hover:text-slate-200 border border-transparent'
              }`}
            >
              <DollarSign className="w-4 h-4" />
              Expense Log
            </button>
            <button
              onClick={() => setActiveTab('menu')}
              className={`flex items-center gap-3.5 px-4.5 py-3 rounded-2xl text-sm font-medium transition-all ${
                activeTab === 'menu'
                  ? 'bg-white/[0.04] text-emerald-400 border border-white/5 shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]'
                  : 'text-slate-400 hover:bg-white/[0.02] hover:text-slate-200 border border-transparent'
              }`}
            >
              <Layers className="w-4 h-4" />
              Menu Catalog Editor
            </button>
            <button
              onClick={() => setActiveTab('inventory')}
              className={`flex items-center gap-3.5 px-4.5 py-3 rounded-2xl text-sm font-medium transition-all ${
                activeTab === 'inventory'
                  ? 'bg-white/[0.04] text-emerald-400 border border-white/5 shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]'
                  : 'text-slate-400 hover:bg-white/[0.02] hover:text-slate-200 border border-transparent'
              }`}
            >
              <Package className="w-4 h-4" />
              Aggregated Stock
            </button>
          </nav>
        </div>

        {/* Database Sync Status Indicator */}
        <div className="flex items-center justify-between border-t border-white/5 pt-6 bg-slate-950/20">
          <div className="flex items-center gap-2">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
            </span>
            <span className="text-[11px] text-slate-500 font-semibold tracking-wide">Cloud Database Synced</span>
          </div>
          <button
            onClick={handleRefresh}
            className="p-2 hover:bg-white/5 active:bg-white/10 rounded-xl text-slate-400 hover:text-slate-200 transition-colors border border-transparent hover:border-white/5"
            title="Refresh database data"
          >
            <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </aside>

      {/* Main Content Pane */}
      <main className="flex-1 flex flex-col min-w-0 bg-[#050507]/30 overflow-y-auto z-10 relative">
        
        {/* Glass Header */}
        <header className="h-20 border-b border-white/5 flex items-center justify-between px-4 md:px-8 bg-[#050507]/40 backdrop-blur-md sticky top-0 z-20">
          <div className="flex items-center gap-3 min-w-0">
            <h2 className="text-base md:text-lg font-black tracking-tight text-slate-100 flex items-center gap-2 truncate">
              {activeTab === 'dashboard' && '📊 Live Sales Overview'}
              {activeTab === 'history' && '🧾 Order Audit Log'}
              {activeTab === 'expenses' && '💸 Expense Audit Log'}
              {activeTab === 'menu' && '🍔 Menu Catalog Editor'}
              {activeTab === 'inventory' && '📦 Aggregated Inventory & Stock'}
            </h2>
            <span className="hidden sm:inline text-[10px] font-black px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 shadow-[0_0_10px_rgba(16,185,129,0.1)] uppercase tracking-wider">
              Online
            </span>
          </div>
          {/* Mobile refresh (the sidebar one is hidden on small screens) */}
          <button
            onClick={handleRefresh}
            className="md:hidden p-2 hover:bg-white/5 active:bg-white/10 rounded-xl text-slate-400 hover:text-slate-200 transition-colors border border-transparent hover:border-white/5 shrink-0"
            title="Refresh database data"
          >
            <RefreshCw className={`w-5 h-5 ${refreshing ? 'animate-spin' : ''}`} />
          </button>
        </header>

        {loading ? (
          <div className="flex-1 flex items-center justify-center">
            <div className="flex flex-col items-center gap-3 bg-white/[0.02] border border-white/5 p-8 rounded-3xl backdrop-blur-lg">
              <div className="w-8 h-8 border-2 border-emerald-500/20 border-t-emerald-400 rounded-full animate-spin"></div>
              <p className="text-xs text-slate-400 font-semibold uppercase tracking-wider mt-2">Loading cloud aggregates...</p>
            </div>
          </div>
        ) : (
          <div className="p-4 sm:p-6 md:p-8 pb-24 md:pb-8 flex flex-col gap-6 md:gap-8 max-w-7xl mx-auto w-full">
            
            {/* TAB 1: DASHBOARD */}
            {activeTab === 'dashboard' && (
              <>
                {/* Stats Grid */}
                <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                  
                  {/* Today's Net Profit */}
                  <div className="bg-[#0c0c0e]/60 border border-white/5 rounded-3xl p-6 flex flex-col justify-between h-36 relative overflow-hidden backdrop-blur-xl shadow-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.03)]">
                    <div className="absolute right-0 top-0 w-24 h-24 bg-emerald-500/5 rounded-full blur-2xl"></div>
                    <span className="text-[11px] font-bold uppercase tracking-widest text-slate-400">Today&apos;s Net Profit</span>
                    <span className="text-3xl font-black text-emerald-400 tracking-tight mt-1">{formatPrice(todayStats.netSales)}</span>
                    <span className="text-[10px] text-slate-500 flex items-center gap-1 font-semibold mt-2">
                      <Sparkles className="w-3.5 h-3.5 text-emerald-500" /> All-time: {formatPrice(allTimeStats.netSales)}
                    </span>
                  </div>

                  {/* Total Orders Taken */}
                  <div className="bg-[#0c0c0e]/60 border border-white/5 rounded-3xl p-6 flex flex-col justify-between h-36 relative overflow-hidden backdrop-blur-xl shadow-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.03)]">
                    <span className="text-[11px] font-bold uppercase tracking-widest text-slate-400">Today&apos;s Orders</span>
                    <span className="text-3xl font-black text-slate-100 tracking-tight mt-1">{todayStats.count}</span>
                    <span className="text-[10px] text-slate-500 mt-2 font-semibold">All-time: {allTimeStats.count} &bull; {uniqueDevices.length} devices</span>
                  </div>

                  {/* Cash Sales Split */}
                  <div className="bg-[#0c0c0e]/60 border border-white/5 rounded-3xl p-6 flex flex-col justify-between h-36 backdrop-blur-xl shadow-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.03)]">
                    <span className="text-[11px] font-bold uppercase tracking-widest text-slate-400">Today&apos;s Cash Sales</span>
                    <span className="text-3xl font-black text-sky-400 tracking-tight mt-1">{formatPrice(todayStats.cashSales)}</span>
                    <span className="text-[10px] text-slate-500 mt-2 font-semibold">All-time: {formatPrice(allTimeStats.cashSales)}</span>
                  </div>

                  {/* GCash Sales Split */}
                  <div className="bg-[#0c0c0e]/60 border border-white/5 rounded-3xl p-6 flex flex-col justify-between h-36 backdrop-blur-xl shadow-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.03)]">
                    <span className="text-[11px] font-bold uppercase tracking-widest text-slate-400">Today&apos;s GCash Sales</span>
                    <span className="text-3xl font-black text-indigo-400 tracking-tight mt-1">{formatPrice(todayStats.gcashSales)}</span>
                    <span className="text-[10px] text-slate-500 mt-2 font-semibold">All-time: {formatPrice(allTimeStats.gcashSales)}</span>
                  </div>
                </div>

                {/* Dashboard Inner Grid */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                  
                  {/* Left Column: Live Kitchen Queue */}
                  <div className="lg:col-span-2 bg-[#0c0c0e]/60 border border-white/5 rounded-3xl p-6 flex flex-col gap-5 backdrop-blur-xl shadow-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.03)]">
                    <div className="flex justify-between items-center">
                      <h3 className="text-sm font-black uppercase tracking-wider text-slate-300">Live Kitchen Queue</h3>
                      <span className="text-xs text-slate-400 font-bold">Latest 10 orders</span>
                    </div>

                    <div className="flex flex-col gap-3.5 max-h-[500px] overflow-y-auto pr-2">
                      {orders.slice(0, 10).map(order => {
                        const displayId = order.id >= 1000000000 ? order.id % 1000000000 : order.id;
                        return (
                          <div key={order.id} className="bg-white/[0.02] border border-white/5 rounded-2xl p-4 flex items-center justify-between transition-all hover:bg-white/[0.04]">
                            <div className="flex flex-col gap-1.5">
                              <div className="flex items-center gap-2.5">
                                <span className="text-sm font-black text-emerald-400">#{String(displayId).padStart(4, '0')}</span>
                                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-white/5 border border-white/10 text-slate-300">
                                  {order.table_label ? order.table_label : 'Take Out'}
                                </span>
                                <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${order.payment_method === 'CASH' ? 'bg-sky-500/10 border border-sky-500/20 text-sky-400' : 'bg-indigo-500/10 border border-indigo-500/20 text-indigo-400'}`}>
                                  {order.payment_method}
                                </span>
                              </div>
                              <span className="text-xs text-slate-300 font-medium">
                                {order.order_items?.map(i => `${i.quantity}x ${i.item_name} (${i.variant_name}${i.flavor ? ` - ${i.flavor.includes(': ') ? i.flavor.split(': ').pop() : i.flavor}` : ''})`).join(', ')}
                              </span>
                              <span className="text-[10px] font-semibold text-slate-500 flex items-center gap-1.5">
                                <User className="w-3 h-3 text-slate-500" /> Cashier: {order.cashier_name || 'Popot'} &bull; {new Date(order.timestamp).toLocaleTimeString()}
                              </span>
                            </div>

                            <button
                              onClick={() => toggleOrderServed(order.id, order.is_served)}
                              className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold transition-all border ${
                                order.is_served
                                  ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20 shadow-[0_0_10px_rgba(16,185,129,0.05)]'
                                  : 'bg-amber-500/10 border-amber-500/20 text-amber-400 hover:bg-amber-500/20 shadow-[0_0_10px_rgba(245,158,11,0.05)]'
                              }`}
                            >
                              {order.is_served ? (
                                <>
                                  <CheckCircle className="w-3.5 h-3.5" />
                                  Served
                                </>
                              ) : (
                                <>
                                  <Clock className="w-3.5 h-3.5 animate-pulse" />
                                  Preparing
                                </>
                              )}
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  {/* Right Column: Financial Integrity Z-Reading (App Clone Layout) */}
                  <div className="bg-[#0c0c0e]/60 border border-white/5 rounded-3xl p-6 flex flex-col gap-6 backdrop-blur-xl shadow-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.03)] justify-between">
                    <div>
                      <div className="flex justify-between items-center mb-2">
                        <h3 className="text-sm font-black uppercase tracking-wider text-slate-300">End of Day Report</h3>
                        <div className="flex gap-2">
                          <button
                            onClick={handlePrintZReading}
                            className="p-2 hover:bg-white/5 active:bg-white/10 rounded-xl text-slate-300 hover:text-slate-100 transition-colors border border-white/5"
                            title="Print Z-Reading"
                          >
                            <Printer className="w-4 h-4" />
                          </button>
                        </div>
                      </div>

                      {/* Report day picker — the owner counts the drawer the next morning, so they
                          can pull up any past day's takings here. */}
                      <div className="flex items-center justify-between gap-2 mb-4 bg-white/[0.03] border border-white/10 rounded-xl px-3 py-2">
                        <div className="flex items-center gap-2 min-w-0">
                          <Calendar className="w-4 h-4 text-emerald-400 shrink-0" />
                          <span className="text-xs font-bold text-slate-300 truncate">
                            {isReportToday ? 'Today' : reportDateLabel}
                          </span>
                        </div>
                        <div className="flex items-center gap-1.5">
                          <input
                            type="date"
                            value={reportDate}
                            max={todayKey}
                            onChange={e => setReportDate(e.target.value || todayKey)}
                            className="bg-white/[0.03] border border-white/10 px-2 py-1 rounded-lg text-[11px] text-slate-200 outline-none focus:border-emerald-500/50 transition-all font-semibold"
                          />
                          {!isReportToday && (
                            <button
                              onClick={() => setReportDate(todayKey)}
                              className="text-[10px] font-bold text-emerald-400 hover:text-emerald-300 px-2 py-1 rounded-lg border border-emerald-500/20 hover:bg-emerald-500/10 transition-all whitespace-nowrap"
                            >
                              Today
                            </button>
                          )}
                        </div>
                      </div>

                      <div className="flex flex-col gap-4">
                        <div className="flex justify-between items-center border-b border-white/5 pb-3.5 text-sm">
                          <span className="text-slate-400 font-semibold">Total Sales (Gross)</span>
                          <span className="text-right">
                            <span className="font-bold text-slate-200 block">{formatPrice(todayStats.grossSales)}</span>
                            <span className="text-[10px] text-slate-500 font-semibold">{isReportToday ? 'today' : reportDateLabel} &bull; all-time {formatPrice(allTimeStats.grossSales)}</span>
                          </span>
                        </div>
                        <div className="flex justify-between border-b border-white/5 pb-3.5 text-sm">
                          <span className="text-slate-400 font-semibold">Discounts Given</span>
                          <span className="font-bold text-red-400">-{formatPrice(todayStats.discounts)}</span>
                        </div>

                        {/* Net Revenue + Profits — the headline the owner counts against the drawer */}
                        <div className="flex justify-between items-center border-b border-white/5 pb-3.5 text-sm">
                          <span className="text-slate-400 font-semibold">Net Revenue</span>
                          <span className="font-bold text-slate-200">{formatPrice(todayStats.netSales)}</span>
                        </div>
                        <div className="flex justify-between items-center border-b border-white/5 pb-3.5">
                          <span className="text-slate-200 font-bold text-sm">Profits (Net Cash Flow)</span>
                          <span className="font-black text-emerald-400 text-lg">{formatPrice(todayStats.netSales - todayExpenses)}</span>
                        </div>
                        
                        {/* Goal Progress bar clone */}
                        <div className="flex flex-col gap-1 border-b border-white/5 pb-3.5">
                          <div className="flex justify-between text-xs font-semibold">
                            <span className="text-slate-400">Goal Progress (Target {formatPrice(targetSales)})</span>
                            <span className="text-emerald-400 font-black">{goalProgressPct.toFixed(0)}%</span>
                          </div>
                          <div className="w-full bg-white/5 border border-white/5 rounded-full h-2 overflow-hidden mt-1">
                            <div className="bg-emerald-500 h-full rounded-full transition-all duration-700" style={{ width: `${goalProgressPct}%` }}></div>
                          </div>
                        </div>

                        <div className="flex flex-col gap-2 border-b border-white/5 pb-3.5">
                          <div className="flex justify-between text-sm">
                            <span className="text-slate-400 font-semibold">Operating Expenses</span>
                            <span className="font-bold text-red-400">-{formatPrice(todayExpenses)}</span>
                          </div>
                          {todayExpenseList.length > 0 ? (
                            <div className="flex flex-col gap-1 pl-1">
                              {todayExpenseList.map((e) => (
                                <div key={e.id} className="flex justify-between items-center text-[11px]">
                                  <span className="text-slate-400 truncate">
                                    <span className="text-slate-300 font-medium">{e.description || 'Expense'}</span>
                                    <span className="text-slate-600 ml-1.5">
                                      · {e.recorded_by || '—'} · {new Date(e.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                    </span>
                                  </span>
                                  <span className="text-red-400/80 font-semibold whitespace-nowrap ml-2">-{formatPrice(e.amount)}</span>
                                </div>
                              ))}
                            </div>
                          ) : (
                            <span className="text-[11px] text-slate-600 pl-1">No expenses recorded {isReportToday ? 'today' : 'this day'}.</span>
                          )}
                        </div>

                        {/* Cash Drawer Status clone */}
                        <div className="flex flex-col gap-1 border-b border-white/5 pb-3.5">
                          <div className="flex justify-between text-sm">
                            <span className="text-slate-300 font-bold">Estimated Drawer Balance</span>
                            <span className="font-black text-emerald-400 text-base">{formatPrice(todayStats.cashSales + STARTING_FLOAT - todayExpenses)}</span>
                          </div>
                          <div className="flex justify-between text-[10px] text-slate-500 font-semibold">
                            <span>(Float: {formatPrice(STARTING_FLOAT)} + Cash: {formatPrice(todayStats.cashSales)} &minus; Expenses: {formatPrice(todayExpenses)})</span>
                          </div>
                        </div>

                        {/* Payment split clone */}
                        <div className="flex flex-col gap-1">
                          <div className="flex justify-between text-xs font-bold text-slate-400">
                            <span>Payment Modes Split</span>
                            <span>Cash: {cashPct.toFixed(0)}% &bull; GCash: {(100 - cashPct).toFixed(0)}%</span>
                          </div>
                          <div className="w-full bg-white/5 border border-white/5 rounded-full h-3 overflow-hidden mt-1 flex">
                            <div className="bg-sky-500 h-full transition-all duration-500" style={{ width: `${cashPct}%` }}></div>
                            <div className="bg-indigo-500 h-full transition-all duration-500" style={{ width: `${100 - cashPct}%` }}></div>
                          </div>
                        </div>

                      </div>
                    </div>

                    <span className="text-[10px] text-slate-500 leading-relaxed font-semibold mt-4">
                      Note: Figures above are for {isReportToday ? 'today' : reportDateLabel}. Expenses are aggregated from cashier terminal cash drawer entries. Estimates assume a ₱1,500 starting cash float.
                    </span>
                  </div>
                </div>

                {/* Food & Drink Totals — per-item running totals, separate from shop takings */}
                <div className="bg-[#0c0c0e]/60 border border-white/5 rounded-3xl p-6 backdrop-blur-xl shadow-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.03)] flex flex-col gap-5">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="flex flex-col">
                      <h3 className="text-sm font-black uppercase tracking-wider text-slate-300 flex items-center gap-2">
                        <Utensils className="w-4 h-4 text-emerald-400" /> Food &amp; Drink Totals
                      </h3>
                      <span className="text-[11px] text-slate-500 font-semibold mt-0.5">
                        Per item · {breakdownPeriod === 'week' ? 'This Week' : 'This Month'} ({breakdownRangeLabel})
                      </span>
                    </div>
                    {/* Week / Month toggle */}
                    <div className="flex items-center gap-1 bg-white/[0.03] border border-white/10 rounded-xl p-1">
                      {(['week', 'month'] as const).map(p => (
                        <button
                          key={p}
                          onClick={() => setBreakdownPeriod(p)}
                          className={`px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all ${
                            breakdownPeriod === p
                              ? 'bg-emerald-500/20 border border-emerald-500/30 text-emerald-300'
                              : 'text-slate-400 hover:text-slate-200'
                          }`}
                        >
                          {p === 'week' ? 'Per Week' : 'Per Month'}
                        </button>
                      ))}
                    </div>
                  </div>

                  {foodBreakdown.length === 0 ? (
                    <span className="text-xs text-slate-600 font-semibold py-4 text-center">
                      No sales recorded for this {breakdownPeriod === 'week' ? 'week' : 'month'} yet.
                    </span>
                  ) : (
                    <div className="flex flex-col gap-5">
                      {foodBreakdownGrouped.map(group => (
                        <div key={group.categoryName} className="flex flex-col gap-2">
                          <div className="flex justify-between items-center">
                            <span className="text-xs font-black uppercase tracking-wider text-emerald-400">{group.categoryName}</span>
                            <span className="text-xs font-black text-emerald-400">{formatPrice(group.total)}</span>
                          </div>
                          <div className="flex flex-col gap-1.5">
                            {group.rows.map(row => (
                              <div key={row.itemName} className="flex justify-between items-center bg-white/[0.02] border border-white/5 rounded-xl px-3.5 py-2.5">
                                <div className="flex flex-col min-w-0">
                                  <span className="text-sm font-semibold text-slate-200 truncate">{row.itemName}</span>
                                  <span className="text-[10px] text-slate-500 font-semibold">{row.quantity} sold</span>
                                </div>
                                <span className="text-sm font-bold text-slate-100 whitespace-nowrap ml-2">{formatPrice(row.sales)}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      ))}
                      <div className="flex justify-between items-center border-t border-white/10 pt-4 mt-1">
                        <span className="text-sm font-black text-slate-200">All Food &amp; Drinks</span>
                        <span className="text-xl font-black text-emerald-400">{formatPrice(foodBreakdownTotal)}</span>
                      </div>
                    </div>
                  )}
                </div>
              </>
            )}

            {/* TAB 2: ORDER HISTORY TIMELINE */}
            {activeTab === 'history' && (
              <div className="flex flex-col gap-6 bg-[#0c0c0e]/60 border border-white/5 rounded-3xl p-6 backdrop-blur-xl shadow-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.03)]">
                
                {/* Filter and Actions Bar */}
                <div className="flex flex-wrap items-end justify-between gap-4 border-b border-white/5 pb-6">
                  <div className="flex flex-wrap items-center gap-4">
                    {/* Date Picker */}
                    <div className="flex flex-col gap-1.5">
                      <label className="text-[10px] uppercase font-bold text-slate-500 tracking-wider">Filter Date</label>
                      <input
                        type="date"
                        value={filterDate}
                        onChange={e => setFilterDate(e.target.value)}
                        className="bg-white/[0.03] border border-white/10 px-3.5 py-2.5 rounded-xl text-xs text-slate-200 outline-none focus:border-emerald-500/50 transition-all font-semibold"
                      />
                    </div>
                    {/* Device Selector */}
                    <div className="flex flex-col gap-1.5">
                      <label className="text-[10px] uppercase font-bold text-slate-500 tracking-wider">Cashier Device</label>
                      <select
                        value={filterDeviceId}
                        onChange={e => setFilterDeviceId(e.target.value)}
                        className="bg-white/[0.03] border border-white/10 px-3.5 py-2.5 rounded-xl text-xs text-slate-200 outline-none focus:border-emerald-500/50 transition-all font-semibold"
                      >
                        <option value="all">All Devices</option>
                        {uniqueDevices.map(id => (
                          <option key={id} value={id}>Device {id.slice(0, 8)}...</option>
                        ))}
                      </select>
                    </div>
                    {/* Payment Selector */}
                    <div className="flex flex-col gap-1.5">
                      <label className="text-[10px] uppercase font-bold text-slate-500 tracking-wider">Payment Method</label>
                      <select
                        value={filterPayment}
                        onChange={e => setFilterPayment(e.target.value)}
                        className="bg-white/[0.03] border border-white/10 px-3.5 py-2.5 rounded-xl text-xs text-slate-200 outline-none focus:border-emerald-500/50 transition-all font-semibold"
                      >
                        <option value="all">All Payments</option>
                        <option value="CASH">Cash</option>
                        <option value="GCASH">GCash</option>
                      </select>
                    </div>
                  </div>

                  <button
                    onClick={exportCSV}
                    className="flex items-center gap-2 px-5 py-3 bg-emerald-500 text-slate-950 rounded-2xl text-xs font-black hover:bg-emerald-400 active:scale-[0.98] transition-all"
                  >
                    <Download className="w-4 h-4" />
                    Export CSV Report
                  </button>
                </div>

                {/* Timeline of Order logs */}
                <div className="flex flex-col gap-4">
                  {getFilteredOrders().length === 0 ? (
                    <div className="py-12 text-center text-slate-500 font-semibold text-sm">
                      No orders match the selected filters.
                    </div>
                  ) : (
                    getFilteredOrders().map(o => {
                      const displayId = o.id >= 1000000000 ? o.id % 1000000000 : o.id;
                      const isExpanded = expandedOrders[o.id] || false;
                      const formattedTime = new Date(o.timestamp).toLocaleString();

                      return (
                        <div
                          key={o.id}
                          className={`border rounded-2xl overflow-hidden transition-all duration-300 ${
                            o.is_voided
                              ? 'bg-red-500/[0.01] border-red-500/10 opacity-70 hover:opacity-100'
                              : 'bg-white/[0.02] border-white/5 hover:bg-white/[0.03]'
                          }`}
                        >
                          {/* Top Row / Card Header */}
                          <div
                            onClick={() => toggleOrderExpand(o.id)}
                            className="p-4 flex flex-wrap items-center justify-between gap-4 cursor-pointer select-none"
                          >
                            <div className="flex items-center gap-3">
                              <span className="text-sm font-black text-emerald-400">
                                #{String(displayId).padStart(4, '0')}
                              </span>
                              {o.is_voided && (
                                <span className="text-[9px] font-black px-2 py-0.5 rounded-full bg-red-500/10 border border-red-500/25 text-red-400 uppercase tracking-widest animate-pulse">
                                  Voided
                                </span>
                              )}
                              <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full bg-white/5 border border-white/10 ${o.is_voided ? 'text-slate-500 line-through' : 'text-slate-300'}`}>
                                {o.table_label ? o.table_label : 'Take Out'}
                              </span>
                              <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${o.payment_method === 'CASH' ? 'bg-sky-500/10 border border-sky-500/20 text-sky-400' : 'bg-indigo-500/10 border border-indigo-500/20 text-indigo-400'}`}>
                                {o.payment_method}
                              </span>
                              <span className="text-xs text-slate-400 font-semibold hidden md:inline">
                                {formattedTime}
                              </span>
                            </div>

                            <div className="flex items-center gap-4">
                              <span className="text-xs text-slate-400 font-semibold flex items-center gap-1.5">
                                <User className="w-3.5 h-3.5 text-slate-400" /> {o.cashier_name || 'Popot'}
                              </span>
                              <span className={`text-sm font-black ${o.is_voided ? 'text-slate-500 line-through' : 'text-slate-200'}`}>{formatPrice(o.total)}</span>
                              
                              {/* Status Indicator Served/Preparing */}
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  toggleOrderServed(o.id, o.is_served);
                                }}
                                className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[10px] font-bold transition-all border ${
                                  o.is_served
                                    ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20'
                                    : 'bg-amber-500/10 border-amber-500/20 text-amber-400 hover:bg-amber-500/20'
                                }`}
                              >
                                {o.is_served ? 'Served' : 'Preparing'}
                              </button>

                              <div>
                                {isExpanded ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
                              </div>
                            </div>
                          </div>

                          {/* Accordion content: Receipt details clone */}
                          {isExpanded && (
                            <div className="bg-[#050507]/40 border-t border-white/5 p-5 flex flex-col gap-5 font-sans">
                              
                              {/* Edit details form */}
                              <div className="bg-white/[0.01] border border-white/5 rounded-2xl p-4 flex flex-col gap-4">
                                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">Edit & Sync Order Details</h4>
                                <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 items-end">
                                  <div className="flex flex-col gap-1.5">
                                    <label className="text-[10px] uppercase font-bold text-slate-500">Name / Type</label>
                                    <input
                                      type="text"
                                      value={editTableLabels[o.id] !== undefined ? editTableLabels[o.id] : (o.table_label || '')}
                                      onChange={(e) => setEditTableLabels(prev => ({ ...prev, [o.id]: e.target.value }))}
                                      className="bg-white/[0.03] border border-white/10 px-3 py-2 rounded-xl text-xs text-slate-200 outline-none focus:border-emerald-500/50"
                                      placeholder="Take Out"
                                    />
                                  </div>
                                  <div className="flex flex-col gap-1.5">
                                    <label className="text-[10px] uppercase font-bold text-slate-500">Cashier Name</label>
                                    <input
                                      type="text"
                                      value={editCashierNames[o.id] !== undefined ? editCashierNames[o.id] : (o.cashier_name || '')}
                                      onChange={(e) => setEditCashierNames(prev => ({ ...prev, [o.id]: e.target.value }))}
                                      className="bg-white/[0.03] border border-white/10 px-3 py-2 rounded-xl text-xs text-slate-200 outline-none focus:border-emerald-500/50"
                                    />
                                  </div>
                                  <div className="flex flex-col gap-1.5">
                                    <label className="text-[10px] uppercase font-bold text-slate-500">Payment Mode</label>
                                    <select
                                      value={editPaymentMethods[o.id] !== undefined ? editPaymentMethods[o.id] : o.payment_method}
                                      onChange={(e) => setEditPaymentMethods(prev => ({ ...prev, [o.id]: e.target.value }))}
                                      className="bg-white/[0.03] border border-white/10 px-3 py-2 rounded-xl text-xs text-slate-200 outline-none focus:border-emerald-500/50 font-semibold"
                                    >
                                      <option value="CASH">Cash</option>
                                      <option value="GCASH">GCash</option>
                                    </select>
                                  </div>
                                  <div className="flex flex-col gap-1.5">
                                    <label className="text-[10px] uppercase font-bold text-slate-500">GCash Ref Reference</label>
                                    <input
                                      type="text"
                                      value={editReferenceIds[o.id] !== undefined ? editReferenceIds[o.id] : (o.payment_reference || '')}
                                      onChange={(e) => setEditReferenceIds(prev => ({ ...prev, [o.id]: e.target.value }))}
                                      className="bg-white/[0.03] border border-white/10 px-3 py-2 rounded-xl text-xs text-slate-200 outline-none focus:border-emerald-500/50"
                                      placeholder="N/A"
                                    />
                                  </div>
                                </div>
                                <div className="flex justify-end gap-3 mt-1 pt-3 border-t border-white/5">
                                  <button
                                    onClick={() => toggleOrderVoid(o.id, o.is_voided || false)}
                                    className={`px-4 py-2 rounded-xl text-xs font-bold transition-all border ${
                                      o.is_voided
                                        ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20'
                                        : 'bg-red-500/10 border-red-500/20 text-red-400 hover:bg-red-500/20'
                                    }`}
                                  >
                                    {o.is_voided ? '🟢 Unvoid Order' : '🔴 Void Order'}
                                  </button>
                                  <button
                                    onClick={() => handleUpdateOrderDetails(o.id)}
                                    className="px-5 py-2 bg-emerald-500 hover:bg-emerald-400 text-slate-950 rounded-xl text-xs font-black transition-all active:scale-[0.98]"
                                  >
                                    💾 Save & Sync to Devices
                                  </button>
                                </div>
                              </div>

                              <div className="flex justify-between items-center border-b border-white/5 pb-2 mt-2">
                                <span className="text-[10px] uppercase font-bold text-slate-500 tracking-wider">Itemized Line Items</span>
                                {editItems[o.id] === undefined ? (
                                  <button
                                    onClick={() => startEditItems(o)}
                                    className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[10px] font-bold text-sky-400 bg-sky-500/10 border border-sky-500/20 hover:bg-sky-500/20 transition-all"
                                  >
                                    <Edit2 className="w-3 h-3" /> Edit Items
                                  </button>
                                ) : (
                                  <span className="text-[10px] uppercase font-bold text-amber-400 tracking-wider">● Editing</span>
                                )}
                              </div>

                              {editItems[o.id] === undefined ? (
                                /* Read-only — mirrors the readable kitchen-queue format, flavor included */
                                <div className="flex flex-col gap-2.5">
                                  {(o.order_items || []).map((item, idx) => (
                                    <div key={idx} className="flex justify-between text-xs font-semibold gap-3">
                                      <span className="text-slate-300">
                                        {item.quantity}x {item.item_name} &bull; {item.variant_name}
                                        {item.flavor && (
                                          <span className="text-[10px] text-emerald-400 ml-2 font-bold uppercase tracking-wider">
                                            ({item.flavor})
                                          </span>
                                        )}
                                      </span>
                                      <span className="text-slate-400 whitespace-nowrap">{formatPrice(item.total_price)}</span>
                                    </div>
                                  ))}
                                  {(o.order_items?.length ?? 0) === 0 && (
                                    <span className="text-xs text-slate-600">No items.</span>
                                  )}
                                </div>
                              ) : (
                                /* Edit mode — name, size, flavor, qty × price, remove */
                                <div className="flex flex-col gap-2">
                                  {getOrderItems(o).map((item, idx) => (
                                    <div key={idx} className="flex items-center gap-1.5 text-xs">
                                      <input
                                        type="text"
                                        value={item.item_name}
                                        onChange={(e) => updateItemField(o.id, idx, 'item_name', e.target.value)}
                                        placeholder="Item name"
                                        className="flex-1 min-w-0 bg-white/[0.03] border border-white/10 px-2.5 py-1.5 rounded-lg text-slate-200 outline-none focus:border-emerald-500/50"
                                      />
                                      <input
                                        type="text"
                                        value={item.variant_name}
                                        onChange={(e) => updateItemField(o.id, idx, 'variant_name', e.target.value)}
                                        placeholder="Size"
                                        className="w-16 bg-white/[0.03] border border-white/10 px-2 py-1.5 rounded-lg text-slate-300 outline-none focus:border-emerald-500/50"
                                      />
                                      <input
                                        type="text"
                                        value={item.flavor || ''}
                                        onChange={(e) => updateItemField(o.id, idx, 'flavor', e.target.value)}
                                        placeholder="Flavor"
                                        className="w-40 bg-white/[0.03] border border-white/10 px-2 py-1.5 rounded-lg text-emerald-300 outline-none focus:border-emerald-500/50"
                                      />
                                      <input
                                        type="number"
                                        min={0}
                                        value={item.quantity}
                                        onChange={(e) => updateItemField(o.id, idx, 'quantity', e.target.value)}
                                        title="Quantity"
                                        className="w-12 bg-white/[0.03] border border-white/10 px-2 py-1.5 rounded-lg text-slate-200 text-center outline-none focus:border-emerald-500/50"
                                      />
                                      <span className="text-slate-500">×</span>
                                      <input
                                        type="number"
                                        min={0}
                                        step="0.01"
                                        value={item.unit_price}
                                        onChange={(e) => updateItemField(o.id, idx, 'unit_price', e.target.value)}
                                        title="Unit price"
                                        className="w-16 bg-white/[0.03] border border-white/10 px-2 py-1.5 rounded-lg text-slate-200 text-right outline-none focus:border-emerald-500/50"
                                      />
                                      <span className="w-16 text-right text-slate-400 font-semibold whitespace-nowrap">{formatPrice(item.quantity * item.unit_price)}</span>
                                      <button
                                        onClick={() => removeItemRow(o.id, idx)}
                                        title="Remove item"
                                        className="p-1.5 rounded-lg text-red-400 hover:bg-red-500/10 border border-transparent hover:border-red-500/20 transition-all"
                                      >
                                        <Trash2 className="w-3.5 h-3.5" />
                                      </button>
                                    </div>
                                  ))}
                                  <div className="flex items-center justify-between mt-1">
                                    <button
                                      onClick={() => addItemRow(o.id)}
                                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 hover:bg-emerald-500/20 transition-all"
                                    >
                                      <Plus className="w-3.5 h-3.5" /> Add item
                                    </button>
                                    <div className="flex gap-2">
                                      <button
                                        onClick={() => cancelEditItems(o.id)}
                                        className="px-4 py-1.5 rounded-lg text-xs font-bold text-slate-400 bg-white/[0.03] border border-white/10 hover:bg-white/[0.06] transition-all"
                                      >
                                        Cancel
                                      </button>
                                      <button
                                        onClick={() => handleUpdateOrderDetails(o.id)}
                                        className="px-4 py-1.5 rounded-lg text-xs font-black text-slate-950 bg-emerald-500 hover:bg-emerald-400 transition-all active:scale-[0.98]"
                                      >
                                        💾 Save Items
                                      </button>
                                    </div>
                                  </div>
                                </div>
                              )}

                              {(() => {
                                const edited = editItems[o.id] !== undefined;
                                const sub = edited ? workingSubtotal(o) : o.subtotal;
                                const tot = edited ? Math.max(0, sub - o.discount_deduction) : o.total;
                                return (
                                  <div className="border-t border-white/5 pt-3.5 flex flex-col gap-1.5 max-w-xs ml-auto w-full text-xs font-semibold">
                                    <div className="flex justify-between text-slate-500">
                                      <span>Subtotal{edited && <span className="text-amber-400 ml-1">(edited)</span>}</span>
                                      <span>{formatPrice(sub)}</span>
                                    </div>
                                    {o.discount_deduction > 0 && (
                                      <div className="flex justify-between text-red-400">
                                        <span>Discount ({o.discount_label})</span>
                                        <span>-{formatPrice(o.discount_deduction)}</span>
                                      </div>
                                    )}
                                    <div className="flex justify-between text-slate-200 font-black border-t border-white/5 pt-2 text-sm">
                                      <span className="text-slate-300 font-bold">Total Bill</span>
                                      <span className="text-emerald-400">{formatPrice(tot)}</span>
                                    </div>
                                    {edited && (
                                      <span className="text-[10px] text-amber-400/80 font-normal mt-1">Unsaved changes — press “Save Items” to apply.</span>
                                    )}
                                  </div>
                                );
                              })()}
                            </div>
                          )}
                        </div>
                      );
                    })
                  )}
                </div>
              </div>
            )}

            {/* TAB 3: MENU EDITOR WITH CATEGORY SELECTOR CHIPS */}
            {/* TAB: EXPENSE AUDIT LOG */}
            {activeTab === 'expenses' && (
              <div className="flex flex-col gap-6">
                {/* Summary cards */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                  <div className="bg-white/[0.02] border border-white/5 rounded-3xl p-5 flex flex-col gap-1">
                    <span className="text-[10px] uppercase tracking-widest font-bold text-slate-500">Today&apos;s Expenses</span>
                    <span className="text-2xl font-black text-red-400">-{formatPrice(todayExpenses)}</span>
                    <span className="text-[10px] text-slate-600">{todayExpenseList.length} entr{todayExpenseList.length === 1 ? 'y' : 'ies'} today</span>
                  </div>
                  <div className="bg-white/[0.02] border border-white/5 rounded-3xl p-5 flex flex-col gap-1">
                    <span className="text-[10px] uppercase tracking-widest font-bold text-slate-500">All-Time Expenses</span>
                    <span className="text-2xl font-black text-slate-200">-{formatPrice(allExpensesTotal)}</span>
                    <span className="text-[10px] text-slate-600">{expenses.length} total entries</span>
                  </div>
                  <div className="bg-white/[0.02] border border-white/5 rounded-3xl p-5 flex flex-col gap-1">
                    <span className="text-[10px] uppercase tracking-widest font-bold text-slate-500">Recorded By</span>
                    <span className="text-2xl font-black text-slate-200">{uniqueRecorders}</span>
                    <span className="text-[10px] text-slate-600">cashier{uniqueRecorders === 1 ? '' : 's'} logging expenses</span>
                  </div>
                </div>

                {/* Full log */}
                <div className="bg-white/[0.01] border border-white/5 rounded-3xl overflow-hidden">
                  <div className="grid grid-cols-12 gap-2 px-5 py-3 border-b border-white/5 text-[10px] uppercase font-bold text-slate-500 tracking-wider">
                    <span className="col-span-4">Description</span>
                    <span className="col-span-3">Recorded By</span>
                    <span className="col-span-2">Date</span>
                    <span className="col-span-2 text-right">Amount</span>
                    <span className="col-span-1" />
                  </div>
                  {expenses.length === 0 ? (
                    <div className="px-5 py-12 text-center text-sm text-slate-600">No expenses recorded yet.</div>
                  ) : (
                    expenses.map((e) => (
                      <div key={e.id} className="grid grid-cols-12 gap-2 px-5 py-3 border-b border-white/5 last:border-0 text-xs items-center hover:bg-white/[0.02] transition-colors">
                        <span className="col-span-4 text-slate-200 font-medium truncate">{e.description || 'Expense'}</span>
                        <span className="col-span-3 text-slate-400 flex items-center gap-1.5 truncate">
                          <User className="w-3 h-3 shrink-0" /> {e.recorded_by || '—'}
                        </span>
                        <span className="col-span-2 text-slate-500 whitespace-nowrap">
                          {new Date(e.timestamp).toLocaleDateString([], { month: 'short', day: 'numeric' })} {new Date(e.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </span>
                        <span className="col-span-2 text-right text-red-400 font-bold whitespace-nowrap">-{formatPrice(e.amount)}</span>
                        <span className="col-span-1 flex justify-end">
                          <button
                            onClick={() => handleDeleteExpense(e)}
                            title="Delete this expense on every device"
                            className="p-1.5 rounded-lg text-slate-600 hover:text-red-400 hover:bg-red-500/10 transition-colors"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </span>
                      </div>
                    ))
                  )}
                </div>
                <p className="text-[10px] text-slate-600 leading-relaxed font-semibold">
                  Audit log of all operating expenses recorded on the cashier terminals, newest first. Syncs live from every device; deleting an entry here removes it everywhere.
                </p>
              </div>
            )}

            {activeTab === 'menu' && (
              <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
                
                {/* Left side: Categories list */}
                <div className="lg:col-span-1 bg-[#0c0c0e]/60 border border-white/5 rounded-3xl p-6 flex flex-col gap-5 backdrop-blur-xl shadow-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.03)] h-fit">
                  <div className="flex items-center justify-between">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">Categories</h3>
                    <button
                      onClick={() => setShowCategoryModal(true)}
                      className="p-1.5 bg-emerald-500/10 hover:bg-emerald-500/20 rounded-xl text-emerald-400 border border-emerald-500/25 active:scale-[0.95] transition-all"
                    >
                      <Plus className="w-4 h-4" />
                    </button>
                  </div>
                  <div className="flex flex-col gap-2">
                    {categories.map(cat => (
                      <div key={cat.id} className="flex items-center justify-between p-3.5 bg-white/[0.02] hover:bg-white/[0.04] rounded-2xl border border-white/5 transition-colors">
                        <div className="flex items-center gap-2.5">
                          {getCategoryIcon(cat.id)}
                          <span className="text-xs font-bold text-slate-200">{cat.name}</span>
                        </div>
                        <button
                          onClick={() => handleDeleteCategory(cat.id)}
                          className="p-1.5 hover:bg-white/5 rounded-lg text-red-400 hover:text-red-300 transition-colors border border-transparent hover:border-white/5"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Right side: Items grid */}
                <div className="lg:col-span-3 flex flex-col gap-6">
                  
                  {/* Category Chips Bar: Clones App layout */}
                  <div className="flex flex-col gap-4 bg-[#0c0c0e]/60 border border-white/5 rounded-3xl p-6 backdrop-blur-xl shadow-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.03)]">
                    <div className="flex justify-between items-center flex-wrap gap-4">
                      <div>
                        <h3 className="text-base font-black">Catalog Products</h3>
                        <p className="text-xs text-slate-500 mt-0.5">Click a category tab below to filter catalog items</p>
                      </div>
                      <button
                        onClick={() => {
                          setEditingItem(null);
                          setItemName('');
                          setItemCategory(categories[0]?.id || '');
                          setItemFlavors('');
                          setItemVariants([{ id: 'regular', name: 'Regular', basePrice: 0, priceByFlavor: {} }]);
                          setPerFlavorPricing(false);
                          setShowItemModal(true);
                        }}
                        className="flex items-center gap-1.5 px-4.5 py-2.5 bg-emerald-500 text-slate-950 rounded-2xl text-xs font-black hover:bg-emerald-400 transition-all active:scale-[0.98]"
                      >
                        <Plus className="w-4 h-4" />
                        Add Product
                      </button>
                    </div>

                    {/* Category Selector Chips (Matches Android app chip selector) */}
                    <div className="flex items-center gap-3 overflow-x-auto pb-1 mt-2">
                      <button
                        onClick={() => setSelectedCategoryId('all')}
                        className={`flex items-center gap-2 px-4 py-2.5 rounded-2xl text-xs font-bold transition-all border ${
                          selectedCategoryId === 'all'
                            ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400'
                            : 'bg-white/[0.02] border-white/5 text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        <Menu className="w-4 h-4" />
                        All Items
                      </button>
                      {categories.map(cat => (
                        <button
                          key={cat.id}
                          onClick={() => setSelectedCategoryId(cat.id)}
                          className={`flex items-center gap-2 px-4 py-2.5 rounded-2xl text-xs font-bold transition-all border ${
                            selectedCategoryId === cat.id
                              ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400'
                              : 'bg-white/[0.02] border-white/5 text-slate-400 hover:text-slate-200'
                          }`}
                        >
                          {getCategoryIcon(cat.id)}
                          {cat.name}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Products Grid featuring App ItemCard Clones */}
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {menuItems
                      .filter(item => selectedCategoryId === 'all' || item.category_id === selectedCategoryId)
                      .map(item => {
                        const categoryName = categories.find(c => c.id === item.category_id)?.name || 'Default';
                        const startingPrice = getStartingPrice(item.variants_json);
                        const isLowStock = checkIsItemLowStock(item.id);

                        return (
                          <div
                            key={item.id}
                            className="bg-[#0c0c0e]/60 border border-white/5 rounded-3xl h-44 p-5 flex flex-col justify-between hover:scale-[1.02] hover:border-emerald-500/20 transition-all duration-300 relative overflow-hidden group shadow-lg shadow-[inset_0_1px_1px_rgba(255,255,255,0.03)]"
                          >
                            {/* Low Stock Badge (Matches app top right badge) */}
                            {isLowStock && (
                              <div className="absolute right-0 top-0 bg-red-500/10 border-b border-l border-red-500/20 px-3 py-1 rounded-bl-2xl text-[9px] font-black text-red-400 uppercase tracking-widest animate-pulse">
                                Low Stock
                              </div>
                            )}

                            {/* Item Icon Badge (Matches app top left badge) */}
                            <div className="flex items-center justify-between">
                              <div className="w-10 h-10 rounded-2xl bg-white/[0.03] border border-white/10 flex items-center justify-center">
                                {getCategoryIcon(item.category_id)}
                              </div>
                              <span className={`text-[9px] font-bold px-2 py-0.5 rounded-full ${item.is_available ? 'bg-emerald-500/10 border border-emerald-500/20 text-emerald-400' : 'bg-red-500/10 border border-red-500/20 text-red-400'}`}>
                                {item.is_available ? 'Active' : 'Unavailable'}
                              </span>
                            </div>

                            {/* Card Body */}
                            <div className="mt-2 flex flex-col gap-0.5">
                              <h4 className="text-sm font-black text-slate-100 line-clamp-1 group-hover:text-emerald-400 transition-colors">{item.name}</h4>
                              <p className="text-[10px] text-slate-500 font-semibold line-clamp-1">
                                Flavors: {item.flavors.split('|').join(', ') || 'None'}
                              </p>
                              <span className="text-xs font-bold text-slate-300 mt-1">Starting price: {formatPrice(startingPrice)}</span>
                            </div>

                            {/* Action Overlay */}
                            <div className="flex gap-2 border-t border-white/5 pt-3 mt-1.5">
                              <button
                                onClick={() => handleEditItemClick(item)}
                                className="flex-1 flex items-center justify-center gap-1.5 py-1.5 bg-white/5 hover:bg-emerald-500/10 hover:text-emerald-400 rounded-xl text-[10px] font-black uppercase tracking-wider transition-all border border-transparent hover:border-emerald-500/10 active:scale-[0.97]"
                              >
                                <Edit2 className="w-3 h-3" />
                                Edit Product
                              </button>
                              <button
                                onClick={() => handleDeleteItem(item.id)}
                                className="p-1.5 bg-red-500/5 hover:bg-red-500/15 text-red-400 rounded-xl text-xs font-bold transition-all border border-red-500/10 active:scale-[0.95]"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          </div>
                        );
                      })}
                  </div>
                </div>
              </div>
            )}

            {/* TAB 4: INVENTORY */}
            {activeTab === 'inventory' && (
              <div className="bg-[#0c0c0e]/60 border border-white/5 rounded-3xl p-6 backdrop-blur-xl shadow-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.03)] flex flex-col gap-6">
                <div>
                  <h3 className="text-base font-black">Stock Management Audit</h3>
                  <p className="text-xs text-slate-500 mt-0.5">Adjust ingredients and raw material stock levels. System updates automatically when app orders are served.</p>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr className="border-b border-white/5 text-slate-400 font-bold uppercase tracking-wider">
                        <th className="py-4 px-4">Raw Ingredient</th>
                        <th className="py-4 px-4">Current Stock</th>
                        <th className="py-4 px-4">Reorder Threshold</th>
                        <th className="py-4 px-4">Stock Status</th>
                        <th className="py-4 px-4 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {inventory.map(item => {
                        const isLow = item.current_stock <= item.reorder_threshold;
                        return (
                          <tr key={item.id} className="border-b border-white/[0.02] hover:bg-white/[0.01] transition-all">
                            <td className="py-4.5 px-4 font-bold text-slate-200 text-sm">{item.item_name}</td>
                            <td className="py-4.5 px-4 font-black text-slate-100 text-sm">
                              {item.current_stock} <span className="text-xs text-slate-400 font-semibold">{item.unit}</span>
                            </td>
                            <td className="py-4.5 px-4 text-slate-400 font-semibold">
                              {item.reorder_threshold} <span className="text-[10px] text-slate-500">{item.unit}</span>
                            </td>
                            <td className="py-4.5 px-4">
                              <span className={`px-2.5 py-1.5 rounded-xl text-[10px] font-bold border uppercase tracking-wider ${
                                isLow
                                  ? 'bg-amber-500/10 border-amber-500/20 text-amber-400 animate-pulse'
                                  : 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400'
                              }`}>
                                {isLow ? 'Low Stock Alert' : 'Healthy'}
                              </span>
                            </td>
                            <td className="py-4.5 px-4 text-right">
                              <button
                                onClick={() => {
                                  setEditingInventory(item);
                                  setShowInventoryModal(true);
                                }}
                                className="px-3.5 py-2 bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/20 rounded-xl text-[10px] font-black uppercase tracking-wider transition-all"
                              >
                                Adjust Stock
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </main>

      {/* PRINT SIMULATED Z-READING DIALOG */}
      {showPrintModal && printSummary && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-md flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-white/10 rounded-3xl w-full max-w-sm p-6 flex flex-col gap-4 shadow-2xl relative">
            <h3 className="text-sm font-black uppercase tracking-wider text-slate-400 text-center">Receipt Printer Preview</h3>
            
            {/* Simulation of thermal ticket */}
            <div className="bg-white text-slate-950 p-6 rounded-lg shadow-inner font-mono text-xs flex flex-col gap-3 border border-slate-300">
              <div className="text-center font-bold">
                <p className="text-sm">*** BREW NI CAT ***</p>
                <p>EOD Z-READING SUMMARY</p>
              </div>
              <div className="border-b border-dashed border-slate-400 my-1"></div>
              <div className="flex flex-col gap-1">
                <div className="flex justify-between"><span>DATE:</span><span>{new Date(printSummary.timestamp).toLocaleDateString()}</span></div>
                <div className="flex justify-between"><span>TIME:</span><span>{new Date(printSummary.timestamp).toLocaleTimeString()}</span></div>
                <div className="flex justify-between"><span>ORDERS COUNT:</span><span>{printSummary.orderCount}</span></div>
              </div>
              <div className="border-b border-dashed border-slate-400 my-1"></div>
              <div className="flex flex-col gap-1">
                <div className="flex justify-between font-bold"><span>GROSS SALES:</span><span>₱{printSummary.grossSales.toFixed(2)}</span></div>
                <div className="flex justify-between font-bold"><span>DISCOUNTS:</span><span>-₱{printSummary.discounts.toFixed(2)}</span></div>
                <div className="flex justify-between font-bold"><span>NET REVENUE:</span><span>₱{printSummary.netRevenue.toFixed(2)}</span></div>
                <div className="flex justify-between"><span>OPERATING EXP:</span><span>-₱{printSummary.expenses.toFixed(2)}</span></div>
              </div>
              <div className="border-b border-dashed border-slate-400 my-1"></div>
              <div className="flex flex-col gap-1">
                <div className="flex justify-between"><span>CASH SALES:</span><span>₱{printSummary.cashSales.toFixed(2)}</span></div>
                <div className="flex justify-between"><span>GCASH SALES:</span><span>₱{printSummary.gcashSales.toFixed(2)}</span></div>
              </div>
              <div className="border-b border-dashed border-slate-400 my-1"></div>
              <div className="flex flex-col gap-1 font-bold">
                <div className="flex justify-between"><span>STARTING FLOAT:</span><span>₱{printSummary.startingFloat.toFixed(2)}</span></div>
                <div className="flex justify-between text-sm"><span>CASH DRAWER:</span><span>₱{printSummary.drawerBalance.toFixed(2)}</span></div>
                <div className="flex justify-between text-sm"><span>NET PROFITS:</span><span>₱{printSummary.profits.toFixed(2)}</span></div>
              </div>
              <div className="border-b border-dashed border-slate-400 my-1"></div>
              <p className="text-center font-bold text-[10px]">*** END OF REPORT ***</p>
            </div>

            <div className="flex gap-3 justify-end mt-2">
              <button
                onClick={() => {
                  setShowPrintModal(false);
                  setPrintSummary(null);
                }}
                className="px-4 py-2 bg-white/5 border border-white/10 hover:bg-white/10 rounded-xl text-xs font-bold transition-all text-slate-300"
              >
                Close Preview
              </button>
              <button
                onClick={() => {
                  window.print();
                }}
                className="px-4.5 py-2 bg-emerald-500 hover:bg-emerald-400 rounded-xl text-xs font-black text-slate-950 transition-all"
              >
                Print Ticket
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Category Modal */}
      {showCategoryModal && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-md flex items-center justify-center p-4">
          <div className="bg-[#0c0c0e]/90 border border-white/10 rounded-3xl w-full max-w-sm p-6 flex flex-col gap-4 shadow-2xl">
            <h3 className="text-sm font-black uppercase tracking-wider text-slate-300">Add Menu Category</h3>
            <input
              type="text"
              placeholder="e.g. Milk Tea / Snacks"
              value={newCategoryName}
              onChange={e => setNewCategoryName(e.target.value)}
              className="bg-white/[0.03] border border-white/10 px-4 py-3 rounded-2xl text-xs text-slate-200 outline-none focus:border-emerald-500/50 transition-all font-semibold"
            />
            <div className="flex gap-3 justify-end mt-2">
              <button
                onClick={() => setShowCategoryModal(false)}
                className="px-4 py-2 text-xs font-bold text-slate-400 hover:text-slate-200 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleAddCategory}
                className="px-4 py-2 bg-emerald-500 text-slate-950 rounded-xl text-xs font-black hover:bg-emerald-400 transition-colors"
              >
                Add Category
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Inventory Modal */}
      {showInventoryModal && editingInventory && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-md flex items-center justify-center p-4">
          <div className="bg-[#0c0c0e]/90 border border-white/10 rounded-3xl w-full max-w-sm p-6 flex flex-col gap-4 shadow-2xl">
            <div>
              <h3 className="text-sm font-black uppercase tracking-wider text-slate-300">Adjust Raw Stock</h3>
              <p className="text-[10px] text-slate-500 mt-1 font-bold uppercase tracking-wider">{editingInventory.item_name} &bull; Current: {editingInventory.current_stock} {editingInventory.unit}</p>
            </div>
            <input
              type="number"
              placeholder="e.g. 50 (or -10 to reduce)"
              value={stockAdjustment}
              onChange={e => setStockAdjustment(e.target.value)}
              className="bg-white/[0.03] border border-white/10 px-4 py-3 rounded-2xl text-xs text-slate-200 outline-none focus:border-emerald-500/50 transition-all font-semibold"
            />
            <div className="flex gap-3 justify-end mt-2">
              <button
                onClick={() => setShowInventoryModal(false)}
                className="px-4 py-2 text-xs font-bold text-slate-400 hover:text-slate-200 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveStock}
                className="px-4 py-2 bg-emerald-500 text-slate-950 rounded-xl text-xs font-black hover:bg-emerald-400 transition-colors"
              >
                Apply Change
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Item Modal */}
      {showItemModal && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-md flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-[#0c0c0e]/95 border border-white/10 rounded-3xl w-full max-w-lg p-6 flex flex-col gap-5 my-8 shadow-2xl">
            <h3 className="text-base font-black uppercase tracking-wider text-slate-300">{editingItem ? 'Edit Product Item' : 'Add New Product Item'}</h3>

            <div className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold uppercase tracking-wider text-slate-500">Product Name</label>
                <input
                  type="text"
                  placeholder="e.g. Classic Pearl Milk Tea"
                  value={itemName}
                  onChange={e => setItemName(e.target.value)}
                  className="bg-white/[0.03] border border-white/10 px-4 py-2.5 rounded-2xl text-xs text-slate-200 outline-none focus:border-emerald-500/50 transition-all font-semibold"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold uppercase tracking-wider text-slate-500">Category</label>
                <select
                  value={itemCategory}
                  onChange={e => setItemCategory(e.target.value)}
                  className="bg-white/[0.03] border border-white/10 px-4 py-2.5 rounded-2xl text-xs text-slate-200 outline-none focus:border-emerald-500/50 transition-all font-semibold"
                >
                  <option value="" disabled>Select a category</option>
                  {categories.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold uppercase tracking-wider text-slate-500">Flavors (comma separated)</label>
                <input
                  type="text"
                  placeholder="e.g. Chocolate, Matcha, Wintermelon"
                  value={itemFlavors}
                  onChange={e => setItemFlavors(e.target.value)}
                  className="bg-white/[0.03] border border-white/10 px-4 py-2.5 rounded-2xl text-xs text-slate-200 outline-none focus:border-emerald-500/50 transition-all font-semibold"
                />
              </div>

              {(() => {
                const flavorsList = itemFlavors.split(',').map(f => f.trim()).filter(Boolean);
                const showPerFlavor = perFlavorPricing && flavorsList.length > 0;
                return (
                  <div className="flex flex-col gap-2">
                    <div className="flex justify-between items-center mb-1">
                      <label className="text-[10px] font-bold uppercase tracking-wider text-slate-500">Sizes & Variant Prices</label>
                      <div className="flex items-center gap-3">
                        {flavorsList.length > 0 && (
                          <label className="flex items-center gap-1.5 text-[10px] font-bold text-slate-400 cursor-pointer select-none">
                            <input
                              type="checkbox"
                              checked={perFlavorPricing}
                              onChange={e => setPerFlavorPricing(e.target.checked)}
                              className="accent-emerald-500"
                            />
                            Price per flavor
                          </label>
                        )}
                        <button
                          onClick={() => {
                            setItemVariants(prev => [
                              ...prev,
                              { id: 'size_' + Date.now().toString().slice(-4), name: '', basePrice: 0, priceByFlavor: {} }
                            ]);
                          }}
                          className="flex items-center gap-1 text-[10px] text-emerald-400 hover:text-emerald-300 font-black uppercase tracking-wider"
                        >
                          <Plus className="w-3.5 h-3.5" /> Add Size
                        </button>
                      </div>
                    </div>

                    <div className="flex flex-col gap-2 max-h-72 overflow-y-auto pr-1">
                      {itemVariants.map((v, index) => (
                        <div key={index} className="flex flex-col gap-2 bg-white/[0.02] border border-white/5 rounded-xl p-2">
                          <div className="flex gap-2 items-center">
                            <input
                              type="text"
                              placeholder="Size (e.g. Large / 16oz)"
                              value={v.name}
                              onChange={e => {
                                const updated = [...itemVariants];
                                updated[index].name = e.target.value;
                                updated[index].id = e.target.value.toLowerCase().replace(/[^a-z0-9]/g, '_');
                                setItemVariants(updated);
                              }}
                              className="flex-1 bg-white/[0.03] border border-white/10 px-3 py-2 rounded-xl text-xs outline-none focus:border-emerald-500/50 transition-all font-semibold"
                            />
                            {!showPerFlavor && (
                              <input
                                type="number"
                                placeholder="Price"
                                value={v.basePrice || ''}
                                onChange={e => {
                                  const updated = [...itemVariants];
                                  updated[index].basePrice = parseFloat(e.target.value) || 0;
                                  setItemVariants(updated);
                                }}
                                className="w-24 bg-white/[0.03] border border-white/10 px-3 py-2 rounded-xl text-xs outline-none focus:border-emerald-500/50 transition-all font-semibold"
                              />
                            )}
                            <button
                              onClick={() => setItemVariants(itemVariants.filter((_, idx) => idx !== index))}
                              className="p-2 bg-red-500/5 hover:bg-red-500/10 border border-red-500/10 rounded-xl text-red-400 hover:text-red-300 transition-all"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                          {showPerFlavor && (
                            <div className="flex flex-col gap-1.5 pl-1">
                              {flavorsList.map(flavor => (
                                <div key={flavor} className="flex gap-2 items-center">
                                  <span className="flex-1 text-[11px] text-slate-400 font-semibold truncate">{flavor}</span>
                                  <input
                                    type="number"
                                    placeholder="Price"
                                    value={v.priceByFlavor?.[flavor] || ''}
                                    onChange={e => {
                                      const updated = [...itemVariants];
                                      updated[index] = {
                                        ...updated[index],
                                        priceByFlavor: {
                                          ...updated[index].priceByFlavor,
                                          [flavor]: parseFloat(e.target.value) || 0
                                        }
                                      };
                                      setItemVariants(updated);
                                    }}
                                    className="w-24 bg-white/[0.03] border border-white/10 px-3 py-2 rounded-xl text-xs outline-none focus:border-emerald-500/50 transition-all font-semibold"
                                  />
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                );
              })()}
            </div>

            <div className="flex gap-3 justify-end mt-4">
              <button
                onClick={() => {
                  setShowItemModal(false);
                  setEditingItem(null);
                }}
                className="px-4 py-2 text-xs font-bold text-slate-400 hover:text-slate-200 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveItem}
                className="px-5 py-2.5 bg-emerald-500 text-slate-950 rounded-xl text-xs font-black hover:bg-emerald-400 transition-colors"
              >
                Save Product
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Mobile bottom tab bar (replaces the sidebar on small screens) */}
      <nav className="md:hidden fixed bottom-0 inset-x-0 z-30 flex items-stretch justify-around bg-[#0c0c0e]/95 backdrop-blur-xl border-t border-white/10 pb-[env(safe-area-inset-bottom)]">
        {([
          { id: 'dashboard', label: 'Sales', Icon: TrendingUp },
          { id: 'history', label: 'Audit', Icon: Receipt },
          { id: 'expenses', label: 'Expenses', Icon: DollarSign },
          { id: 'menu', label: 'Menu', Icon: Layers },
          { id: 'inventory', label: 'Stock', Icon: Package },
        ] as const).map(({ id, label, Icon }) => (
          <button
            key={id}
            onClick={() => setActiveTab(id)}
            className={`flex-1 flex flex-col items-center justify-center gap-1 py-2.5 text-[10px] font-bold transition-colors ${
              activeTab === id ? 'text-emerald-400' : 'text-slate-500 hover:text-slate-300'
            }`}
          >
            <Icon className="w-5 h-5" />
            {label}
          </button>
        ))}
      </nav>
    </div>
  );
}
