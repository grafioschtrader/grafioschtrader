package grafioschtrader.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import grafioschtrader.entities.Currencypair;

public class CrossRateRequest {
  public List<String> securityCurrencyList;
  public String existingCurrencies[];
  
  public void setSecurityCurrencyList(List<String> securityCurrencyList) {
    this.securityCurrencyList = securityCurrencyList;
  }
  public void setExistingCurrencies(String[] existingCurrencies) {
    this.existingCurrencies = existingCurrencies;
  }

  public List<Currencypair> getExistingCurrencies() {
    List<Currencypair> currencypairs = new ArrayList<>();
    for(String existingCurrency: existingCurrencies) {
      String[] fromTo = existingCurrency.split(Pattern.quote("|"));
      currencypairs.add(new Currencypair(fromTo[0], fromTo[1]));
    }
    return currencypairs;
  }
  
  public boolean needNewCurrencypair(String mainCurrency) {
    return (securityCurrencyList.size() == 1 && existingCurrencies.length == 0 
        && !securityCurrencyList.get(0).equals(mainCurrency)) || securityCurrencyList.size() > 1;   
  }
}