package grafioschtrader.dto;

import java.util.List;

import grafioschtrader.types.SecuritySearchStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Result of the security lookup by ISIN, currency and exchange. The lookup never fails as a whole: a provider that
    cannot be asked reports its status, and the candidates of the others remain.""")
public record SecurityLookupResponse(
    @Schema(description = "Listings that do not contradict ISIN, currency and exchange of the query") List<SecurityLookupCandidateDto> candidates,
    @Schema(description = "Outcome of every provider that was asked") List<ProviderStatus> providerStatus,
    @Schema(description = """
        True when an instrument of the chosen asset class can receive dividends at all, by the same rule as the security
        form; otherwise no dividend connector is searched and the dialog shows no dividend table""") boolean dividendConnectorApplicable,
    @Schema(description = """
        True when an instrument of the chosen asset class can have splits, by the same rule as the security form; a
        directly held bond, for example, has none""") boolean splitConnectorApplicable) {

  @Schema(description = "Outcome of the search at one provider")
  public record ProviderStatus(@Schema(description = "Short id of the provider") String providerId,
      @Schema(description = "Outcome of the search") SecuritySearchStatus status,
      @Schema(description = "Short technical explanation, may be null") String detail) {
  }
}
