package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

public interface IDateAndClose {
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Schema(description = "Date of close")
  LocalDate getDate();

  @Schema(description = "Close price")
  Double getClose();
}
