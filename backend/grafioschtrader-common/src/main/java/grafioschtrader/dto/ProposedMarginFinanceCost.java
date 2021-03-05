package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;

public class ProposedMarginFinanceCost {
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate untilDate;
  public int daysToPay;
  public double financeCost;
}
