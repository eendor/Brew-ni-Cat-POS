import { createClient } from '@supabase/supabase-js';
import { env } from '$env/dynamic/public';

// Same project + publishable key the Android app ships (see DatabaseSeeder.kt). Both are public
// by nature (they end up in the browser bundle either way) and row-level security is what
// actually protects the data: the publishable key on its own reads nothing but `app_release`;
// every other table requires the signed-in store account.
// Env vars still win, so a staging deploy can point elsewhere without a code change.
const DEFAULT_SUPABASE_URL = 'https://hyeotyohpdpmmvquotnd.supabase.co';
const DEFAULT_SUPABASE_ANON_KEY = 'sb_publishable_orak9Nk7HGB_qFHgXMdIzA_11T8NfYQ';

const url = env.NEXT_PUBLIC_SUPABASE_URL || DEFAULT_SUPABASE_URL;
const key = env.NEXT_PUBLIC_SUPABASE_ANON_KEY || DEFAULT_SUPABASE_ANON_KEY;

export const supabase = url && key ? createClient(url, key) : null;
