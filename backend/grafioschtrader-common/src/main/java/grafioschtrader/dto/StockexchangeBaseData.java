package grafioschtrader.dto;

import java.util.List;

import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.StockexchangeMic;

public class StockexchangeBaseData {
  public List<Stockexchange> stockexchanges;
  public List<StockexchangeHasSecurity> hasSecurity;
  public List<StockexchangeMic> stockexchangeMics;
  public List<ValueKeyHtmlSelectOptions> countries;
  
  public StockexchangeBaseData(List<Stockexchange> stockexchanges, List<StockexchangeHasSecurity> hasSecurity,
      List<StockexchangeMic> stockexchangeMics, List<ValueKeyHtmlSelectOptions> countries) {
    super();
    this.stockexchanges = stockexchanges;
    this.hasSecurity = hasSecurity;
    this.stockexchangeMics = stockexchangeMics;
    this.countries = countries;
  }
  
}
