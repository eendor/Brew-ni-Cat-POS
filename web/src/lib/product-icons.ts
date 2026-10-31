export interface ProductIconOption {
  key: string;
  label: string;
}

export const PRODUCT_ICON_KEY_REGEX = /^[a-z0-9_]{1,40}$/;

/**
 * 30 product icon keys shared between the Android POS (`FluentIcons.productIconOptions`)
 * and the Web POS Menu Catalog Editor. Every key satisfies `items_icon_check` (`^[a-z0-9_]{1,40}$`).
 */
export const PRODUCT_ICON_OPTIONS: readonly ProductIconOption[] = [
  { key: 'coffee', label: 'Coffee' },
  { key: 'cup_soda', label: 'Iced Cup' },
  { key: 'glass_water', label: 'Water Glass' },
  { key: 'milk', label: 'Milk / Shake' },
  { key: 'leaf', label: 'Matcha / Tea' },
  { key: 'cookie', label: 'Cookie / Bite' },
  { key: 'circle_dot', label: 'Takoyaki Ball' },
  { key: 'flame', label: 'Spicy / Hot' },
  { key: 'sandwich', label: 'Sandwich / Snack' },
  { key: 'buldak', label: 'Buldak Pan' },
  { key: 'sedaap', label: 'Mi Sedaap' },
  { key: 'soup', label: 'Noodles / Soup' },
  { key: 'pizza', label: 'Pizza / Slice' },
  { key: 'croissant', label: 'Croissant / Pastry' },
  { key: 'donut', label: 'Donut' },
  { key: 'cake_slice', label: 'Cake Slice' },
  { key: 'ice_cream_cone', label: 'Ice Cream Cone' },
  { key: 'ice_cream_bowl', label: 'Dessert Bowl' },
  { key: 'candy', label: 'Candy / Sweet' },
  { key: 'popcorn', label: 'Popcorn / Fries' },
  { key: 'drumstick', label: 'Chicken / Meat' },
  { key: 'egg', label: 'Egg Add-on' },
  { key: 'salad', label: 'Salad / Veggie' },
  { key: 'cherry', label: 'Fruit / Berry' },
  { key: 'citrus', label: 'Citrus / Soda' },
  { key: 'cat', label: 'Cat Treat' },
  { key: 'user', label: 'Single Combo' },
  { key: 'users', label: 'Couple Combo' },
  { key: 'users_round', label: 'Barkada Combo' },
  { key: 'package', label: 'Take-out Box' },
] as const;

const VALID_ICON_KEYS = new Set<string>(PRODUCT_ICON_OPTIONS.map((o) => o.key));

export function isValidProductIconKey(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    PRODUCT_ICON_KEY_REGEX.test(value) &&
    VALID_ICON_KEYS.has(value)
  );
}

export function normalizeProductIconKey(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  if (!trimmed) return null;
  return isValidProductIconKey(trimmed) ? trimmed : null;
}

export function getProductIconLabel(key: unknown): string | null {
  const normalized = normalizeProductIconKey(key);
  if (!normalized) return null;
  return PRODUCT_ICON_OPTIONS.find((opt) => opt.key === normalized)?.label ?? null;
}

/**
 * Mirrors Android `FluentIcons.menuItemIcon(itemId, iconKey = null)` default mapping
 * when an item does not have a custom `icon` key saved.
 */
export function resolveDefaultProductIconKey(
  itemId: string,
  categoryId?: string | null
): string {
  switch (itemId) {
    case 'drink_cat_feine':
      return 'coffee';
    case 'drink_matcha':
      return 'leaf';
    case 'drink_oreo':
      return 'cookie';
    case 'drink_soda':
      return 'cup_soda';
    case 'drink_bottled_water':
      return 'glass_water';
    case 'bite_takoyaki':
      return 'circle_dot';
    case 'bite_fries':
      return 'flame';
    case 'bite_nachos':
      return 'sandwich';
    case 'bite_cat_treats':
      return 'cat';
    case 'buldak_carbo':
    case 'buldak_cheese':
      return 'buldak';
    case 'sedaap_spicy_chicken':
    case 'sedaap_original':
      return 'sedaap';
    case 'combo_single_paw':
      return 'user';
    case 'combo_couple_cats':
      return 'users';
    case 'combo_association':
      return 'users_round';
    case 'bite_takeout_box':
      return 'package';
  }

  if (itemId.startsWith('drink_')) return 'coffee';
  if (itemId.startsWith('buldak_')) return 'buldak';
  if (itemId.startsWith('sedaap_')) return 'sedaap';
  if (itemId.startsWith('bite_')) return 'cookie';
  if (itemId.startsWith('combo_')) return 'users';

  switch (categoryId) {
    case 'cat_drinks':
      return 'coffee';
    case 'combos':
      return 'users';
    case 'cat_buldak':
      return 'flame';
    case 'cat_takeout':
      return 'package';
    default:
      return 'cookie';
  }
}

export function resolveEffectiveProductIconKey(item: {
  id: string;
  category_id?: string | null;
  icon?: string | null;
}): string {
  return (
    normalizeProductIconKey(item.icon) ??
    resolveDefaultProductIconKey(item.id, item.category_id)
  );
}

export function stripIconColumnIfUnsupported<T extends Record<string, unknown>>(
  payload: T,
  error: { message?: string; details?: string; code?: string } | null | undefined
): { shouldRetry: boolean; fallbackPayload: Omit<T, 'icon'> } {
  const combined = `${error?.message ?? ''} ${error?.details ?? ''}`.toLowerCase();
  const mentionsIconColumn =
    combined.includes('icon') &&
    (combined.includes('column') ||
      combined.includes('schema cache') ||
      error?.code === 'PGRST204' ||
      error?.code === '42703');
  const { icon: _ignored, ...fallbackPayload } = payload;
  return {
    shouldRetry: mentionsIconColumn && 'icon' in payload,
    fallbackPayload,
  };
}
