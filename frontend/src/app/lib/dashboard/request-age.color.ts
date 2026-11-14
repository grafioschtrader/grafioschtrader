/** Age in days from which a request carries no red mark any more. */
export const REQUEST_AGE_FADE_DAYS = 5;

/** Opacity of the red background of a request created just now. */
const STRONGEST_ALPHA = 0.6;

const MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

/**
 * Computes the red background of the creation time cell of an open administrator request. The mark is strongest for a
 * request created just now and fades linearly until it has vanished after {@link REQUEST_AGE_FADE_DAYS} days; from then
 * on the cell has no background. The colour is translucent, which keeps the text readable in the light and the dark
 * theme.
 *
 * @param creationTime - Creation time as sent by the server (`yyyy-MM-dd HH:mm:ss`, UTC without zone designator)
 * @param nowMillis - Current time in milliseconds since the epoch
 * @returns A style object for the cell, or null when the request is five days old or older or the creation time
 *   cannot be parsed
 */
export function requestAgeCellStyle(
  creationTime: string | null | undefined,
  nowMillis: number
): { [key: string]: string } | null {
  if (!creationTime) {
    return null;
  }
  const createdMillis = Date.parse(creationTime.replace(' ', 'T') + 'Z');
  if (Number.isNaN(createdMillis)) {
    return null;
  }
  const ageDays = Math.max((nowMillis - createdMillis) / MILLIS_PER_DAY, 0);
  if (ageDays >= REQUEST_AGE_FADE_DAYS) {
    return null;
  }
  const alpha = STRONGEST_ALPHA * (1 - ageDays / REQUEST_AGE_FADE_DAYS);
  return { 'background-color': `rgba(220, 38, 38, ${alpha.toFixed(3)})` };
}
