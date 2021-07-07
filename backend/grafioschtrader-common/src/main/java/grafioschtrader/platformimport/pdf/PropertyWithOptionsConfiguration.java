package grafioschtrader.platformimport.pdf;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grafioschtrader.common.ValueFormatConverter;
import grafioschtrader.platformimport.ImportProperties;

/**
 * For each property to parse a instance of this class exists.
 *
 * @author Hugo Graf
 *
 */
public class PropertyWithOptionsConfiguration {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  public String fieldName;
  public Class<?> dataType;
  public boolean firstColumnTable;

  public String prevRegex;
  public String nextRegex;

  public String prevConcatenatedString;
  public String nextConcatenatedString;

  public Pattern regexPattern;

  /**
   * Contains the template first word of the previous line.
   */
  public String startPL;
  /**
   * Contains the template first word of the same line.
   */
  public String startSL;
  public String startNL;

  private static Pattern startLineOptionsPattern = Pattern.compile("^\\[(.*)\\]");

  /**
   * Value pattern which is searched
   */
  private String valueTypeRegex;

  /**
   * The line number on the template
   */
  private int lineNo;

  /**
   * The counter of the word in the line, the space separates a word.
   */
  int propteryColumn;

  public boolean optional;

  public PropertyWithOptionsConfiguration(String fieldName, Class<?> dataType, String valueTypeRegex, int lineNo,
      int propteryColumn) {
    this.fieldName = fieldName;
    this.dataType = dataType;
    this.valueTypeRegex = valueTypeRegex;
    this.lineNo = lineNo;
    this.propteryColumn = propteryColumn;
  }

  /**
   * At the end of regex setting, this method must be called for every property.
   *
   */
  public void createRegex() {
    String regex = valueTypeRegex;
    regex = this.createPrevNextConcatenatedRegex(regex);
    regex = this.createPrevNextRegex(regex);
    log.info("{} PL: {}  SL: {}  NL: {}  Regex: {}", fieldName, startPL, startSL, startNL, regex);
    regexPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
  }

  private String createPrevNextConcatenatedRegex(String regex) {
    if (prevConcatenatedString != null) {
      regex = prevConcatenatedString + regex;
    }
    if (nextConcatenatedString != null) {
      regex = regex + nextConcatenatedString;
    }
    return regex;

  }

  private String createPrevNextRegex(String regex) {
    if (prevRegex != null) {
      regex = prevRegex + regex;
    }
    if (nextRegex != null) {
      regex = regex + nextRegex;
    }
    return regex;
  }

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

  private boolean lineBeginningContains(String startString, String[] formInputLines, int startRow,
      ImportProperties importProperties, ValueFormatConverter valueFormatConverter, String target,
      String[] rowSplitSpace) throws Exception {

    if (rowSplitSpace.length > 0 && startString.equalsIgnoreCase(target)) {
      if (hasRegexSelectorOtherThanLineBeginning()) {
        return matchRegexAndSetValue(formInputLines[startRow], importProperties, valueFormatConverter);
      } else {
        // The property has no other selector than PL|SL|NL. In this case the position
        // of FIELD value
        // is estimated by the templates word count.
        rowSplitSpace = formInputLines[startRow].split("\\s+");
        return rowSplitSpace.length > propteryColumn
            ? matchRegexAndSetValue(rowSplitSpace[propteryColumn], importProperties, valueFormatConverter)
            : false;
      }
    }
    return false;
  }

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
