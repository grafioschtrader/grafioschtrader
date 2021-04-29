package grafioschtrader.repository.helper;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.reportviews.FromToCurrency;
import grafioschtrader.repository.CurrencypairJpaRepository;

public class HoldingsHelper {
  public static Map<FromToCurrency, Currencypair> getUsedCurrencypiarsByIdTenant(Integer idTenant,
      CurrencypairJpaRepository currencypairJpaRepository) {
    List<Currencypair> currencypairs = currencypairJpaRepository
        .getAllCurrencypairsByTenantInPortfolioAndAccounts(idTenant);
    return transformToCurrencypairMapWithFromCurrencyAsKey(currencypairs);
  }

  public static Map<FromToCurrency, Currencypair> transformToCurrencypairMapWithFromCurrencyAsKey(
      List<Currencypair> currencypairs) {
    return currencypairs.stream()
        .collect(Collectors.toMap(
            currencypair -> new FromToCurrency(currencypair.getFromCurrency(), currencypair.getToCurrency()),
            Function.identity()));
  }

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
