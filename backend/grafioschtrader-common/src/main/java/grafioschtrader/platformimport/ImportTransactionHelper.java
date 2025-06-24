package grafioschtrader.platformimport;

/**
 * Utility class providing file type detection and constants for transaction import processing.
 * 
 * <p>This abstract helper class contains constants for supported file extensions and utility methods
 * to determine the file type of uploaded transaction documents. The file type determines which
 * import processing pipeline should be used - CSV for tabular data, PDF for individual documents,
 * or TXT for batch-processed documents from GT-PDF-Transform containing multiple converted PDFs.</p>
 * 
 * <h3>Supported File Types</h3>
 * <ul>
 *   <li><b>CSV</b> - Comma-separated values files with tabular transaction data</li>
 *   <li><b>PDF</b> - Individual trading platform statements and confirmations</li>
 *   <li><b>TXT</b> - Batch-processed files from GT-PDF-Transform containing multiple converted PDF documents</li>
 * </ul>
 */
public abstract class ImportTransactionHelper {

  public static final String CSV_FILE_NAME_ENDING = ".csv";
  public static final String PDF_FILE_NAME_ENDING = ".pdf";
  public static final String TXT_FILE_NAME_ENDING = ".txt";

  /**
   * Checks if a filename has a CSV file extension (case-insensitive).
   * 
   * @param fileName The filename to check
   * @return true if the filename ends with .csv (case-insensitive)
   */
  public static boolean isCsvEnding(String fileName) {
    return fileName.toLowerCase().endsWith(ImportTransactionHelper.CSV_FILE_NAME_ENDING);
  }

  /**
   * Checks if a filename has a PDF file extension (case-insensitive).
   * 
   * @param fileName The filename to check
   * @return true if the filename ends with .pdf (case-insensitive)
   */
  public static boolean isPdfEnding(String fileName) {
    return fileName.toLowerCase().endsWith(ImportTransactionHelper.PDF_FILE_NAME_ENDING);
  }

}
