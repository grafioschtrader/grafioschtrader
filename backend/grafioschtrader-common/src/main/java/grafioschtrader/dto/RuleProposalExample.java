package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    One existing value of a user-defined field that is sent to the language model as an example when a rule is
    proposed. The fields other than the ID are exactly what leaves the instance.""")
public record RuleProposalExample(
    @Schema(description = "ID of the security; it is not sent to the model") Integer idSecuritycurrency,
    @Schema(description = "ISIN of the security") String isin,
    @Schema(description = "Ticker symbol of the security") String ticker,
    @Schema(description = "Name of the security") String name,
    @Schema(description = "MIC of the stock exchange of the security") String mic,
    @Schema(description = "Currency of the security") String currency,
    @Schema(description = "The value of the field") String value) {
}
