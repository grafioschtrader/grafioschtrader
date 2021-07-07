package grafioschtrader.reportviews.currencypair;

import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.entities.Currencypair;

/**
 * It may be used to return the quotation for a Currencypair with a certain
 * Date.
 *
 * @author Hugo Graf
 *
 */
public class CurrencypairWithHistoryquote {

  public Currencypair currencypair;
  public ISecuritycurrencyIdDateClose historyquote;

  public CurrencypairWithHistoryquote(Currencypair currencypair, ISecuritycurrencyIdDateClose historyquote) {
    super();
    this.currencypair = currencypair;
    this.historyquote = historyquote;
  }

}
