package grafioschtrader.entities;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
 * Custom HTTP header sent with requests for a generic connector. Supports the {apiKey} placeholder in the header value,
 * enabling API key authentication via headers (e.g., Authorization: Bearer {apiKey}).
 */
@Entity
@Table(name = GenericConnectorHttpHeader.TABNAME)
@Schema(description = """
    Custom HTTP header for a generic connector. Supports {apiKey} placeholder in header_value \
    for API key-based authentication via headers.""")
public class GenericConnectorHttpHeader extends BaseID<Integer> implements Serializable {

  public static final String TABNAME = "generic_connector_http_header";
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_http_header")
  private Integer idHttpHeader;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "id_generic_connector")
  @JsonIgnore
  private GenericConnectorDef genericConnectorDef;

  @NotNull
  @Size(max = 64)
  @Column(name = "header_name")
  @Schema(description = "HTTP header name, e.g. 'X-Api-Key', 'Authorization'")
  private String headerName;

  @NotNull
  @Size(max = 512)
  @Column(name = "header_value")
  @Schema(description = "HTTP header value. Supports {apiKey} placeholder, e.g. 'Bearer {apiKey}'")
  private String headerValue;

  @Override
  public Integer getId() {
    return idHttpHeader;
  }

  public Integer getIdHttpHeader() {
    return idHttpHeader;
  }

  public void setIdHttpHeader(Integer idHttpHeader) {
    this.idHttpHeader = idHttpHeader;
  }

  public GenericConnectorDef getGenericConnectorDef() {
    return genericConnectorDef;
  }

  public void setGenericConnectorDef(GenericConnectorDef genericConnectorDef) {
    this.genericConnectorDef = genericConnectorDef;
  }

  public String getHeaderName() {
    return headerName;
  }

  public void setHeaderName(String headerName) {
    this.headerName = headerName;
  }

  public String getHeaderValue() {
    return headerValue;
  }

  public void setHeaderValue(String headerValue) {
    this.headerValue = headerValue;
  }
}
