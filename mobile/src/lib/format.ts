/** "★ 4.6 (12)" or null when the party has no ratings yet. */
export function ratingText(avg: number | null | undefined, count: number | null | undefined): string | null {
  if (avg == null || !count) return null;
  return `★ ${avg.toFixed(1)} (${count})`;
}
