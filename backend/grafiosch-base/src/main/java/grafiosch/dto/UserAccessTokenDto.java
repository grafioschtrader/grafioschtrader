package grafiosch.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.entities.UserAccessToken;
import grafiosch.types.AccessTokenScope;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One personal access token of the caller as the token dialog lists it. Neither the hash nor the token itself is part
 * of it.
 */
@Schema(description = "A personal access token of the caller, without its secret value or hash")
public record UserAccessTokenDto(@Schema(description = "Unique identifier of the token") Integer idUserAccessToken,
    @Schema(description = "Name chosen by the user") String tokenName,
    @Schema(description = "First characters of the token, so tokens can be told apart in a list") String tokenPrefix,
    @Schema(description = "What the token may do") AccessTokenScope scope,
    @Schema(description = "Creation time in UTC") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT) LocalDateTime createdAt,
    @Schema(description = "Expiry in UTC; the token is rejected from then on") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT) LocalDateTime expiresAt,
    @Schema(description = """
        Last use in UTC, updated at most once per hour; null when the token was never used""") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT) LocalDateTime lastUsedAt,
    @Schema(description = "True when the expiry has been reached") boolean expired) {

  public static UserAccessTokenDto of(UserAccessToken token, LocalDateTime nowUtc) {
    return new UserAccessTokenDto(token.getIdUserAccessToken(), token.getTokenName(), token.getTokenPrefix(),
        token.getScope(), token.getCreatedAt(), token.getExpiresAt(), token.getLastUsedAt(), token.isExpired(nowUtc));
  }
}
