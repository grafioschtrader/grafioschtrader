package grafiosch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Answer to the creation of a personal access token. It is the only place where the token appears in clear text; the
 * server keeps nothing but its hash and can never show it again.
 */
@Schema(description = """
    The created personal access token. plainToken is returned exactly once and cannot be retrieved later""")
public record UserAccessTokenCreatedDto(
    @Schema(description = "The stored properties of the token") UserAccessTokenDto token,
    @Schema(description = "The token in clear text, to be copied into the client configuration now") String plainToken) {
}
