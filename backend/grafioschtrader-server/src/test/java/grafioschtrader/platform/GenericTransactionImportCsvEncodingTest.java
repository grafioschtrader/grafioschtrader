package grafioschtrader.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platformimport.csv.ImportTransactionHelperCsv;
import grafioschtrader.platformimport.csv.TemplateConfigurationAndStateCsv;
import grafioschtrader.types.TemplateCategory;
import grafioschtrader.types.TemplateFormatType;

/**
 * Guards the character encoding resolution of the CSV transaction import.
 *
 * <p>
 * The CSV export of Grafioschtrader writes UTF-8 without a BOM, and the import has to recognise that from the bytes
 * alone. Purely statistical detection does not: for a mostly ASCII file with only a handful of two byte sequences
 * Mozilla's UniversalDetector reports GB18030, which turns the umlauts of the German template header into CJK
 * characters. The column check of the template then fails and the whole upload is rejected with
 * {@code gt.import.column.missmatch} - the exported file cannot be re-imported at all.
 * </p>
 *
 * <p>
 * Runs without a Spring context or database.
 * </p>
 */
class GenericTransactionImportCsvEncodingTest {

  private static final String TEMPLATE_DIR = "/testdata/import_template/grafioschtrader/";
  private static final String GERMAN_TEMPLATE = "csv_base-csv-20000101-de.tmpl";
  private static final String EXPORTED_CSV = "/testdata/import_transaction/e2e/limit1/Migros/gt_transactions_Migros.csv";
  private static final int COLUMN_COUNT = 16;

  @Test
  @DisplayName("A UTF-8 export with few umlauts is resolved as UTF-8 and not as the detector's GB18030")
  void utf8ExportIsNotMisdetected() throws IOException {
    assertEquals(StandardCharsets.UTF_8.name(), GenericTransactionImportCSV.resolveCharset(readExportedCsv()));
  }

  @Test
  @DisplayName("Header of the exported CSV still matches all columns of the German template")
  void exportedHeaderMatchesGermanTemplate() throws IOException {
    byte[] content = readExportedCsv();
    String header = new String(content, Charset.forName(GenericTransactionImportCSV.resolveCharset(content)))
        .split("\\R")[0].replaceAll("\\uFEFF", "");

    TemplateConfigurationAndStateCsv template = germanTemplate();
    assertTrue(template.isValidTemplateForForm(header), "Template columns do not match the exported header: " + header);
    assertEquals(COLUMN_COUNT, template.getColumnPropertyMapping().size());
  }

  @Test
  @DisplayName("Reading the same bytes as GB18030 destroys the umlaut columns - the regression this guards against")
  void gb18030ReadingBreaksTheHeader() throws IOException {
    String header = new String(readExportedCsv(), Charset.forName("GB18030")).split("\\R")[0];

    TemplateConfigurationAndStateCsv template = germanTemplate();
    assertFalse(template.isValidTemplateForForm(header),
        "Reading the export as GB18030 must not produce a valid header - otherwise this test proves nothing");
    assertEquals(COLUMN_COUNT - 2, template.getColumnPropertyMapping().size(),
        "Titelwaehrung and Waehrung are the two columns lost to the wrong encoding");
  }

  @Test
  @DisplayName("Single byte content with an umlaut is not valid UTF-8 and stays with the detector")
  void legacySingleByteContentKeepsDetection() {
    // 'W' 0xE4 'h' - the cp1252 encoding of "Wäh", which UTF-8 cannot decode.
    byte[] cp1252 = { 'D', 'a', 't', 'u', 'm', ';', 'W', (byte) 0xE4, 'h' };
    assertEquals("WINDOWS-1252", GenericTransactionImportCSV.resolveCharset(cp1252).toUpperCase());
  }

  @Test
  @DisplayName("Undetectable content falls back to UTF-8 instead of a null encoding")
  void undetectableContentFallsBackToUtf8() {
    assertEquals(StandardCharsets.UTF_8.name(), GenericTransactionImportCSV.resolveCharset(new byte[0]));
  }

  private byte[] readExportedCsv() throws IOException {
    try (InputStream is = getClass().getResourceAsStream(EXPORTED_CSV)) {
      assertNotNull(is, "Exported CSV fixture not found: " + EXPORTED_CSV);
      return is.readAllBytes();
    }
  }

  private TemplateConfigurationAndStateCsv germanTemplate() throws IOException {
    String[] fileNameParts = GERMAN_TEMPLATE.replaceFirst("\\.tmpl$", "").split("-");
    ImportTransactionTemplate itt = new ImportTransactionTemplate(
        TemplateCategory.valueOf(fileNameParts[0].toUpperCase()),
        TemplateFormatType.valueOf(fileNameParts[1].toUpperCase()), fileNameParts[3].toLowerCase());
    itt.setValidSince(
        LocalDate.parse(fileNameParts[2], DateTimeFormatter.ofPattern(GlobalConstants.SHORT_STANDARD_DATE_FORMAT)));
    try (InputStream is = getClass().getResourceAsStream(TEMPLATE_DIR + GERMAN_TEMPLATE)) {
      assertNotNull(is, "Template file not found: " + GERMAN_TEMPLATE);
      itt.setTemplateAsTxt(new String(is.readAllBytes(), StandardCharsets.UTF_8));
    }
    Map<TemplateConfigurationAndStateCsv, ImportTransactionTemplate> templateMap = ImportTransactionHelperCsv
        .readTemplates(List.of(itt), Locale.GERMAN);
    return templateMap.keySet().iterator().next();
  }
}
