/**
 * Route keys the host owns. Everything the library itself routes to is declared in {@code BaseSettings}; only the few
 * paths an application has to choose for itself live here. The values match the Grafioschtrader ones so a browser spec
 * can be moved between the two suites without rewriting its URLs.
 */
export class GrafioschSettings {

  /** Landing route of the verification link in the registration mail. */
  public static readonly TOKEN_VERIFY_KEY = 'tokenverify';

  /** Tab menu combining the inbox and the forwarding settings. */
  public static readonly USER_MESSAGE_KEY = 'usermessage';

  /** Tab menu combining the two propose-change views. */
  public static readonly PROPOSE_CHANGE_TAB_MENU_KEY = 'proposeChangeTabMenu';

  /** ngx-translate falls back to this language when a key is missing in the active one. */
  public static readonly DEFAULT_LANGUAGE = 'en';

  /** Base of the context sensitive help; the library appends language and help id. */
  public static readonly HELP_URL_BASE = '//grafioschtrader.github.io/gt-user-manual';
}
