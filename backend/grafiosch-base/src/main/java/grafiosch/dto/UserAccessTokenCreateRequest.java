package grafiosch.dto;

import grafiosch.common.DynamicFormField;
import grafiosch.entities.UserAccessToken;
import grafiosch.types.AccessTokenScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a personal access token. The class is also the form definition of the creation dialog, so
 * the constraints of the form come from the Bean Validation annotations here.
 */
@Schema(description = "Request body for creating a personal access token for a machine client such as an AI assistant")
public class UserAccessTokenCreateRequest {

  @Schema(description = "Name chosen by the user to recognize the token, for example 'Claude Code laptop'. Unique per user")
  @NotBlank
  @Size(min = 1, max = 40)
  @DynamicFormField(uiOrder = "1.1")
  private String tokenName;

  @Schema(description = "What the token may do. Only READ can be created for the time being")
  @NotNull
  private AccessTokenScope scope = AccessTokenScope.READ;

  @Schema(description = "Lifetime of the token in days, counted from now. There are no tokens without expiry")
  @NotNull
  @Min(1)
  @Max(UserAccessToken.MAX_EXPIRES_IN_DAYS)
  @DynamicFormField(uiOrder = "1.2")
  private Integer expiresInDays;

  public UserAccessTokenCreateRequest() {
  }

  public UserAccessTokenCreateRequest(String tokenName, AccessTokenScope scope, Integer expiresInDays) {
    this.tokenName = tokenName;
    this.scope = scope;
    this.expiresInDays = expiresInDays;
  }

  public String getTokenName() {
    return tokenName;
  }

  public void setTokenName(String tokenName) {
    this.tokenName = tokenName;
  }

  public AccessTokenScope getScope() {
    return scope;
  }

  public void setScope(AccessTokenScope scope) {
    this.scope = scope;
  }

  public Integer getExpiresInDays() {
    return expiresInDays;
  }

  public void setExpiresInDays(Integer expiresInDays) {
    this.expiresInDays = expiresInDays;
  }
}
