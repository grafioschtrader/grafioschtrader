package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    The decision of the user about one proposal. It names the rule version and the value the user saw, so that a
    decision about a proposal that changed meanwhile is refused as stale.""")
public record UDFFillProposalDecision(@Schema(description = "The security") Integer idSecuritycurrency,
    @Schema(description = "The fill rule version of the proposal the user saw") int ruleVersion,
    @Schema(description = "The value the user saw") String value) {
}
