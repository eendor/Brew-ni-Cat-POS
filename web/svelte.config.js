import adapter from '@sveltejs/adapter-vercel';

/** @type {import('@sveltejs/kit').Config} */
const config = {
  kit: {
    adapter: adapter(),
    // Vercel already holds NEXT_PUBLIC_SUPABASE_* from the Next.js version of this panel,
    // so keep reading them under the same names.
    env: { publicPrefix: 'NEXT_PUBLIC_' },
  },
};

export default config;
