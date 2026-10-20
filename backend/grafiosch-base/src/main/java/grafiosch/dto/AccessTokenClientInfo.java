package grafiosch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the token dialog tells the user about connecting a client. The library does not know which paths of the
 * application accept a token, so the application supplies the endpoint and a configuration example.
 */
@Schema(description = "Connection details the token dialog shows; supplied by the application that accepts the tokens")
public record AccessTokenClientInfo(
    @Schema(description = "Public URL of the endpoint that accepts a token; null when the application has not configured its public address") String endpointUrl,
    @Schema(description = """
        Configuration example for a client. It contains the placeholder <TOKEN> for the token and ${TIME_ZONE}, which
        the dialog replaces with the time zone of the browser""") String configSnippet) {
}
