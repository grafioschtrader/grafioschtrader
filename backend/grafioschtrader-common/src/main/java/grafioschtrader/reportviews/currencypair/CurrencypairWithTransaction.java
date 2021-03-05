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

  public CurrencypairWithTransaction(Currencypair currencypair, List<Transaction> transactionList) {
    super();
    this.currencypair = currencypair;
    this.transactionList = transactionList;
  }

}
