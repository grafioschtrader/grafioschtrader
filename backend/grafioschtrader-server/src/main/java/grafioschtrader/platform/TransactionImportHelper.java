package grafioschtrader.platform;

import java.util.List;

import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.ImportKnownOtherFlags;

public abstract class TransactionImportHelper {

  /**
   * Try to get the security be the ISIN or Symbol with the expected currency.
   *
   * @param importTransactionPos
   * @param securityJpaRepository
   */
  public static void setSecurityToImportWhenPossible(ImportTransactionPos importTransactionPos,
      SecurityJpaRepository securityJpaRepository) {

    if (importTransactionPos.getIsin() != null) {
      String currency = importTransactionPos.getCurrencySecurity() == null ? importTransactionPos.getCurrencyAccount()
          : importTransactionPos.getCurrencySecurity();
      Security security = securityJpaRepository.findByIsinAndCurrency(importTransactionPos.getIsin(), currency);
      if (security != null) {
        importTransactionPos.setSecurity(security);
      } else {
        List<Security> securities = securityJpaRepository.findByIsin(importTransactionPos.getIsin());
        if (securities.size() == 1) {
          // When there is only one
          importTransactionPos.setSecurity(securities.get(0));
          importTransactionPos.addKnowOtherFlags(ImportKnownOtherFlags.SECURITY_CURRENCY_MISMATCH);
        }
      }
    } else if (importTransactionPos.getSymbolImp() != null) {
      // Used in corner Trader
      String currency = importTransactionPos.getCurrencySecurity() == null ? importTransactionPos.getCurrencyAccount()
          : importTransactionPos.getCurrencySecurity();
      String[] tickerSymbolParts = importTransactionPos.getSymbolImp().split(":");
      if (tickerSymbolParts.length > 0) {
        Security security = securityJpaRepository.findByTickerSymbolAndCurrency(tickerSymbolParts[0], currency);
        if (security != null) {
          importTransactionPos.setSecurity(security);
        }
      }
    }
  }
}
