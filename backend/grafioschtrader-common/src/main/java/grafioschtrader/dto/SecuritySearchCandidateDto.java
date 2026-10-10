package grafioschtrader.dto;

import grafioschtrader.types.IdentityEvidence;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    One instrument listing proposed by a data provider search. Every identity field carries its own evidence, so a
    caller can tell a value the provider confirmed from one that is merely compatible with the query. A missing value
    stays null and its evidence UNKNOWN; it is never filled in from the query. A provider symbol is the identifier of
    that provider only and is not automatically the URL extension of any price feed.""")
public record SecuritySearchCandidateDto(
    @Schema(description = "Short id of the provider that proposed the candidate, e.g. 'yahoo', 'openfigi', 'six'") String providerId,
    @Schema(description = """
        Symbol of the instrument at this provider, e.g. 'NOVN.SW' at Yahoo, 'XETR:DE0007164600' at Xetra or the ticker
        at OpenFIGI""") String providerSymbol,
    @Schema(description = """
        Further stable identifier of the provider, e.g. the FIGI at OpenFIGI or the ValorId at SIX; null when the
        provider has none""") String providerInstrumentId,
    @Schema(description = "Exchange code as the provider writes it, e.g. 'EBS' at Yahoo or 'SE' at OpenFIGI") String providerExchangeCode,
    @Schema(description = "ISIN of the candidate; the queried ISIN when the provider filtered by it") String isin,
    @Schema(description = "How firmly the provider established the ISIN") IdentityEvidence isinEvidence,
    @Schema(description = """
        Trading currency as the provider delivers it, including minor units such as 'GBp'; null when unknown""") String currency,
    @Schema(description = "How firmly the provider established the currency") IdentityEvidence currencyEvidence,
    @Schema(description = "MIC of the listing; null when the provider exchange code cannot be mapped") String mic,
    @Schema(description = "How firmly the provider established the MIC") IdentityEvidence micEvidence,
    @Schema(description = "Name of the instrument as the provider delivers it") String name,
    @Schema(description = """
        Instrument type in the provider's own vocabulary, e.g. 'EQUITY', 'ETP' or 'Common Stock'. It is not mapped to
        an asset class here.""") String providerInstrumentType) {

  /**
   * Tells whether the listing exchange of this candidate is established by the provider.
   *
   * @return true when a MIC is present and its evidence is RETURNED or FILTERED
   */
  public boolean hasEstablishedMic() {
    return mic != null && micEvidence.isEstablished();
  }
}
