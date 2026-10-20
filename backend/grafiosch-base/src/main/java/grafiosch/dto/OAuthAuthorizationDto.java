package grafiosch.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.entities.OAuthAuthorization;
import grafiosch.types.AccessTokenScope;
import io.swagger.v3.oas.annotations.media.Schema;

/** One client the caller authorized through OAuth, as the token dialog lists it. */
@Schema(description = "A client the caller authorized through OAuth; revoking it ends all its tokens at once")
public record OAuthAuthorizationDto(
    @Schema(description = "Unique identifier of the authorization") Integer idOAuthAuthorization,
    @Schema(description = "Name from the client's metadata document") String clientName,
    @Schema(description = "URL of the client's metadata document, which identifies the client") String clientId,
    @Schema(description = "What the client may do") AccessTokenScope scope,
    @Schema(description = "Time of the consent in UTC") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT) LocalDateTime createdAt,
    @Schema(description = "End of the authorization in UTC, at the latest 90 days after the consent") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT) LocalDateTime expiresAt,
    @Schema(description = "Time of the last token refresh in UTC") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT) LocalDateTime lastRefreshAt,
    @Schema(description = "True when the authorization ended or was revoked") boolean expired) {

  public static OAuthAuthorizationDto of(OAuthAuthorization authorization, LocalDateTime nowUtc) {
    return new OAuthAuthorizationDto(authorization.getIdOAuthAuthorization(), authorization.getClientName(),
        authorization.getClientId(), authorization.getScope(), authorization.getCreatedAt(),
        authorization.getExpiresAt(), authorization.getLastRefreshAt(), !authorization.isActive(nowUtc));
  }
}
