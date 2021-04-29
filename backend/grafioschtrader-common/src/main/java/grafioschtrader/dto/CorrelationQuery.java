package grafioschtrader.dto;

import java.time.LocalDate;

public class CorrelationQuery {

  Integer[] IdSecurityCurrency;
  LocalDate fromDate;
  LocalDate toDate;
  byte periodType;
  byte rollingDurationType;

}
