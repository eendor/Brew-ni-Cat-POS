<script>
  import {
    CakeSlice, Candy, Cat, Cherry, CircleDot, Citrus, Coffee, Cookie, Croissant, CupSoda, Donut,
    Drumstick, Egg, Flame, GlassWater, IceCreamBowl, IceCreamCone, Leaf, Milk, Package, Pizza,
    Popcorn, Salad, Sandwich, Soup, User, Users, UsersRound,
  } from '@lucide/svelte';

  /** The same 30 product icons the Android POS draws, by key. */
  const ICONS = {
    coffee: Coffee, cup_soda: CupSoda, glass_water: GlassWater, milk: Milk, leaf: Leaf, cookie: Cookie,
    circle_dot: CircleDot, flame: Flame, buldak: Flame, sandwich: Sandwich, sedaap: Soup, soup: Soup,
    pizza: Pizza, croissant: Croissant, donut: Donut, cake_slice: CakeSlice, ice_cream_cone: IceCreamCone,
    ice_cream_bowl: IceCreamBowl, candy: Candy, popcorn: Popcorn, drumstick: Drumstick, egg: Egg,
    salad: Salad, cherry: Cherry, citrus: Citrus, cat: Cat, user: User, users: Users,
    users_round: UsersRound, package: Package,
  };

  let { iconKey, size = 20 } = $props();
  const Icon = $derived(ICONS[iconKey] || Cookie);
</script>

<Icon {size} aria-hidden="true" />
