package grafiosch.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.entities.UserPasskey;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    A passkey of the signed-in user as the sign-in security dialog shows it. Key material and credential id stay on the
    server.""")
public record UserPasskeyDto(@Schema(description = "Unique identifier of the passkey") Integer idUserPasskey,
    @Schema(description = "Name the user gave the passkey, at most 40 characters") String label,
    @Schema(description = "Registration time in UTC") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT) LocalDateTime createdAt,
    @Schema(description = "Last sign-in in UTC; null when the passkey was never used") @JsonFormat(pattern = BaseConstants.STANDARD_DATE_TIME_FORMAT) LocalDateTime lastUsedAt,
    @Schema(description = """
        Whether the passkey was synchronized across devices (backup state) when it was registered; later changes of the
        authenticator are not tracked""") boolean synced) {

  public static UserPasskeyDto of(UserPasskey passkey) {
    return new UserPasskeyDto(passkey.getIdUserPasskey(), passkey.getLabel(), passkey.getCreatedAt(),
        passkey.getLastUsedAt(), passkey.isBackupState());
  }
}
