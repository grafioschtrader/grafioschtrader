package grafioschtrader.priceupdate.historyquote;

import java.util.Date;

public interface SecurityCurrencyMaxHistoryquoteData<S> {
  S getSecurityCurrency();

  /**
   * Date of youngest history quote
   *
   * @return
   */
  Date getDate();
}
