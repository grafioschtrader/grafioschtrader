package grafioschtrader.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import io.swagger.v3.oas.annotations.media.Schema;

public interface ISecuritycurrencyIdDateClose {
  Integer getIdSecuritycurrency();

  @Schema(type = "string", description = "Date as string, format yyyy-mm-dd", example = "2020-04-16", required = true)
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  Date getDate();

  double getClose();
}
