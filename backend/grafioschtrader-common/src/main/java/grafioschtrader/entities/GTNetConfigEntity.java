package grafioschtrader.entities;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.BaseID;
import grafioschtrader.gtnet.SupplierConsumerLogTypes;
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
      Indicates whether data exchange is enabled with this remote instance. When true, bidirectional
      data exchange is active. Set to true when exchange requests are accepted.""")
  @Column(name = "exchange")
  private boolean exchange = true;
  

  @Schema(description = """
      Logging level for this server acting as supplier (receiving requests from remote).
      SCL_OFF=no logging, SCL_OVERVIEW=exchange statistics, SCL_DETAIL=detailed audit trail.""")
  @Column(name = "supplier_log")
  @PropertyAlwaysUpdatable
  private byte supplierLog = SupplierConsumerLogTypes.SCL_OVERVIEW.getValue();

  @Schema(description = """
      Logging level for this server acting as consumer (sending requests to remote).
      SCL_OFF=no logging, SCL_OVERVIEW=exchange statistics, SCL_DETAIL=detailed audit trail.""")
  @Column(name = "consumer_log")
  @PropertyAlwaysUpdatable
  private byte consumerLog = SupplierConsumerLogTypes.SCL_OVERVIEW.getValue();

  @Schema(description = """
      Priority level for using this remote domain as a supplier. A value of 0 means that this supplier is not used.
      Values greater than 0 indicate the priority (lower numbers = higher priority). Multiple suppliers
      with different priorities can be configured for failover scenarios.""")
  @Column(name = "consumer_usage")
  @PropertyAlwaysUpdatable
  private byte consumerUsage = 10;
  

  public boolean isExchange() {
    return exchange;
  }

  public void setExchange(boolean exchange) {
    this.exchange = exchange;
  }

  public Integer getIdGtNetEntity() {
    return idGtNetEntity;
  }

  public void setIdGtNetEntity(Integer idGtNetEntity) {
    this.idGtNetEntity = idGtNetEntity;
  }

  public SupplierConsumerLogTypes getSupplierLog() {
    return SupplierConsumerLogTypes.getSupplierConsumerLogType(supplierLog);
  }

  public void setSupplierLog(SupplierConsumerLogTypes supplierLog) {
    this.supplierLog = supplierLog.getValue();
  }

  public SupplierConsumerLogTypes getConsumerLog() {
    return SupplierConsumerLogTypes.getSupplierConsumerLogType(consumerLog);
  }

  public void setConsumerLog(SupplierConsumerLogTypes consumerLog) {
    this.consumerLog = consumerLog.getValue();
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
