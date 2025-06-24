package grafioschtrader.platformimport.pdf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import grafioschtrader.entities.ImportTransactionPosFailed;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.FailedParsedTemplateState;
import grafioschtrader.platformimport.ImportProperties;

/**
 * Parses PDF text input against multiple transaction import templates simultaneously to extract financial transaction data.
 * 
 * <p>This class attempts to match PDF document text against all available templates for a trading platform,
 * determining which template best fits the document structure. It handles both required and optional fields,
 * and resolves conflicts when multiple templates match the same document.</p>
 * 
 * <h3>Multi-Template Matching</h3>
 * <p>The parser tries all templates simultaneously rather than sequentially, which enables:</p>
 * <ul>
 *   <li>Automatic template selection based on document content</li>
 *   <li>Fallback to alternative templates if primary template fails</li>
 *   <li>Conflict resolution using template validity dates</li>
 *   <li>Detailed error reporting showing how far each template progressed</li>
 * </ul>
 * 
 * <h3>Two-Phase Parsing</h3>
 * <p>Parsing occurs in two phases:</p>
 * <ol>
 *   <li><b>Required Properties</b> - All mandatory fields must match for template to be considered</li>
 *   <li><b>Optional Properties</b> - Additional fields are parsed to complete transaction data</li>
 * </ol>
 * 
 * <h3>Template Selection Logic</h3>
 * <p>When multiple templates match the same document:</p>
 * <ul>
 *   <li>Templates with validity dates before the transaction date are considered</li>
 *   <li>The most recent valid template is selected</li>
 *   <li>This handles document format changes over time</li>
 * </ul>
 *
 * <h3>Error Handling</h3>
 * <p>When parsing fails, the class provides detailed diagnostic information showing:</p>
 * <ul>
 *   <li>Which templates were attempted</li>
 *   <li>The last successfully matched field for each template</li>
 *   <li>Template purposes and validity dates for troubleshooting</li>
 * </ul>
 * 
 * <h3>Text Preprocessing</h3>
 * <p>Input text is automatically normalized by removing empty lines, standardizing
 * line endings, and collapsing multiple spaces to ensure consistent parsing.</p>
 */
public class ParseFormInputPDFasTXT {

  /** Pattern for removing empty lines from PDF text input. */
  private static final String REMOVE_EMPTY_LINE_PATTERN = "(?m)^\\s*$[\n\r]{1,}";

  /** Original PDF text input to be parsed. */
  private String inputString;
  
  /** Map of available templates and their configurations for this parsing session. */
  private Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap;
  
  /** Normalized input text split into individual lines for processing. */
  private String[] formInputLines;
  
  /** List of parsing states, one for each template being attempted. */
  private List<FormInputTemplateMatchState> formInputTemplateMatchStateList = new ArrayList<>();

  /**
   * Creates a new PDF form parser with the specified input text and available templates.
   * 
   * @param inputString PDF text content to be parsed for transaction data
   * @param templateScannedMap Map of template configurations to import templates
   */
  public ParseFormInputPDFasTXT(String inputString,
      Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap) {
    this.inputString = inputString;
    this.templateScannedMap = templateScannedMap;
  }

  /**
   * Parses the input text using all available templates to extract transaction properties.
   * 
   * @return List of ImportProperties if parsing succeeded, null if no template matched
   * @throws Exception if parsing encounters errors
   */
  public List<ImportProperties> parseInput() throws Exception {
    return parseInput(null);
  }

  /**
   * Parses the input text with an optional file number for tracking multiple documents.
   * Processes the text through all available templates and returns the best match.
   * 
   * @param fileNumber Optional file identifier for batch processing
   * @return List of ImportProperties if parsing succeeded, null if no template matched
   * @throws Exception if parsing encounters errors
   */
  public List<ImportProperties> parseInput(Integer fileNumber) throws Exception {
    templateScannedMap
        .forEach((k, v) -> formInputTemplateMatchStateList.add(new FormInputTemplateMatchState(k, fileNumber)));
    String formInput = inputString.replaceAll(REMOVE_EMPTY_LINE_PATTERN, "")
        .replaceAll("\r\n|\r|\n", System.lineSeparator()).replaceAll(" +", " ").trim();
    formInputLines = formInput.split(System.lineSeparator());
    for (int i = 0; i < formInputLines.length; i++) {
      formInputLines[i] = formInputLines[i].trim();
    }
    for (int row = 0; row < formInputLines.length; row++) {
      matchRequiredTemplatesPropertyByRow(row);
    }
    Map<FormInputTemplateMatchState, List<ImportProperties>> matchingTemplateMap = matchOptionalTemplatesProperties();
    return getMatchingPropertyList(matchingTemplateMap);
  }

  /**
   * Returns the template that successfully parsed the given transaction properties.
   * Used to identify which template configuration matched the document.
   * 
   * @param importPropertiesList The transaction properties that were successfully parsed
   * @return The import transaction template that generated these properties
   */
  public ImportTransactionTemplate getSuccessTemplate(List<ImportProperties> importPropertiesList) {
    FormInputTemplateMatchState formInputTemplateMatchState = formInputTemplateMatchStateList.stream()
        .filter(fitms -> fitms.getImportPropertiesList() == importPropertiesList).findFirst().get();
    return formInputTemplateMatchState.getTemplateConfigurationPDFasTXT().getImportTransactionTemplate();
  }

  /**
   * Generates failure records for transaction import tracking when parsing fails.
   * Creates diagnostic information showing the progress of each attempted template.
   * 
   * @param idTransactionPos Transaction position ID for error tracking
   * @return List of failure records with template IDs and last matched properties
   */
  public List<ImportTransactionPosFailed> getImportTransactionPosFailed(Integer idTransactionPos) {
    List<ImportTransactionPosFailed> importTransactionPosFailedList = new ArrayList<>();
    for (FormInputTemplateMatchState fitms : formInputTemplateMatchStateList) {
      TemplateConfigurationPDFasTXT templateConfigurationPDFasTXT = fitms.getTemplateConfigurationPDFasTXT();
      ImportTransactionTemplate importTransactionTemplate = templateConfigurationPDFasTXT
          .getImportTransactionTemplate();
      importTransactionPosFailedList.add(new ImportTransactionPosFailed(idTransactionPos,
          importTransactionTemplate.getIdTransactionImportTemplate(), fitms.getLastMatchingProperty()));
    }
    return importTransactionPosFailedList;
  }

  /**
   * Returns diagnostic information about parsing failures for troubleshooting.
   * Shows the last successfully matched property for each attempted template.
   * 
   * @return List of failed template states with progress information
   */
  public List<FailedParsedTemplateState> getLastMatchingProperties() {
    List<FailedParsedTemplateState> failedParseTemplateStateList = new ArrayList<>();
    for (FormInputTemplateMatchState fitms : formInputTemplateMatchStateList) {
      TemplateConfigurationPDFasTXT templateConfigurationPDFasTXT = fitms.getTemplateConfigurationPDFasTXT();
      ImportTransactionTemplate importTransactionTemplate = templateConfigurationPDFasTXT
          .getImportTransactionTemplate();
      failedParseTemplateStateList.add(new FailedParsedTemplateState(importTransactionTemplate.getTemplatePurpose(),
          importTransactionTemplate.getValidSince(), fitms.getLastMatchingProperty()));
    }
    return failedParseTemplateStateList;
  }

  /**
   * Attempts to match required template properties for all templates against a specific text line.
   * Each template maintains its own parsing state as it progresses through the document.
   * 
   * @param row Line number in the normalized input text
   * @throws Exception if property matching encounters errors
   */
  private void matchRequiredTemplatesPropertyByRow(int row) throws Exception {
    for (FormInputTemplateMatchState tms : formInputTemplateMatchStateList) {
      tms.matchTemplatesProperties(formInputLines, row);
    }
  }

  /**
   * Processes optional properties for templates that successfully matched all required fields.
   * Only templates that completed required property matching participate in this phase.
   * 
   * @return Map of successful template states to their extracted transaction properties
   * @throws Exception if optional property processing encounters errors
   */
  private Map<FormInputTemplateMatchState, List<ImportProperties>> matchOptionalTemplatesProperties() throws Exception {
    Map<FormInputTemplateMatchState, List<ImportProperties>> matchingTemplateMap = new HashMap<>();

    for (FormInputTemplateMatchState tms : formInputTemplateMatchStateList) {
      if (tms.isFormMatches()) {
        tms.scanForOptionalProperties(formInputLines);
        matchingTemplateMap.put(tms, tms.getImportPropertiesList());
      }
    }
    return matchingTemplateMap;
  }

  /**
   * Selects the best matching template when multiple templates successfully parse the document.
   * Uses template validity dates to choose the most appropriate template for the transaction date.
   * 
   * @param matchingTemplateMap Map of successful template matches
   * @return Transaction properties from the selected template, or null if no matches
   */
  private List<ImportProperties> getMatchingPropertyList(
      Map<FormInputTemplateMatchState, List<ImportProperties>> matchingTemplateMap) {
    NavigableMap<Long, List<ImportProperties>> timeMatchingPropertiesMap = new TreeMap<>();
    if (matchingTemplateMap.size() > 1) {
      for (Map.Entry<FormInputTemplateMatchState, List<ImportProperties>> entry : matchingTemplateMap.entrySet()) {
        FormInputTemplateMatchState formInputTemplateMatchState = entry.getKey();
        TemplateConfigurationPDFasTXT templateConfigurationPDFasTXT = formInputTemplateMatchState
            .getTemplateConfigurationPDFasTXT();
        ImportTransactionTemplate importTransactionTemplate = templateScannedMap.get(templateConfigurationPDFasTXT);
        ImportProperties importProperties = entry.getValue().get(0);

        if (importTransactionTemplate.getValidSince().getTime() < importProperties.getDatetime().getTime()) {
          timeMatchingPropertiesMap.put(importTransactionTemplate.getValidSince().getTime(), entry.getValue());
        }
      }
      return timeMatchingPropertiesMap.lastEntry().getValue();
    } else if (matchingTemplateMap.size() == 1) {
      return matchingTemplateMap.entrySet().iterator().next().getValue();
    }
    return null;
  }
}
