package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Represents a watchlist with security status information")
public interface WatchlistSecurityStatus {
  
  @Schema(description = "The unique identifier of the watchlist.")
  public Integer getIdWatchlist();
  
  @Schema(description = "Indicates whether the watchlist contains any securities (1 = has securities, 0 = empty).")
  public Integer getHasSecurity(); 
}
