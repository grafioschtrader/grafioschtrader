package grafioschtrader.platformimport.pdf;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.TemplateConfiguration;

/**
 * PDF-specific template configuration for parsing financial transaction data from PDF documents converted to text.
 * 
 * <p>This class handles PDF documents that contain a single transaction per document. It uses an anchor point 
 * system to locate transaction fields within the PDF text structure. Fields are defined with anchor points 
 * that reference static text elements to precisely locate variable data.</p>
 * 
 * <h3>Anchor Point System</h3>
 * <p>Transaction values are located using anchor points that reference static text:</p>
 * <ul>
 *   <li><b>P</b> - Previous word (also works at line beginning)</li>
 *   <li><b>N</b> - Next word (also works at line end)</li>
 *   <li><b>Pc</b> - Previous concatenated (no space between anchor and value)</li>
 *   <li><b>Nc</b> - Next concatenated (no space between anchor and value)</li>
 *   <li><b>SL</b> - Start of same line</li>
 *   <li><b>PL</b> - Start of previous line</li>
 *   <li><b>NL</b> - Start of next line</li>
 * </ul>
 * 
 * <h3>Field Positioning</h3>
 * <p>Fields are defined using the format: <code>{fieldName|anchor1|anchor2|...}</code></p>
 * <p>Additional field options:</p>
 * <ul>
 *   <li><b>R</b> - Repeatable (for table rows with multiple executions)</li>
 *   <li><b>O</b> - Optional (field may not be present in all documents)</li>
 * </ul>
 * 
 * <h3>Regular Expression Support</h3>
 * <p>Anchor points can use regular expressions for flexible matching:</p>
 * <ul>
 *   <li>Non-capture groups: <code>(?:CREDIT|DEBIT)</code></li>
 *   <li>Multiple word options: <code>[Purchase|Buy|Kauf]</code></li>
 * </ul>
 * 
 * <h3>PDF Template Example</h3>
 * <pre>
 * Transaction Date: {datetime|P|N}
 * (?:Purchase|Sale) Order {transType|P|N}
 * ISIN: {isin|P} Security Name
 * Quantity: {units|P} at {quotation|P|N}
 * Total Amount: {ta|P|N}
 * [END]
 * dateFormat=dd.MM.yyyy
 * transType=ACCUMULATE|Purchase
 * transType=REDUCE|Sale
 * </pre>
 * 
 * <h3>Declarative Approach</h3>
 * <p>This class follows a declarative approach where templates define field positions 
 * rather than imperative code. This enables handling diverse trading platform PDF 
 * formats without custom implementations for each platform.</p>
 */
public class TemplateConfigurationPDFasTXT extends TemplateConfiguration {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /** Pattern to match field definitions like {transType|P|N}. */
  private static final Pattern templatePropertyMatcher = Pattern.compile("(\\{[A-Z,a-z]{2}\\w*[\\|A-Za-z]*\\})");

  /** Regex pattern for matching any non-whitespace string values. */
  private String stringTypeRegex = "[^\\s]*";

  /** List of configured properties with their anchor points and options. */
  private List<PropertyWithOptionsConfiguration> propertyOptionsList = new ArrayList<>();

  /** Template lines split for processing. */
  private String[] templateLines;

  /**
   * Creates a new PDF template configuration.
   * 
   * @param importTransactionTemplate The template containing PDF field definitions
   * @param userLocale                User's locale for number and date formatting
   */
  public TemplateConfigurationPDFasTXT(ImportTransactionTemplate importTransactionTemplate, Locale userLocale) {
    super(importTransactionTemplate, userLocale);
  }

  /**
   * Processes PDF template lines to extract field definitions with anchor points. Each line before [END] is analyzed
   * for field definitions like {datetime|P|N}.
   * 
   * @param templateLines          Array of template lines
   * @param startRowConfig         Index where configuration section begins
   * @param dataViolationException Exception to collect validation errors
   */
  @Override
  protected void readTemplateProperties(String[] templateLines, int startRowConfig,
      DataViolationException dataViolationException) {

    log.info("Import template: {}", this.importTransactionTemplate.getTemplatePurpose());
    this.templateLines = templateLines;
    for (int i = 0; i <= startRowConfig - 1; i++) {
      createRowPropertiesPattern(i, startRowConfig - 1, dataViolationException);
    }
  }

  /**
   * Processes a single template line to extract field definitions and create property configurations. Builds anchor
   * point patterns and validates field positioning within the line.
   * 
   * @param startRow               Current line index being processed
   * @param lastLine               Index of the last template line
   * @param dataViolationException Exception to collect validation errors
   */
  private void createRowPropertiesPattern(int startRow, int lastLine, DataViolationException dataViolationException) {
    String[] rowSplitSpace = templateLines[startRow].split("\\s+(?![^\\[]*\\])");

    Matcher matcher = templatePropertyMatcher.matcher(templateLines[startRow]);
    while (matcher.find()) {
      // Every property with Options like {date|P|N}
      String propertyWithOptionsAndBraces = matcher.group(1);

      String propertyOptionsString = propertyWithOptionsAndBraces.substring(1,
          propertyWithOptionsAndBraces.length() - 1);

      String[] propertyOptionsSplit = propertyOptionsString.split(Pattern.quote("|"));
      String fieldName = propertyOptionsSplit[0];

      int propteryColumn = this.getColumnOfPropertyInRow(rowSplitSpace, propertyOptionsString);

      Class<?> dataType = propertyDataTypeMap.get(fieldName);
      PropertyWithOptionsConfiguration propertyWithOptions = new PropertyWithOptionsConfiguration(fieldName, dataType,
          addBraces(getValuePattern(dataType)), startRow, propteryColumn);
      propertyOptionsList.add(propertyWithOptions);

      for (int i = 1; i < propertyOptionsSplit.length; i++) {
        switch (propertyOptionsSplit[i]) {
        case "P":
          propertyWithOptions.prevRegex = getPrevStringPattern(rowSplitSpace, propteryColumn);
          break;
        case "N":
          propertyWithOptions.nextRegex = getNextStringPattern(rowSplitSpace, propteryColumn);
          break;
        case "Pc":
          propertyWithOptions.prevConcatenatedString = getPrevConcatenatedString(rowSplitSpace, propteryColumn,
              propertyWithOptionsAndBraces);
          break;
        case "Nc":
          propertyWithOptions.nextConcatenatedString = getNextConcatenatedString(rowSplitSpace, propteryColumn,
              propertyWithOptionsAndBraces);
          break;
        case "PL":
        case "PLI":
          if (startRow > 0) {
            String[] rowSplitSpacePL = templateLines[startRow - 1].split("\\s+", 2);
            propertyWithOptions.startPL = setFirstWord(rowSplitSpacePL, propertyWithOptionsAndBraces,
                dataViolationException, propertyOptionsSplit[i], 1);
            propertyWithOptions.startLineSingleCount = propertyOptionsSplit[i].equals("PLI") ? false : true;
          } else {
            dataViolationException.addDataViolation(propertyWithOptionsAndBraces, "gt.imptemplate.pl.row", null, false);
          }
          break;
        case "SL":
        case "SLI":
          propertyWithOptions.startSL = setFirstWord(rowSplitSpace, propertyWithOptionsAndBraces,
              dataViolationException, propertyOptionsSplit[i], 2);
          propertyWithOptions.startLineSingleCount = propertyOptionsSplit[i].equals("SLI") ? false : true;
          break;
        case "NL":
        case "NLI":
          if (startRow < lastLine) {
            String[] rowSplitSpaceNL = templateLines[startRow + 1].split("\\s+", 2);
            propertyWithOptions.startNL = setFirstWord(rowSplitSpaceNL, propertyWithOptionsAndBraces,
                dataViolationException, propertyOptionsSplit[i], 1);
            propertyWithOptions.startLineSingleCount = propertyOptionsSplit[i].equals("NLI") ? false : true;
          } else {
            dataViolationException.addDataViolation(propertyWithOptionsAndBraces, "gt.imptemplate.nl.row", null, false);
          }
          break;
        case "R":
          propertyWithOptions.firstColumnTable = true;
          break;
        case "O":
          propertyWithOptions.optional = true;
          break;
        default:
          dataViolationException.addDataViolation(propertyWithOptionsAndBraces, "gt.imptemplate.anchor.config",
              propertyOptionsSplit[i], false);
        }
      }
      propertyWithOptions.createRegex();
    }
  }

  /**
   * Validates and extracts the first word from a line for anchor point configuration.
   * 
   * @param rowSplitSpace                Words from the line
   * @param propertyWithOptionsAndBraces Full property definition for error reporting
   * @param dataViolationException       Exception to collect validation errors
   * @param propertyOption               The anchor option being processed
   * @param requiredWords                Minimum number of words required in the line
   * @return First word of the line, or null if validation fails
   */
  private String setFirstWord(String[] rowSplitSpace, String propertyWithOptionsAndBraces,
      DataViolationException dataViolationException, String propertyOption, int requiredWords) {
    if (rowSplitSpace.length >= requiredWords) {
      return rowSplitSpace[0];
    } else {
      dataViolationException.addDataViolation(propertyWithOptionsAndBraces, "gt.imptemplate.line.beginning",
          propertyOption, false);
    }
    return null;
  }

  /**
   * Returns the appropriate regex pattern for a field based on its data type.
   * 
   * @param dataType Java class type of the field
   * @return Regex pattern string for matching the data type
   */
  private String getValuePattern(Class<?> dataType) {
    String typeRegext = null;
    if (String.class == dataType) {
      typeRegext = stringTypeRegex;
    } else if (Double.class == dataType || Integer.class == dataType) {
      typeRegext = numberTypeRegex;
    } else if (Date.class == dataType) {
      typeRegext = dateTypeRegex;
    } else if (LocalTime.class == dataType) {
      typeRegext = timeTypeRegex;
    }

    return typeRegext;
  }

  /**
   * Finds the column index of a property within a template line.
   * 
   * @param rowSplitSpace         Words from the template line
   * @param propertyOptionsString The property definition to locate
   * @return Column index, or -1 if not found
   */
  private int getColumnOfPropertyInRow(String[] rowSplitSpace, String propertyOptionsString) {
    for (int i = 0; i < rowSplitSpace.length; i++) {
      if (rowSplitSpace[i].contains(propertyOptionsString)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Extracts the prefix text that comes before a field in the same word. Used for concatenated anchor points (Pc
   * option).
   * 
   * @param rowSplitSpace                Words from the template line
   * @param propertyColum                Column index of the property
   * @param propertyWithOptionsAndBraces Full property definition
   * @return Quoted regex pattern for the prefix text
   */
  private String getPrevConcatenatedString(String[] rowSplitSpace, int propertyColum,
      String propertyWithOptionsAndBraces) {
    String fullWord = rowSplitSpace[propertyColum];
    return Pattern.quote(fullWord.substring(0, fullWord.indexOf(propertyWithOptionsAndBraces)));
  }

  /**
   * Extracts the suffix text that comes after a field in the same word. Used for concatenated anchor points (Nc
   * option).
   * 
   * @param rowSplitSpace                Words from the template line
   * @param propertyColum                Column index of the property
   * @param propertyWithOptionsAndBraces Full property definition
   * @return Quoted regex pattern for the suffix text
   */
  private String getNextConcatenatedString(String[] rowSplitSpace, int propertyColum,
      String propertyWithOptionsAndBraces) {
    String fullWord = rowSplitSpace[propertyColum];
    return Pattern.quote(
        fullWord.substring(fullWord.indexOf(propertyWithOptionsAndBraces) + propertyWithOptionsAndBraces.length()));
  }

  /**
   * Creates regex pattern for the word that precedes a field. Handles line beginning and non-capture group patterns.
   * 
   * @param rowSplitSpace Words from the template line
   * @param propertyColum Column index of the property
   * @return Regex pattern for previous word anchor
   */
  private String getPrevStringPattern(String[] rowSplitSpace, int propertyColum) {
    if (propertyColum == 0) {
      // Property is at the beginning of the line
      return "^";
    } else {
      if (isNonCaptureGroup(rowSplitSpace, propertyColum, -1)) {
        return rowSplitSpace[propertyColum - 1] + "\\s+";
      } else {
        return Pattern.quote(rowSplitSpace[propertyColum - 1]) + "\\s+";
      }
    }
  }

  /**
   * Creates regex pattern for the word that follows a field. Handles line ending and non-capture group patterns.
   * 
   * @param rowSplitSpace Words from the template line
   * @param propertyColum Column index of the property
   * @return Regex pattern for next word anchor
   */
  private String getNextStringPattern(String[] rowSplitSpace, int propertyColum) {
    if (propertyColum == rowSplitSpace.length - 1) {
      // Property is at the of the line
      return "$";
    } else {
      if (isNonCaptureGroup(rowSplitSpace, propertyColum, 1)) {
        return "\\s+" + rowSplitSpace[propertyColum + 1];
      } else {
        return "\\s+" + Pattern.quote(rowSplitSpace[propertyColum + 1]);
      }
    }
  }

  /**
   * Checks if a word is a non-capturing regex group pattern.
   * 
   * @param rowSplitSpace Words from the template line
   * @param propertyColum Base column index
   * @param addValue      Offset to check (typically -1 or +1)
   * @return true if the word is a non-capture group like (?:option1|option2)
   */
  private boolean isNonCaptureGroup(String[] rowSplitSpace, int propertyColum, int addValue) {
    return rowSplitSpace[propertyColum + addValue].startsWith("(?:")
        && rowSplitSpace[propertyColum + addValue].endsWith(")");
  }

  /**
   * Wraps a regex pattern in capturing parentheses.
   * 
   * @param regex The regex pattern to wrap
   * @return Pattern wrapped in parentheses for capture group
   */
  private String addBraces(String regex) {
    return "(" + regex + ")";
  }

  public List<PropertyWithOptionsConfiguration> getPropertyWithOptionsConfiguration() {
    return this.propertyOptionsList;
  }

  /**
   * Returns the number of columns (words) in the template line containing a property.
   * 
   * @param property The property configuration
   * @return Number of space-separated words in the line
   */
  public int getColumnNoOnProperty(PropertyWithOptionsConfiguration property) {
    return templateLines[property.getLineNo()].split("\\s+").length;
  }

  @Override
  public ImportTransactionTemplate getImportTransactionTemplate() {
    return importTransactionTemplate;
  }

}
