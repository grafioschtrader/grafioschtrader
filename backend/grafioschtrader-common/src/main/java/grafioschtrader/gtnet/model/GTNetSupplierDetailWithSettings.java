package grafioschtrader.gtnet.model;

import grafiosch.entities.GTNetSupplierDetail;
import grafioschtrader.entities.GTNetSupplierDetailHist;
import grafioschtrader.entities.GTNetSupplierDetailLast;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Wraps a {@link GTNetSupplierDetail} with its optional child settings.
 * Since the parent entity (in grafiosch-base) cannot hold direct references to these grafioschtrader-common
 * entities, this DTO combines them for the REST response.
 */
@Schema(description = """
    Combines a GTNetSupplierDetail with its optional historical and intraday settings.
    For HISTORICAL_PRICES entries, histSettings is populated; for LAST_PRICE entries, lastSettings is populated.""")
public class GTNetSupplierDetailWithSettings {

  @Schema(description = "The supplier detail entry (entity kind, entity ID, etc.)")
  private GTNetSupplierDetail detail;

  @Schema(description = "Historical price data quality settings. Null for LAST_PRICE entries.")
  private GTNetSupplierDetailHist histSettings;

  @Schema(description = "Intraday price settings. Null for HISTORICAL_PRICES entries.")
  private GTNetSupplierDetailLast lastSettings;

  public GTNetSupplierDetailWithSettings() {
  }

  public GTNetSupplierDetailWithSettings(GTNetSupplierDetail detail, GTNetSupplierDetailHist histSettings,
      GTNetSupplierDetailLast lastSettings) {
    this.detail = detail;
    this.histSettings = histSettings;
    this.lastSettings = lastSettings;
  }

  public GTNetSupplierDetail getDetail() {
    return detail;
  }

  public void setDetail(GTNetSupplierDetail detail) {
    this.detail = detail;
  }

  public GTNetSupplierDetailHist getHistSettings() {
    return histSettings;
  }

  public void setHistSettings(GTNetSupplierDetailHist histSettings) {
    this.histSettings = histSettings;
  }

  public GTNetSupplierDetailLast getLastSettings() {
    return lastSettings;
  }

  public void setLastSettings(GTNetSupplierDetailLast lastSettings) {
    this.lastSettings = lastSettings;
  }
}
