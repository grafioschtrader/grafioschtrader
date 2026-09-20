package grafioschtrader.algo;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;

/**
 * DTO for auto-generating an AlgoTop hierarchy from the tenant's end-of-day portfolio holdings at a reference date. No
 * watchlist is required or linked; an inherited watchlist ID supplied by older clients is ignored. Extends
 * {@link AlgoTopCreate} so the existing {@code saveOnlyAttributes()} dispatch can detect this subclass.
 */
public class AlgoTopCreateFromPortfolio extends AlgoTopCreate {

  private static final long serialVersionUID = 1L;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  private LocalDate referenceDate;

  public LocalDate getReferenceDate() {
    return referenceDate;
  }

  public void setReferenceDate(LocalDate referenceDate) {
    this.referenceDate = referenceDate;
  }
}
