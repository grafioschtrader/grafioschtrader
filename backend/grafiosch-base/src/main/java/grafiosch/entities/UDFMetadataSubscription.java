package grafiosch.entities;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.common.PropertyOnlyCreation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = UDFMetadataSubscription.TABNAME)
public class UDFMetadataSubscription extends UserBaseID {
  public static final String TABNAME = "udf_metadata_subscription";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_udf_metadata_subscription")
  private Integer idUDFMetadataSubscription;

  @NotNull
  @PropertyOnlyCreation
  @Column(name = "id_udf_metadata")
  private Integer idUDFMetadata;

  @Column(name = "id_user")
  private Integer idUser;

  @Min(1)
  @Max(99)
  @PropertyAlwaysUpdatable
  @Column(name = "ui_order")
  private byte uiOrder;

  @Override
  public Integer getId() {
    return idUDFMetadataSubscription;
  }

  public Integer getIdUDFMetadataSubscription() {
    return idUDFMetadataSubscription;
  }

  public void setIdUDFMetadataSubscription(Integer id) {
    this.idUDFMetadataSubscription = id;
  }

  public Integer getIdUDFMetadata() {
    return idUDFMetadata;
  }

  public void setIdUDFMetadata(Integer id) {
    this.idUDFMetadata = id;
  }

  @Override
  public Integer getIdUser() {
    return idUser;
  }

  @Override
  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  public byte getUiOrder() {
    return uiOrder;
  }

  public void setUiOrder(byte uiOrder) {
    this.uiOrder = uiOrder;
  }
}
