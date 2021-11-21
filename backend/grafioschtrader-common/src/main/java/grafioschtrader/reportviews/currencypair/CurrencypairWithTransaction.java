package grafioschtrader.reportviews.currencypair;

import java.util.List;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Transaction;

public class CurrencypairWithTransaction {

  public double sumAmountFrom = 0d;
  public double sumAmountTo = 0d;

  public double gainTo = 0d;
  public double gainFrom = 0d;

  public Currencypair currencypair;
  public List<Transaction> transactionList;

  public CurrencypairWithTransaction cwtReverse;

  public CurrencypairWithTransaction(Currencypair currencypair) {
    this.currencypair = currencypair;
  }

  @Override
  public String toString() {
    return "CurrencypairWithTransaction [sumAmountFrom=" + sumAmountFrom + ", sumAmountTo=" + sumAmountTo + ", gainTo="
        + gainTo + ", gainFrom=" + gainFrom + ", currencypair=" + currencypair + "]";
  }

}
