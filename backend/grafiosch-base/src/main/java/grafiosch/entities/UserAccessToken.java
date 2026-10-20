package grafiosch.entities;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import grafiosch.types.AccessTokenKind;
import grafiosch.types.AccessTokenScope;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A personal access token with which a machine client - for example an AI assistant - authenticates on the paths the
 * application opens for it. Only the SHA-256 hash of the token is stored; the token itself is shown to the user exactly
 * once, in the answer to its creation.
 *
 * <p>
 * The entity is never serialized. The REST layer works with its own transfer objects, so neither the hash nor the
 * entity reach a client. Tokens are not exported with "export my data": a hash is worthless on another instance. The
 * account deletion removes them.
 * </p>
 *
 * <p>
 * A token is never changed after it was created. A different name, scope or lifetime means revoking it and creating a
 * new one.
 * </p>
 *
 * <p>
 * The same table holds the tokens issued to OAuth clients ({@link AccessTokenKind#OAUTH_ACCESS},
 * {@link AccessTokenKind#OAUTH_REFRESH}). They belong to an {@link OAuthAuthorization}, carry the client's name as
 * token name and are removed together with it.
 * </p>
 */
@Entity
@Table(name = UserAccessToken.TABNAME)
public class UserAccessToken extends UserBaseID {

  public static final String TABNAME = "user_access_token";

  /** Prefix of every token, so that secret scanners recognize a leaked one. */
  public static final String TOKEN_PREFIX = "gpat_";

  /** Number of leading characters stored in clear text so the user can tell tokens apart in a list. */
  public static final int DISPLAY_PREFIX_LENGTH = 12;

  /** Length of the token_name column; an OAuth token takes the client name, cut to it. */
  public static final int MAX_TOKEN_NAME_LENGTH = 40;

  /** Longest lifetime of a token; there are no tokens without expiry. */
  public static final int MAX_EXPIRES_IN_DAYS = 365;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_user_access_token")
  private Integer idUserAccessToken;

  @Basic(optional = false)
  @Column(name = "id_user")
  private Integer idUser;

  @Basic(optional = false)
  @Column(name = "token_name")
  private String tokenName;

  /** SHA-256 of the whole token as lower case hex. */
  @Basic(optional = false)
  @Column(name = "token_hash")
  private String tokenHash;

  @Basic(optional = false)
  @Column(name = "token_prefix")
  private String tokenPrefix;

  @Basic(optional = false)
  @Column(name = "scope")
  private byte scope;

  /** UTC */
  @Basic(optional = false)
  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  /** UTC, written at most once per hour and token. */
  @Column(name = "last_used_at")
  private LocalDateTime lastUsedAt;

  /** UTC */
  @Basic(optional = false)
  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Basic(optional = false)
  @Column(name = "token_kind")
  private byte tokenKind = AccessTokenKind.PERSONAL.getValue();

  /** The OAuth family of an OAuth token; null for a personal token. */
  @Column(name = "id_oauth_authorization")
  private Integer idOAuthAuthorization;

  /** UTC; set when a refresh token was used and replaced. A consumed token presented again revokes its family. */
  @Column(name = "consumed_at")
  private LocalDateTime consumedAt;

  /** The resource an OAuth access token is valid for; null for every other kind. */
  @Column(name = "audience")
  private String audience;

  public UserAccessToken() {
  }

  /**
   * Creates a token issued to an OAuth client.
   *
   * @param authorization the family the token belongs to
   * @param kind          {@link AccessTokenKind#OAUTH_ACCESS} or {@link AccessTokenKind#OAUTH_REFRESH}
   * @param tokenHash     SHA-256 of the token
   * @param tokenPrefix   the first characters of the token
   * @param createdAt     UTC
   * @param expiresAt     UTC, never after the end of the family
   */
  public static UserAccessToken forOAuth(OAuthAuthorization authorization, AccessTokenKind kind, String tokenHash,
      String tokenPrefix, LocalDateTime createdAt, LocalDateTime expiresAt) {
    String name = authorization.getClientName();
    UserAccessToken token = new UserAccessToken(authorization.getIdUser(),
        name.length() > MAX_TOKEN_NAME_LENGTH ? name.substring(0, MAX_TOKEN_NAME_LENGTH) : name, tokenHash, tokenPrefix,
        authorization.getScope(), createdAt, expiresAt);
    token.tokenKind = kind.getValue();
    token.idOAuthAuthorization = authorization.getIdOAuthAuthorization();
    token.audience = kind == AccessTokenKind.OAUTH_ACCESS ? authorization.getResource() : null;
    return token;
  }

  public UserAccessToken(Integer idUser, String tokenName, String tokenHash, String tokenPrefix, AccessTokenScope scope,
      LocalDateTime createdAt, LocalDateTime expiresAt) {
    this.idUser = idUser;
    this.tokenName = tokenName;
    this.tokenHash = tokenHash;
    this.tokenPrefix = tokenPrefix;
    this.scope = scope.getValue();
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public Integer getIdUserAccessToken() {
    return idUserAccessToken;
  }

  @Override
  public Integer getIdUser() {
    return idUser;
  }

  @Override
  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  public String getTokenName() {
    return tokenName;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public String getTokenPrefix() {
    return tokenPrefix;
  }

  public AccessTokenScope getScope() {
    return AccessTokenScope.getAccessTokenScopeByValue(scope);
  }

  public void setScope(AccessTokenScope scope) {
    this.scope = scope.getValue();
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public LocalDateTime getLastUsedAt() {
    return lastUsedAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public AccessTokenKind getTokenKind() {
    return AccessTokenKind.getAccessTokenKindByValue(tokenKind);
  }

  public Integer getIdOAuthAuthorization() {
    return idOAuthAuthorization;
  }

  public LocalDateTime getConsumedAt() {
    return consumedAt;
  }

  public void setConsumedAt(LocalDateTime consumedAt) {
    this.consumedAt = consumedAt;
  }

  public String getAudience() {
    return audience;
  }

  /**
   * Hashes a token the way it is stored. A token carries 256 bits of randomness, so a fast hash suffices and keeps the
   * authentication of every request at a single index lookup.
   *
   * @param plainToken the token in clear text
   * @return SHA-256 as 64 lower case hex characters
   */
  public static String hashToken(String plainToken) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(plainToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      // Every Java runtime has to provide SHA-256
      throw new IllegalStateException(e);
    }
  }

  /**
   * Tells whether the token may no longer be used.
   *
   * @param nowUtc the current UTC time
   * @return true when the expiry is reached
   */
  public boolean isExpired(LocalDateTime nowUtc) {
    return !expiresAt.isAfter(nowUtc);
  }

  @Override
  public Integer getId() {
    return idUserAccessToken;
  }
}
