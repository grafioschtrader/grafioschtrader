package grafiosch.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.gtnet.AcceptRequestTypes;
import grafiosch.gtnet.GTNetServerStateTypes;
import grafiosch.gtnet.IExchangeKindType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Entity
@Table(name = GTNetEntity.TABNAME)
@Schema(description = """
    This defines what can be exchanged with an instance. One entry per exchangeable information object.""")
public class GTNetEntity extends BaseID<Integer> {

  public static final String TABNAME = "gt_net_entity";

  /**
   * Registry for resolving enum names to byte values.
   * Applications must register their IExchangeKindType enum values during startup.
   */
  @Transient
  private static volatile IExchangeKindType[] exchangeKindTypes;

  /**
   * Registers the application-specific exchange kind types for JSON deserialization.
   * This must be called during application startup before any GTNetEntity instances
   * are deserialized from JSON.
   *
   * @param types the array of exchange kind type enum values from the application
   */
  public static void registerExchangeKindTypes(IExchangeKindType[] types) {
    exchangeKindTypes = types;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net_entity")
  private Integer idGtNetEntity;

  @Schema(description = "Reference to the parent GTNet domain entry")
  @Column(name = "id_gt_net", nullable = false, insertable = false, updatable = false)
  private Integer idGtNet;

  @Schema(description = "What type of information is provided? Stored as byte, application defines interpretation.")
  @Column(name = "entity_kind")
  private byte entityKind;

  @Schema(description = """
      Defines how this server handles incoming data exchange requests: AC_CLOSED (no requests),
      AC_OPEN (accepts requests), or AC_PUSH_OPEN (accepts requests and pushed updates).
      AC_PUSH_OPEN is only available for certain exchange kinds.""")
  @Column(name = "accept_request")
  @PropertyAlwaysUpdatable
  private byte acceptRequest;

  @Schema(description = """
      Server state for data sharing. Indicates whether the remote domain is available to provide
      this kind of data. Uses GTNetServerStateTypes enum values.""")
  @Column(name = "server_state")
  private byte serverState;

  @Schema(description = """
      Maximum number of items that can be transferred in a single request.
      For example, 300 means a maximum of 300 items per request. Valid range: 10-999.""")
  @Column(name = "max_limit")
  @Min(value = 10)
  @Max(value = 999)
  private Short maxLimit = 300;

  @Schema(description = "Entity-specific configuration for exchange settings, logging, and consumer usage")
  @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
  @PrimaryKeyJoinColumn(name = "id_gt_net_entity", referencedColumnName = "id_gt_net_entity")
  private GTNetConfigEntity gtNetConfigEntity;

  /**
   * Gets the raw byte value of the entity kind for JPA persistence.
   *
   * @return the entity kind as a byte value
   */
  @JsonIgnore
  public byte getEntityKindValue() {
    return entityKind;
  }

  /**
   * Sets the entity kind using a raw byte value.
   *
   * @param entityKind the entity kind byte value
   */
  @JsonIgnore
  public void setEntityKindValue(byte entityKind) {
    this.entityKind = entityKind;
  }

  /**
   * Gets the entity kind value for JSON serialization.
   * Returns the byte value which clients can interpret based on their enum definitions.
   *
   * @return the entity kind as a byte value
   */
  @JsonProperty("entityKind")
  public byte getEntityKind() {
    return entityKind;
  }

  /**
   * Sets the entity kind from JSON deserialization.
   * Accepts either a numeric byte value or a string enum name.
   * When a string is provided, it is resolved using the registered exchange kind types.
   *
   * @param value the entity kind as a Number (byte value) or String (enum name)
   * @throws IllegalArgumentException if the string value cannot be resolved to an exchange kind
   */
  @JsonSetter("entityKind")
  public void setEntityKind(Object value) {
    if (value instanceof Number number) {
      this.entityKind = number.byteValue();
    } else if (value instanceof String name) {
      this.entityKind = resolveEntityKindFromName(name);
    } else if (value != null) {
      throw new IllegalArgumentException("entityKind must be a number or string, got: " + value.getClass());
    }
  }

  /**
   * Resolves an entity kind enum name to its byte value using the registered exchange kind types.
   *
   * @param name the enum name (e.g., "HISTORICAL_PRICES")
   * @return the corresponding byte value
   * @throws IllegalArgumentException if the name cannot be resolved
   */
  private byte resolveEntityKindFromName(String name) {
    if (exchangeKindTypes != null) {
      for (IExchangeKindType type : exchangeKindTypes) {
        if (type.name().equals(name)) {
          return type.getValue();
        }
      }
    }
    throw new IllegalArgumentException("Unknown entityKind: " + name
        + ". Ensure GTNetEntity.registerExchangeKindTypes() was called during application startup.");
  }

  public Integer getIdGtNetEntity() {
    return idGtNetEntity;
  }

  public void setIdGtNetEntity(Integer idGtNetEntity) {
    this.idGtNetEntity = idGtNetEntity;
  }

  public AcceptRequestTypes getAcceptRequest() {
    return AcceptRequestTypes.getAcceptRequestType(acceptRequest);
  }

  public void setAcceptRequest(AcceptRequestTypes acceptRequest) {
    this.acceptRequest = acceptRequest.getValue();
  }

  /**
   * Checks if this entity accepts incoming data requests.
   *
   * @return true if acceptRequest is AC_OPEN or AC_PUSH_OPEN
   */
  public boolean isAccepting() {
    return getAcceptRequest().isAccepting();
  }

  public GTNetServerStateTypes getServerState() {
    return GTNetServerStateTypes.getGTNetServerStateType(serverState);
  }

  public void setServerState(GTNetServerStateTypes serverState) {
    this.serverState = serverState.getValue();
  }

  public Integer getIdGtNet() {
    return idGtNet;
  }

  public void setIdGtNet(Integer idGtNet) {
    this.idGtNet = idGtNet;
  }

  public GTNetConfigEntity getGtNetConfigEntity() {
    return gtNetConfigEntity;
  }

  public void setGtNetConfigEntity(GTNetConfigEntity gtNetConfigEntity) {
    this.gtNetConfigEntity = gtNetConfigEntity;
  }

  /**
   * Returns the existing GTNetConfigEntity or creates a new one if none exists.
   * For persisted entities, sets the config entity's ID; for new entities, the ID must be set after persistence.
   *
   * @return the existing or newly created GTNetConfigEntity
   */
  public GTNetConfigEntity getOrCreateConfigEntity() {
    if (gtNetConfigEntity == null) {
      gtNetConfigEntity = new GTNetConfigEntity();
      if (idGtNetEntity != null) {
        gtNetConfigEntity.setIdGtNetEntity(idGtNetEntity);
      }
    }
    return gtNetConfigEntity;
  }

  public Short getMaxLimit() {
    return maxLimit;
  }

  public void setMaxLimit(Short maxLimit) {
    this.maxLimit = maxLimit;
  }

  @Override
  public Integer getId() {
    return idGtNetEntity;
  }
}
