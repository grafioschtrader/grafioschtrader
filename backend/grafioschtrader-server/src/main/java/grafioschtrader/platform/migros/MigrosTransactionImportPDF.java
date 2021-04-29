package grafioschtrader.platform.migros;

import java.util.List;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platform.GenericTransactionImportPDF;

/**
 * Some old PDF of Migros Bank contains in the total similar like this
 * 34.0E([0-9])EE</br>
 *
 */
public class MigrosTransactionImportPDF extends GenericTransactionImportPDF {

  public MigrosTransactionImportPDF(final ImportTransactionHead importTransactionHead,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    super(importTransactionHead, importTransactionTemplateList);
  }

  @Override
  protected String cleanReadPDF(String readPDFAsText) {
    if (readPDFAsText.startsWith("ABCDE")) {
      return cleanGreyPointAsChar(readPDFAsText);
    } else {
      return readPDFAsText;
    }
  }

  private String cleanGreyPointAsChar(String readPDFAsText) {
    return readPDFAsText.replaceFirst("E([0-9])EE", "$1");
  }

}
