// The panel only works signed in to the store account and reads everything from Supabase in
// the browser, so there's nothing to render on the server.
export const ssr = false;
export const prerender = false;
