package grafioschtrader.entities;

import static jakarta.persistence.InheritanceType.JOINED;

import com.fasterxml.jackson.annotation.JsonProperty;

import grafioschtrader.types.UDFDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = UDFMetadata.TABNAME)
@Inheritance(strategy = JOINED)
@Schema(description = "A meta description is required for the user defined fields. This is the general description of this metadata.")
public abstract class UDFMetadata extends UserBaseID {

  public static final String TABNAME = "udf_metadata";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_udf_metadata")
  private Integer idUDFMetadata;
  
  @Schema(description = "Possibly this construct of the UDF is also used for system-wide input fields. In this case, the user with ID = 0 would be used.")
  @Column(name = "id_user")
  @NotNull
  private Integer idUser;

 
  @Schema(description = "This is the property name used by the user interface.")
  @Column(name = "description")
  @NotNull
  private String description;

  @Schema(description = "This optional help text is provided as a tooltip in the user interface of the property")
  @Column(name = "description_help")
  private String descriptionHelp;
 
  
  @Schema(description = "For validation and other purposes the data type is required")
  @Column(name = "udf_data_type")
  @NotNull
  private byte udfDataType;

  @Schema(description = "The field length must be limited")
  @Column(name = "field_size")
  @NotNull
  private String fieldSize;
  
  @Schema(description = "Controls the order of the fields during user input ")
  @Column(name = "ui_order")
  @NotNull
  private byte uiOrder;

  public UDFMetadata() {
  }

  public Integer getIdUser() {
    return idUser;
  }


  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }


  public String getDescription() {
    return description;
  }


  public void setDescription(String description) {
    this.description = description;
  }


  public String getDescriptionHelp() {
    return descriptionHelp;
  }


  public void setDescriptionHelp(String descriptionHelp) {
    this.descriptionHelp = descriptionHelp;
  }

 
  public UDFDataType getUdfDataType() {
    return UDFDataType.getUDFDataType(udfDataType);
  }


  public void setUdfDataType(UDFDataType udfDataType) {
    this.udfDataType = udfDataType.getValue();
  }


  public Integer getIdUDFMetadata() {
    return idUDFMetadata;
  }


  public String getFieldSize() {
    return fieldSize;
  }


  public void setFieldSize(String fieldSize) {
    this.fieldSize = fieldSize;
  }
  
  
  public byte getUiOrder() {
    return uiOrder;
  }

  public void setUiOrder(byte uiOrder) {
    this.uiOrder = uiOrder;
  }

  @Override
  public Integer getId() {
   return idUDFMetadata;
  }
  
}
