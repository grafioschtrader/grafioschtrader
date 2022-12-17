package grafioschtrader.entities;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = SecurityDerivedLink.TABNAME)
public class SecurityDerivedLink implements Serializable {

  public static final String TABNAME = "security_derived_link";

  private static final long serialVersionUID = 1L;

  @EmbeddedId
  private SecurityDerivedLinkKey idEm;

  @Column(name = "id_link_securitycurrency")
  private Integer idLinkSecuritycurrency;

  public static final List<String> ALLOWED_VAR_NAMES_LINKS = SecurityDerivedLink.getAllowVarNameLinks();

  public static final int ALLOWED_NUMBER_OF_VAR_NAMES = 5;

  public static final String FIRST_VAR_NAME_LETTER = "o";

  public Integer getIdSecuritycurrency() {
    return idEm.idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    idEm.idSecuritycurrency = idSecuritycurrency;
  }

  public String getVarName() {
    return idEm.varName;
  }

  public void setVarName(String varName) {
    idEm.varName = varName;
  }

  public SecurityDerivedLink() {
    this.idEm = new SecurityDerivedLinkKey();
  }

  public Integer getIdLinkSecuritycurrency() {
    return idLinkSecuritycurrency;
  }

  public static class SecurityDerivedLinkKey implements Serializable {
    private static final long serialVersionUID = 1L;

    public SecurityDerivedLinkKey() {
    }

    public SecurityDerivedLinkKey(Integer idSecuritycurrency) {
      this.idSecuritycurrency = idSecuritycurrency;
    }

    @Column(name = "id_securitycurrency")
    private Integer idSecuritycurrency;

    @Column(name = "var_name")
    private String varName;

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      SecurityDerivedLinkKey that = (SecurityDerivedLinkKey) o;
      return Objects.equals(idSecuritycurrency, that.idSecuritycurrency) && Objects.equals(varName, that.varName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(idSecuritycurrency, varName);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    SecurityDerivedLink that = (SecurityDerivedLink) o;
    return idEm.equals(that.idEm) && Objects.equals(idLinkSecuritycurrency, that.idLinkSecuritycurrency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idEm.hashCode(), idLinkSecuritycurrency);
  }

  public static List<String> getAllowVarNameLinks() {
    List<String> allowedVarNames = new ArrayList<>();
    int firtVarName = SecurityDerivedLink.FIRST_VAR_NAME_LETTER.charAt(0);
    for (int i = 0; i < SecurityDerivedLink.ALLOWED_NUMBER_OF_VAR_NAMES; i++) {
      allowedVarNames.add(String.valueOf((char) (firtVarName + i)));
    }
    return allowedVarNames;

  }

}
