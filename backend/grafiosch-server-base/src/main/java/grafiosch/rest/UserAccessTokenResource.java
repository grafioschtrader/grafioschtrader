package grafiosch.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.dto.AccessTokenClientInfo;
import grafiosch.dto.OAuthAuthorizationDto;
import grafiosch.dto.UserAccessTokenCreateRequest;
import grafiosch.dto.UserAccessTokenCreatedDto;
import grafiosch.dto.UserAccessTokenDto;
import grafiosch.dynamic.model.FormDefinitionRegistry;
import grafiosch.entities.User;
import grafiosch.entities.UserAccessToken;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.OAuthAuthorizationJpaRepository;
import grafiosch.repository.UserAccessTokenJpaRepository;
import grafiosch.security.AccessTokenAuthenticationService;
import grafiosch.security.AccessTokenClientInfoProvider;
import grafiosch.service.DailyLimitService;
import grafiosch.types.AccessTokenKind;
import grafiosch.types.OperationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Management of the caller's personal access tokens. The resource is authenticated with the browser JWT; an access
 * token can neither create nor list tokens, because the application accepts a token only on its machine endpoint.
 *
 * <p>
 * The resource exists only with {@code g.use.accesstoken=true}; otherwise every path answers 404. It is on the write
 * allow list of read-only tenants: a user with a read-only home tenant may still create a reading token and revoke
 * tokens.
 * </p>
 */
@RestController
@RequestMapping(RequestMappings.USER_ACCESS_TOKEN_MAP)
@ConditionalOnProperty(prefix = "g.use", name = "accesstoken", havingValue = "true")
@Tag(name = RequestMappings.USER_ACCESS_TOKEN, description = "Personal access tokens for machine clients")
public class UserAccessTokenResource {

  private final UserAccessTokenJpaRepository userAccessTokenJpaRepository;
  private final OAuthAuthorizationJpaRepository oAuthAuthorizationJpaRepository;
  private final DailyLimitService dailyLimitService;
  private final AccessTokenAuthenticationService accessTokenAuthenticationService;
  private final ObjectProvider<AccessTokenClientInfoProvider> clientInfoProvider;

  public UserAccessTokenResource(UserAccessTokenJpaRepository userAccessTokenJpaRepository,
      OAuthAuthorizationJpaRepository oAuthAuthorizationJpaRepository, DailyLimitService dailyLimitService,
      AccessTokenAuthenticationService accessTokenAuthenticationService,
      ObjectProvider<AccessTokenClientInfoProvider> clientInfoProvider) {
    this.userAccessTokenJpaRepository = userAccessTokenJpaRepository;
    this.oAuthAuthorizationJpaRepository = oAuthAuthorizationJpaRepository;
    this.dailyLimitService = dailyLimitService;
    this.accessTokenAuthenticationService = accessTokenAuthenticationService;
    this.clientInfoProvider = clientInfoProvider;
    FormDefinitionRegistry.register(UserAccessTokenCreateRequest.class);
  }

  @Operation(summary = "Returns the personal access tokens of the caller, without hash or token value", tags = {
      RequestMappings.USER_ACCESS_TOKEN })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<UserAccessTokenDto>> getTokens() {
    LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
    return new ResponseEntity<>(
        userAccessTokenJpaRepository.findByIdUserAndKind(getUser().getIdUser(), AccessTokenKind.PERSONAL.getValue())
            .stream().map(t -> UserAccessTokenDto.of(t, nowUtc)).toList(),
        HttpStatus.OK);
  }

  @Operation(summary = "Returns the clients the caller authorized through OAuth, one row per client", tags = {
      RequestMappings.USER_ACCESS_TOKEN })
  @GetMapping(value = "/oauthauthorization", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<OAuthAuthorizationDto>> getOAuthAuthorizations() {
    LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
    return new ResponseEntity<>(oAuthAuthorizationJpaRepository.findByIdUserOrderByCreatedAtDesc(getUser().getIdUser())
        .stream().map(a -> OAuthAuthorizationDto.of(a, nowUtc)).toList(), HttpStatus.OK);
  }

  @Operation(summary = "Revokes the authorization of a client by deleting it with all its tokens", tags = {
      RequestMappings.USER_ACCESS_TOKEN })
  @DeleteMapping(value = "/oauthauthorization/{idOAuthAuthorization}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteOAuthAuthorization(@PathVariable final Integer idOAuthAuthorization) {
    if (oAuthAuthorizationJpaRepository.deleteByIdUserAndIdOAuthAuthorization(getUser().getIdUser(),
        idOAuthAuthorization) == 0) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Creates a personal access token and returns it in clear text exactly once", description = """
      Only the hash of the token is stored. The answer carries Cache-Control: no-store, and the token can never be
      retrieved again. Creating a token is refused while the caller works in a tenant other than the home tenant.""", tags = {
      RequestMappings.USER_ACCESS_TOKEN })
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<UserAccessTokenCreatedDto> createToken(
      @Valid @RequestBody UserAccessTokenCreateRequest request) {
    if (!accessTokenAuthenticationService.isAccessTokenAllowed()) {
      // Listing and revoking stay possible, so a user can still clean up after the administrator closed the access
      throw new DataViolationException("token.name", "g.access.token.disabled", null);
    }
    return ResponseEntity.status(HttpStatus.OK).cacheControl(CacheControl.noStore())
        .body(userAccessTokenJpaRepository.createToken(getUser(), request));
  }

  @Operation(summary = "Revokes a personal access token of the caller by deleting it", tags = {
      RequestMappings.USER_ACCESS_TOKEN })
  @DeleteMapping(value = "/{idUserAccessToken}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteToken(@PathVariable final Integer idUserAccessToken) {
    User user = getUser();
    if (userAccessTokenJpaRepository.deletePersonalToken(user.getIdUser(), idUserAccessToken) == 0) {
      return ResponseEntity.notFound().build();
    }
    dailyLimitService.log(user.getIdUser(), UserAccessToken.class.getSimpleName(), OperationType.DELETE, 1);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Returns the endpoint and a configuration example the token dialog shows", tags = {
      RequestMappings.USER_ACCESS_TOKEN })
  @GetMapping(value = "/clientinfo", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<AccessTokenClientInfo> getClientInfo() {
    AccessTokenClientInfoProvider provider = clientInfoProvider.getIfAvailable();
    return new ResponseEntity<>(provider == null ? new AccessTokenClientInfo(null, null) : provider.getClientInfo(),
        HttpStatus.OK);
  }

  private User getUser() {
    return (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
  }
}
