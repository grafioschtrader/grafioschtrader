package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public interface StockexchangeHasSecurity {
  @Schema(description = "Id of stock exchange")
  Integer getId();

  @Schema(description = "0 no security, 1 has one or more security")
  Byte getS();
}
