'use client';

import React from 'react';
import {
  CakeSlice,
  Candy,
  Cat,
  Cherry,
  CircleDot,
  Citrus,
  Coffee,
  Cookie,
  Croissant,
  CupSoda,
  Donut,
  Drumstick,
  Egg,
  Flame,
  GlassWater,
  IceCreamBowl,
  IceCreamCone,
  Leaf,
  Milk,
  Package,
  Pizza,
  Popcorn,
  Salad,
  Sandwich,
  Soup,
  Sparkles,
  User,
  Users,
  UsersRound,
} from 'lucide-react';
import {
  PRODUCT_ICON_OPTIONS,
  getProductIconLabel,
  normalizeProductIconKey,
} from '@/lib/product-icons';

interface ProductIconProps {
  iconKey: string;
  className?: string;
}

export function ProductIcon({
  iconKey,
  className = 'w-5 h-5 text-emerald-400',
}: ProductIconProps) {
  switch (iconKey) {
    case 'coffee':
      return <Coffee className={className} />;
    case 'cup_soda':
      return <CupSoda className={className} />;
    case 'glass_water':
      return <GlassWater className={className} />;
    case 'milk':
      return <Milk className={className} />;
    case 'leaf':
      return <Leaf className={className} />;
    case 'cookie':
      return <Cookie className={className} />;
    case 'circle_dot':
      return <CircleDot className={className} />;
    case 'flame':
    case 'buldak':
      return <Flame className={className} />;
    case 'sandwich':
      return <Sandwich className={className} />;
    case 'sedaap':
    case 'soup':
      return <Soup className={className} />;
    case 'pizza':
      return <Pizza className={className} />;
    case 'croissant':
      return <Croissant className={className} />;
    case 'donut':
      return <Donut className={className} />;
    case 'cake_slice':
      return <CakeSlice className={className} />;
    case 'ice_cream_cone':
      return <IceCreamCone className={className} />;
    case 'ice_cream_bowl':
      return <IceCreamBowl className={className} />;
    case 'candy':
      return <Candy className={className} />;
    case 'popcorn':
      return <Popcorn className={className} />;
    case 'drumstick':
      return <Drumstick className={className} />;
    case 'egg':
      return <Egg className={className} />;
    case 'salad':
      return <Salad className={className} />;
    case 'cherry':
      return <Cherry className={className} />;
    case 'citrus':
      return <Citrus className={className} />;
    case 'cat':
      return <Cat className={className} />;
    case 'user':
      return <User className={className} />;
    case 'users':
      return <Users className={className} />;
    case 'users_round':
      return <UsersRound className={className} />;
    case 'package':
      return <Package className={className} />;
    default:
      return <Cookie className={className} />;
  }
}

interface ProductIconPickerProps {
  value: string | null;
  defaultIconKey: string;
  onChange: (nextIconKey: string | null) => void;
}

export function ProductIconPicker({
  value,
  defaultIconKey,
  onChange,
}: ProductIconPickerProps) {
  const normalized = normalizeProductIconKey(value);
  const activeLabel = getProductIconLabel(normalized) ?? 'Auto (By Category / ID)';

  return (
    <div className="flex flex-col gap-2" data-testid="product-icon-picker">
      <div className="flex items-center justify-between">
        <label className="text-[10px] font-bold uppercase tracking-wider text-txt-3">
          Product Icon (30 Icons)
        </label>
        <span className="text-[10px] font-bold text-emerald-400">
          {activeLabel}
        </span>
      </div>

      <div className="grid grid-cols-3 sm:grid-cols-4 gap-1.5 max-h-44 overflow-y-auto p-1.5 bg-surface-2 border border-hair rounded-2xl">
        <button
          type="button"
          onClick={() => onChange(null)}
          className={`flex items-center gap-1.5 px-2.5 py-2 rounded-xl text-[10px] font-bold transition-all border text-left ${
            normalized === null
              ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-300'
              : 'bg-surface border-hair text-txt-2 hover:text-txt-1'
          }`}
        >
          <Sparkles className="w-3.5 h-3.5 shrink-0 text-emerald-400" />
          <span className="truncate">Auto</span>
        </button>

        {PRODUCT_ICON_OPTIONS.map((opt) => {
          const selected = normalized === opt.key;
          return (
            <button
              key={opt.key}
              type="button"
              onClick={() => onChange(opt.key)}
              title={`${opt.label} (${opt.key})`}
              className={`flex items-center gap-1.5 px-2.5 py-2 rounded-xl text-[10px] font-bold transition-all border text-left ${
                selected
                  ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-300'
                  : 'bg-surface border-hair text-txt-2 hover:text-txt-1'
              }`}
            >
              <ProductIcon
                iconKey={opt.key}
                className={`w-3.5 h-3.5 shrink-0 ${
                  selected
                    ? 'text-emerald-400'
                    : opt.key === defaultIconKey && normalized === null
                      ? 'text-emerald-400/70'
                      : 'text-txt-2'
                }`}
              />
              <span className="truncate">{opt.label}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
