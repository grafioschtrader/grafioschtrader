package grafioschtrader.entities;

import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.common.PropertyAlwaysUpdatable;

/**
 * It contains the header information of the transaction import templates.
 * 
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = ImportTransactionPlatform.TABNAME)
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ImportTransactionPlatform extends Auditable {

  public static final String TABNAME = "imp_trans_platform";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_trans_imp_platform")
  private Integer idTransactionImportPlatform;

  @Basic(optional = false)
  @NotBlank
  @Size(min = 1, max = 32)
  @PropertyAlwaysUpdatable
  private String name;

  @Column(name = "id_csv_imp_impl")
  @PropertyAlwaysUpdatable
  private String idCsvImportImplementation;

  public Integer getIdTransactionImportPlatform() {
    return idTransactionImportPlatform;
  }

  public void setIdTransactionImportPlatform(Integer idTransactionImportPlatform) {
    this.idTransactionImportPlatform = idTransactionImportPlatform;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getIdCsvImportImplementation() {
    return idCsvImportImplementation;
  }

  public void setIdCsvImportImplementation(String idCsvImportImplementation) {
    this.idCsvImportImplementation = idCsvImportImplementation;
  }

  @Override
  @JsonIgnore
  public Integer getId() {
    return this.getIdTransactionImportPlatform();
  }

}
