import { BaseID } from '../../entities/base.id';

/** State of the sign-in methods of the signed-in user. */
export interface SecondFactorState {
  totpEnabled: boolean;
  recoveryCodesLeft: number;
  passkeyCount: number;
}

/** A new authenticator-app secret, shown once: as text, as otpauth URI and as QR code (PNG, Base64). */
export interface TotpSetup {
  secretBase32: string;
  otpauthUri: string;
  qrCodePng: string;
}

/** Recovery codes in clear text; the answer that carries them is the only time they are readable. */
export interface RecoveryCodes {
  codes: string[];
}

/** Sign-in methods the instance offers besides e-mail and password; read by the login page before sign-in. */
export interface LoginMethods {
  /** Page origins on which a passkey works; empty when passkeys are not available */
  passkeyOrigins: string[];
}

/** A credential as PublicKeyCredential.toJSON() writes it, for a registration or a sign-in. */
export type PasskeyCredentialJSON = RegistrationResponseJSON | AuthenticationResponseJSON;

/** Options of a WebAuthn ceremony as the server issues them, with the id of the stored challenge. */
export interface PasskeyOptions<T> {
  challengeId: string;
  publicKey: T;
}

/** A passkey of the user as the server lists it; key material and credential id stay on the server. */
export class UserPasskey implements BaseID {
  idUserPasskey: number | null = null;
  label: string | null = null;
  createdAt: string | null = null;
  lastUsedAt: string | null = null;
  /** Synchronized across devices when it was registered */
  synced = false;

  getId(): number | null {
    return this.idUserPasskey;
  }
}
