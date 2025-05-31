package grafioschtrader.priceupdate.historyquote;

import java.util.Date;
import java.util.List;

import grafioschtrader.entities.Securitycurrency;

public interface IHistoryqouteEntityBaseAccess<S extends Securitycurrency<S>> {

  List<SecurityCurrencyMaxHistoryquoteData<S>> getMaxHistoryquoteResult(short maxHistoryRetry,
      BaseHistoryquoteThru<S> baseHisotryquote, List<Integer> idsStockexchange);

  /**
   * Load security or currency pair with its history quotes and call method for loading additional prices.
   */
  S catchUpSecurityCurrencypairHisotry(S securitycurrency, Date fromDate, Date toDate);

}
