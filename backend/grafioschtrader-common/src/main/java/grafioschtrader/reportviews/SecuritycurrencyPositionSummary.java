package grafioschtrader.reportviews;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.entities.Securitycurrency;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contains the close price of a currency pair or security.
 *
 * @param <T>
 */
@Schema(description = "Abstract base class for financial instrument position summaries containing latest pricing information")
public abstract class SecuritycurrencyPositionSummary<T extends Securitycurrency<?>> {

  /**
   * Reference to the underlying financial instrument (security or currency pair) for which this position summary
   * provides pricing information.
   */
  @JsonIgnore
  public T securitycurrency;

  @Schema(description = "Latest available price for the financial instrument, may be real-time or historical")
  public Double closePrice = null;
  
  @Schema(description = "Timestamp when the close price was determined, indicating data freshness and validity")
  public Date closeDate;
}
