package grafioschtrader.reportviews.performance;

import io.swagger.v3.oas.annotations.media.Schema;

/**
* Represents a period step with holiday or missing data classification.
* 
* <p>
* This class serves as a base container for period steps that may represent
* holidays, missing data days, or other non-trading periods within performance
* analysis windows. It provides the foundation for more specific period step
* implementations.
* </p>
*/
public class PeriodStepMissingHoliday {
  @Schema(description = "Type of day classification - normal trading day, holiday, or missing data")
  public HolidayMissing holidayMissing;

  public PeriodStepMissingHoliday(HolidayMissing holidayMissing) {
    this.holidayMissing = holidayMissing;
  }

}
