package grafioschtrader.platform.degiro;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import grafioschtrader.common.ValueFormatConverter;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.platform.GenericTransactionImportCSV;
import grafioschtrader.platformimport.csv.TemplateConfigurationAndStateCsv;

public class DegiroTransactionImportCSV extends GenericTransactionImportCSV {

  /**
   * Degiro add 0.1 percentage for fx trades which are not included in the given
   * currency exchange rate
   */
  public static double autoFXTranderCostFactorOnExchangeRate = 1.001;

  public DegiroTransactionImportCSV(ImportTransactionHead importTransactionHead, MultipartFile uploadFile,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    super(importTransactionHead, uploadFile, importTransactionTemplateList);
  }

  @Override
  protected ParseLineSuccessError parseDataLine(Integer lineNumber, String values[],
      TemplateConfigurationAndStateCsv template, ValueFormatConverter valueFormatConverter) {
    ParseLineSuccessError pl = super.parseDataLine(lineNumber, values, template, valueFormatConverter);
    if (template.getTemplateId() == 1) {
      pl.importProperties.setTransType(pl.importProperties.getUnits() < 0 ? "Sell" : "Buy");
      if (pl.importProperties.getOrder() == null) {
        pl.importProperties.setOrder(GenericTransactionImportCSV.ORDER_NOTHING);
      }
      if (pl.importProperties.getCex() != null) {
        pl.importProperties.setCex(pl.importProperties.getCex() * autoFXTranderCostFactorOnExchangeRate);
      }
    }
    return pl;
  }
}
