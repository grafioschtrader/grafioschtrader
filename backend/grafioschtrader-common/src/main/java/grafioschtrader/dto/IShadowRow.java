package grafioschtrader.dto;

import java.time.LocalDate;

public interface IShadowRow {
  LocalDate getTransferDate();

  LocalDate getDate();

  Double getClose();

  Double getOpen();

  Double getHigh();

  Double getLow();

  Long getVolume();

  Byte getCreateType();
}
