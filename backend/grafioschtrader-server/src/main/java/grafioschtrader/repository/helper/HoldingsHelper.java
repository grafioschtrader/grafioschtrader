package grafioschtrader.repository.helper;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.reportviews.FromToCurrency;
import grafioschtrader.repository.CurrencypairJpaRepository;

/**
 * Utility class for currency conversion operations in holdings calculations.
 * 
 * <p>
 * Provides efficient currency pair lookup and conversion capabilities for multi-currency portfolio management.
 * Optimizes currency operations through caching and automatic currency pair creation.
 * </p>
 */
public class HoldingsHelper {

  /**
   * Retrieves all currency pairs used within a tenant's portfolios and accounts.
   * 
   * <p>
   * Returns a map for O(1) lookup performance during holdings calculations.
   * </p>
   * 
   * @param idTenant                  the tenant identifier
   * @param currencypairJpaRepository repository for currency pair data access
   * @return map of FromToCurrency keys to Currencypair entities
   */
  public static Map<FromToCurrency, Currencypair> getUsedCurrencypiarsByIdTenant(Integer idTenant,
      CurrencypairJpaRepository currencypairJpaRepository) {
    List<Currencypair> currencypairs = currencypairJpaRepository
        .getAllCurrencypairsByTenantInPortfolioAndAccounts(idTenant);
    return transformToCurrencypairMapWithFromCurrencyAsKey(currencypairs);
  }

  /**
   * Transforms a list of currency pairs into a map for efficient lookup operations.
   * 
   * <p>
   * Converts list to map with FromToCurrency keys for O(1) lookup instead of O(n) scanning.
   * </p>
   * 
   * @param currencypairs list of currency pair entities to transform
   * @return map with FromToCurrency keys and Currencypair values
   */
  public static Map<FromToCurrency, Currencypair> transformToCurrencypairMapWithFromCurrencyAsKey(
      List<Currencypair> currencypairs) {
    return currencypairs.stream()
        .collect(Collectors.toMap(
            currencypair -> new FromToCurrency(currencypair.getFromCurrency(), currencypair.getToCurrency()),
            Function.identity()));
  }

  /**
   * Retrieves or creates a currency pair for the specified conversion direction.
   * 
   * <p>
   * Uses three-tier lookup: cache → database → create new. Updates cache with newly found or created currency pairs for
   * optimization.
   * </p>
   * 
   * @param currencypairJpaRepository     repository for currency pair operations
   * @param currencypairFromToCurrencyMap cache map (updated by this method)
   * @param fromCurrency                  source currency code
   * @param toCurrency                    target currency code
   * @return the currency pair entity for the specified conversion
   */
  @Transactional
  public static Currencypair getCurrency(CurrencypairJpaRepository currencypairJpaRepository,
      Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap, String fromCurrency, String toCurrency) {
    Currencypair currencypair = currencypairFromToCurrencyMap.get(new FromToCurrency(fromCurrency, toCurrency));

    if (currencypair == null) {
      currencypair = currencypairJpaRepository.findByFromCurrencyAndToCurrency(fromCurrency, toCurrency);
      if (currencypair == null) {
        currencypair = currencypairJpaRepository.createNonExistingCurrencypair(fromCurrency, toCurrency, false);
      }
      currencypairFromToCurrencyMap.put(new FromToCurrency(fromCurrency, toCurrency), currencypair);
    }
    return currencypair;
  }
}
