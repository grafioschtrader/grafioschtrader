package grafioschtrader.common;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.FromToCurrency;
import grafioschtrader.reportviews.FromToCurrencyWithDate;
import grafioschtrader.types.TransactionType;

public abstract class DataBusinessHelper {

  public static double roundStandard(double valueToRound) {
    return DataHelper.round(valueToRound, GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
  }

  public static double round(double valueToRound) {
    return DataHelper.round(valueToRound, BaseConstants.FID_MAX_FRACTION_DIGITS);
  }

  public static double divideMultiplyExchangeRate(double amount, Double currencyExRate, String sourceCurrency,
      String targetCurrency) {
    return divideMultiplyExchangeRate(amount, currencyExRate, sourceCurrency, targetCurrency, false);
  }

  public static double divideMultiplyExchangeRate(double amount, Double currencyExRate, String sourceCurrency,
      String targetCurrency, boolean forReverseCalculation) {
    if (currencyExRate != null) {
      Currencypair currencypair = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(sourceCurrency, targetCurrency);
      if (currencypair != null) {
        if (sourceCurrency.equals(currencypair.getFromCurrency()) && !forReverseCalculation) {
          return amount * currencyExRate;
        } else {
          return amount / currencyExRate;
        }
      }
    }
    return amount;
  }

  /**
   * Gets the exchange rate for a transaction, which depends on the transaction
   * time or the the transactions exchange rate.
   */
  public static Double getCurrencyExchangeRateToMainCurreny(Transaction transaction,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    Double exchangeRate = null;
    if (transaction.getCashaccount() != null && dateCurrencyMap != null && dateCurrencyMap.getMainCurrency() != null) {
      if (transaction.getTransactionType() == TransactionType.HYPOTHETICAL_SELL
          && !dateCurrencyMap.getMainCurrency().equals(transaction.getSecurity().getCurrency())) {
        Currencypair currencypair = dateCurrencyMap
            .getCurrencypairByFromCurrency(transaction.getSecurity().getCurrency());
        exchangeRate = currencypair.getSLast();

      } else if (!dateCurrencyMap.getMainCurrency().equals(transaction.getCashaccount().getCurrency())
          && !dateCurrencyMap.getMainCurrency().equals(transaction.getSecurity().getCurrency())) {
        // Both cash account and security currency are different to the main currency

        exchangeRate = dateCurrencyMap.getPriceByDateAndFromCurrency(transaction.getTransactionDateAsDate(),
            transaction.getSecurity().getCurrency(), false);
        if (exchangeRate == null) {

          // For example: Main currency CHF and transaction which does not involved this
          // main currency.
          exchangeRate = dateCurrencyMap.getPriceByDateAndFromCurrency(transaction.getTransactionDateAsDate(),
              transaction.getCashaccount().getCurrency(), true);

          // System.err.println("Try: " + transaction.getTransactionTime() + " "
          // + transaction.getCashaccount().getCurrency() + ", result: " + exchangeRate);

        }
      } else if (transaction.getIdCurrencypair() != null
          && (transaction.getCashaccount().getCurrency().equals(dateCurrencyMap.getMainCurrency())
              || transaction.getSecurity().getCurrency().equals(dateCurrencyMap.getMainCurrency()))) {
        exchangeRate = transaction.getCurrencyExRate();
      }

    }
    return exchangeRate;
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
    Map<Object, Boolean> map = new ConcurrentHashMap<>();
    return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
  }

  /**
   * Returns for DEPOSIT/WITHDRAWAL transaction the amount in the expected
   * currency.
   *
   * @param transaction                         DEPOSIT/WITHDRAWAL transaction
   * @param fromToCurrencyWithDateMap
   * @param mainCurrency                        The from currency / main currency
   * @param exchangeRateConnectedTransactionMap A account transfer is tracked by
   *                                            this map,
   * @param currencypairFromToCurrencyMap       May be used for transactions of
   *                                            today or in the future
   */
  public static CashaccountTransfer calcDepositOnTransactionsOfCashaccount(Transaction transaction,
      Map<FromToCurrencyWithDate, Double> fromToCurrencyWithDateMap, String mainCurrency,
      Map<Integer, Double> exchangeRateConnectedTransactionMap,
      Map<FromToCurrency, Currencypair> currencypairFromToCurrencyMap) {
    CashaccountTransfer ct = new CashaccountTransfer();
    // Transaction cost only WITHDRAWAL expected

    double transactionCost = transaction.getTransactionCost() == null ? 0.0 : transaction.getTransactionCost();
    if (transaction.getIdCurrencypair() == null) {
      if (mainCurrency.equals(transaction.getCashaccount().getCurrency())) {
        // Transaction which has the same currency as the main currency
        // For example a1.CHF to a2.CHF or a1.CHF to b1.CHF when CHF is main currency
        ct.amountMC = transaction.getCashaccountAmount() + transactionCost;
        ct.cashAccountTransactionFeeMC = transactionCost;
      } else {
        // Cash account has a different currency as main currency but there is no
        // currency pair.
        // That means there must be a transaction with a cash account outside this
        // portfolio.
        // For example a1.EUR to b1.EUR when EUR not main currency
        Double exchangeRate = fromToCurrencyWithDateMap.get(new FromToCurrencyWithDate(
            transaction.getCashaccount().getCurrency(), mainCurrency, transaction.getTransactionDate()));
        if (exchangeRate == null) {
          // Transaction date is not available in the map, because this date is today or
          // in the future
          FromToCurrency fromToCurrency = new FromToCurrency(transaction.getCashaccount().getCurrency(), mainCurrency);
          exchangeRate = currencypairFromToCurrencyMap.get(fromToCurrency).getSLast();
        }
        ct.amountMC = (transaction.getCashaccountAmount() + transactionCost) * exchangeRate;
        ct.cashAccountTransactionFeeMC = transactionCost * exchangeRate;
      }
    } else if (transaction.isCashaccountTransfer()) {
      // It is a cash transfer between to accounts of tenant portfolios
      // For example a1.CHF to a2.USD or a2.USD to a1.CHF or a1.USD to a1.GBP
      Double otherTransactionDeposit = exchangeRateConnectedTransactionMap.get(transaction.getIdTransaction());
      if (otherTransactionDeposit == null) {
        // First record of a connected transaction
        Double exchangeRate = fromToCurrencyWithDateMap.get(new FromToCurrencyWithDate(
            transaction.getCashaccount().getCurrency(), mainCurrency, transaction.getTransactionDate()));
        if (exchangeRate == null) {
          if (mainCurrency.equals(transaction.getCashaccount().getCurrency())) {
            ct.amountMC = transaction.getCashaccountAmount() + transactionCost;
            ct.cashAccountTransactionFeeMC = transactionCost;
          } else {
            ct.amountMC = (transaction.getCashaccountAmount() + transactionCost) / transaction.getCurrencyExRate();
            ct.cashAccountTransactionFeeMC = transactionCost / transaction.getCurrencyExRate();
          }
        } else {
          ct.amountMC = (transaction.getCashaccountAmount() + transactionCost) * exchangeRate;
          ct.cashAccountTransactionFeeMC = transactionCost * exchangeRate;
        }

        exchangeRateConnectedTransactionMap.put(transaction.getConnectedIdTransaction(), ct.amountMC * -1);

      } else {
        // 2nd record of a connected transaction
        ct.amountMC = otherTransactionDeposit;
        exchangeRateConnectedTransactionMap.remove(transaction.getIdTransaction());
      }
    }
    return ct;
  }

  /**
   * Gets the currency pair depending on the main currency and source and target
   * currency.
   */
  public static Currencypair getCurrencypairWithSetOfFromAndTo(String sourceCurrency, String targetCurrency) {
    Currencypair currencypair = null;

    if (sourceCurrency != null && !targetCurrency.equals(sourceCurrency)) {
      currencypair = new Currencypair(sourceCurrency, targetCurrency);
    }
    return currencypair;
  }

  public static <T, U> boolean compareCollectionsUnSorted(final Collection<T> coll1, final Collection<U> coll2,
      final BiPredicate<T, U> predicate) {
    return coll1.size() == coll2.size() && coll1.stream()
        .allMatch(coll1Item -> coll2.stream().anyMatch(col2Item -> predicate.test(coll1Item, col2Item)));
  }

  public static <T, U> boolean compareCollectionsSorted(final List<T> coll1, final List<U> coll2,
      final BiPredicate<T, U> predicate) {
    return coll1.size() == coll2.size()
        && IntStream.range(0, coll1.size()).allMatch(i -> predicate.test(coll1.get(i), coll2.get(i)));
  }

  public static class CashaccountTransfer {
    public double amountMC = 0.0;
    public double cashAccountTransactionFeeMC = 0.0;
  }

}
