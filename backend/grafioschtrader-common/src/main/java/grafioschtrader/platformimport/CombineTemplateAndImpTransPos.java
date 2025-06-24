package grafioschtrader.platformimport;

import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Combines an import transaction position with its corresponding import template for processing")
public class CombineTemplateAndImpTransPos {
  @Schema(description = "The transaction position containing file information and import status")
  public final ImportTransactionPos importTransactionPos;
  
  @Schema(description = "The template used for parsing the transaction document")
  public final ImportTransactionTemplate importTransactionTemplate;

  public CombineTemplateAndImpTransPos(ImportTransactionPos importTransactionPos,
      ImportTransactionTemplate importTransactionTemplate) {
    super();
    this.importTransactionPos = importTransactionPos;
    this.importTransactionTemplate = importTransactionTemplate;
  }

  
  public boolean isFullPath() {
    return importTransactionPos.getFileNameOriginal().contains("\\")
        || importTransactionPos.getFileNameOriginal().startsWith("/");
  }

}
