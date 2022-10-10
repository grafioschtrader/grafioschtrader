package grafioschtrader.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;

public class LastpricesExchange {

  @Schema(description = "The supplier could neither write nor read prices.")
  public int notWriteNorReadCount;
  
  public Collection<LastpriceSecurity> lastpriceSecurities = new ArrayList<>();
  public Collection<LastpriceCurrency> lastpriceCurrencies = new ArrayList<>();

  public void addSecurity(String isin, String currency, Date timestamp, Double open, Double low, Double high,
      Double last, Long volume) {
  }

  public static class LastpriceSecurity extends Lastprice {

    public String isin;
    public String currency;

    public LastpriceSecurity(String isin, String currency, Date timestamp, Double open, Double low, Double high,
        Double last, Long volume) {
      super(timestamp, open, low, high, last, volume);
      this.isin = isin;
      this.currency = currency;
    }
  }

  public static class LastpriceCurrency extends Lastprice {
    public String fromCurrency;
    public String toCurrency;

    public LastpriceCurrency(String fromCurrency, String toCurrency, Date timestamp, Double open, Double low,
        Double high, Double last, Long volume) {
      super(timestamp, open, low, high, last, volume);
      this.fromCurrency = fromCurrency;
      this.toCurrency = toCurrency;
    }
  }

  public static class Lastprice {
    public Lastprice(Date timestamp, Double open, Double low, Double high, Double last, Long volume) {
      this.timestamp = timestamp;
      this.open = open;
      this.low = low;
      this.high = high;
      this.last = last;
      this.volume = volume;
    }

    @Schema(description = "Time of the last instraday price update")
    public Date timestamp;

    @Schema(description = "Opening price for the last or current trading day")
    public Double open;

    @Schema(description = "Lowest price for the last or current trading day.")
    public Double low;

    @Schema(description = "Higest price for the last or current trading day.")
    public Double high;

    @Schema(description = "The most current price - possibly with after hour trade.")
    public Double last;

    @Schema(description = "The traded volume for this trading day. Cryptocurrencies can also have a volume.")
    public Long volume;
  }
}
