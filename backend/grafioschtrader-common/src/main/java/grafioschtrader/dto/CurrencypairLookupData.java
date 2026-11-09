package grafioschtrader.dto;

import java.util.List;
import java.util.Optional;

/**
 * What a connector may use to derive the URL extension of a currency pair that is being created: the two currencies and
 * the hits the currency pair search providers delivered.
 *
 * @param fromCurrency base currency of the pair, e.g. EUR
 * @param toCurrency   quote currency of the pair, e.g. CAD
 * @param hits         hits of all providers that found the pair; never null
 */
public record CurrencypairLookupData(String fromCurrency, String toCurrency, List<CurrencypairSearchHit> hits) {

  public CurrencypairLookupData {
    hits = hits == null ? List.of() : List.copyOf(hits);
  }

  /**
   * Returns the hit of a provider.
   *
   * @param providerId short id of the provider
   * @return the first hit of that provider, or empty when it found nothing
   */
  public Optional<CurrencypairSearchHit> hitOf(String providerId) {
    return hits.stream().filter(h -> providerId.equals(h.providerId())).findFirst();
  }
}
