package grafioschtrader.dto;

import java.util.ArrayList;
import java.util.List;

import grafioschtrader.entities.Currencypair;

public class CrossRateResponse {

  public final String mainCurrency;
  public List<CurrenciesAndClosePrice> currenciesAndClosePrice = new ArrayList<>();

  public CrossRateResponse(String mainCurrency) {
    this.mainCurrency = mainCurrency;
  }

  public static class CurrenciesAndClosePrice {
    public final Currencypair currencypair;
    public final List<IDateAndClose> closeAndDateList;

    public CurrenciesAndClosePrice(Currencypair currencypair, List<IDateAndClose> closeAndDateList) {
      this.currencypair = currencypair;
      this.closeAndDateList = closeAndDateList;
    }

  }
}
