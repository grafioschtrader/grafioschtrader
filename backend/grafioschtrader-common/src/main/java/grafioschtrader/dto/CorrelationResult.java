package grafioschtrader.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The calculated result of correlation set")
public class CorrelationResult {

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Schema(description = "Oldest date which all instruments have a closing price.")
  public final LocalDate firstAvailableDate;
  
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Schema(description = "Latest date which all instruments have a closing price.")
  public final LocalDate lastAvailableDate;
  
  @Schema(description = "The instruments of this correlation set.")
  public CorrelationInstrument correlationInstruments[];
  @Schema(description = "If there are no overlapping closing prices, these objects will be created.")
  public List<MinMaxDateHistoryquote> mmdhList = new ArrayList<>(); 

  public CorrelationResult(LocalDate firstAvailableDate, LocalDate lastAvailableDate) {
    this.firstAvailableDate = firstAvailableDate;
    this.lastAvailableDate = lastAvailableDate;
  }

  public static class CorrelationInstrument {
    public final Integer idSecuritycurrency;
    public final double correlations[];
    

    public CorrelationInstrument(Integer idSecuritycurrency, double[] correlations) {
      this.idSecuritycurrency = idSecuritycurrency;
      this.correlations = new double[correlations.length];
      for (int i = 0; i < correlations.length; i++) {
        this.correlations[i] = DataHelper.round(correlations[i], 3);
      }
    }
  }
  
  @Schema(description = "Contains the most recent and oldest closing prices of the instruments.")
  public static class MinMaxDateHistoryquote {
    public final Integer idSecuritycurrency;
    
    @Schema(description = "The oldest date of the closing price.")
    @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
    public final LocalDate minDate;
    
    @Schema(description = "The date of the most recent closing price.")
    @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
    public final LocalDate maxDate;
    
    public MinMaxDateHistoryquote(Integer idSecuritycurrency, LocalDate minDate, LocalDate maxDate) {
      this.idSecuritycurrency = idSecuritycurrency;
      this.minDate = minDate;
      this.maxDate = maxDate;
    }
    
  }
}
