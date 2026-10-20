package grafiosch.security;

import grafiosch.types.AccessTokenScope;

/**
 * Who a request authenticated with a personal access token was made by. The access token filter puts it into the
 * request attribute {@link #REQUEST_ATTRIBUTE}, so that code behind the filter knows the token and its scope without
 * holding a user object of its own.
 *
 * @param idUser            the owner of the token
 * @param idUserAccessToken the token that authenticated the request
 * @param scope             what the token may do
 */
public record AccessTokenPrincipal(Integer idUser, Integer idUserAccessToken, AccessTokenScope scope) {

  public static final String REQUEST_ATTRIBUTE = AccessTokenPrincipal.class.getName();
}
