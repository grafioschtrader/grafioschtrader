package grafioschtrader.entities;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Schema(description = """
    A derived instrument can refer to more than one instrument.
    This entity contains the corresponding ID of the relevant security and the variable name.
    The historical closing price and the intraday prices of these securities are used for the calculation.""")
@Entity
@Table(name = SecurityDerivedLink.TABNAME)
public class SecurityDerivedLink implements Serializable {

  public static final String TABNAME = "security_derived_link";

  private static final long serialVersionUID = 1L;

  @EmbeddedId
  private SecurityDerivedLinkKey idEm;

  @Schema(description = "ID of the security which is referenced for the calculation.")
  @Column(name = "id_link_securitycurrency")
  private Integer idLinkSecuritycurrency;

  public static final List<String> ALLOWED_VAR_NAMES_LINKS = SecurityDerivedLink.getAllowedVarNameLinks();

  /**
   * The maximum permitted number of variables or linked securities.
   */
  public static final int ALLOWED_NUMBER_OF_VAR_NAMES = 5;

  /**
   * The variables are referenced by individual letters, starting with this letter in ascending order.    
   */
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

  /**
   * The ID of the security and the variable name are unique and are used as the key.
   */
  public static class SecurityDerivedLinkKey implements Serializable {
    private static final long serialVersionUID = 1L;

    public SecurityDerivedLinkKey() {
    }

    public SecurityDerivedLinkKey(Integer idSecuritycurrency) {
      this.idSecuritycurrency = idSecuritycurrency;
    }

    @Schema(description = "The ID of the derived instrument.")
    @Column(name = "id_securitycurrency")
    private Integer idSecuritycurrency;

    @Schema(description = "The variable name of the derived instrument.")
    @Column(name = "var_name")
    private String varName;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
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
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SecurityDerivedLink that = (SecurityDerivedLink) o;
    return idEm.equals(that.idEm) && Objects.equals(idLinkSecuritycurrency, that.idLinkSecuritycurrency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idEm.hashCode(), idLinkSecuritycurrency);
  }

  /**
   * Generate a list of allowed variable names starting from a defined initial
   * letter
   */
  public static List<String> getAllowedVarNameLinks() {
    return IntStream.range(0, SecurityDerivedLink.ALLOWED_NUMBER_OF_VAR_NAMES)
        .mapToObj(i -> String.valueOf((char) (SecurityDerivedLink.FIRST_VAR_NAME_LETTER.charAt(0) + i)))
        .collect(Collectors.toList());
  }

}
