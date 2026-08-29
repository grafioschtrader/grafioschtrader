package grafiosch.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.gtnet.SupplierConsumerLogTypes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Entity-specific configuration of one {@link GTNetEntity}, sharing its primary key.
 *
 * <p>
 * This is the dependent side of the shared key: {@code id_gt_net_entity} is at the same time the primary key and the
 * foreign key to {@code gt_net_entity}, which is what the database states with
 * {@code FK_GtNetConfigEntity_GtNetEntity ... ON DELETE CASCADE}. The row is therefore established by the
 * {@link #gtNetEntity} association and not by assigning {@link #idGtNetEntity}: {@code @MapsId} derives the identifier
 * from the association at flush time. Declaring the association on the other side instead would tell Hibernate that
 * {@code gt_net_entity} holds the foreign key, which reverses its insert and delete ordering against the real
 * constraint - a peer could then not be deleted at all, because the database cascade had already removed this row when
 * Hibernate issued its own delete for it.
 * </p>
 */
@Schema(description = "Entity-specific configuration for exchange settings, logging, and consumer usage priority.")
@Entity
@Table(name = GTNetConfigEntity.TABNAME)
public class GTNetConfigEntity extends BaseID<Integer> {
  public static final String TABNAME = "gt_net_config_entity";

  @Id
  @Schema(description = "Primary key, shared with GTNetEntity. References the parent GTNetEntity.")
  @Column(name = "id_gt_net_entity")
  private Integer idGtNetEntity;

  /**
   * The owning end of the shared primary key. Never serialized: it is reached from {@link GTNetEntity} and writing it
   * out would let Jackson recurse back into the parent.
   */
  @JsonIgnore
  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "id_gt_net_entity")
  private GTNetEntity gtNetEntity;

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

  /**
   * Returns the parent entity this configuration belongs to.
   *
   * @return the owning GTNetEntity, null as long as the configuration was never attached to one
   */
  public GTNetEntity getGtNetEntity() {
    return gtNetEntity;
  }

  /**
   * Attaches this configuration to its parent entity. Normally not called directly -
   * {@link GTNetEntity#setGtNetConfigEntity(GTNetConfigEntity)} and {@link GTNetEntity#getOrCreateConfigEntity()} do it
   * - but required wherever a configuration is built without going through the parent, such as the upsert of
   * {@code GTNetConfigEntityResource}.
   *
   * @param gtNetEntity the parent entity whose primary key this configuration shares
   */
  public void setGtNetEntity(GTNetEntity gtNetEntity) {
    this.gtNetEntity = gtNetEntity;
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
