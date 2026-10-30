import { GlobalSessionNames } from '../../app/lib/global.session.names';

/**
 * Language of the texts before a user is known: the one of the last session in this tab, else the browser's.
 *
 * @param browserLanguage Language reported by the browser
 * @returns 'de' or 'en'
 */
export function mobileBootstrapLanguage(browserLanguage: string | undefined): string {
  const language = sessionStorage.getItem(GlobalSessionNames.LANGUAGE) ?? browserLanguage ?? 'en';
  return ['de', 'en'].includes(language) ? language : 'en';
}
