package grafioschtrader.platformimport;

public abstract class ImportTransactionHelper {

  public static final String CSV_FILE_NAME_ENDING = ".csv";
  public static final String PDF_FILE_NAME_ENDING = ".pdf";
  public static final String TXT_FILE_NAME_ENDING = ".txt";

  public static boolean isCsvEnding(String fileName) {
    return fileName.toLowerCase().endsWith(ImportTransactionHelper.CSV_FILE_NAME_ENDING);
  }

  public static boolean isPdfEnding(String fileName) {
    return fileName.toLowerCase().endsWith(ImportTransactionHelper.PDF_FILE_NAME_ENDING);
  }

}
