package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;

public class CorrelationResult {
  
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate firstAvailableDate;
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate lastAvailableDate;
  public CorrelationInstrument correlationInstruments[];
  
   
  public CorrelationResult(LocalDate firstAvailableDate, LocalDate lastAvailableDate) {
    this.firstAvailableDate = firstAvailableDate;
    this.lastAvailableDate = lastAvailableDate;
  }


  public static class CorrelationInstrument {
    public Integer idSecuritycurrency;
    public double correlations[];
    public double annualizedReturn;
    public double standardDeviation;
    public double maxPercentageChange;
    
    
    public CorrelationInstrument(Integer idSecuritycurrency, double[] correlations) {
      this.idSecuritycurrency = idSecuritycurrency;
      this.correlations = new double[correlations.length];
      for(int i = 0; i < correlations.length; i++) {
        this.correlations[i] = DataHelper.round(correlations[i], 3);
      }
    }
  }
}
