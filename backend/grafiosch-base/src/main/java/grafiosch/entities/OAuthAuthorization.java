package grafiosch.entities;

import java.time.LocalDateTime;

import grafiosch.types.AccessTokenScope;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The token family of one OAuth consent: a user allowed one client, identified by the URL of its metadata document, to
 * act on one resource with one scope. The access and refresh tokens issued under it are rows of {@link UserAccessToken}
 * that reference this family and disappear with it.
 *
 * <p>
 * A family ends at {@link #expiresAt}, at the latest 90 days after the consent, however often its tokens are refreshed;
 * no token outlives it. Revoking it in the token dialog deletes it together with its tokens. A replayed refresh token
 * marks it as revoked, which every token of the family then fails on.
 * </p>
 *
 * <p>
 * Never exported: its tokens are hashes that are worthless on another instance, and a client has to obtain a new
 * consent there anyway. The account deletion removes it.
 * </p>
 */
@Entity
@Table(name = OAuthAuthorization.TABNAME)
public class OAuthAuthorization extends UserBaseID {

  public static final String TABNAME = "oauth_authorization";

  /** Longest life of a family, counted from the consent. */
  public static final int MAX_LIFETIME_DAYS = 90;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_oauth_authorization")
  private Integer idOAuthAuthorization;

  @Basic(optional = false)
  @Column(name = "id_user")
  private Integer idUser;

  /** The client's identity: the HTTPS URL of its metadata document. */
  @Basic(optional = false)
  @Column(name = "client_id")
  private String clientId;

  /** The name from the client's metadata document, at most 100 characters. */
  @Basic(optional = false)
  @Column(name = "client_name")
  private String clientName;

  @Basic(optional = false)
  @Column(name = "scope")
  private byte scope;

  /** The canonical URI of the resource the tokens are bound to. */
  @Basic(optional = false)
  @Column(name = "resource")
  private String resource;

  /** UTC */
  @Basic(optional = false)
  @Column(name = "created_at")
  private LocalDateTime createdAt;

  /** UTC; the family and all its tokens end here. */
  @Basic(optional = false)
  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  /** UTC; set when a replayed refresh token revoked the family. */
  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  /** UTC; time of the last refresh, which may happen at most once a minute. */
  @Column(name = "last_refresh_at")
  private LocalDateTime lastRefreshAt;

  public OAuthAuthorization() {
  }

  public OAuthAuthorization(Integer idUser, String clientId, String clientName, AccessTokenScope scope, String resource,
      LocalDateTime createdAt) {
    this.idUser = idUser;
    this.clientId = clientId;
    this.clientName = clientName;
    this.scope = scope.getValue();
    this.resource = resource;
    this.createdAt = createdAt;
    this.expiresAt = createdAt.plusDays(MAX_LIFETIME_DAYS);
  }

  public Integer getIdOAuthAuthorization() {
    return idOAuthAuthorization;
  }

  @Override
  public Integer getIdUser() {
    return idUser;
  }

  @Override
  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  public String getClientId() {
    return clientId;
  }

  public String getClientName() {
    return clientName;
  }

  public AccessTokenScope getScope() {
    return AccessTokenScope.getAccessTokenScopeByValue(scope);
  }

  public String getResource() {
    return resource;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public LocalDateTime getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(LocalDateTime revokedAt) {
    this.revokedAt = revokedAt;
  }

  public LocalDateTime getLastRefreshAt() {
    return lastRefreshAt;
  }

  public void setLastRefreshAt(LocalDateTime lastRefreshAt) {
    this.lastRefreshAt = lastRefreshAt;
  }

  /**
   * Tells whether tokens of this family may still be used.
   *
   * @param nowUtc the current UTC time
   * @return true when the family is neither revoked nor expired
   */
  public boolean isActive(LocalDateTime nowUtc) {
    return revokedAt == null && expiresAt.isAfter(nowUtc);
  }

  @Override
  public Integer getId() {
    return idOAuthAuthorization;
  }
}
