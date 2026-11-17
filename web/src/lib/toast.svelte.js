/**
 * Short-lived messages at the bottom of the screen. An action that can be reversed runs at
 * once and offers Undo here instead of asking "Are you sure?" first.
 */
export const toasts = $state([]);

let nextId = 1;
const timers = new Map();

export function dismiss(id) {
  clearTimeout(timers.get(id));
  timers.delete(id);
  const i = toasts.findIndex((t) => t.id === id);
  if (i >= 0) toasts.splice(i, 1);
}

/**
 * @param {string} message
 * @param {{ tone?: 'info' | 'error', undo?: () => unknown, duration?: number }} [options]
 */
export function notify(message, { tone = 'info', undo, duration } = {}) {
  const id = nextId++;
  toasts.push({ id, message, tone, undo });
  const ms = duration ?? (undo ? 8000 : tone === 'error' ? 8000 : 4000);
  timers.set(id, setTimeout(() => dismiss(id), ms));
  return id;
}
