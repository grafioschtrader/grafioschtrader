package grafioschtrader.platformimport;

import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;

public class CombineTemplateAndImpTransPos {
  public final ImportTransactionPos importTransactionPos;
  public final ImportTransactionTemplate importTransactionTemplate;

  public CombineTemplateAndImpTransPos(ImportTransactionPos importTransactionPos,
      ImportTransactionTemplate importTransactionTemplate) {
    super();
    this.importTransactionPos = importTransactionPos;
    this.importTransactionTemplate = importTransactionTemplate;
  }

  /*
   * public String getFileType() { return
   * ImportTransactionHelper.isCsvEnding(importTransactionPos.getFileNameOriginal(
   * )) ? "C" :
   * ImportTransactionHelper.isPdfEnding(importTransactionPos.getFileNameOriginal(
   * )) && importTransactionPos.getIdFilePart() == null ? "P" : "T"; }
   */
  public boolean isFullPath() {
    return importTransactionPos.getFileNameOriginal().contains("\\")
        || importTransactionPos.getFileNameOriginal().startsWith("/");
  }

}
