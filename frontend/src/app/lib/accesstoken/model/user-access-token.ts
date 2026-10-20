import { BaseID } from '../../entities/base.id';

/**
 * A personal access token of the user as the server lists it. Neither the token itself nor its hash is ever part of
 * it; the token is shown once, in the answer to its creation.
 */
export class UserAccessToken implements BaseID {
  idUserAccessToken: number = null;
  tokenName: string = null;
  /** First characters of the token, to tell tokens apart */
  tokenPrefix: string = null;
  scope: string = null;
  createdAt: string = null;
  expiresAt: string = null;
  lastUsedAt: string = null;
  expired = false;

  getId(): number {
    return this.idUserAccessToken;
  }
}

/** An application the user authorized through OAuth; revoking it ends all its tokens. */
export interface OAuthAuthorization {
  idOAuthAuthorization: number;
  clientName: string;
  /** URL of the application's metadata document, which identifies it */
  clientId: string;
  scope: string;
  createdAt: string;
  expiresAt: string;
  lastRefreshAt: string;
  expired: boolean;
}

/** Body of the creation request; the form fields come from the backend definition UserAccessTokenCreateRequest. */
export interface UserAccessTokenCreateRequest {
  tokenName: string;
  scope: string;
  expiresInDays: number;
}

/** Answer to the creation, the only place where the token appears in clear text. */
export interface UserAccessTokenCreated {
  token: UserAccessToken;
  plainToken: string;
}

/** Endpoint and configuration example supplied by the application that accepts the tokens. */
export interface AccessTokenClientInfo {
  endpointUrl: string;
  /** Contains <TOKEN> for the token and ${TIME_ZONE} for the time zone of the browser */
  configSnippet: string;
}
