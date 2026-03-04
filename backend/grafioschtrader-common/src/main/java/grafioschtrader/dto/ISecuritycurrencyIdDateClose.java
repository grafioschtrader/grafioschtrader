package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

public interface ISecuritycurrencyIdDateClose {
  Integer getIdSecuritycurrency();

  @Schema(type = "string", description = "Date as string, format yyyy-mm-dd", example = "2020-04-16")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = BaseConstants.STANDARD_DATE_FORMAT)
  LocalDate getDate();

  double getClose();
}
