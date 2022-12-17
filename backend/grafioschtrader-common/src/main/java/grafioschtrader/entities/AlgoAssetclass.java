package grafioschtrader.entities;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = AlgoAssetclass.TABNAME)
@DiscriminatorValue("A")
public class AlgoAssetclass extends AlgoAssetclassSecurity {

  public static final String TABNAME = "algo_assetclass";

  private static final long serialVersionUID = 1L;

  @Column(name = "id_algo_assetclass_security_p")
  private Integer idAlgoAssetclassParent;

  @JoinColumn(name = "id_asset_class", referencedColumnName = "id_asset_class")
  @ManyToOne
  private Assetclass assetclass;

  public AlgoAssetclass() {
  }

  public AlgoAssetclass(Integer idTenant, Integer idAlgoAssetclassParent, Assetclass assetclass, Float percentage) {
    this.idTenant = idTenant;
    this.idAlgoAssetclassParent = idAlgoAssetclassParent;
    this.assetclass = assetclass;
    this.percentage = percentage;
  }

  /**
   * List of securities to this asset class which may be used or have a strategy
   */
  @JoinColumn(name = "id_algo_assetclass_security_p")
  @OneToMany(cascade = CascadeType.ALL)
  private List<AlgoSecurity> algoSecurityList;

  @Transient
  private Float addedPercentage;

  public Float getAddedPercentage() {
    addedPercentage = (float) algoSecurityList.stream().mapToDouble(algoSecurity -> algoSecurity.getPercentage()).sum();
    return addedPercentage;
  }

  public Integer getIdAlgoAssetclassParent() {
    return idAlgoAssetclassParent;
  }

  public void setIdAlgoAssetclassParent(Integer idAlgoAssetclassParent) {
    this.idAlgoAssetclassParent = idAlgoAssetclassParent;
  }

  public Assetclass getAssetclass() {
    return assetclass;
  }

  public void setAssetclass(Assetclass assetclass) {
    this.assetclass = assetclass;
  }

  public List<AlgoSecurity> getAlgoSecurityList() {
    return algoSecurityList;
  }

  public void setAlgoSecurityList(List<AlgoSecurity> algoSecurityList) {
    this.algoSecurityList = algoSecurityList;
  }

}
