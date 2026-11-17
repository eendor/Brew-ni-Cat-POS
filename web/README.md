# Brew ni Cat POS web panel

The owner's panel for the POS. It reads the same Supabase project as the tablets, so sales, orders, expenses, the menu, stock, rewards and the website content all live in one place.

Built with SvelteKit (Svelte 5, plain JavaScript) as a single-page app, deployed on Vercel.

## Pages

| Page | What it does |
| --- | --- |
| Sales (`/`) | The day's net sales against the ₱10,000 goal, cash and GCash, what sold, closing the drawer, and the Z-Reading printout. |
| Orders (`/orders`) | Every sale, grouped by day and filtered by day, tablet and payment. Pick one to fix it or void it. Exports CSV. |
| Expenses (`/expenses`) | Expenses recorded on the tablets, by day. Deleting one offers Undo. |
| Menu (`/menu`, `/stock`) | Items by category with sizes, flavors and icons; categories; ingredient stock. |
| Rewards (`/rewards`) | Check and redeem reward codes, and manage point rewards and game prizes. |
| Website (`/settings`) | The public website's banner, hours, contact, address, payments, links, riders, about page and gallery. |

## Run it

```bash
npm install
npm run dev      # http://localhost:5173
npm test         # the pure logic in src/lib
npm run build
```

`NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_ANON_KEY` point it at another Supabase project. Without them it uses the shop's project and its publishable key, which reads nothing until a store account signs in.

## Layout

- `src/routes`: one folder per page; `+layout.svelte` has sign-in and the top bar.
- `src/lib/data.svelte.js`: the shared data (orders, menu, stock, expenses) and the realtime feed.
- `src/lib/*.js`: logic with tests in `tests/` (reports, rewards, website content, product icons).
- `src/lib/components`: shared pieces such as the sheet, the order detail and the item editor.
- `src/app.css`: the design tokens and shared classes, in light and dark.
