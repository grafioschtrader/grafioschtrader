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
 * Parse a text form with different templates at once.
 *
 * @author Hugo Graf
 *
 */
public class ParseFormInputPDFasTXT {

  private static final String REMOVE_EMPTY_LINE_PATTERN = "(?m)^\\s*$[\n\r]{1,}";

  private String inputString;
  private Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap;
  private String[] formInputLines;
  private List<FormInputTemplateMatchState> formInputTemplateMatchStateList = new ArrayList<>();

  public ParseFormInputPDFasTXT(String inputString,
      Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap) {
    this.inputString = inputString;
    this.templateScannedMap = templateScannedMap;
  }

  public List<ImportProperties> parseInput() throws Exception {
    return parseInput(null);
  }

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

  public ImportTransactionTemplate getSuccessTemplate(List<ImportProperties> importPropertiesList) {
    FormInputTemplateMatchState formInputTemplateMatchState = formInputTemplateMatchStateList.stream()
        .filter(fitms -> fitms.getImportPropertiesList() == importPropertiesList).findFirst().get();
    return formInputTemplateMatchState.getTemplateConfigurationPDFasTXT().getImportTransactionTemplate();
  }

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

  private void matchRequiredTemplatesPropertyByRow(int row) throws Exception {
    for (FormInputTemplateMatchState tms : formInputTemplateMatchStateList) {
      tms.matchTemplatesProperties(formInputLines, row);
    }
  }

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
   * Return the matching properties. The valid date of template is used if there
   * are more than one matching template.
   *
   * @param matchingTemplateMap
   * @return
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
