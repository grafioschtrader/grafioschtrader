package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.Role;
import grafiosch.entities.TaxCountry;
import grafiosch.entities.TaxUpload;
import grafiosch.entities.TaxYear;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.rest.RequestMappings;
import grafioschtrader.dto.TaxStatementExportRequest;
import grafioschtrader.entities.IctaxSecurityTaxData;
import grafioschtrader.entities.TaxSecurityYearConfig;
import grafioschtrader.entities.TaxSecurityYearConfigId;
import grafioschtrader.reports.SecurityDividendsReport;
import grafioschtrader.reportviews.securitydividends.SecurityDividendsGrandTotal;
import grafioschtrader.repository.IctaxSecurityTaxDataJpaRepository;
import grafioschtrader.repository.TaxCountryJpaRepository;
import grafioschtrader.repository.TaxSecurityYearConfigJpaRepository;
import grafioschtrader.repository.TaxYearJpaRepository;
import grafioschtrader.service.IctaxImportService;
import grafioschtrader.tax.swiss.ech0196.Ech0196BarcodeGenerator;
import grafioschtrader.tax.swiss.ech0196.Ech0196MappingService;
import grafioschtrader.tax.swiss.ech0196.Ech0196PdfGenerator;
import grafioschtrader.tax.swiss.ech0196.Ech0196XmlGenerator;
import grafioschtrader.tax.swiss.ech0196.model.Ech0196TaxStatement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(TaxDataResource.TAX_DATA_MAP)
@Tag(name = TaxDataResource.TAX_DATA, description = "Controller for ICTax Kursliste tax data management")
public class TaxDataResource {

  public static final String TAX_DATA = "taxdata";
  public static final String TAX_DATA_MAP = RequestMappings.API + TAX_DATA;

  /** Valid Swiss canton abbreviations with display labels, served to the frontend for dropdown population. */
  private static final List<ValueKeyHtmlSelectOptions> CANTONS = List.of(
      new ValueKeyHtmlSelectOptions("AG", "AG - Aargau"),
      new ValueKeyHtmlSelectOptions("AI", "AI - Appenzell Innerrhoden"),
      new ValueKeyHtmlSelectOptions("AR", "AR - Appenzell Ausserrhoden"),
      new ValueKeyHtmlSelectOptions("BE", "BE - Bern"),
      new ValueKeyHtmlSelectOptions("BL", "BL - Basel-Landschaft"),
      new ValueKeyHtmlSelectOptions("BS", "BS - Basel-Stadt"),
      new ValueKeyHtmlSelectOptions("FR", "FR - Fribourg"),
      new ValueKeyHtmlSelectOptions("GE", "GE - Genève"),
      new ValueKeyHtmlSelectOptions("GL", "GL - Glarus"),
      new ValueKeyHtmlSelectOptions("GR", "GR - Graubünden"),
      new ValueKeyHtmlSelectOptions("JU", "JU - Jura"),
      new ValueKeyHtmlSelectOptions("LU", "LU - Luzern"),
      new ValueKeyHtmlSelectOptions("NE", "NE - Neuchâtel"),
      new ValueKeyHtmlSelectOptions("NW", "NW - Nidwalden"),
      new ValueKeyHtmlSelectOptions("OW", "OW - Obwalden"),
      new ValueKeyHtmlSelectOptions("SG", "SG - St. Gallen"),
      new ValueKeyHtmlSelectOptions("SH", "SH - Schaffhausen"),
      new ValueKeyHtmlSelectOptions("SO", "SO - Solothurn"),
      new ValueKeyHtmlSelectOptions("SZ", "SZ - Schwyz"),
      new ValueKeyHtmlSelectOptions("TG", "TG - Thurgau"),
      new ValueKeyHtmlSelectOptions("TI", "TI - Ticino"),
      new ValueKeyHtmlSelectOptions("UR", "UR - Uri"),
      new ValueKeyHtmlSelectOptions("VD", "VD - Vaud"),
      new ValueKeyHtmlSelectOptions("VS", "VS - Valais"),
      new ValueKeyHtmlSelectOptions("ZG", "ZG - Zug"),
      new ValueKeyHtmlSelectOptions("ZH", "ZH - Zürich"));

  private static final Set<String> VALID_CANTON_CODES = CANTONS.stream()
      .map(c -> c.key).collect(java.util.stream.Collectors.toUnmodifiableSet());

  @Autowired
  private TaxCountryJpaRepository taxCountryJpaRepository;

  @Autowired
  private TaxYearJpaRepository taxYearJpaRepository;

  @Autowired
  private IctaxSecurityTaxDataJpaRepository ictaxSecurityTaxDataJpaRepository;

  @Autowired
  private TaxSecurityYearConfigJpaRepository taxSecurityYearConfigJpaRepository;

  @Autowired
  private IctaxImportService ictaxImportService;

  @Autowired
  private SecurityDividendsReport securityDividendsReport;

  @Autowired
  private Ech0196MappingService ech0196MappingService;

  @Autowired
  private Ech0196XmlGenerator ech0196XmlGenerator;

  @Autowired
  private Ech0196PdfGenerator ech0196PdfGenerator;

  @Autowired
  private Ech0196BarcodeGenerator ech0196BarcodeGenerator;

  // ==================== Canton options ====================

  @Operation(summary = "Get Swiss canton options for the tax export dialog")
  @GetMapping(value = "/cantons", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<ValueKeyHtmlSelectOptions>> getCantons() {
    return new ResponseEntity<>(CANTONS, HttpStatus.OK);
  }

  // ==================== TreeTable endpoints ====================

  @Operation(summary = "Get full tax data tree: countries → years → uploads")
  @GetMapping("/tree")
  public ResponseEntity<List<TaxCountry>> getTree() {
    return ResponseEntity.ok(taxCountryJpaRepository.findAll());
  }

  @Operation(summary = "Create a new tax country node")
  @PostMapping("/country")
  public ResponseEntity<TaxCountry> createCountry(@RequestBody TaxCountry taxCountry) {
    checkAdmin();
    return ResponseEntity.ok(taxCountryJpaRepository.save(taxCountry));
  }

  @Operation(summary = "Delete a tax country and all cascaded data")
  @DeleteMapping("/country/{id}")
  public ResponseEntity<Void> deleteCountry(@PathVariable int id) {
    checkAdmin();
    taxCountryJpaRepository.deleteById(id);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Create a new tax year node")
  @PostMapping("/year")
  public ResponseEntity<TaxYear> createYear(@RequestBody TaxYear taxYear) {
    checkAdmin();
    taxYear.setTaxCountry(taxCountryJpaRepository.getReferenceById(taxYear.getIdTaxCountry()));
    return ResponseEntity.ok(taxYearJpaRepository.save(taxYear));
  }

  @Operation(summary = "Delete a tax year and all cascaded data")
  @DeleteMapping("/year/{id}")
  public ResponseEntity<Void> deleteYear(@PathVariable int id) {
    checkAdmin();
    taxYearJpaRepository.deleteById(id);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "Upload zip files containing Kursliste XML + index")
  @PostMapping("/year/{idTaxYear}/upload")
  public ResponseEntity<List<TaxUpload>> uploadFiles(@PathVariable int idTaxYear,
      @RequestParam("file") MultipartFile[] files) throws IOException {
    checkAdmin();
    return ResponseEntity.ok(ictaxImportService.uploadAndImport(idTaxYear, files));
  }

  @Operation(summary = "Re-import from stored zip with current ISINs")
  @PostMapping("/upload/{id}/reimport")
  public ResponseEntity<TaxUpload> reimport(@PathVariable int id) throws IOException {
    checkAdmin();
    return ResponseEntity.ok(ictaxImportService.reimport(id));
  }

  @Operation(summary = "Delete an upload, its stored file, and cascaded tax data")
  @DeleteMapping("/upload/{id}")
  public ResponseEntity<Void> deleteUpload(@PathVariable int id) throws IOException {
    checkAdmin();
    ictaxImportService.deleteUpload(id);
    return ResponseEntity.ok().build();
  }

  // ==================== eCH-0196 Export endpoint ====================

  @Operation(summary = "Export eCH-0196 v2.2.0 Swiss electronic tax statement as ZIP (XML + PDF)")
  @PostMapping(value = "/export/ech0196")
  public void exportEch0196(@RequestBody TaxStatementExportRequest request,
      HttpServletResponse response) throws Exception {
    if (request.getCanton() == null || !VALID_CANTON_CODES.contains(request.getCanton().toUpperCase())) {
      throw new DataViolationException("canton", "gt.tax.export.invalid.canton", null);
    }
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    int taxYear = request.getTaxYear();

    List<Integer> idsSecurityaccount = (request.getIdsSecurityaccount() == null || request.getIdsSecurityaccount().isEmpty())
        ? Arrays.asList(-1) : request.getIdsSecurityaccount();
    SecurityDividendsGrandTotal grandTotal = securityDividendsReport
        .getSecurityDividendsGrandTotalByTenant(user.getIdTenant(), idsSecurityaccount, Arrays.asList(-1));

    Ech0196TaxStatement taxStatement = ech0196MappingService.buildTaxStatement(request, grandTotal);
    byte[] xmlBytes = ech0196XmlGenerator.generateXml(taxStatement);

    String docNumericId = deriveNumericId(taxStatement.getId(), taxYear);
    byte[] code128CBytes = ech0196BarcodeGenerator.generateCode128CBarcode(docNumericId);
    List<byte[]> pdf417Images = ech0196BarcodeGenerator.generatePdf417Barcodes(xmlBytes, taxStatement.getId());
    byte[] pdfBytes = ech0196PdfGenerator.generatePdf(xmlBytes, code128CBytes, pdf417Images);

    response.setContentType("application/zip");
    response.setHeader("Content-Disposition",
        "attachment; filename=\"tax_statement_" + taxYear + ".zip\"");

    try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
      zos.putNextEntry(new ZipEntry("tax_statement_" + taxYear + ".xml"));
      zos.write(xmlBytes);
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("tax_statement_" + taxYear + ".pdf"));
      zos.write(pdfBytes);
      zos.closeEntry();
    }
  }

  // ==================== Security exclusion toggle ====================

  @Operation(summary = "Toggle whether a security is excluded from the tax statement export for a given year")
  @PostMapping("/security-exclusion/toggle")
  @Transactional
  public ResponseEntity<Map<String, Boolean>> toggleSecurityExclusion(@RequestBody Map<String, Object> body) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    int idTenant = user.getIdTenant();
    short taxYear = ((Number) body.get("taxYear")).shortValue();
    int idSecuritycurrency = ((Number) body.get("idSecuritycurrency")).intValue();

    TaxSecurityYearConfigId id = new TaxSecurityYearConfigId(idTenant, taxYear, idSecuritycurrency);
    boolean excluded;
    if (taxSecurityYearConfigJpaRepository.existsById(id)) {
      taxSecurityYearConfigJpaRepository.deleteById(id);
      excluded = false;
    } else {
      taxSecurityYearConfigJpaRepository.save(new TaxSecurityYearConfig(idTenant, taxYear, idSecuritycurrency));
      excluded = true;
    }
    return ResponseEntity.ok(Map.of("excluded", excluded));
  }

  private void checkAdmin() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (user.getMostPrivilegedRole() != Role.ROLE_ADMIN) {
      throw new SecurityException("Admin access required");
    }
  }

  /**
   * Derives a 16-digit numeric document ID for CODE128C encoding per eCH-0196 v2.2.0 spec. Format: 196 (standard
   * prefix, 3 digits) + version (2 digits, "20") + clearing-nr (5 digits, "00000" for personal use) + page (3 digits,
   * "000") + barcode-flag (1 digit, "0") + orientation (1 digit, "0") + reading-direction (1 digit, "0").
   */
  private static String deriveNumericId(String statementId, int taxYear) {
    return "1962000000000000";
  }

  // ==================== Report query endpoint ====================

  @Operation(summary = "Query tax data for given ISINs and year (for report enrichment)")
  @GetMapping("/security")
  public ResponseEntity<List<IctaxSecurityTaxData>> getSecurityTaxData(@RequestParam Collection<String> isins,
      @RequestParam short year) {
    return ResponseEntity.ok(ictaxSecurityTaxDataJpaRepository.findByIsinInAndTaxYear(isins, year));
  }
}
