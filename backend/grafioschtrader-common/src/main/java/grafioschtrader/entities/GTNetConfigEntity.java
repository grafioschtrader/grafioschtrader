package grafioschtrader.entities;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.BaseID;
import grafioschtrader.gtnet.GTNetExchangeStatusTypes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Schema(description = "Entity-specific configuration for exchange settings, logging, and consumer usage priority.")
@Entity
@Table(name = GTNetConfigEntity.TABNAME)
public class GTNetConfigEntity extends BaseID<Integer>  {
  public static final String TABNAME = "gt_net_config_entity";

  @Id
  @Schema(description = "Primary key, shared with GTNetEntity. References the parent GTNetEntity.")
  @Column(name = "id_gt_net_entity")
  private Integer idGtNetEntity; 
  
  
  @Schema(description = """
      Exchange status for entities with this remote instance. Determines the direction of the
      data flow: no exchange (0), send only (1), receive only (2), or bidirectional (3). Updated when
      exchange requests are accepted.""")
  @Column(name = "exchange")
  private byte exchange;
  

  @Schema(description = """
      Enables detailed logging of intraday price updates from this remote domain. When true, each price change is
      recorded in GTNetLastpriceDetailLog, providing an audit trail of which client changed which prices and when.
      May impact performance when enabled for high-volume providers.""")
  @Column(name = "use_detail_log")
  @PropertyAlwaysUpdatable
  private boolean useDetailLog;
  
  @Schema(description = """
     Priority level for using this remote domain as a supplier. A value of 0 means that this supplier is not used. Values greater than 0 indicate the priority (lower numbers = higher priority). Multiple suppliers
     with different priorities can be configured for failover scenarios.""")
  @Column(name = "consumer_usage")
  @PropertyAlwaysUpdatable
  private byte consumerUsage;
  

  public GTNetExchangeStatusTypes getExchange() {
    return GTNetExchangeStatusTypes.getGTNetExchangeStatusType(exchange);
  }

  public void setExchange(GTNetExchangeStatusTypes exchange) {
    this.exchange = exchange.getValue();
  }

  public Integer getIdGtNetEntity() {
    return idGtNetEntity;
  }

  public void setIdGtNetEntity(Integer idGtNetEntity) {
    this.idGtNetEntity = idGtNetEntity;
  }

  public boolean isUseDetailLog() {
    return useDetailLog;
  }

  public void setUseDetailLog(boolean useDetailLog) {
    this.useDetailLog = useDetailLog;
  }

  public byte getConsumerUsage() {
    return consumerUsage;
  }

  public void setConsumerUsage(byte consumerUsage) {
    this.consumerUsage = consumerUsage;
  }

  @Override
  public Integer getId() {
    return idGtNetEntity;
  }
}
