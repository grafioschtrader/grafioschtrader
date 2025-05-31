package grafioschtrader.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.EcbExchangeRates;
import grafioschtrader.entities.EcbExchangeRates.DateCurrencyKey;

public interface EcbExchangeRatesRepository extends JpaRepository<EcbExchangeRates, DateCurrencyKey> {

  @Query(value = "SELECT MAX(date) FROM ecb_exchange_rates", nativeQuery = true)
  Date getMaxDate();

  @Query(value = "SELECT date, IF(?4, rate, 1 / rate) AS rate FROM ecb_exchange_rates WHERE currency = ?1 AND date BETWEEN ?2 AND ?3", nativeQuery = true)
  List<CalcRates> getRatesByFromToDate(String currency, Date fromDate, Date toDate, boolean euroBase);

  @Query(nativeQuery = true)
  List<CalcRates> getCrossCurrencyRateForPeriod(String fromCurrency, String toCurrency, Date fromDate, Date toDate);

  @Query(nativeQuery = true)
  String[] checkForExistenceCurrencies(String currency1, String currency2);

  public static interface CalcRates {
    Date getDate();

    double getRate();
  }
}
