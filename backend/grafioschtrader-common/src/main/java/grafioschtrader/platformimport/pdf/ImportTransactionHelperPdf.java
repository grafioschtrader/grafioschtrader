package grafioschtrader.platformimport.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.exceptions.DataViolationException;

public abstract class ImportTransactionHelperPdf {

  /**
   * Transform a input stream of PDF to text string.
   *
   * @param is
   * @return
   * @throws IOException
   */
  public static String transFormPDFToTxt(InputStream is) throws IOException {
    try (PDDocument document = PDDocument.load(is)) {
      PDFTextStripper textStripper = new PDFTextStripper();
      textStripper.setSortByPosition(true);
      return textStripper.getText(document);
    }
  }

  /**
   * Parse the existing Templates. The result can be used to scan forms.
   *
   * @return
   * @throws DataViolationException
   */
  public static Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> readTemplates(
      List<ImportTransactionTemplate> importTransactionTemplateList, Locale userLocale) {

    Map<TemplateConfigurationPDFasTXT, ImportTransactionTemplate> templateScannedMap = new HashMap<>();
    for (ImportTransactionTemplate itt : importTransactionTemplateList) {
      TemplateConfigurationPDFasTXT templateConfigurationPDFasTXT = new TemplateConfigurationPDFasTXT(itt, userLocale);
      templateScannedMap.put(templateConfigurationPDFasTXT, itt);
      templateConfigurationPDFasTXT.parseTemplateAndThrowError(false);
    }

    return templateScannedMap;
  }

}
