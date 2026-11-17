/**
 * Rewards and game prizes the owner manages (Connect FR-088) and the one-time
 * codes staff check at the counter (FR-091). The rules match the checks on
 * public.shop_rewards in the Connect migration 20261117120000, so a bad value
 * is caught here before the database refuses it.
 */
export const MAX_NAME = 60;
export const MAX_DESCRIPTION = 200;
export const MAX_POINTS = 10000;
/** A prize's chance is out of this many plays. */
export const CHANCE_OUT_OF = 1000;
const ID_PATTERN = /^[a-z0-9][a-z0-9-]{1,59}$/;
function wholeNumber(text) {
    const trimmed = text.trim();
    if (!/^\d+$/.test(trimmed))
        return null;
    return Number(trimmed);
}
/** A row id from the name: "Free Latte!" → "reward-free-latte". */
export function rewardIdFor(kind, name, taken = []) {
    const slug = name
        .toLowerCase()
        .normalize('NFKD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
        .slice(0, 40) || 'item';
    const base = `${kind}-${slug}`;
    let id = base;
    for (let n = 2; taken.includes(id); n += 1)
        id = `${base}-${n}`;
    return id;
}
export function draftFromReward(reward) {
    return {
        id: reward.id,
        kind: reward.kind,
        name: reward.name,
        description: reward.description ?? '',
        points: reward.points_cost === null ? '' : String(reward.points_cost),
        chance: reward.win_chance === null ? '' : String(reward.win_chance),
        stock: reward.stock === null ? '' : String(reward.stock),
        dailyLimit: reward.daily_limit === null ? '' : String(reward.daily_limit),
        active: reward.active,
        sortOrder: reward.sort_order,
    };
}
export function emptyDraft(kind, sortOrder) {
    return {
        kind,
        name: '',
        description: '',
        points: kind === 'reward' ? '50' : '',
        chance: kind === 'prize' ? '20' : '',
        stock: '',
        dailyLimit: '',
        active: true,
        sortOrder,
    };
}
/** The row to save, or the first thing wrong with the form. */
export function validateRewardDraft(draft, takenIds = []) {
    const name = draft.name.trim().replace(/\s+/g, ' ');
    if (!name)
        return { ok: false, error: 'Enter a name.' };
    if (name.length > MAX_NAME)
        return { ok: false, error: `Keep the name to ${MAX_NAME} characters.` };
    const description = draft.description.trim();
    if (description.length > MAX_DESCRIPTION) {
        return { ok: false, error: `Keep the description to ${MAX_DESCRIPTION} characters.` };
    }
    let points = null;
    let chance = null;
    if (draft.kind === 'reward') {
        points = wholeNumber(draft.points);
        if (points === null || points < 1 || points > MAX_POINTS) {
            return { ok: false, error: `Points must be a whole number from 1 to ${MAX_POINTS}.` };
        }
    }
    else {
        chance = wholeNumber(draft.chance);
        if (chance === null || chance < 1 || chance > CHANCE_OUT_OF) {
            return { ok: false, error: `The chance must be from 1 to ${CHANCE_OUT_OF} (out of ${CHANCE_OUT_OF} plays).` };
        }
    }
    const limit = (text, label) => {
        if (!text.trim())
            return { ok: true, value: null };
        const n = wholeNumber(text);
        if (n === null || n > 100000)
            return { ok: false, error: `${label} must be a whole number, or blank for no limit.` };
        return { ok: true, value: n };
    };
    const stock = limit(draft.stock, 'Stock');
    if (!stock.ok)
        return stock;
    const daily = limit(draft.dailyLimit, 'The daily limit');
    if (!daily.ok)
        return daily;
    if (daily.value === 0)
        return { ok: false, error: 'A daily limit of 0 hides it; turn it off instead.' };
    const id = draft.id ?? rewardIdFor(draft.kind, name, takenIds);
    if (!ID_PATTERN.test(id))
        return { ok: false, error: 'That name makes an id the database refuses; try another.' };
    return {
        ok: true,
        value: {
            id,
            kind: draft.kind,
            name,
            description,
            points_cost: points,
            win_chance: chance,
            stock: stock.value,
            daily_limit: daily.value,
            active: draft.active,
            sort_order: draft.sortOrder,
        },
    };
}
/** The total chance of the active prizes, out of 1000 plays. */
export function totalPrizeChance(rewards) {
    return rewards
        .filter((r) => r.kind === 'prize' && r.active)
        .reduce((sum, r) => sum + (r.win_chance ?? 0), 0);
}
/** "About 1 in 50 plays", or "Every play". */
export function chanceLabel(chance) {
    if (chance >= CHANCE_OUT_OF)
        return 'Every play';
    return `About 1 in ${Math.max(1, Math.round(CHANCE_OUT_OF / chance))} plays`;
}
/** Letters and digits only, upper case, so "rwd ab12 cd34" still matches. */
export function normalizeCode(text) {
    return text.toUpperCase().replace(/[^A-Z0-9]/g, '');
}
const when = (iso) => iso
    ? new Date(iso).toLocaleString('en-PH', { timeZone: 'Asia/Manila', dateStyle: 'medium', timeStyle: 'short' })
    : '';
/** One line for staff about a checked code. */
export function codeCheckMessage(result) {
    if (result.ok) {
        const what = result.source === 'mini-game' ? 'game prize' : 'reward';
        return result.status === 'redeemed'
            ? `Done. ${result.code} (${result.rewardName}) is now used. Give the ${what}.`
            : `Valid: ${result.rewardName} (${what}), good until ${when(result.expiresAt)}. Mark it used when you give it.`;
    }
    switch (result.error) {
        case 'already_redeemed':
            return `Already used on ${when(result.redeemedAt)}. Don't give it again.`;
        case 'expired':
            return `Expired on ${when(result.expiresAt)}.`;
        case 'unknown_code':
            return 'No such code. Check the letters with the customer.';
        case 'forbidden':
            return 'Only the store account can check codes. Sign in again.';
        default:
            return 'Could not check the code. Try again.';
    }
}
