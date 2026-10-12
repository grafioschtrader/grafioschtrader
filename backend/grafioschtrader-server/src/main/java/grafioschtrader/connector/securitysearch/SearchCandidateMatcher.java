package grafioschtrader.connector.securitysearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import grafioschtrader.dto.SecuritySearchCandidateDto;
import grafioschtrader.types.IdentityEvidence;
import grafioschtrader.types.SecuritySearchStatus;

/**
 * Turns the listings a provider delivered into a {@link SecuritySearchResult}. It is the one place that decides whether
 * a candidate contradicts the query, so all providers apply the same identity rules:
 * <ul>
 * <li>A candidate is rejected when an established ISIN, currency or MIC differs from the value in the query. A value
 * the provider did not establish never causes a rejection - it stays unknown.</li>
 * <li>Currencies are compared after folding minor units into their main currency ({@code GBp}/{@code GBX} into
 * {@code GBP}, {@code ZAc} into {@code ZAR}, {@code ILA} into {@code ILS}), because providers quote London,
 * Johannesburg and Tel Aviv listings in minor units while the instrument currency is the main one. The candidate keeps
 * the currency as delivered.</li>
 * <li>The remaining candidates are ordered so that those confirming the MIC come first, then those confirming the
 * currency and then the ISIN. No candidate is chosen; ambiguity is left to the caller.</li>
 * </ul>
 */
public final class SearchCandidateMatcher {

  private static final Map<String, String> MINOR_UNIT_CURRENCIES = Map.of("GBp", "GBP", "GBX", "GBP", "ZAc", "ZAR",
      "ZAC", "ZAR", "ILA", "ILS", "ILa", "ILS");

  private SearchCandidateMatcher() {
  }

  /**
   * Removes duplicates and candidates contradicting the query, orders the rest and applies the result limit.
   *
   * @param providerId    id of the provider that delivered the candidates
   * @param query         the search query
   * @param rawCandidates the listings as the provider delivered them, with their evidence set by the adapter
   * @return {@link SecuritySearchStatus#OK} with the candidates, or {@link SecuritySearchStatus#NO_MATCH} when none
   *         remains
   */
  public static SecuritySearchResult match(String providerId, SecuritySearchQuery query,
      List<SecuritySearchCandidateDto> rawCandidates) {
    Map<String, SecuritySearchCandidateDto> unique = new LinkedHashMap<>();
    rawCandidates.forEach(c -> unique
        .putIfAbsent(c.providerSymbol() + "|" + c.providerInstrumentId() + "|" + c.mic() + "|" + c.currency(), c));
    int rejected = 0;
    List<SecuritySearchCandidateDto> accepted = new ArrayList<>();
    for (SecuritySearchCandidateDto candidate : unique.values()) {
      if (contradicts(query, candidate)) {
        rejected++;
      } else {
        accepted.add(candidate);
      }
    }
    if (accepted.isEmpty()) {
      return new SecuritySearchResult(providerId, SecuritySearchStatus.NO_MATCH, List.of(), rejected,
          rejected > 0 ? rejected + " candidate(s) contradicted the queried ISIN, currency or MIC" : null);
    }
    List<SecuritySearchCandidateDto> ordered = accepted.stream()
        .sorted(Comparator.comparingInt((SecuritySearchCandidateDto c) -> score(query, c)).reversed())
        .limit(query.maxResults()).toList();
    return new SecuritySearchResult(providerId, SecuritySearchStatus.OK, ordered, rejected, null);
  }

  /**
   * Tells whether an established identity field of the candidate differs from the query.
   *
   * @param query     the search query
   * @param candidate the candidate
   * @return true when ISIN, currency or MIC contradict the query
   */
  static boolean contradicts(SecuritySearchQuery query, SecuritySearchCandidateDto candidate) {
    return differs(query.isin(), candidate.isin(), candidate.isinEvidence())
        || differs(normalizeCurrency(query.currency()), normalizeCurrency(candidate.currency()),
            candidate.currencyEvidence())
        || differs(query.mic(), candidate.mic(), candidate.micEvidence());
  }

  /**
   * Folds a minor-unit currency into its main currency, so that {@code GBp} and {@code GBP} compare equal.
   *
   * @param currency the currency as delivered, may be null
   * @return the main currency in upper case, or null
   */
  public static String normalizeCurrency(String currency) {
    if (currency == null) {
      return null;
    }
    return MINOR_UNIT_CURRENCIES.getOrDefault(currency, currency.toUpperCase(Locale.ROOT));
  }

  private static boolean differs(String queried, String delivered, IdentityEvidence evidence) {
    return queried != null && delivered != null && evidence.isEstablished() && !queried.equalsIgnoreCase(delivered);
  }

  private static int score(SecuritySearchQuery query, SecuritySearchCandidateDto c) {
    int score = 0;
    if (c.hasEstablishedMic() && Objects.equals(query.mic(), c.mic())) {
      score += 4;
    }
    if (c.currency() != null && c.currencyEvidence().isEstablished()) {
      score += 2;
    }
    if (c.isin() != null && c.isinEvidence().isEstablished()) {
      score += 1;
    }
    return score;
  }
}
