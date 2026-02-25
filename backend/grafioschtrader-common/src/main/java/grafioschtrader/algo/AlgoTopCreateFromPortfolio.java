package grafioschtrader.algo;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;

/**
 * DTO for auto-generating an AlgoTop hierarchy from the tenant's current portfolio holdings at a given reference date.
 * Extends {@link AlgoTopCreate} so the existing {@code saveOnlyAttributes()} dispatch can detect this subclass.
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
