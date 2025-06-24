package grafioschtrader.platformimport;

import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import grafiosch.common.EnumHelper;
import grafiosch.common.ValueFormatConverter;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.types.ImportKnownOtherFlags;
import grafioschtrader.types.TransactionType;

/**
 * Abstract base class for configuring and parsing transaction import templates from PDF or CSV text sources.
 * 
 * <p>
 * This class handles configuration parsing, number formatting, date/time patterns, transaction type mappings, and
 * locale-specific formatting rules.
 * </p>
 * 
 * <h3>Template Structure</h3>
 * <p>
 * Templates contain property definitions followed by a configuration section marked by the [END] delimiter.
 * </p>
 * 
 * <h3>Configuration Options</h3>
 * <p>
 * Configuration keys supported in the [END] section:
 * </p>
 * <ul>
 * <li>dateFormat - Date format pattern (e.g., "yyyy-MM-dd")</li>
 * <li>timeFormat - Time format pattern (e.g., "HH:mm:ss")</li>
 * <li>transType - Transaction type mappings: "TRANSACTION_TYPE|word1,word2,word3"</li>
 * <li>overRuleSeparators - Custom number separators (e.g., "All<'|.>")</li>
 * <li>otherFlagOptions - Feature flags separated by "|"</li>
 * <li>ignoreTaxOnDivInt - Transaction type for tax exemption</li>
 * </ul>
 * 
 * <p>
 * This class is not thread-safe. Parsing errors are collected in a DataViolationException.
 * </p>
 */
public abstract class TemplateConfiguration {

  /**
   * Delimiter marking the start of the configuration section in template text. All lines after this marker contain
   * configuration key-value pairs.
   */
  public static final String SECTION_END = "[END]";

  /** Configuration key for tax exemption on dividend/interest transactions. */
  private static final String CONF_IGNORE_TAX_ON_DIV_INT = "ignoreTaxOnDivInt";

  /** Configuration key for transaction type mappings. */
  private static final String CONF_TRANSACTION_TYPE = "transType";

  /** Configuration key for date format pattern. */
  private static final String CONF_DATE_FORMAT = "dateFormat";

  /** Configuration key for time format pattern. */
  private static final String CONF_TIME_FORMAT = "timeFormat";

  /** Configuration key for custom number format separators. */
  private static final String CONF_OVER_RULE_SEPARATORS = "overRuleSeparators";

  /** Configuration key for optional feature flags. */
  private static final String CONF_OTHER_FLAG_OPTIONS = "otherFlagOptions";

  /** Wildcard separator configuration applying to all locales. */
  private static final String ALL_SEPARATOR = "All";

  /** Set of optional feature flags that can modify import behavior. */
  protected EnumSet<ImportKnownOtherFlags> importKnownOtherFlagsSet = EnumSet.noneOf(ImportKnownOtherFlags.class);

  /** The import transaction template being configured. */
  protected ImportTransactionTemplate importTransactionTemplate;

  /** Normalized template text with consistent line endings and whitespace. */
  protected String template;

  /** User's locale for number and date formatting. */
  protected Locale userLocale;

  /** Decimal separator character for number parsing (e.g., '.' or ','). */
  protected char decimalSeparator;

  /** Transaction type text that should be treated as tax-exempt. */
  protected String ignoreTaxOnDivInt;

  /** Regex pattern for matching thousand separators in numbers. */
  protected String thousandSeparatorsPattern = "";

  /** Regex pattern for recognizing numeric values in text. */
  protected String numberTypeRegex;

  /** Regex pattern for recognizing date values in text. */
  protected String dateTypeRegex;

  /** Regex pattern for recognizing time values in text. */
  protected String timeTypeRegex;

  /** Locale-specific formatting symbols for numbers. */
  protected DecimalFormatSymbols decimalFormatSymbols;

  /** Static mapping of property names to their expected Java data types. */
  protected static Map<String, Class<?>> propertyDataTypeMap = ValueFormatConverter
      .getDataTypeOfPropertiesByBean(new ImportProperties(null, null, null));

  /** Date format pattern string (e.g., "yyyy-MM-dd"). */
  protected String dateFormat;

  /** Time format pattern string (e.g., "HH:mm:ss"). */
  protected String timeFormat;

  /**
   * Maps transaction type text from trading platform documents to internal transaction types. Examples: "Purchase" ->
   * ACCUMULATE, "Sale" -> REDUCE, "Dividend" -> DIVIDEND
   */
  protected Map<String, TransactionType> transactionTypesMap = new HashMap<>();

  /**
   * Abstract method that subclasses must implement to read template-specific properties. This method is called after
   * the configuration section has been parsed.
   * 
   * @param templateLines          Array of template lines, already normalized
   * @param startRowConfig         Index where the configuration section begins (after [END])
   * @param dataViolationException Exception object to collect any validation errors
   */
  protected abstract void readTemplateProperties(String[] templateLines, int startRowConfig,
      DataViolationException dataViolationException);

  /**
   * Constructs a new template configuration with the specified import template and user locale.
   * 
   * @param importTransactionTemplate The import template containing the raw template text
   * @param userLocale                The user's locale for formatting numbers and dates
   */
  protected TemplateConfiguration(ImportTransactionTemplate importTransactionTemplate, Locale userLocale) {
    this.importTransactionTemplate = importTransactionTemplate;
    this.userLocale = userLocale;
  }

  /**
   * Parses the template and immediately throws an exception if any validation errors are found.
   * 
   * @param forSaving Whether this parsing is for saving the template (enables additional validation)
   * @throws DataViolationException if any validation errors are found during parsing
   */
  public void parseTemplateAndThrowError(boolean forSaving) {
    final DataViolationException dataViolationException = parseTemplat(forSaving);
    if (dataViolationException.hasErrors()) {
      throw dataViolationException;
    }
  }

  /**
   * Parses an import template for financial transaction documents and extracts field mappings and configuration.
   * Handles the complete template processing workflow: normalization, configuration parsing, field mapping setup, and
   * validation.
   * 
   * @param forSaving Whether this parsing is for template validation before saving
   * @return A DataViolationException containing any validation errors found (may be empty)
   */
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
   * Reads and parses the configuration section of the template (after the [END] marker). Processes configuration
   * key-value pairs for date/time formats, transaction types, number separators, and feature flags.
   * 
   * @param templateLines Array of all template lines
   * @return Index of the line containing [END], or -1 if not found
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

  /**
   * Processes optional feature flags that modify transaction import behavior. Flags enable special handling for
   * different trading platform quirks and document formats.
   * 
   * <p>
   * Available flags include:
   * </p>
   * <ul>
   * <li>BOND_QUOTATION_CORRECTION - Adjusts bond interest rates for payment frequency</li>
   * <li>NO_TAX_ON_DIVIDEND_INTEREST - Marks dividends as tax-exempt</li>
   * <li>BASE_CURRENCY_MAYBE_INVERSE - Handles reverse currency pair scenarios</li>
   * <li>CASH_SECURITY_CURRENCY_MISMATCH_BUT_EXCHANGE_RATE - Auto-calculates missing exchange rates</li>
   * </ul>
   * 
   * @param flagOptionsStr Pipe-separated list of flag names
   */
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
   * Creates number format separator patterns for parsing monetary amounts from trading platform documents. Different
   * regions and platforms use varying thousands/decimal separators, requiring flexible parsing.
   * 
   * <p>
   * Supports configurations like:
   * </p>
   * <ul>
   * <li>"All<'|.>" - Use apostrophe thousands, period decimal for all locales</li>
   * <li>"de-CH<'|.>" - Swiss format (apostrophe thousands, period decimal)</li>
   * <li>"de-DE<.|,>" - German format (period thousands, comma decimal)</li>
   * </ul>
   * 
   * @param separatorsConfig Separator configuration string, or null to use locale defaults
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

  /**
   * Parses separator configuration and applies overrides if they match the current locale.
   * 
   * @param separatorsConfig Separator configuration string (e.g., "de-CH<'|.>")
   * @return Separators object with parsed values, or null if no match found
   */
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

  /**
   * Extracts separator values from a locale-specific configuration match.
   * 
   * @param localMatch A matched separator configuration (e.g., "de-CH<'|.>")
   * @return Separators object with extracted thousand and decimal separators
   */
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

  /**
   * Hook method for subclasses to handle additional configuration options. Called during configuration parsing for
   * unrecognized configuration keys.
   * 
   * @param splitEqual Array containing the configuration key and value (split on "=")
   */
  protected void addionalConfigurations(String[] splitEqual) {
    // Default implementation does nothing - subclasses can override
  }

  /**
   * Validates that the import template has the minimum required configuration for processing financial transaction
   * documents. Ensures date formats are valid and transaction type mappings are defined.
   * 
   * @param dataViolationException Exception object to collect validation errors
   */
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

  /**
   * Private inner class for holding separator configuration values.
   */
  private static class Separators {
    /** Thousand separator characters as a string. */
    public String thousandSeparators;
    
    /** Decimal separator character. */
    public char decimalSeparator;

    public Separators(String thousandSeparators) {
      this.thousandSeparators = thousandSeparators;
    }
  }
}
