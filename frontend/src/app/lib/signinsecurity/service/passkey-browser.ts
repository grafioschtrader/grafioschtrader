import type { PasskeyCredentialJSON } from '../model/sign-in-security';

/**
 * The browser side of passkeys, using only the JSON helpers of the WebAuthn API: `parseCreationOptionsFromJSON`,
 * `parseRequestOptionsFromJSON` and `PublicKeyCredential.prototype.toJSON`. No library is added. A browser lacking one
 * of them offers no passkey.
 */
export class PasskeyBrowser {
  /** Whether this browser supports passkeys with the JSON helpers. */
  static isSupported(): boolean {
    const credential = typeof PublicKeyCredential === 'undefined' ? undefined : PublicKeyCredential;
    return (
      !!navigator.credentials &&
      !!credential &&
      typeof credential.parseCreationOptionsFromJSON === 'function' &&
      typeof credential.parseRequestOptionsFromJSON === 'function' &&
      typeof credential.prototype.toJSON === 'function'
    );
  }

  /**
   * Whether the current page may use the passkeys of this instance: the browser supports them and the page is served
   * from one of the origins the server accepts.
   *
   * @param passkeyOrigins - Origins the server named in its login methods
   */
  static isAvailable(passkeyOrigins: string[]): boolean {
    return passkeyOrigins.includes(location.origin) && PasskeyBrowser.isSupported();
  }

  /**
   * Lets the browser create a passkey.
   *
   * @param options - The creation options of the server
   * @returns the credential as JSON, or null when the user cancelled the browser dialog
   */
  static async create(options: PublicKeyCredentialCreationOptionsJSON): Promise<PasskeyCredentialJSON | null> {
    try {
      const credential = await navigator.credentials.create({
        publicKey: PublicKeyCredential.parseCreationOptionsFromJSON(options)
      });
      return credential instanceof PublicKeyCredential ? credential.toJSON() : null;
    } catch (error) {
      return PasskeyBrowser.cancelled(error);
    }
  }

  /**
   * Lets the browser sign in with a passkey of this site.
   *
   * @param options - The request options of the server
   * @returns the assertion as JSON, or null when the user cancelled the browser dialog
   */
  static async get(options: PublicKeyCredentialRequestOptionsJSON): Promise<PasskeyCredentialJSON | null> {
    try {
      const credential = await navigator.credentials.get({
        publicKey: PublicKeyCredential.parseRequestOptionsFromJSON(options)
      });
      return credential instanceof PublicKeyCredential ? credential.toJSON() : null;
    } catch (error) {
      return PasskeyBrowser.cancelled(error);
    }
  }

  /** A cancelled or timed-out browser dialog is no error message; anything else is rethrown. */
  private static cancelled(error: unknown): null {
    if (error instanceof DOMException && error.name === 'NotAllowedError') {
      return null;
    }
    throw error;
  }
}
