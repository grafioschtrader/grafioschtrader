package grafioschtrader.dto;

import java.util.List;

import grafioschtrader.types.UDFFillPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    What this instance offers for the automatic filling of user-defined security fields. The edit form of a field
    builds its selections from this answer only.""")
public record UDFFillSourcesOffer(
    @Schema(description = "The offered fill sources, each with the data types it supports") List<UDFFillSourceOption> fillSources,
    @Schema(description = "The resolvers of the fill source RESOLVER") List<UDFFillResolverOption> resolvers,
    @Schema(description = "True when a tenant provider or permitted instance fallback can propose a rule") boolean ruleProposal,
    @Schema(description = "True when the user's home tenant has a provider for value suggestions") boolean valueSuggestion,
    @Schema(description = """
        The fill periods a field with the fill source RESOLVER or LLM may have, in this order; no period is always
        possible as well""") List<UDFFillPeriod> fillPeriods) {
}
