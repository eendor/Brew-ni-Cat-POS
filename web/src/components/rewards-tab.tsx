'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Gift, Dices, Plus, Save, RefreshCw, AlertCircle, CheckCircle2, Ticket, Trash2 } from 'lucide-react';
import {
  CHANCE_OUT_OF,
  chanceLabel,
  codeCheckMessage,
  draftFromReward,
  emptyDraft,
  normalizeCode,
  totalPrizeChance,
  validateRewardDraft,
  type CodeCheck,
  type RewardDraft,
  type RewardKind,
  type ShopReward,
} from '@/lib/rewards';

interface RewardsTabProps {
  supabase: any;
}

type Feedback = { type: 'ok' | 'err'; message: string };

const input =
  'w-full bg-surface-2 border border-hair rounded-xl px-3 py-2 text-sm text-txt-1 focus:outline-none focus:border-emerald-500/60';

export function RewardsTab({ supabase }: RewardsTabProps) {
  const [rewards, setRewards] = useState<ShopReward[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [draft, setDraft] = useState<RewardDraft | null>(null);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const [code, setCode] = useState('');
  const [checking, setChecking] = useState(false);
  const [checked, setChecked] = useState<CodeCheck | null>(null);

  const load = useCallback(async () => {
    if (!supabase) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setLoadError(null);
    try {
      const { data, error } = await supabase
        .from('shop_rewards')
        .select('id, kind, name, description, points_cost, win_chance, stock, daily_limit, active, sort_order, updated_at')
        .order('kind')
        .order('sort_order');
      if (error) throw error;
      setRewards((data ?? []) as ShopReward[]);
    } catch (e: any) {
      setLoadError(e?.message || 'Could not load the rewards.');
    } finally {
      setLoading(false);
    }
  }, [supabase]);

  useEffect(() => {
    load();
  }, [load]);

  const prizeTotal = useMemo(() => totalPrizeChance(rewards), [rewards]);
  const ids = useMemo(() => rewards.map((r) => r.id), [rewards]);

  const startNew = (kind: RewardKind) => {
    const sameKind = rewards.filter((r) => r.kind === kind);
    const next = sameKind.reduce((max, r) => Math.max(max, r.sort_order), 0) + 10;
    setFeedback(null);
    setDraft(emptyDraft(kind, next));
  };

  const save = async () => {
    if (!draft) return;
    const result = validateRewardDraft(draft, ids);
    if (!result.ok) {
      setFeedback({ type: 'err', message: result.error });
      return;
    }
    setSaving(true);
    setFeedback(null);
    try {
      const row = result.value;
      const query = draft.id
        ? supabase.from('shop_rewards').update(row).eq('id', row.id)
        : supabase.from('shop_rewards').insert(row);
      const { error } = await query;
      if (error) throw error;
      setFeedback({ type: 'ok', message: `Saved ${row.name}. The app shows it right away.` });
      setDraft(null);
      await load();
    } catch (e: any) {
      setFeedback({ type: 'err', message: e?.message || 'Could not save.' });
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (reward: ShopReward) => {
    const { error } = await supabase
      .from('shop_rewards')
      .update({ active: !reward.active })
      .eq('id', reward.id);
    if (error) setFeedback({ type: 'err', message: error.message });
    await load();
  };

  const remove = async (reward: ShopReward) => {
    if (!window.confirm(`Delete ${reward.name}? Codes already given stay valid. To hide it for now, turn it off instead.`)) return;
    const { error } = await supabase.from('shop_rewards').delete().eq('id', reward.id);
    if (error) setFeedback({ type: 'err', message: error.message });
    setDraft(null);
    await load();
  };

  const checkCode = async (redeem: boolean) => {
    const clean = normalizeCode(code);
    if (clean.length < 4) return;
    setChecking(true);
    try {
      const { data, error } = await supabase.rpc('verify_reward_code', { p_code: code.trim(), p_redeem: redeem });
      if (error) throw error;
      setChecked(data as CodeCheck);
    } catch {
      setChecked({ ok: false, error: 'network' });
    } finally {
      setChecking(false);
    }
  };

  const section = (kind: RewardKind) => {
    const rows = rewards.filter((r) => r.kind === kind);
    return (
      <div className="bg-surface border border-hair rounded-3xl p-5 flex flex-col gap-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="flex items-center gap-2 text-sm font-bold text-txt-1">
            {kind === 'reward' ? <Gift className="w-4 h-4" /> : <Dices className="w-4 h-4" />}
            {kind === 'reward' ? 'Rewards (points)' : 'Game prizes'}
          </h3>
          <button
            onClick={() => startNew(kind)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-surface-3 border border-hair text-xs font-bold text-emerald-400 hover:bg-surface-2"
          >
            <Plus className="w-3.5 h-3.5" /> Add
          </button>
        </div>
        {kind === 'prize' && prizeTotal > CHANCE_OUT_OF && (
          <p className="flex items-start gap-2 text-xs text-amber-400">
            <AlertCircle className="w-4 h-4 shrink-0" />
            The prizes that are on add up to {prizeTotal} out of {CHANCE_OUT_OF}, so every play wins and the
            later prizes are picked less than set. Lower some chances.
          </p>
        )}
        {rows.length === 0 && <p className="text-xs text-txt-3">None yet.</p>}
        <ul className="flex flex-col divide-y divide-hair">
          {rows.map((r) => (
            <li key={r.id} className="flex items-center gap-3 py-2.5">
              <button className="flex-1 min-w-0 text-left" onClick={() => { setFeedback(null); setDraft(draftFromReward(r)); }}>
                <span className={`block text-sm font-semibold truncate ${r.active ? 'text-txt-1' : 'text-txt-3 line-through'}`}>
                  {r.name}
                </span>
                <span className="block text-[11px] text-txt-3">
                  {r.kind === 'reward' ? `${r.points_cost} points` : `${r.win_chance}/${CHANCE_OUT_OF} · ${chanceLabel(r.win_chance ?? 1)}`}
                  {r.stock !== null && ` · ${r.stock} left`}
                  {r.daily_limit !== null && ` · ${r.daily_limit} a day`}
                </span>
              </button>
              <label className="flex items-center gap-1.5 text-[11px] text-txt-2">
                <input type="checkbox" checked={r.active} onChange={() => toggleActive(r)} />
                On
              </label>
            </li>
          ))}
        </ul>
      </div>
    );
  };

  if (!supabase) {
    return <p className="text-sm text-txt-3">Rewards need the online database.</p>;
  }

  return (
    <div className="flex flex-col gap-5 pb-24 md:pb-0">
      <div className="bg-surface border border-hair rounded-3xl p-5 flex flex-col gap-3">
        <h3 className="flex items-center gap-2 text-sm font-bold text-txt-1">
          <Ticket className="w-4 h-4" /> Check a code
        </h3>
        <p className="text-xs text-txt-3">
          Type the code on the customer&rsquo;s phone. Check it first, then mark it used when you hand over the item.
        </p>
        <div className="flex flex-col sm:flex-row gap-2">
          <input
            className={`${input} font-mono uppercase tracking-wider`}
            value={code}
            onChange={(e) => { setCode(e.target.value); setChecked(null); }}
            placeholder="RWD-AB12CD34"
            autoCapitalize="characters"
            autoComplete="off"
            maxLength={20}
          />
          <button
            disabled={checking || normalizeCode(code).length < 4}
            onClick={() => checkCode(false)}
            className="px-4 py-2 rounded-xl bg-surface-3 border border-hair text-xs font-bold text-txt-1 disabled:opacity-40"
          >
            Check
          </button>
          <button
            disabled={checking || !(checked?.ok && checked.status === 'valid')}
            onClick={() => checkCode(true)}
            className="px-4 py-2 rounded-xl bg-emerald-500 text-slate-950 text-xs font-black disabled:opacity-40"
          >
            Mark used
          </button>
        </div>
        {checked && (
          <p role="status" className={`flex items-start gap-2 text-sm ${checked.ok ? 'text-emerald-400' : 'text-red-400'}`}>
            {checked.ok ? <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" /> : <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />}
            {codeCheckMessage(checked)}
          </p>
        )}
      </div>

      {loadError && (
        <p className="flex items-center gap-2 text-sm text-red-400">
          <AlertCircle className="w-4 h-4" /> {loadError}
          <button onClick={load} className="underline">Retry</button>
        </p>
      )}
      {feedback && !draft && (
        <p className={`text-sm ${feedback.type === 'ok' ? 'text-emerald-400' : 'text-red-400'}`}>{feedback.message}</p>
      )}

      {draft && (
        <div className="bg-surface border border-emerald-500/40 rounded-3xl p-5 flex flex-col gap-3">
          <h3 className="text-sm font-bold text-txt-1">
            {draft.id ? 'Edit' : 'New'} {draft.kind === 'reward' ? 'reward' : 'game prize'}
          </h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <label className="flex flex-col gap-1 text-xs text-txt-2 sm:col-span-2">
              Name
              <input className={input} value={draft.name} maxLength={60} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
            </label>
            <label className="flex flex-col gap-1 text-xs text-txt-2 sm:col-span-2">
              Description (optional)
              <input className={input} value={draft.description} maxLength={200} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
            </label>
            {draft.kind === 'reward' ? (
              <label className="flex flex-col gap-1 text-xs text-txt-2">
                Points it costs
                <input className={input} inputMode="numeric" value={draft.points} onChange={(e) => setDraft({ ...draft, points: e.target.value })} />
              </label>
            ) : (
              <label className="flex flex-col gap-1 text-xs text-txt-2">
                Chance, out of {CHANCE_OUT_OF} plays
                <input className={input} inputMode="numeric" value={draft.chance} onChange={(e) => setDraft({ ...draft, chance: e.target.value })} />
                {Number(draft.chance) > 0 && <span className="text-txt-3">{chanceLabel(Number(draft.chance))}</span>}
              </label>
            )}
            <label className="flex flex-col gap-1 text-xs text-txt-2">
              Stock left (blank = no limit)
              <input className={input} inputMode="numeric" value={draft.stock} onChange={(e) => setDraft({ ...draft, stock: e.target.value })} />
            </label>
            <label className="flex flex-col gap-1 text-xs text-txt-2">
              Most per day (blank = no limit)
              <input className={input} inputMode="numeric" value={draft.dailyLimit} onChange={(e) => setDraft({ ...draft, dailyLimit: e.target.value })} />
            </label>
            <label className="flex items-center gap-2 text-xs text-txt-2 self-end pb-2">
              <input type="checkbox" checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} />
              On (customers can see it)
            </label>
          </div>
          {feedback?.type === 'err' && <p className="text-sm text-red-400">{feedback.message}</p>}
          <div className="flex flex-wrap gap-2 justify-end">
            {draft.id && (
              <button
                onClick={() => remove(rewards.find((r) => r.id === draft.id)!)}
                className="mr-auto flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-bold text-red-400 border border-hair"
              >
                <Trash2 className="w-3.5 h-3.5" /> Delete
              </button>
            )}
            <button onClick={() => setDraft(null)} className="px-4 py-2 rounded-xl text-xs font-bold text-txt-2 border border-hair">
              Cancel
            </button>
            <button
              disabled={saving}
              onClick={save}
              className="flex items-center gap-1.5 px-5 py-2 rounded-xl bg-emerald-500 text-slate-950 text-xs font-black disabled:opacity-50"
            >
              <Save className="w-3.5 h-3.5" /> {saving ? 'Saving…' : 'Save'}
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <p className="flex items-center gap-2 text-sm text-txt-3">
          <RefreshCw className="w-4 h-4 animate-spin" /> Loading rewards…
        </p>
      ) : (
        <div className="grid grid-cols-1 xl:grid-cols-2 gap-5">
          {section('reward')}
          {section('prize')}
        </div>
      )}
    </div>
  );
}
