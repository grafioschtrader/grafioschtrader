package grafioschtrader.connector.securitysearch;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.SecuritySearchStatus;

/**
 * Result of one provider search. Ambiguous or incomplete candidates are kept for the caller to resolve; nothing here
 * picks the first one.
 *
 * @param providerId         id of the provider that was asked
 * @param status             outcome of the search
 * @param candidates         the candidates that do not contradict the query, best established first; empty unless the
 *                           status is {@link SecuritySearchStatus#OK}
 * @param rejectedCandidates number of candidates removed because their ISIN, currency or MIC contradicted the query
 * @param detail             short technical explanation of a failure or an empty result, may be null
 * @param otherMicCandidates the rejected candidates whose only contradiction is an established MIC other than the
 *                           queried one. They are never offered, but tell the user that the instrument may be listed on
 *                           another exchange than the one chosen.
 */
public record SecuritySearchResult(String providerId, SecuritySearchStatus status,
    List<SecuritySearchCandidateDto> candidates, int rejectedCandidates, String detail,
    List<SecuritySearchCandidateDto> otherMicCandidates) {

  public SecuritySearchResult {
    candidates = candidates == null ? List.of() : List.copyOf(candidates);
    otherMicCandidates = otherMicCandidates == null ? List.of() : List.copyOf(otherMicCandidates);
  }

  /**
   * Creates a result without candidates on other exchanges.
   *
   * @see #SecuritySearchResult(String, SecuritySearchStatus, List, int, String, List)
   */
  public SecuritySearchResult(String providerId, SecuritySearchStatus status,
      List<SecuritySearchCandidateDto> candidates, int rejectedCandidates, String detail) {
    this(providerId, status, candidates, rejectedCandidates, detail, List.of());
  }

  /**
   * Creates the result of a search that could not be carried out or found nothing.
   *
   * @param providerId id of the provider
   * @param status     the failure or empty status
   * @param detail     short explanation, may be null
   * @return the result without candidates
   */
  public static SecuritySearchResult failure(String providerId, SecuritySearchStatus status, String detail) {
    return new SecuritySearchResult(providerId, status, List.of(), 0, detail);
  }

  /**
   * Returns the only candidate listed on the given exchange, as needed for an automatic resolution. A candidate counts
   * only when the provider established its MIC. When several remain, the ticker hint may narrow them to the one whose
   * provider symbol is the ticker itself or the ticker followed by an exchange suffix ({@code NOVN.SW} rather than
   * {@code NOVNEE.SW}).
   *
   * @param mic        MIC of the exchange
   * @param tickerHint ticker of the instrument, may be null
   * @return the candidate, or empty when there is none or the choice stays ambiguous
   */
  public Optional<SecuritySearchCandidateDto> uniqueCandidateOnMic(String mic, String tickerHint) {
    List<SecuritySearchCandidateDto> onMic = candidates.stream()
        .filter(c -> c.hasEstablishedMic() && c.mic().equals(mic)).toList();
    if (onMic.size() > 1 && tickerHint != null) {
      List<SecuritySearchCandidateDto> narrowed = onMic.stream()
          .filter(c -> isTickerSymbol(c.providerSymbol(), tickerHint)).toList();
      if (!narrowed.isEmpty()) {
        onMic = narrowed;
      }
    }
    return onMic.size() == 1 ? Optional.of(onMic.getFirst()) : Optional.empty();
  }

  private static boolean isTickerSymbol(String providerSymbol, String ticker) {
    if (providerSymbol == null) {
      return false;
    }
    String symbol = providerSymbol.toUpperCase(Locale.ROOT);
    String tickerUpper = ticker.toUpperCase(Locale.ROOT);
    return symbol.equals(tickerUpper) || symbol.startsWith(tickerUpper + ".");
  }
}
