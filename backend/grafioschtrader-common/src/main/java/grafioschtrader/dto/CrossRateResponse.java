package grafioschtrader.dto;

import java.util.ArrayList;
import java.util.List;

import grafioschtrader.entities.Currencypair;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response containing required currency pairs and their corresponding close prices for cross rate calculation.")
public class CrossRateResponse {

  @Schema(description = "The main currency of the tenant from which all cross rates are derived.", example = "CHF")
  public final String mainCurrency;
  @Schema(description = "List of currency pairs and their most recent closing prices.")
  public List<CurrenciesAndClosePrice> currenciesAndClosePrice = new ArrayList<>();

 
  public CrossRateResponse(String mainCurrency) {
    this.mainCurrency = mainCurrency;
  }

  public static class CurrenciesAndClosePrice {
    @Schema(description = "Currency pair associated with the price information.")
    public final Currencypair currencypair;
    public final List<IDateAndClose> closeAndDateList;

    public CurrenciesAndClosePrice(Currencypair currencypair, List<IDateAndClose> closeAndDateList) {
      this.currencypair = currencypair;
      this.closeAndDateList = closeAndDateList;
    }

  }
}
