package grafiosch.repository;

import grafiosch.dto.UserAccessTokenCreateRequest;
import grafiosch.dto.UserAccessTokenCreatedDto;
import grafiosch.entities.User;
import grafiosch.entities.UserAccessToken;

public interface UserAccessTokenJpaRepositoryCustom extends BaseRepositoryCustom<UserAccessToken> {

  /**
   * Creates a personal access token for the user. The token is drawn from a secure random source, only its hash is
   * stored, and the clear text is returned in the answer and nowhere else.
   *
   * @param user    the authenticated user, working in the home tenant
   * @param request name, scope and lifetime of the token
   * @return the stored properties together with the token in clear text
   */
  UserAccessTokenCreatedDto createToken(User user, UserAccessTokenCreateRequest request);
}
