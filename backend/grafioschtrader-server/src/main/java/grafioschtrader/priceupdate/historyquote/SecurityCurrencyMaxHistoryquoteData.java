package grafioschtrader.priceupdate.historyquote;

import java.util.Date;

/**
 * Projection interface for retrieving the most recent history quote date for a security currency.
 *
 * @param <S> the type of the security currency entity (e.g., Currencypair)
 */
public interface SecurityCurrencyMaxHistoryquoteData<S> {

  /**
   * The security currency entity whose history quotes are being analyzed.
   *
   * @return the security currency instance
   */
  S getSecurityCurrency();

  /**
   * The date of the latest history quote available for the security currency.
   *
   * @return the most recent quote date
   */
  Date getDate();
}


