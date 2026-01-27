package grafioschtrader.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
import grafiosch.entities.TenantBaseID;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Header entity for organizing GTNet security import operations. Groups multiple security import
 * positions together, allowing users to batch lookup securities from GTNet peers.
 */
@Schema(description = """
    Header entity for GTNet security import operations. Groups multiple security import positions
    together for batch processing. Users can create named import sets with optional notes to
    organize their security lookup requests.
    """)
@Entity
@Table(name = GTNetSecurityImpHead.TABNAME)
public class GTNetSecurityImpHead extends TenantBaseID {

  public static final String TABNAME = "gt_net_security_imp_head";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_gt_net_security_imp_head")
  private Integer idGtNetSecurityImpHead;

  @Schema(description = "Reference to the tenant that owns this import set.")
  @JsonIgnore
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Schema(description = "User-defined name for the import set, used to identify and organize multiple import batches.")
  @Basic(optional = false)
  @NotBlank
  @Size(min = 1, max = 40)
  private String name;

  @Schema(description = "Optional notes or description for the import set.")
  @Column(name = "note")
  @Size(max = BaseConstants.FID_MAX_LETTERS)
  private String note;

  public GTNetSecurityImpHead() {
    super();
  }

  public GTNetSecurityImpHead(Integer idTenant, String name, String note) {
    super();
    this.idTenant = idTenant;
    this.name = name;
    this.note = note;
  }

  public Integer getIdGtNetSecurityImpHead() {
    return idGtNetSecurityImpHead;
  }

  public void setIdGtNetSecurityImpHead(Integer idGtNetSecurityImpHead) {
    this.idGtNetSecurityImpHead = idGtNetSecurityImpHead;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  @Override
  public Integer getId() {
    return this.idGtNetSecurityImpHead;
  }

  @Override
  public String toString() {
    return "GTNetSecurityImpHead [idGtNetSecurityImpHead=" + idGtNetSecurityImpHead + ", idTenant=" + idTenant
        + ", name=" + name + ", note=" + note + "]";
  }
}
