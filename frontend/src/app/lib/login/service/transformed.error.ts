import { GetTransformedError } from './get.transformed.error';

/**
 * A failed request reduced to what the toast and the login view need: either a translated message from the server
 * (msg) or a translation key (msgKey), and the error class of the backend it was mapped from.
 */
export class TransformedError {
  /** Translation key of the message; absent when the server sent the message text itself. */
  public msgKey?: string;
  /** Error class of the backend the error body was mapped from; absent for errors without a known class. */
  public errorClass?: GetTransformedError;
  /** The session ended; the login view offers to sign in again. */
  public bringUpDialog = false;
  /** The session of an administrator ended; the login view offers the self-release dialog. */
  public bringUpAdminSelfReleaseDialog = false;

  constructor(
    public msg: string = '',
    public interpolateParams: any = {}
  ) {}

  isEmtpy(): boolean {
    return this.msg.trim().length === 0;
  }

  getMsgOrKey(): string | undefined {
    return this.isEmtpy() ? this.msgKey : this.msg;
  }
}
