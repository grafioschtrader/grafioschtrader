package grafioschtrader.platform;

import java.util.ArrayList;
import java.util.List;

import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.TransactionType;

/**
 * Base class for import different data types like csv, txt of pdf.
 *
 * @author Hugo Graf
 *
 */
public abstract class GenericTransactionImportCsvPdfBase {
  protected final ImportTransactionHead importTransactionHead;
  protected List<ImportTransactionTemplate> importTransactionTemplateList;

  public GenericTransactionImportCsvPdfBase(final ImportTransactionHead importTransactionHead,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    this.importTransactionHead = importTransactionHead;
    this.importTransactionTemplateList = importTransactionTemplateList;
  }

  protected void setCashaccoutWhenPossibe(List<Cashaccount> cashaccountList,
      ImportTransactionPos importTransactionPos) {
    Cashaccount preferedCashaccount = Portfolio.getPreferedCashaccountForPorfolioBySecurityAccountAndCurrency(
        cashaccountList, importTransactionHead.getSecurityaccount().getIdSecuritycashAccount(),
        importTransactionPos.getCurrencyAccount());
    if (preferedCashaccount != null) {
      importTransactionPos.setCashaccount(preferedCashaccount);
    }
  }

  protected void checkAndSaveSuccessImportTransaction(ImportTransactionTemplate importTransactionTemplate,
      List<Cashaccount> cashaccountList, List<ImportProperties> importPropertiesList, String fileNameOriginal,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      SecurityJpaRepository securityJpaRepository) {
    List<ImportTransactionPos> importTransactionPosList = new ArrayList<>();
    switch (importPropertiesList.get(0).getTransactionType()) {
    case ACCUMULATE:
    case DIVIDEND:
    case REDUCE:
      importTransactionPosList.add(createSecurityTransaction(importTransactionTemplate, cashaccountList,
          importPropertiesList, fileNameOriginal, securityJpaRepository));
      break;

    default:
      if (importPropertiesList.size() > 1) {
        // Account Transfer in the same portfolio
        importTransactionPosList.addAll(accountTransferSamePortfolio(importTransactionTemplate, cashaccountList,
            importPropertiesList, fileNameOriginal, importTransactionPosJpaRepository));
      } else {
        // Transaction without security
        importTransactionPosList.add(ImportTransactionPos.createFromImportPropertiesSuccess(
            importTransactionHead.getIdTenant(), fileNameOriginal, importTransactionHead.getIdTransactionHead(),
            importTransactionTemplate.getIdTransactionImportTemplate(), importPropertiesList.get(0)));
      }
    }
    importTransactionPosList.forEach(importTransactionPos -> {
      setCashaccountAndCheckReadyState(cashaccountList, importTransactionPos, importTransactionPosJpaRepository);
      importTransactionPosJpaRepository.save(importTransactionPos);
    });
  }

  /**
   * Cash transfer from one cash account to another of the the same portfolio
   *
   *
   * @param importTransactionTemplate
   * @param cashaccountList
   * @param importPropertiesList
   * @param fileNameOriginal
   * @param importTransactionPosJpaRepository
   * @return
   */
  private List<ImportTransactionPos> accountTransferSamePortfolio(ImportTransactionTemplate importTransactionTemplate,
      List<Cashaccount> cashaccountList, List<ImportProperties> importPropertiesList, String fileNameOriginal,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository) {

    List<ImportTransactionPos> importTransactionPosList = new ArrayList<>();

    int lineNumberIndex = 0;
    if (importPropertiesList.get(0).getTransactionType() == TransactionType.WITHDRAWAL) {
      lineNumberIndex = 1;
    }

    ImportProperties depositIp = importPropertiesList.get(lineNumberIndex);
    ImportProperties withdrawalIp = importPropertiesList.get((lineNumberIndex + 1) % 2);

    Currencypair currencypair = DataHelper.getCurrencypairWithSetOfFromAndTo(withdrawalIp.getCac(), depositIp.getCac());
    Double exchangeRate = null;
    if (currencypair != null) {
      if (depositIp.getCac().equals(currencypair.getFromCurrency())) {
        exchangeRate = Math.abs(withdrawalIp.getTa() / depositIp.getTa());
      } else {
        exchangeRate = Math.abs(depositIp.getTa() / withdrawalIp.getTa());
      }
      depositIp.setCex(exchangeRate);
      withdrawalIp.setCex(exchangeRate);
    }

    importTransactionPosList
        .add(createAndSaveImportTransactionPos(importTransactionPosJpaRepository, importTransactionTemplate,
            importTransactionHead.getIdTenant(), fileNameOriginal, importTransactionHead.getIdTransactionHead(),
            importTransactionTemplate.getIdTransactionImportTemplate(), depositIp));

    importTransactionPosList
        .add(createAndSaveImportTransactionPos(importTransactionPosJpaRepository, importTransactionTemplate,
            importTransactionHead.getIdTenant(), fileNameOriginal, importTransactionHead.getIdTransactionHead(),
            importTransactionTemplate.getIdTransactionImportTemplate(), withdrawalIp));

    importTransactionPosList.get(0).setConnectedIdTransactionPos(importTransactionPosList.get(1).getIdTransactionPos());
    importTransactionPosList.get(1).setConnectedIdTransactionPos(importTransactionPosList.get(0).getIdTransactionPos());
    return importTransactionPosList;
  }

  protected ImportTransactionPos createSecurityTransaction(ImportTransactionTemplate importTransactionTemplate,
      List<Cashaccount> cashaccountList, List<ImportProperties> importPropertiesList, String fileNameOriginal,
      SecurityJpaRepository securityJpaRepository) {
    ImportTransactionPos importTransactionPos = ImportTransactionPos.createFromImportPropertiesSecuritySuccess(
        importTransactionHead.getIdTenant(), fileNameOriginal, importTransactionHead.getIdTransactionHead(),
        importTransactionTemplate.getIdTransactionImportTemplate(), importPropertiesList);
    TransactionImportHelper.setSecurityToImportWhenPossible(importTransactionPos, securityJpaRepository);
    return importTransactionPos;
  }

  protected void setCashaccountAndCheckReadyState(List<Cashaccount> cashaccountList,
      ImportTransactionPos importTransactionPos, ImportTransactionPosJpaRepository importTransactionPosJpaRepository) {
    setCashaccoutWhenPossibe(cashaccountList, importTransactionPos);
    importTransactionPosJpaRepository.setCheckReadyForSingleTransaction(importTransactionPos);
  }

  private ImportTransactionPos createAndSaveImportTransactionPos(
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      ImportTransactionTemplate importTransactionTemplate, Integer idTenant, String fileName, Integer idTransactionHead,
      Integer idTransactionImportTemplate, ImportProperties importProperties) {
    ImportTransactionPos importTransactionPos = ImportTransactionPos.createFromImportPropertiesSuccess(
        importTransactionHead.getIdTenant(), fileName, importTransactionHead.getIdTransactionHead(),
        importTransactionTemplate.getIdTransactionImportTemplate(), importProperties);
    return importTransactionPosJpaRepository.save(importTransactionPos);
  }

}
