package grafioschtrader.dto;

import java.util.List;

import grafioschtrader.dto.SecurityLookupResponse.ProviderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Result of the currency pair lookup: for the history and the intraday feed every connector that can serve the pair,
    with the URL extension it derived and the outcome of a trial request. The lookup never fails as a whole: a provider
    that cannot be asked reports its status, and the connectors that need no provider remain.""")
public record CurrencypairLookupResponse(@Schema(description = """
    Connectors for the end of day prices, those that delivered data first and among them the one with the oldest
    closing price first""") List<ConnectorAlternativeDto> historyConnectors,
    @Schema(description = "Connectors for the last price, those that delivered data first") List<ConnectorAlternativeDto> intraConnectors,
    @Schema(description = "Outcome of every provider that was searched for the pair") List<ProviderStatus> providerStatus) {
}
