package grafioschtrader.platform.migros;

import java.util.List;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platform.GenericTransactionImportPDF;

/**
 * Migros Bank-specific PDF transaction importer with custom text cleaning for legacy document formats.
 * 
 * <p>This specialized importer extends the generic PDF import functionality to handle specific formatting
 * issues found in older Migros Bank PDF transaction documents. Some legacy PDFs from Migros Bank contain
 * corrupted numeric values where digits are surrounded by extraneous "E" characters, likely due to OCR
 * processing artifacts or PDF generation issues in older systems.</p>
 */
public class MigrosTransactionImportPDF extends GenericTransactionImportPDF {

  /**
   * Creates a new Migros Bank PDF transaction importer with legacy document support.
   * 
   * @param importTransactionHead Import session container for Migros Bank transactions
   * @param importTransactionTemplateList Available Migros Bank-specific import templates
   */
  public MigrosTransactionImportPDF(final ImportTransactionHead importTransactionHead,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    super(importTransactionHead, importTransactionTemplateList);
  }

  /**
   * Applies Migros Bank-specific text cleaning to handle legacy PDF formatting issues.
   * Automatically detects documents containing corrupted numeric patterns and applies
   * appropriate cleaning transformations while preserving modern documents unchanged.
   * 
   * <p>The method identifies legacy documents through content analysis and applies
   * regex-based pattern replacement to restore correct numeric formatting. Documents
   * that don't contain formatting artifacts are passed through unchanged.</p>
   * 
   * @param readPDFAsText Raw text extracted from the Migros Bank PDF document
   * @return Cleaned text with corrected numeric formatting ready for template parsing
   */
  @Override
  protected String cleanReadPDF(String readPDFAsText) {
    if (readPDFAsText.startsWith("ABCDE")) {
      return cleanGreyPointAsChar(readPDFAsText);
    } else {
      return readPDFAsText;
    }
  }

  /**
   * Removes corrupted digit formatting from legacy Migros Bank PDF text.
   * Applies regex pattern matching to replace {@code E[digit]EE} sequences with
   * the actual digit, correcting OCR or PDF generation artifacts in older documents.
   * 
   * <p>This method specifically handles the pattern where digits in numeric values
   * are corrupted with surrounding "E" characters, such as converting "34.0E5EE"
   * back to "34.05" for proper numeric parsing.</p>
   * 
   * @param readPDFAsText PDF text containing corrupted digit patterns
   * @return Text with corrected numeric formatting
   */
  private String cleanGreyPointAsChar(String readPDFAsText) {
    return readPDFAsText.replaceFirst("E([0-9])EE", "$1");
  }

}
