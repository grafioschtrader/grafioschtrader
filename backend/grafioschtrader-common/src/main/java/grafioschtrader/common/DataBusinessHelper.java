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

/**
 * An abstract utility class providing common business logic helper methods for financial calculations, data
 * transformations, and comparisons within the Grafioschtrader application.
 *
 * <p>
 * This class encapsulates reusable logic related to currency conversions, transaction processing, and collection
 * comparisons. It aims to centralize these operations to ensure consistency and reduce code duplication across
 * different parts of the application.
 * </p>
 */
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

  /**
   * Converts an amount from a source currency to a target currency using a given exchange rate, with an option for
   * reverse calculation logic.
   *
   * <p>
   * The method determines whether to multiply or divide by the {@code currencyExRate} based on the relationship between
   * {@code sourceCurrency}, {@code targetCurrency}, and the implied Currencypair derived from them. The
   * {@code forReverseCalculation} flag can invert this logic.
   * </p>
   *
   * @param amount                The amount in the source currency.
   * @param currencyExRate        The exchange rate. The interpretation depends on the currency pair and
   *                              {@code forReverseCalculation}.
   * @param sourceCurrency        The ISO code of the source currency.
   * @param targetCurrency        The ISO code of the target currency.
   * @param forReverseCalculation If {@code true}, the multiplication/division logic is inverted. This might be used
   *                              when the provided {@code currencyExRate} is for the inverse pair.
   * @return The converted amount in the target currency. If {@code currencyExRate} is null or if source and target
   *         currencies are the same, the original amount is returned.
   * @see #getCurrencypairWithSetOfFromAndTo(String, String)
   */
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
   * Gets the exchange rate for a transaction to convert its value to the main currency. The rate depends on the
   * transaction type, its date, and the currencies involved (cash account currency, security currency, main currency).
   *
   * <p>
   * For hypothetical sell transactions, it uses the last known price of the security's currency pair. For other
   * transactions, it attempts to find a historical price from {@code dateCurrencyMap}. If both cash account and
   * security currencies differ from the main currency, it prioritizes the security's currency conversion, then the cash
   * account's currency if the former fails. If the transaction involves a currency pair ({@code idCurrencypair} is not
   * null) and one of the transaction's currencies is the main currency, the transaction's own {@code currencyExRate} is
   * used.
   * </p>
   *
   * @param transaction     The Transaction for which to determine the exchange rate.
   * @param dateCurrencyMap A map (DateTransactionCurrencypairMap) containing historical exchange rates and the main
   *                        currency definition. Can be null.
   * @return The exchange rate to convert the transaction's relevant amount to the main currency, or {@code null} if a
   *         rate cannot be determined.
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

  /**
   * Creates a Predicate that maintains state about what it has seen previously, allowing filtering of elements in a
   * stream to keep only those that are distinct based on a given key extractor function.
   *
   * <p>
   * This is useful for stream operations like {@code stream.filter(distinctByKey(MyObject::getKey))}. The predicate
   * uses a ConcurrentHashMap to store seen keys, making it thread-safe if the stream is processed in parallel (though
   * the distinctness guarantee in parallel streams depends on the encounter order if not otherwise specified).
   * </p>
   *
   * @param <T>          The type of the input elements.
   * @param keyExtractor A Function that extracts the key to be used for distinctness comparison.
   * @return A Predicate that returns {@code true} for an element if its key has not been seen before.
   */
  public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
    Map<Object, Boolean> map = new ConcurrentHashMap<>();
    return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
  }

  /**
   * Calculates the cash account transfer amounts in the main currency for DEPOSIT or WITHDRAWAL transactions. This
   * method handles various scenarios, including transactions in the main currency, transactions in foreign currencies
   * requiring exchange rate lookups, and linked cash account transfers.
   *
   * @param transaction                         The DEPOSIT or WITHDRAWAL Transaction to process.
   * @param fromToCurrencyWithDateMap           A map (FromToCurrencyWithDate to Double) providing historical exchange
   *                                            rates. Used for transactions on dates where historical data is
   *                                            available.
   * @param mainCurrency                        The ISO code of the portfolio's main currency.
   * @param exchangeRateConnectedTransactionMap A map (Integer transaction ID to Double amount) used to track amounts
   *                                            for linked cash account transfers. The first part of a transfer stores
   *                                            its calculated main currency amount (negated) with the connected
   *                                            transaction's ID as key. The second part retrieves this amount. Entries
   *                                            are removed after the second part is processed.
   * @param currencypairFromToCurrencyMap       A map (FromToCurrency to Currencypair) providing current exchange rates
   *                                            (e.g., {@code sLast}). Used for transactions dated today or in the
   *                                            future where historical rates are not yet in
   *                                            {@code fromToCurrencyWithDateMap}.
   * @return A CashaccountTransfer object containing the calculated {@code amountMC} (amount in main currency) and
   *         {@code cashAccountTransactionFeeMC} (transaction fee in main currency).
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
   * Creates a Currencypair object representing the relationship between a source and a target currency. If the source
   * and target currencies are the same, or if either is null, it returns {@code null} as no currency pair is needed or
   * can be formed.
   *
   * @param sourceCurrency The ISO code of the source (from) currency.
   * @param targetCurrency The ISO code of the target (to) currency.
   * @return A new Currencypair instance if source and target currencies are different and not null, otherwise
   *         {@code null}.
   */
  public static Currencypair getCurrencypairWithSetOfFromAndTo(String sourceCurrency, String targetCurrency) {
    Currencypair currencypair = null;

    if (sourceCurrency != null && !targetCurrency.equals(sourceCurrency)) {
      currencypair = new Currencypair(sourceCurrency, targetCurrency);
    }
    return currencypair;
  }

  /**
   * Compares two collections to determine if they contain the same elements, irrespective of their order. The
   * comparison of individual elements is performed using the provided BiPredicate.
   *
   * <p>
   * The collections are considered equal if they have the same size and for every element in the first collection,
   * there is at least one element in the second collection that satisfies the predicate, and vice-versa (implicitly,
   * due to size check and {@code allMatch} on the first collection).
   * </p>
   *
   * @param <T>       The type of elements in the first collection.
   * @param <U>       The type of elements in the second collection.
   * @param coll1     The first collection.
   * @param coll2     The second collection.
   * @param predicate A BiPredicate that defines how elements from {@code coll1} and {@code coll2} are compared. It
   *                  should return {@code true} if the two elements are considered equivalent.
   * @return {@code true} if the collections are considered equal based on size and element-wise predicate matching,
   *         {@code false} otherwise.
   */
  public static <T, U> boolean compareCollectionsUnSorted(final Collection<T> coll1, final Collection<U> coll2,
      final BiPredicate<T, U> predicate) {
    return coll1.size() == coll2.size() && coll1.stream()
        .allMatch(coll1Item -> coll2.stream().anyMatch(col2Item -> predicate.test(coll1Item, col2Item)));
  }

  /**
   * Compares two lists to determine if they contain the same elements in the same order. The comparison of individual
   * elements at corresponding positions is performed using the provided BiPredicate.
   *
   * <p>
   * The lists are considered equal if they have the same size and for every index {@code i}, the element
   * {@code coll1.get(i)} and {@code coll2.get(i)} satisfy the predicate.
   * </p>
   *
   * @param <T>       The type of elements in the first list.
   * @param <U>       The type of elements in the second list.
   * @param coll1     The first list.
   * @param coll2     The second list.
   * @param predicate A BiPredicate that defines how elements from {@code coll1} and {@code coll2} at the same position
   *                  are compared. It should return {@code true} if the two elements are considered equivalent.
   * @return {@code true} if the lists are considered equal based on size and ordered element-wise predicate matching,
   *         {@code false} otherwise.
   */
  public static <T, U> boolean compareCollectionsSorted(final List<T> coll1, final List<U> coll2,
      final BiPredicate<T, U> predicate) {
    return coll1.size() == coll2.size()
        && IntStream.range(0, coll1.size()).allMatch(i -> predicate.test(coll1.get(i), coll2.get(i)));
  }

  /**
   * A simple data structure to hold results from cash account transfer calculations, specifically the amount and
   * transaction fee converted to the main currency.
   */
  public static class CashaccountTransfer {
    /**
     * The calculated amount of the transaction (deposit or withdrawal) in the main currency. Defaults to 0.0.
     */
    public double amountMC = 0.0;
    /**
     * The calculated transaction fee associated with the cash account operation, converted to the main currency.
     * Defaults to 0.0.
     */
    public double cashAccountTransactionFeeMC = 0.0;
  }

}
