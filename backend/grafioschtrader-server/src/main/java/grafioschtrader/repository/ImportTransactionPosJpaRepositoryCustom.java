package grafioschtrader.repository;

import java.util.List;
import java.util.Map;

import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.platformimport.CombineTemplateAndImpTransPos;
import grafioschtrader.repository.ImportTransactionPosJpaRepositoryImpl.SavedImpPosAndTransaction;

public interface ImportTransactionPosJpaRepositoryCustom {

  List<CombineTemplateAndImpTransPos> getCombineTemplateAndImpTransPosListByTransactionHead(Integer idTransactionHead);

  List<ImportTransactionPos> setSecurity(Integer idSecuritycurrency, List<Integer> idTransactionPosList);

  List<ImportTransactionPos> setCashAccount(Integer idSecuritycashAccount, List<Integer> idTransactionPosList);

  List<ImportTransactionPos> adjustCurrencyExRateOrQuotation(List<Integer> idTransactionPosList);

  List<ImportTransactionPos> acceptTotalDiff(List<Integer> idTransactionPosList);

  void deleteMultiple(List<Integer> idTransactionPosList);

  List<ImportTransactionPos> setIdTransactionMayBe(Integer idTransactionMaybe, List<Integer> idTransactionPosList);

  void setCheckReadyForSingleTransaction(ImportTransactionPos itp);

  void addPossibleExchangeRateForDividend(ImportTransactionHead importTransactionHead, ImportTransactionPos itp);

  List<ImportTransactionPos> createAndSaveTransactionsByIds(List<Integer> idTransactionPosList);

  /**
   * Creates and saves transactions from the "ImportTransactionPos" data, while
   * still making possible corrections automatically.
   *
   * @return
   */
  List<SavedImpPosAndTransaction> createAndSaveTransactionsFromImpPos(
      List<ImportTransactionPos> importTransactionPosList, Map<Integer, ImportTransactionPos> idItpMap);

  void setTrasactionIdToNullWhenExists(Integer idTransaction);
}
