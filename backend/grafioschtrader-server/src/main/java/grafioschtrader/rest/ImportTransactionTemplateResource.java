package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.User;
import grafiosch.rest.UpdateCreateDeleteAuditResource;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.SuccessFailedImportTransactionTemplate;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.platformimport.FormTemplateCheck;
import grafioschtrader.repository.ImportTransactionTemplateJpaRepository;
import grafioschtrader.repository.TradingPlatformPlanJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(RequestGTMappings.IMPORTTRANSACTIONTEMPLATE_MAP)
@Tag(name = RequestGTMappings.IMPORTTRANSACTIONTEMPLATE, description = "Controller for Import Transaction Tempalte")
public class ImportTransactionTemplateResource extends UpdateCreateDeleteAuditResource<ImportTransactionTemplate> {

  @Autowired
  private ImportTransactionTemplateJpaRepository importTransactionTemplateJpaRepository;

  @Autowired
  private TradingPlatformPlanJpaRepository tradingPlatformPlanJpaRepository;

  @Operation(summary = "Retrieve all import transaction templates for a specific platform", 
      description = """
          Returns a list of all import transaction templates associated with the specified import transaction platform. 
          Templates define parsing rules for converting raw financial data (CSV, PDF) into structured transaction records. 
          Optionally excludes template content for performance when only metadata is needed.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONTEMPLATE })
  @GetMapping(value = "/importtransactionplatform/{idTransactionImportPlatform}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionTemplate>> getImportTransactionPlatformByPlatform(
      @Parameter(description = "ID of the import transaction platform", required = true) @PathVariable final Integer idTransactionImportPlatform,
      @Parameter(description = "true when a empty template is requried", required = true) @RequestParam() final boolean excludeTemplate) {
    return getImportTransactionPlatformById(idTransactionImportPlatform, excludeTemplate);
  }

  @Operation(summary = "Retrieve import transaction templates by trading platform plan",
      description = """
          Returns all import transaction templates for the platform associated with the specified trading platform plan.
          This endpoint provides an indirect way to access templates through the trading platform relationship, useful
          when working with specific broker configurations.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONTEMPLATE })
  @GetMapping(value = "/importtransactionplatform/tradingplatformplan/{idTradingPlatformPlan}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ImportTransactionTemplate>> getImportTransactionPlatformByTradingPlatformPlan(
      @Parameter(description = "Id of trading platform plan", required = true) @PathVariable final Integer idTradingPlatformPlan,
      @Parameter(description = "true when a empty template is requried", required = true) @RequestParam() final boolean excludeTemplate) {
    TradingPlatformPlan tradingPlatformPlan = tradingPlatformPlanJpaRepository.findById(idTradingPlatformPlan)
        .orElse(null);
    return getImportTransactionPlatformById(
        tradingPlatformPlan.getImportTransactionPlatform().getIdTransactionImportPlatform(), excludeTemplate);
  }

  @Operation(summary = "Get CSV template options for HTML select elements",
      description = """
          Returns CSV templates for the specified trading platform plan formatted as key-value options suitable for
          HTML select dropdowns. Each option contains the template ID as the key and a descriptive name (ID + purpose)
          as the display value.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONTEMPLATE })
  @GetMapping(value = "/importtransactionplatform/tradingplatformplan/csv/{idTradingPlatformPlan}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getCSVTemplateIdsAsValueKeyHtmlSelectOptions(
      @Parameter(description = "Id of trading platform plan", required = true) @PathVariable final Integer idTradingPlatformPlan) {
    return new ResponseEntity<>(
        importTransactionTemplateJpaRepository.getCSVTemplateIdsAsValueKeyHtmlSelectOptions(idTradingPlatformPlan),
        HttpStatus.OK);
  }

  /**
   * Shared utility method for retrieving import transaction templates by platform ID with optional content exclusion.
   * This method provides the core template retrieval logic used by multiple public endpoints, enabling consistent
   * data access patterns and optional performance optimization through content exclusion.
   * 
   * <p>The method retrieves all templates for the specified platform ordered by template purpose for predictable
   * sorting. When content exclusion is enabled, the template text content is removed from each template entity,
   * significantly reducing response size and improving performance when only template metadata is needed.</p>
   * 
   * @param idTransactionImportPlatform The platform ID to retrieve templates for
   * @param excludeTemplate When true, removes template content from response for better performance
   * @return ResponseEntity containing the list of templates with optional content exclusion applied
   */
  private ResponseEntity<List<ImportTransactionTemplate>> getImportTransactionPlatformById(
      Integer idTransactionImportPlatform, boolean excludeTemplate) {
    List<ImportTransactionTemplate> importTransactionTemplateList = importTransactionTemplateJpaRepository
        .findByIdTransactionImportPlatformOrderByTemplatePurpose(idTransactionImportPlatform);
    if (excludeTemplate) {
      importTransactionTemplateList
          .forEach(importTransactionTemplate -> importTransactionTemplate.setTemplateAsTxt(null));
    }
    return new ResponseEntity<>(importTransactionTemplateList, HttpStatus.OK);
  }

  @Override
  protected UpdateCreateJpaRepository<ImportTransactionTemplate> getUpdateCreateJpaRepository() {
    return importTransactionTemplateJpaRepository;
  }

  @Operation(summary = "Validate transaction data against PDF templates",
      description = """
          Processes form input (typically extracted PDF text) against available PDF templates to identify matching
          parsing rules and extract structured transaction data. This endpoint attempts to match the input against all
          PDF templates for the platform and returns either successful parsing results with extracted transaction
          details or failure information indicating why no templates matched.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONTEMPLATE })
  @PostMapping(value = "/checkformagainsttemplate")
  public ResponseEntity<FormTemplateCheck> checkFormAgainstTemplate(@RequestBody FormTemplateCheck formTemplateCheck)
      throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(importTransactionTemplateJpaRepository.checkFormAgainstTemplate(formTemplateCheck,
        user.createAndGetJavaLocale()), HttpStatus.OK);
  }

  @Operation(summary = "Get available languages for template creation",
      description = """
          Returns all supported languages that can be used when creating import transaction templates. This enables
          multi-language template support for international trading platforms with locale-specific formats and parsing
          rules.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONTEMPLATE })
  @GetMapping(value = "/languages", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getPossibleLanguagesForTemplate() {
    return new ResponseEntity<>(importTransactionTemplateJpaRepository.getPossibleLanguagesForTemplate(),
        HttpStatus.OK);
  }

  @Operation(
      summary = "Export platform templates as ZIP archive", 
      description = """
          Creates and downloads a ZIP file containing all import transaction templates for the specified platform. 
          Templates are exported with standardized filenames (category-format-date-language.tmpl) for easy 
          identification and deployment. Useful for backup, sharing templates between environments, or bulk template 
          management.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONTEMPLATE })
  @GetMapping(value = "/exportalltemplates/{idTransactionImportPlatform}", produces = "application/zip")
  public void getTemplatesByPlatformPlanAsZip(
      @Parameter(description = "ID of the import transaction platform", required = true) @PathVariable final Integer idTransactionImportPlatform,
      HttpServletResponse response) throws Exception {
    importTransactionTemplateJpaRepository.getTemplatesByPlatformPlanAsZip(idTransactionImportPlatform, response);
  }

  @Operation(
      summary = "Upload multiple template files", 
      description = """
          Processes and imports multiple template files for the specified platform. Files must follow the naming 
          convention: category-format-date-language.tmpl (e.g., 'accumulate-csv-2024-01-15-en.tmpl'). The operation 
          validates file names, extracts metadata, validates template content, and creates or updates template records. 
          Returns detailed statistics about successful uploads, updates, and various error conditions.""",
      tags = { RequestGTMappings.IMPORTTRANSACTIONTEMPLATE })
  @PostMapping(value = "uploadtemplatefiles/{idTransactionImportPlatform}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<SuccessFailedImportTransactionTemplate> uploadImportTemplateFiles(
      @PathVariable() Integer idTransactionImportPlatform, @RequestParam("file") MultipartFile[] uploadFiles)
      throws Exception {
    return new ResponseEntity<>(
        importTransactionTemplateJpaRepository.uploadImportTemplateFiles(idTransactionImportPlatform, uploadFiles),
        HttpStatus.OK);
  }

  
  @Override
  protected String getPrefixEntityLimit() {
    return GlobalConstants.GT_LIMIT_DAY;
  }

}
