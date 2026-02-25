package grafioschtrader.entities;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.BaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Maps a single field from a data provider's response to a target entity field. For FS_HISTORY endpoints, target
 * fields are: date, open, high, low, close, volume. For FS_INTRA endpoints: last, open, high, low, volume,
 * prevClose, changePercentage, timestamp.
 */
@Entity
@Table(name = GenericConnectorFieldMapping.TABNAME)
@Schema(description = """
    Maps a response field from a data provider to a target entity field. The sourceExpression meaning depends \
    on the response format: JSON property name/path, CSV column index, regex group number, or CSS selector.""")
public class GenericConnectorFieldMapping extends BaseID<Integer> implements Serializable {

  public static final String TABNAME = "generic_connector_field_mapping";
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_field_mapping")
  private Integer idFieldMapping;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "id_endpoint")
  @JsonIgnore
  private GenericConnectorEndpoint endpoint;

  @NotNull
  @Size(max = 20)
  @Column(name = "target_field")
  @PropertyAlwaysUpdatable
  @Schema(description = """
      Target entity field name. For FS_HISTORY: date, open, high, low, close, volume. \
      For FS_INTRA: last, open, high, low, volume, prevClose, changePercentage, timestamp.""")
  private String targetField;

  @NotNull
  @Size(max = 255)
  @Column(name = "source_expression")
  @PropertyAlwaysUpdatable
  @Schema(description = """
      Source expression for extracting the value. Meaning depends on response format: \
      JSON = property name or dot-path, CSV = ignored (use csvColumnIndex), \
      HTML REGEX_GROUPS = capture group number (1-based), HTML MULTI_SELECTOR = CSS selector.""")
  private String sourceExpression;

  @Column(name = "csv_column_index")
  @PropertyAlwaysUpdatable
  @Schema(description = "0-based column index for CSV responses or position index for HTML SPLIT_POSITIONS mode")
  private Short csvColumnIndex;

  @Size(max = 64)
  @Column(name = "divider_expression")
  @PropertyAlwaysUpdatable
  @Schema(description = "Optional divider to apply to the parsed value, e.g. '100' for custom unit conversion")
  private String dividerExpression;

  @Column(name = "is_required")
  @PropertyAlwaysUpdatable
  @Schema(description = "Whether this field must be present for the record to be valid")
  private boolean required;

  @Override
  public Integer getId() {
    return idFieldMapping;
  }

  public Integer getIdFieldMapping() {
    return idFieldMapping;
  }

  public void setIdFieldMapping(Integer idFieldMapping) {
    this.idFieldMapping = idFieldMapping;
  }

  public GenericConnectorEndpoint getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(GenericConnectorEndpoint endpoint) {
    this.endpoint = endpoint;
  }

  public String getTargetField() {
    return targetField;
  }

  public void setTargetField(String targetField) {
    this.targetField = targetField;
  }

  public String getSourceExpression() {
    return sourceExpression;
  }

  public void setSourceExpression(String sourceExpression) {
    this.sourceExpression = sourceExpression;
  }

  public Short getCsvColumnIndex() {
    return csvColumnIndex;
  }

  public void setCsvColumnIndex(Short csvColumnIndex) {
    this.csvColumnIndex = csvColumnIndex;
  }

  public String getDividerExpression() {
    return dividerExpression;
  }

  public void setDividerExpression(String dividerExpression) {
    this.dividerExpression = dividerExpression;
  }

  public boolean isRequired() {
    return required;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }
}
