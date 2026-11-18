/** Cat Treats is bought to feed the café cats, so it is listed after the food. */
export const isCatTreats = (c) => c.id === 'cat_treats' || c.name?.trim().toLowerCase() === 'cat treats';

/** The same categories with Cat Treats moved to the end; the others keep their order. */
export function catTreatsLast(categories) {
  return [...categories.filter((c) => !isCatTreats(c)), ...categories.filter(isCatTreats)];
}
