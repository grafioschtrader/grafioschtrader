package grafiosch.security;

/**
 * Implemented by the application that accepts access tokens, to decide at run time whether they may be used at all. The
 * deployment switch {@code g.use.accesstoken} decides whether the token code is loaded; this bean lets an administrator
 * open and close the access without a restart, for example through a global parameter.
 *
 * <p>
 * While it answers false, no token authenticates and no personal token can be created; listing and revoking existing
 * tokens stays possible. Without such a bean tokens are always allowed.
 * </p>
 */
public interface AccessTokenAvailability {

  boolean isAccessTokenAllowed();
}
