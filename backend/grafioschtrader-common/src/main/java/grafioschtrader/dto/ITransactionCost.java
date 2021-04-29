package grafioschtrader.dto;

public interface ITransactionCost {
  int getIdSecurityaccount();

  double getPrice();

  int getSpecInvestInstrument();

  int getCategoryType();

  int getIdStockexchange();

  double getTransactionCost();
}
