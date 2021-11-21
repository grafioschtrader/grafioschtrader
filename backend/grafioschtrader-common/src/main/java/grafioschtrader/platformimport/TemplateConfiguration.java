package grafioschtrader.platformimport;

import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import grafioschtrader.common.EnumHelper;
import grafioschtrader.common.ValueFormatConverter;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.types.ImportKnownOtherFlags;
import grafioschtrader.types.TransactionType;

/**
 *
 * Base configuration for parsing templates as pdf(txt) or csv(txt).
 *
 * @author Hugo Graf
 *
 */
public abstract class TemplateConfiguration {

  public static final String SECTION_END = "[END]";
  private static final String CONF_IGNORE_TAX_ON_DIV_INT = "ignoreTaxOnDivInt";
  private static final String CONF_TRANSACTION_TYPE = "transType";
  private static final String CONF_DATE_FORMAT = "dateFormat";
  private static final String CONF_TIME_FORMAT = "timeFormat";
  private static final String CONF_OVER_RULE_SEPARATORS = "overRuleSeparators";
  private static final String CONF_OTHER_FLAG_OPTIONS = "otherFlagOptions";

  private static final String ALL_SEPARATOR = "All";

  protected EnumSet<ImportKnownOtherFlags> importKnownOtherFlagsSet = EnumSet.noneOf(ImportKnownOtherFlags.class);

  protected ImportTransactionTemplate importTransactionTemplate;
  protected String template;

  protected Locale userLocale;
  protected char decimalSeparator;

  protected String ignoreTaxOnDivInt;
  protected String thousandSeparatorsPattern = "";
  protected String numberTypeRegex;
  protected String dateTypeRegex;
  protected String timeTypeRegex;
  protected DecimalFormatSymbols decimalFormatSymbols;

  /**
   * Contains the different possible properties with it data types.
   */
  protected static Map<String, Class<?>> propertyDataTypeMap = ValueFormatConverter
      .getDataTypeOfPropertiesByBean(new ImportProperties(null, null, null));

  /**
   * Format of date
   */
  protected String dateFormat;

  /**
   * Time format
   */
  protected String timeFormat;

  /**
   * A template may handle different transaction types.
   */
  protected Map<String, TransactionType> transactionTypesMap = new HashMap<>();

  protected abstract void readTemplateProperties(String[] templateLines, int startRowConfig,
      DataViolationException dataViolationException);

  protected TemplateConfiguration(ImportTransactionTemplate importTransactionTemplate, Locale userLocale) {
    this.importTransactionTemplate = importTransactionTemplate;
    this.userLocale = userLocale;
  }

  public void parseTemplateAndThrowError(boolean forSaving) {
    final DataViolationException dataViolationException = parseTemplat(forSaving);
    if (dataViolationException.hasErrors()) {
      throw dataViolationException;
    }
  }

  protected DataViolationException parseTemplat(boolean forSaving) {
    final DataViolationException dataViolationException = new DataViolationException();
    template = importTransactionTemplate.getTemplateAsTxt().replaceAll("(?m)^\\s*$[\n\r]{1,}", "")
        .replaceAll("\r\n|\r|\n", System.lineSeparator()).replaceAll(" +", " ").trim();
    String[] templateLines = template.split(System.lineSeparator());

    int startRowConfig = readTemplateConfiguration(templateLines);
    decimalSeparator = decimalFormatSymbols.getDecimalSeparator();
    numberTypeRegex = createNumberRegexByLocale();
    readTemplateProperties(templateLines, startRowConfig, dataViolationException);

    if (forSaving) {
      validateTemplate(dataViolationException);
    }
    return dataViolationException;
  }

  /**
   * Reads the END section.
   *
   * @param templateLines
   * @return
   */
  private int readTemplateConfiguration(String[] templateLines) {
    int startRowConfig = -1;
    String separators = null;
    for (int i = 0; i < templateLines.length; i++) {
      if (templateLines[i].toUpperCase().equals(SECTION_END)) {
        startRowConfig = i;
      }
      if (startRowConfig > 0 && i > startRowConfig) {
        String splitEqual[] = templateLines[i].split("=");
        switch (splitEqual[0]) {
        case CONF_IGNORE_TAX_ON_DIV_INT:
          ignoreTaxOnDivInt = splitEqual[1];
          break;
        case CONF_TRANSACTION_TYPE:
          String transTypeSplit[] = splitEqual[1].split(Pattern.quote("|"));
          String transTypeWordsSplit[] = transTypeSplit[1].split(",");
          for (String element : transTypeWordsSplit) {
            transactionTypesMap.put(element.trim(), TransactionType.getTransactionTypeByName((transTypeSplit[0])));
          }
          break;
        case CONF_DATE_FORMAT:
          dateFormat = splitEqual[1];
          dateTypeRegex = dateFormat.replaceAll("\\-", "\\\\-").replaceAll("\\.", "\\\\.")
              .replaceFirst("yyyy", "\\\\d{4}").replaceFirst("MMM", "\\\\w{3}").replaceFirst("hh|HH", "\\\\d{1,2}")
              .replaceAll("dd|MM|yy|mm|ss", "\\\\d{2}").replaceFirst("a", "[AaPp][Mm]");
          break;
        case CONF_TIME_FORMAT:
          timeFormat = splitEqual[1];
          timeTypeRegex = timeFormat.replaceAll("dd|MM|yy|mm|ss", "\\\\d{2}").replaceFirst("a", "[AaPp][Mm]");
          break;
        case CONF_OVER_RULE_SEPARATORS:
          separators = splitEqual[1];
          break;
        case CONF_OTHER_FLAG_OPTIONS:
          processOtherFlagOptions(splitEqual[1]);
          break;
        default:
          // May be some other configurations
          addionalConfigurations(splitEqual);
        }
      }
    }
    createSeparatorPattern(separators);
    return startRowConfig;
  }

  private void processOtherFlagOptions(String flagOptionsStr) {
    String[] flagOptions = flagOptionsStr.split(Pattern.quote("|"));
    for (String flagOption : flagOptions) {
      String option = "CAN_" + flagOption;
      ImportKnownOtherFlags okof = EnumHelper.enumContainsNameAsString(option, ImportKnownOtherFlags.class);
      if (okof != null) {
        importKnownOtherFlagsSet.add(okof);
      }
    }
  }

  /**
   * All< '|.> or de-CH<'|.>de-DE<,|.>
   *
   * @param separators
   */

  private void createSeparatorPattern(String separatorsConfig) {
    decimalFormatSymbols = new DecimalFormatSymbols(userLocale);
    String thousandSeparators = "" + decimalFormatSymbols.getGroupingSeparator();

    Separators separators = setAndOverRuleSeparators(separatorsConfig);

    if (separators != null) {
      decimalFormatSymbols.setDecimalSeparator(separators.decimalSeparator);
      thousandSeparators = separators.thousandSeparators;
    }

    if (thousandSeparators.contains("'")) {
      thousandSeparators += "’";
    } else if (thousandSeparators.contains("’")) {
      thousandSeparators += "'";
    }

    if (thousandSeparators.length() > 1) {
      thousandSeparatorsPattern = "[";
    }
    for (int k = 0; k < thousandSeparators.length(); k++) {
      thousandSeparatorsPattern += Pattern.quote(thousandSeparators.substring(k, k + 1))
          + (k + 1 == thousandSeparators.length() ? "" : "|");
    }
    if (thousandSeparators.length() > 1) {
      thousandSeparatorsPattern += "]";
    }
  }

  private Separators setAndOverRuleSeparators(String separatorsConfig) {
    if (separatorsConfig != null) {
      Pattern p = Pattern.compile("((?:" + ALL_SEPARATOR + "|[a-z]{2,2}\\-[A-Z]{2,2})<[ '\\.\\,’]{0,3}\\|.?>)");
      Matcher matcher = p.matcher(separatorsConfig);
      String userLaCo = userLocale.toString().replace("_", "-");
      while (matcher.find()) {
        String match = matcher.group();
        if (match.startsWith(ALL_SEPARATOR) || match.startsWith(userLaCo) && match.endsWith(">")) {
          return setSeparators(match);
        }
      }
    }
    return null;
  }

  private Separators setSeparators(String localMatch) {
    Pattern pSeparators = Pattern.compile("<([ '\\.\\,’]{0,3})\\|(.?)>");
    Matcher mSeparators = pSeparators.matcher(localMatch);
    while (mSeparators.find()) {
      Separators separators = new Separators(mSeparators.group(1));
      separators.decimalSeparator = mSeparators.group(2).equals("") ? null : mSeparators.group(2).charAt(0);
      return separators;
    }
    return null;
  }

  protected void addionalConfigurations(String[] splitEqual) {
  }

  protected void validateTemplate(final DataViolationException dataViolationException) {
    if (dateFormat == null) {
      dataViolationException.addDataViolation(CONF_DATE_FORMAT, "gt.imptemplate.date", null, false);
    } else {
      try {
        new SimpleDateFormat(dateFormat);
      } catch (IllegalArgumentException iae) {
        dataViolationException.addDataViolation(CONF_DATE_FORMAT, "gt.imptemplate.date", null, false);
      }
    }
    if (transactionTypesMap.isEmpty()) {
      dataViolationException.addDataViolation(CONF_TRANSACTION_TYPE, "gt.imptemplate.missing.transactiontype", null,
          false);
    }
  }

  /**
   * Creates the number format with or without thousand and decimal separator.
   *
   * @param locale
   * @return
   */
  private String createNumberRegexByLocale() {
    return "(?:-?(?:(?:[0-9]{1,3}(?:" + thousandSeparatorsPattern + "[0-9]{3})*|\\d+)(?:\\" + decimalSeparator
        + "[0-9]+)?|\\" + decimalSeparator + "[0-9]+))";
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public String getTimeFormat() {
    return timeFormat;
  }

  public char getDecimalSeparator() {
    return decimalSeparator;
  }

  public char getThousandSeparators() {
    return decimalFormatSymbols.getGroupingSeparator();
  }

  public String getThousandSeparatorsPattern() {
    return thousandSeparatorsPattern;
  }

  public Locale getLocale() {
    return userLocale;
  }

  public Map<String, TransactionType> getTransactionTypesMap() {
    return transactionTypesMap;
  }

  public static Map<String, Class<?>> getPropertyDataTypeMap() {
    return propertyDataTypeMap;
  }

  public ImportTransactionTemplate getImportTransactionTemplate() {
    return importTransactionTemplate;
  }

  public EnumSet<ImportKnownOtherFlags> getImportKnownOtherFlagsSet() {
    return importKnownOtherFlagsSet;
  }

  public String getIgnoreTaxOnDivInt() {
    return ignoreTaxOnDivInt;
  }

  private static class Separators {
    public String thousandSeparators;
    public char decimalSeparator;

    public Separators(String thousandSeparators) {
      this.thousandSeparators = thousandSeparators;
    }
  }
}
