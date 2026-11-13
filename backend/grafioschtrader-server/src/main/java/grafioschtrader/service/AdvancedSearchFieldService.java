package grafioschtrader.service;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import grafiosch.entities.User;
import grafiosch.types.UDFDataType;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.WatchlistIndicatorColumn;
import grafioschtrader.evalex.AlertExpressionSupport;
import grafioschtrader.evalex.IndicatorData;
import grafioschtrader.repository.UDFMetadataSecurityJpaRepository;
import grafioschtrader.repository.WatchlistIndicatorColumnJpaRepository;
import grafioschtrader.search.AdvancedSearchField;
import grafioschtrader.search.AdvancedSearchValueType;
import grafioschtrader.types.IndicatorResultType;
import grafioschtrader.types.UDFSpecialGTType;

/**
 * Computes the fields the logged-in user can choose in an advanced search criterion. The validation of a search request
 * and the base data of the search dialog read the same list, so a field that is not offered is also refused.
 * <p>
 * Offered are the stored user-defined fields of securities the user can see and all indicator columns of the tenant. A
 * user-defined field whose value is only computed for the display of a watchlist and never written to {@code udf_data}
 * is not offered, because a search could never find a value of it. A field that holds a link is not offered either,
 * because a link is opened and not searched.
 */
@Service
public class AdvancedSearchFieldService {

  /**
   * Special user-defined fields whose value is computed for the watchlist display and not stored: the yield to maturity
   * is written with {@code persist = false} by {@code YieldToMaturityCalculator}.
   */
  static final Set<UDFSpecialGTType> NOT_STORED_SPECIAL_TYPES = EnumSet
      .of(UDFSpecialGTType.UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY);

  /** Indicator data that makes the evaluation of a column expensive. */
  private static final Set<IndicatorData> EXPENSIVE_DATA = EnumSet.of(IndicatorData.SERIES, IndicatorData.BENCHMARK,
      IndicatorData.DIVIDENDS);

  @Autowired
  private UDFMetadataSecurityJpaRepository udfMetadataSecurityJpaRepository;

  @Autowired
  private WatchlistIndicatorColumnJpaRepository watchlistIndicatorColumnJpaRepository;

  @Autowired
  private MessageSource messages;

  /**
   * A field of the catalogue together with the stored definition behind it, which is what the search reads.
   *
   * @param field     the field as offered to the client
   * @param udf       the definition of a user-defined field, null for an indicator column
   * @param indicator the indicator column, null for a user-defined field
   */
  public record ResolvedField(AdvancedSearchField field, UDFMetadataSecurity udf, WatchlistIndicatorColumn indicator) {
  }

  /**
   * Returns the fields of the logged-in user as offered to the client.
   *
   * @return the fields, user-defined fields first and then the indicator columns, each in their display order
   */
  public List<AdvancedSearchField> getFields() {
    return resolve(currentUser()).values().stream().map(ResolvedField::field).toList();
  }

  /**
   * Resolves the fields of a user with the definitions behind them.
   *
   * @param user the user whose own and system-wide fields and whose tenant's indicator columns are used
   * @return the fields by their key, in display order
   */
  public Map<String, ResolvedField> resolve(User user) {
    Locale locale = user.createAndGetJavaLocale();
    Map<String, ResolvedField> fields = new LinkedHashMap<>();
    for (UDFMetadataSecurity udf : udfMetadataSecurityJpaRepository.getEnabledFieldsForUser(user.getIdUser())) {
      addUdf(fields, udf, locale);
    }
    String suffix = messages.getMessage("advanced.search.indicator.suffix", null, "indicator", locale);
    for (WatchlistIndicatorColumn column : watchlistIndicatorColumnJpaRepository
        .findByIdTenantOrderByUiOrder(user.getIdTenant())) {
      String key = AdvancedSearchField.KEY_PREFIX_INDICATOR + column.getIdWatchlistIndicatorColumn();
      AdvancedSearchValueType valueType = column.getResultType() == IndicatorResultType.BOOLEAN
          ? AdvancedSearchValueType.BOOLEAN
          : AdvancedSearchValueType.NUMBER;
      fields.put(key, new ResolvedField(new AdvancedSearchField(key, column.getLabel() + " (" + suffix + ")", valueType,
          isCandidateLimited(column.getExpression())), null, column));
    }
    return fields;
  }

  private void addUdf(Map<String, ResolvedField> fields, UDFMetadataSecurity udf, Locale locale) {
    if (udf.getUdfDataType() == UDFDataType.UDF_URLString) {
      // A link is opened, not searched: neither a range nor a part of an address is a useful criterion.
      return;
    }
    if (udf.getUdfSpecialType() instanceof UDFSpecialGTType special && NOT_STORED_SPECIAL_TYPES.contains(special)) {
      return;
    }
    String key = AdvancedSearchField.KEY_PREFIX_UDF + udf.getIdUDFMetadata();
    fields.put(key, new ResolvedField(
        new AdvancedSearchField(key, labelOf(udf, locale), valueTypeOf(udf.getUdfDataType()), false), udf, null));
  }

  /**
   * A special field is named by the constant of its special type, which the message bundle defines; the description of
   * such a row holds a token that no bundle defines. Any other field shows its description.
   */
  private String labelOf(UDFMetadataSecurity udf, Locale locale) {
    if (udf.getUdfSpecialType() instanceof Enum<?> special) {
      return messages.getMessage(special.name(), null, udf.getDescription(), locale);
    }
    return udf.getDescription();
  }

  static AdvancedSearchValueType valueTypeOf(UDFDataType udfDataType) {
    return switch (udfDataType) {
    case UDF_Numeric, UDF_NumericInteger -> AdvancedSearchValueType.NUMBER;
    case UDF_DateString, UDF_DateTimeNumeric -> AdvancedSearchValueType.DATE;
    case UDF_String -> AdvancedSearchValueType.TEXT;
    case UDF_Boolean -> AdvancedSearchValueType.BOOLEAN;
    case UDF_URLString -> throw new IllegalArgumentException("A link is not offered in the advanced search");
    };
  }

  /**
   * Whether an expression needs a quote series, a benchmark or dividends. An expression that cannot be analysed is
   * treated as expensive, because nothing is known about the data it reads.
   */
  static boolean isCandidateLimited(String expression) {
    try {
      Set<IndicatorData> required = AlertExpressionSupport.requiredData(expression);
      return required.stream().anyMatch(EXPENSIVE_DATA::contains);
    } catch (RuntimeException e) {
      return true;
    }
  }

  private static User currentUser() {
    return (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
  }
}
