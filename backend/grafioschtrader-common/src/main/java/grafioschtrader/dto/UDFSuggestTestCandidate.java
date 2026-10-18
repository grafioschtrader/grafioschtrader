package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    A security on which the description for the language model is tested. Securities that already hold a value of the
    field come first, so the answer of the model can be compared with it.""")
public record UDFSuggestTestCandidate(@Schema(description = "The security") Integer idSecuritycurrency,
    @Schema(description = "Name of the security") String name,
    @Schema(description = "The value the field holds for this security, or null") String existingValue) {
}
