export class BaseSettings {
  public static readonly API_ENDPOINT = '/api/';

  // Native formats
  public static readonly FORMAT_DATE_SHORT_NATIVE = 'YYYY-MM-DD';
  public static readonly FORMAT_DATE_SHORT_US: string  = 'YYYYMMDD';

  /** Some menu items reference a dialog. If this is the case, the menu item should end with "...". */
  public static readonly DIALOG_MENU_SUFFIX = '...';

  public static readonly FIELD_SUFFIX = '$';

  public static readonly PROPOSE_CHANGE_YOUR_PROPOSAL_KEY = 'proposeyourproposal';
  public static readonly PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY = 'proposerequestforyou';

  public static readonly MAIL_SEND_RECV = 'MailSendRecv';
  public static readonly MAIL_SETTING_FORWARD = 'MailSettingForward';

  public static readonly MAIL_SEND_RECV_KEY = BaseSettings.MAIL_SEND_RECV.toLowerCase();
  public static readonly MAIL_SETTING_FORWARD_KEY = BaseSettings.MAIL_SETTING_FORWARD.toLowerCase();
}
