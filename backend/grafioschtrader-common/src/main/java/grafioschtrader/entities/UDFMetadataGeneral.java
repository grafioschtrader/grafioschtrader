package grafioschtrader.entities;

import java.util.Arrays;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Schema(description = "User defined fields for all tables that have not been specifically defined.")
@Entity
@Table(name = UDFMetadataGeneral.TABNAME)
public class UDFMetadataGeneral extends UDFMetadata {
  public static final String TABNAME = "udf_metadata_general";

  @Schema(description = "The name of the entity to which this UDF refers.")
  @Column(name = "entity")
  @NotNull
  private String entity;

  public UDFMetadataGeneral() {
  }

  public String getEntity() {
    return entity;
  }

  public void setEntity(String entity) {
    this.entity = entity;
  }

  @Override
  public String toString() {
    return "UDFMetadataGeneral [entity=" + entity + ", idUser=" + idUser + ", udfSpecialType=" + udfSpecialType
        + ", description=" + description + ", descriptionHelp=" + descriptionHelp + ", udfDataType=" + udfDataType
        + ", fieldSize=" + fieldSize + ", uiOrder=" + uiOrder + ", getIdUser()=" + getIdUser()
        + ", getUdfSpecialType()=" + getUdfSpecialType() + ", getDescription()=" + getDescription()
        + ", getDescriptionHelp()=" + getDescriptionHelp() + ", getUdfDataType()=" + getUdfDataType()
        + ", getIdUDFMetadata()=" + getIdUDFMetadata() + ", getFieldSize()=" + getFieldSize() + ", getFieldSizeSufix()="
        + getFieldSizeSuffix() + ", getUiOrder()=" + getUiOrder() + ", getId()=" + getId() + ", getFieldLength()="
        + Arrays.toString(getFieldLength()) + "]";
  }


}
