package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    One step of the total return adjustment of a price series. Every price dated before the ex-date is multiplied by
    the cumulative factor; a date on or after the youngest ex-date keeps its price. The client needs the steps to place
    real trade prices, such as transaction markers, on an adjusted line.""")
public class TotalReturnFactor {

  @Schema(description = "Ex-date of a dividend")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  public final LocalDate exDate;

  @Schema(description = """
      Product of the factors 1 - dividend / previous close of this and of every younger ex-date. It applies to each
      date before this ex-date and after the next older one.""")
  public final double factor;

  public TotalReturnFactor(LocalDate exDate, double factor) {
    this.exDate = exDate;
    this.factor = factor;
  }
}
