package grafioschtrader.common;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.reflect.FieldUtils;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.ProposeChangeField;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.FromToCurrency;
import grafioschtrader.reportviews.FromToCurrencyWithDate;
import grafioschtrader.types.TransactionType;

public abstract class DataHelper {

  
  public static double roundStandard(double valueToRound) {
    return round(valueToRound, GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
  }

  public static double round(double valueToRound) {
    return round(valueToRound, GlobalConstants.FID_MAX_FRACTION_DIGITS);
  }

  public static double round(double valueToRound, int numberOfDecimalPlaces) {
    double multipicationFactor = Math.pow(10, numberOfDecimalPlaces);
    double interestedInZeroDPs = valueToRound * multipicationFactor;
    return Math.round(interestedInZeroDPs) / multipicationFactor;
  }

  public static String toStringWithAttributes(Object object) {
    ReflectionToStringBuilder builder = new ReflectionToStringBuilder(object, ToStringStyle.DEFAULT_STYLE) {
      @Override
      protected boolean accept(Field field) {
        try {
          return super.accept(field) && field.get(object) != null;
        } catch (IllegalAccessException e) {
          return super.accept(field);
        }
      }
    };
    return builder.toString();
  }

  /**
   * Properties with empty string of an object are set to null.
   *
   * @param object Object which empty strings are set to null.
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws NoSuchMethodException
   */
  public static void setEmptyStringToNullOrRemoveTraillingSpaces(Object object)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

    PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(object);
    for (PropertyDescriptor property : propertyDescriptors) {
      if (property.getPropertyType() == String.class && property.getWriteMethod() != null) {
        String valueStr = (String) PropertyUtils.getProperty(object, property.getName());
        if (valueStr != null) {
          valueStr = valueStr.trim();
          if (valueStr.isEmpty()) {
            PropertyUtils.setProperty(object, property.getName(), null);
          } else {
            PropertyUtils.setProperty(object, property.getName(), valueStr);
          }
        }
      }
    }
  }
  
  
  public static String generateGUID() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  public static double divideMultiplyExchangeRate(double amount, Double currencyExRate, String sourceCurrency,
      String targetCurrency) {
    return divideMultiplyExchangeRate(amount, currencyExRate, sourceCurrency, targetCurrency, false);
  }

  public static double divideMultiplyExchangeRate(double amount, Double currencyExRate, String sourceCurrency,
      String targetCurrency, boolean forReverseCalculation) {
    if (currencyExRate != null) {
      Currencypair currencypair = DataHelper.getCurrencypairWithSetOfFromAndTo(sourceCurrency, targetCurrency);
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
   * Copy properties from the source to the target object.
   *
   * @param source
   * @param target
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws NoSuchMethodException
   */
  public static void updateEntityWithUpdatable(Object source, Object target,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    updateEntityWithUpdatable(source, new Object[] { target }, updatePropertyLevelClasses);
  }

  public static <T extends Annotation> void updateEntityWithUpdatable(Object source, Object targets[],
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    for (Object target : targets) {
      List<Field> fields = FieldUtils.getAllFieldsList(target.getClass());
      for (Field field : fields) {
        if (fieldContainsAnnotation(field, updatePropertyLevelClasses)) {
          if (fieldContainsAnnotation(field, Set.of(PropertySelectiveUpdatableOrWhenNull.class))
              && PropertyUtils.getProperty(target, field.getName()) != null) {
            // copy value to target when value was not set in target
            continue;
          }
          Object sourceValue = PropertyUtils.getProperty(source, field.getName());
          PropertyUtils.setProperty(target, field.getName(), sourceValue);
        }
      }
    }
  }

  /**
   * Compare the value of field of two objects of the same class. It is not a deep
   * comparison and only certain fields are compared.
   *
   * @param newEntity
   * @param existingEntity
   * @param updatePropertyLevelClasses
   * @return
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws NoSuchMethodException
   */
  public static List<ProposeChangeField> getDiffPropertiesOfEntity(Object newEntity, Object existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    List<ProposeChangeField> proposeChangeFieldList = new ArrayList<>();

    List<Field> fields = FieldUtils.getAllFieldsList(newEntity.getClass());
    for (Field field : fields) {
      if (fieldContainsAnnotation(field, updatePropertyLevelClasses)) {
        String name = field.getName();
        Object valueNew = PropertyUtils.getProperty(newEntity, name);
        Object valueExisting = PropertyUtils.getProperty(existingEntity, name);

        if (!Objects.equals(valueNew, valueExisting)) {
          proposeChangeFieldList
              .add(new ProposeChangeField(name, SerializationUtils.serialize((Serializable) valueNew)));
        }
      }
    }
    return proposeChangeFieldList;
  }

  private static boolean fieldContainsAnnotation(Field field,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    for (Class<? extends Annotation> annotationClass : updatePropertyLevelClasses) {
      if (field.getAnnotation(annotationClass) != null) {
        return true;
      }
    }
    return false;
  }

  public static List<Field> getFieldByPropertiesAnnotation(Class<?> clazz,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    return FieldUtils.getAllFieldsList(clazz).stream()
        .filter(field -> fieldContainsAnnotation(field, updatePropertyLevelClasses)).collect(Collectors.toList());

  }

  /**
   * Gets the exchange rate for a transaction, which depends on the transaction
   * time or the the transactions exchange rate.
   *
   * @param transaction
   * @param dateCurrencyMap
   * @return
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
   * @return
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
   *
   * @param sourceCurrency Normally the currency of the cash account
   * @param targetCurrency Normally the currency of the security
   * @return
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
