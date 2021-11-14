package grafioschtrader.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import grafioschtrader.entities.Currencypair;
import io.swagger.v3.oas.annotations.media.Schema;

public class CrossRateRequest {
  @Schema(description = "Contains the required currencies of the security like 'CHF'")
  public List<String> securityCurrencyList;
  @Schema(description = "Contains the existing currency pairs like 'CHF/USD' as an array")
  public String existingCurrencies[];

  public void setSecurityCurrencyList(List<String> securityCurrencyList) {
    this.securityCurrencyList = securityCurrencyList;
  }

  public void setExistingCurrencies(String[] existingCurrencies) {
    this.existingCurrencies = existingCurrencies;
  }

  public List<Currencypair> getExistingCurrencies() {
    List<Currencypair> currencypairs = new ArrayList<>();
    for (String existingCurrency : existingCurrencies) {
      String[] fromTo = existingCurrency.split(Pattern.quote("|"));
      currencypairs.add(new Currencypair(fromTo[0], fromTo[1]));
    }
    return currencypairs;
  }

  public boolean needNewCurrencypair(String mainCurrency) {
    if (securityCurrencyList.size() == 1 && existingCurrencies.length == 0) {
      return true;
    } else {
      List<Currencypair> ec = getExistingCurrencies();
      for (String sc : securityCurrencyList) {
        if (ec.stream().filter(cp -> cp.getFromCurrency().equals(mainCurrency) && cp.getToCurrency().equals(sc)
            || cp.getFromCurrency().equals(sc) && cp.getToCurrency().equals(mainCurrency)).findFirst().isEmpty()) {
          return true;
        }
      }
      return false;
    }
  }
}
