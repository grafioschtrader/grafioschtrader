import { GetTransformedError } from './get.transformed.error';
import { TransformedError } from './transformed.error';

/**
 * Answer of the password sign-in for an account with an enabled second factor when no valid code came with the
 * request. The client keeps e-mail and password and repeats the sign-in with a code. The error body fills it.
 */
export class SecondFactorRequiredError implements GetTransformedError {
  /** Translated text of the server. */
  public message?: string;
  /** The request carried a code, but it was wrong, malformed or already used. */
  public invalidCode = false;
  /** Too many wrong proofs; no code is checked until the lock expires. */
  public locked = false;

  getTransformedError(): TransformedError {
    const transformedError = new TransformedError();
    transformedError.errorClass = this;
    transformedError.msg = this.message ?? '';
    transformedError.secondFactorRequired = true;
    return transformedError;
  }

  /** Only a refused code or a lock is worth a message; a first prompt for the code is not an error. */
  showsMessage(): boolean {
    return this.invalidCode || this.locked;
  }
}
