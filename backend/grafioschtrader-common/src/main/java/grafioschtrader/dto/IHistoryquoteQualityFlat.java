package grafioschtrader.dto;

public interface IHistoryquoteQualityFlat {
  String getIdConnectorHistory();

  // Stockexchange name
  String getStockexchangeName();

  int getIdStockexchange();

  byte getCategoryType();

  byte getSpecialInvestmentInstrument();

  int getNumberOfSecurities();

  int getActiveNowSecurities();

  int getConnectorCreated();

  int getManualImported();

  int getFilledLinear();

  double getQualityPercentage();
}
