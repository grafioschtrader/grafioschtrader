package grafioschtrader.entities;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.entities.Auditable;
import grafioschtrader.dto.InstrumentLogoRefDto;
import grafioschtrader.types.LogoScope;
import grafioschtrader.validation.ValidISIN;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Shared catalog entry for a single company or issuer SVG logo. Deployed logos reference a static file under
 * {@code assets/company-logos/} via {@link #assetPath}, while user-uploaded logos store sanitized SVG markup in
 * {@link #svgContent}. Multiple securities may reference the same entry (1:N).
 */
@Schema(description = "Shared company or issuer SVG logo entry (either deployed on disk or stored in the database)")
@Entity
@Table(name = InstrumentLogo.TABNAME)
public class InstrumentLogo extends Auditable implements Serializable {

  public static final String TABNAME = "instrument_logo";

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_instrument_logo")
  private Integer idInstrumentLogo;

  @Schema(description = "Optional unique ISIN (ISO 6166); null for issuer wordmarks or ISIN-less entries")
  @ValidISIN
  @Column(name = "isin")
  @PropertyAlwaysUpdatable
  private String isin;

  @Schema(description = "Unique display name of the company or issuer")
  @NotNull
  @Size(min = 1, max = 120)
  @Column(name = "name")
  @PropertyAlwaysUpdatable
  private String name;

  @Schema(description = "Scope of the logo: COMPANY (0) or ISSUER (1)")
  @Column(name = "logo_scope")
  @PropertyAlwaysUpdatable
  private byte logoScope;

  @Schema(description = "Relative path under assets/company-logos/ for deployed SVGs; null for DB-stored SVGs")
  @Size(max = 160)
  @Column(name = "asset_path")
  private String assetPath;

  @Schema(description = "Sanitized SVG markup for user-uploaded DB logos; write-only on JSON payloads")
  @Lob
  @Column(name = "svg_content", columnDefinition = "MEDIUMTEXT")
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @PropertyAlwaysUpdatable
  private String svgContent;

  @Schema(description = "Lowercase hex SHA-256 digest of the SVG content")
  @Column(name = "content_sha256", columnDefinition = "CHAR(64)")
  private String contentSha256;

  @Schema(description = "Newline-separated whole-token name patterns for automatic issuer matching")
  @Size(max = 500)
  @Column(name = "name_patterns")
  private String namePatterns;

  @Schema(description = "Newline-separated exact product-link hostnames for automatic issuer matching")
  @Size(max = 500)
  @Column(name = "hosts")
  private String hosts;

  @Schema(description = "Newline-separated product-link host suffixes (e.g. .vanguard.com) for issuer matching")
  @Size(max = 255)
  @Column(name = "host_suffixes")
  private String hostSuffixes;

  public InstrumentLogo() {
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idInstrumentLogo;
  }

  public Integer getIdInstrumentLogo() {
    return idInstrumentLogo;
  }

  public void setIdInstrumentLogo(Integer idInstrumentLogo) {
    this.idInstrumentLogo = idInstrumentLogo;
  }

  public String getIsin() {
    return isin;
  }

  public void setIsin(String isin) {
    this.isin = isin;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LogoScope getLogoScope() {
    LogoScope scope = LogoScope.getLogoScopeByValue(logoScope);
    return scope != null ? scope : LogoScope.COMPANY;
  }

  public void setLogoScope(LogoScope logoScope) {
    this.logoScope = (logoScope != null ? logoScope : LogoScope.COMPANY).getValue();
  }

  public String getAssetPath() {
    return assetPath;
  }

  public void setAssetPath(String assetPath) {
    this.assetPath = assetPath;
  }

  public String getSvgContent() {
    return svgContent;
  }

  public void setSvgContent(String svgContent) {
    this.svgContent = svgContent;
  }

  public String getContentSha256() {
    return contentSha256;
  }

  public void setContentSha256(String contentSha256) {
    this.contentSha256 = contentSha256;
  }

  public String getNamePatterns() {
    return namePatterns;
  }

  public void setNamePatterns(String namePatterns) {
    this.namePatterns = namePatterns;
  }

  public String getHosts() {
    return hosts;
  }

  public void setHosts(String hosts) {
    this.hosts = hosts;
  }

  public String getHostSuffixes() {
    return hostSuffixes;
  }

  public void setHostSuffixes(String hostSuffixes) {
    this.hostSuffixes = hostSuffixes;
  }

  @JsonIgnore
  public boolean isDeployed() {
    return assetPath != null;
  }

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public boolean isHasSvgContent() {
    return svgContent != null;
  }

  public InstrumentLogoRefDto toRefDto(boolean explicit) {
    String effectiveAssetPath = svgContent != null ? null : assetPath;
    return new InstrumentLogoRefDto(idInstrumentLogo, name, isin, getLogoScope(), effectiveAssetPath, contentSha256,
        explicit, getCreatedBy());
  }
}
