package grafioschtrader.platformimport.pdf;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafiosch.common.ValueFormatConverter;
import grafioschtrader.platformimport.ImportProperties;

/**
 * Configuration class that defines how to locate and extract a single property value from PDF form text.
 * 
 * <p>This class encapsulates all the configuration needed to identify, match, and extract a specific property
 * from structured PDF text. It supports sophisticated matching strategies including line-based positioning,
 * regex pattern matching, and context-aware value extraction. Each instance represents one property to be
 * parsed from a PDF template, with flexible configuration options for handling various PDF document formats.</p>
 * 
 * <h3>Matching Strategies</h3>
 * <p>The class supports multiple matching approaches that can be combined for robust property identification:</p>
 * <ul>
 *   <li><b>Line-based matching:</b> Uses first words from previous, same, or next lines for context</li>
 *   <li><b>Regex pattern matching:</b> Applies compiled regex patterns for precise value extraction</li>
 *   <li><b>Position-based extraction:</b> Uses word position within lines when other selectors are unavailable</li>
 *   <li><b>Context concatenation:</b> Combines multiple regex components for complex pattern matching</li>
 * </ul>
 * 
 */
public class PropertyWithOptionsConfiguration {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  /** Name of the field in ImportProperties where the extracted value will be stored */
  public String fieldName;
  
  /** Java data type for the property value, used for proper type conversion */
  public Class<?> dataType;
  
  /** Flag indicating if this property represents the first column of a table structure */
  public boolean firstColumnTable;

  /** Regex pattern to match text before the value (previous word context) */
  public String prevRegex;
  
  /** Regex pattern to match text after the value (next word context) */
  public String nextRegex;

  /** String to concatenate before the value pattern for complex matching */
  public String prevConcatenatedString;
  
  /** String to concatenate after the value pattern for complex matching */
  public String nextConcatenatedString;

  /** Compiled regex pattern used for actual value matching and extraction */
  public Pattern regexPattern;

  /** Template first word from the previous line, used for line-based context matching */
  public String startPL;
  
  /** Template first word from the same line, used for line-based context matching */
  public String startSL;

  /** Template first word from the next line, used for line-based context matching */
  public String startNL;

  /** Flag indicating if line matching should be restricted to single occurrence */
  public boolean startLineSingleCount;

  /** Pattern for parsing line start options in bracket notation [option1|option2] */
  private static Pattern startLineOptionsPattern = Pattern.compile("^\\[(.*)\\]");

  /** Base regex pattern for matching the value type (e.g., numbers, dates, text) */
  private String valueTypeRegex;

  /** Line number in the template where this property is located */
  private int lineNo;

  /** Word position within the line (space-separated), zero-based index */
  int propteryColumn;

  /** Flag indicating if this property is optional and can be missing */
  public boolean optional;

  /**
   * Creates a new property configuration with basic identification and positioning information.
   * 
   * <p>This constructor establishes the fundamental characteristics of a property including its field name,
   * data type, value pattern, and position within the template structure. Additional configuration through
   * setter methods and createRegex() call is required before the property can be used for matching.</p>
   * 
   * @param fieldName Name of the field in ImportProperties where extracted values will be stored
   * @param dataType Java class representing the expected data type for type conversion
   * @param valueTypeRegex Base regex pattern for matching the specific value type
   * @param lineNo Line number in the template where this property appears
   * @param propteryColumn Word position within the line (space-separated words)
   */
  public PropertyWithOptionsConfiguration(String fieldName, Class<?> dataType, String valueTypeRegex, int lineNo,
      int propteryColumn) {
    this.fieldName = fieldName;
    this.dataType = dataType;
    this.valueTypeRegex = valueTypeRegex;
    this.lineNo = lineNo;
    this.propteryColumn = propteryColumn;
  }

  /**
   * Builds the final regex pattern by combining all configured regex components and context strings.
   * 
   * <p>This method must be called after all regex components have been configured. It combines the base
   * value pattern with any concatenated strings and additional regex patterns to create the complete
   * matching pattern. The resulting compiled pattern is case-insensitive and ready for use in matching operations.</p>
   */
  public void createRegex() {
    String regex = valueTypeRegex;
    regex = this.createPrevNextConcatenatedRegex(regex);
    regex = this.createPrevNextRegex(regex);
    log.info("{} PL: {}  SL: {}  NL: {}  Regex: {}", fieldName, startPL, startSL, startNL, regex);
    regexPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
  }

  /**
   * Adds concatenated strings to the beginning and end of the regex pattern.
   * 
   * @param regex The base regex pattern to modify
   * @return The regex pattern with concatenated strings applied
   */
  private String createPrevNextConcatenatedRegex(String regex) {
    if (prevConcatenatedString != null) {
      regex = prevConcatenatedString + regex;
    }
    if (nextConcatenatedString != null) {
      regex = regex + nextConcatenatedString;
    }
    return regex;
  }

  /**
   * Adds additional regex patterns to the beginning and end of the base pattern.
   * 
   * @param regex The regex pattern to modify
   * @return The regex pattern with additional patterns applied
   */
  private String createPrevNextRegex(String regex) {
    if (prevRegex != null) {
      regex = prevRegex + regex;
    }
    if (nextRegex != null) {
      regex = regex + nextRegex;
    }
    return regex;
  }
  
  /**
   * Attempts to match this property against form input lines and extract the value if found.
   * 
   * <p>This method implements the core matching logic, trying different strategies based on the configured
   * line selectors and regex patterns. It first attempts line-based matching using previous, same, or next
   * line context, then falls back to direct regex matching if no line selectors are configured.</p>
   * 
   * <h4>Matching Strategy Selection</h4>
   * <p>When line selectors (startPL, startSL, startNL) are configured, the method attempts context-based
   * matching. Otherwise, it performs direct regex matching against the current line. This flexible approach
   * handles both structured form layouts and free-form text scenarios.</p>
   * 
   * @param formInputLines Complete array of text lines from the PDF form
   * @param startRow Current row index being processed for matching
   * @param importProperties Container where extracted values will be stored, or null for validation-only matching
   * @param valueFormatConverter Converter for proper data type formatting and locale handling
   * @return true if the property was successfully matched and extracted, false otherwise
   * @throws Exception if value conversion fails or regex matching encounters errors
   */
  public boolean matchePropertyAndSetValue(String[] formInputLines, int startRow, ImportProperties importProperties,
      ValueFormatConverter valueFormatConverter) throws Exception {
    if (startPL != null || startSL != null || startNL != null) {
      return matchLineBeginningAndRegex(startPL, formInputLines, startRow, -1, importProperties, valueFormatConverter)
          || matchLineBeginningAndRegex(startSL, formInputLines, startRow, 0, importProperties, valueFormatConverter)
          || matchLineBeginningAndRegex(startNL, formInputLines, startRow, 1, importProperties, valueFormatConverter);
    } else {
      // Only regex
      return matchRegexAndSetValue(formInputLines[startRow], importProperties, valueFormatConverter);
    }
  }

  
  /**
   * Performs line-based matching by checking if a line starts with expected text and then applying regex matching.
   * 
   * <p>This method implements the line-based matching strategy where properties are identified by context from
   * surrounding lines. It supports both single string matching and multiple option matching using bracket notation
   * [option1|option2]. The method ensures proper bounds checking for line access.</p>
   * 
   * @param startStringTemplate The template string to match at line beginning, may include option brackets
   * @param formInputLines Array of form input text lines
   * @param startRow Current row being processed
   * @param minusPlus Line offset (-1 for previous, 0 for same, 1 for next line)
   * @param importProperties Container for extracted values
   * @param valueFormatConverter Converter for value formatting
   * @return true if line beginning matches and subsequent regex matching succeeds
   * @throws Exception if value processing fails
   */
  private boolean matchLineBeginningAndRegex(String startStringTemplate, String[] formInputLines, int startRow,
      int minusPlus, ImportProperties importProperties, ValueFormatConverter valueFormatConverter) throws Exception {
    boolean matches = false;
    if (startStringTemplate != null && ((minusPlus == 1 && startRow + minusPlus < formInputLines.length)
        || (minusPlus == 0) || (minusPlus == -1 && startRow > 0))) {

      String linePLSLNL = formInputLines[startRow + minusPlus];
      Matcher startLineMatcher = startLineOptionsPattern.matcher(startStringTemplate);
      String[] rowSplitSpace = linePLSLNL.split("\\s+", 2);
      if (startLineMatcher.find()) {
        String[] startLineOptions = startStringTemplate.substring(1, startStringTemplate.length() - 1)
            .split(Pattern.quote("|"));
        for (int i = 0; i < startLineOptions.length && !matches; i++) {
          if (linePLSLNL.length() > startLineOptions[i].length()) {
            String target = linePLSLNL.substring(0, startLineOptions[i].length());
            matches = lineBeginningContains(startLineOptions[i], formInputLines, startRow, importProperties,
                valueFormatConverter, target, rowSplitSpace);
          }
        }
      } else {
        matches = lineBeginningContains(startStringTemplate, formInputLines, startRow, importProperties,
            valueFormatConverter, rowSplitSpace[0], rowSplitSpace);
      }
    }
    return matches;
  }

  /**
   * Checks if a line begins with expected text and performs appropriate value extraction strategy.
   * 
   * <p>This method determines the extraction strategy based on available selectors. When regex selectors are
   * available, it performs full line regex matching. When only line beginning selectors are configured,
   * it uses position-based extraction based on word count in the template.</p>
   * 
   * @param startString Expected string at line beginning
   * @param formInputLines Array of form input lines
   * @param startRow Current row for value extraction
   * @param importProperties Container for extracted values
   * @param valueFormatConverter Converter for value formatting
   * @param target Actual string found at line beginning
   * @param rowSplitSpace Pre-split words from the line
   * @return true if line beginning matches and value extraction succeeds
   * @throws Exception if value processing fails
   */
  private boolean lineBeginningContains(String startString, String[] formInputLines, int startRow,
      ImportProperties importProperties, ValueFormatConverter valueFormatConverter, String target,
      String[] rowSplitSpace) throws Exception {

    if (rowSplitSpace.length > 0 && startString.equalsIgnoreCase(target)) {
      if (hasRegexSelectorOtherThanLineBeginning() || !startLineSingleCount) {
        return matchRegexAndSetValue(formInputLines[startRow], importProperties, valueFormatConverter);
      } else {
        // The property has no other selector than PL|SL|NL. In this case the position
        // of FIELD value
        // is estimated by the templates word count.
        rowSplitSpace = formInputLines[startRow].split("\\s+");
        return (rowSplitSpace.length > propteryColumn)
            ? matchRegexAndSetValue(rowSplitSpace[propteryColumn], importProperties, valueFormatConverter)
            : false;
      }
    }
    return false;
  }

  /**
   * Performs regex matching against input string and extracts the first capture group as the property value.
   * 
   * <p>This method applies the compiled regex pattern to the input string and extracts the value from the first
   * capture group. If ImportProperties container is provided, the extracted value is converted to the appropriate
   * data type and stored. The method gracefully handles conversion failures by returning false.</p>
   * 
   * @param formInputString Input string to match against the regex pattern
   * @param importProperties Container for storing extracted values, or null for validation-only matching
   * @param valueFormatConverter Converter for proper data type conversion and locale handling
   * @return true if regex matches and value extraction/conversion succeeds, false otherwise
   * @throws Exception if regex processing encounters errors
   */
  private boolean matchRegexAndSetValue(String formInputString, ImportProperties importProperties,
      ValueFormatConverter valueFormatConverter) throws Exception {
    Matcher matcher = regexPattern.matcher(formInputString);
    if (matcher.find()) {
      if (importProperties != null) {
        try {
          valueFormatConverter.convertAndSetValue(importProperties, fieldName, matcher.group(1), dataType);
        } catch (Exception e) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * Checks if the property has regex selectors beyond simple line beginning matching.
   * 
   * @return true if additional regex components are configured
   */
  private boolean hasRegexSelectorOtherThanLineBeginning() {
    return prevRegex != null || nextRegex != null || prevConcatenatedString != null || nextConcatenatedString != null;

  }

  public int getLineNo() {
    return lineNo;
  }

  public String getFieldName() {
    return fieldName;
  }

  public boolean isFirstColumnTable() {
    return firstColumnTable;
  }

  public int getPropteryColumn() {
    return propteryColumn;
  }

  public int getPropertyColumnIncludeNext() {
    return propteryColumn + (nextRegex == null ? 0 : 1);
  }

  public int getPropertyColumnIncludePrevious() {
    return propteryColumn - (prevRegex == null ? 0 : 1);
  }

  public String getRegexPattern() {
    return this.regexPattern.pattern();
  }

  public boolean isOptional() {
    return optional;
  }

  public Class<?> getDataType() {
    return dataType;
  }

  @Override
  public String toString() {
    return "PropertyWithOptionsConfiguration [fieldName=" + fieldName + ", firstColumnTable=" + firstColumnTable
        + ", prevRegex=" + prevRegex + ", nextRegex=" + nextRegex + ", prevConcatenatedString=" + prevConcatenatedString
        + ", nextConcatenatedString=" + nextConcatenatedString + ", startPL=" + startPL + ", startSL=" + startSL
        + ", startNL=" + startNL + ", valueTypeRegex=" + valueTypeRegex + ", lineNo=" + lineNo + ", propteryColumn="
        + propteryColumn + "]";
  }

}
