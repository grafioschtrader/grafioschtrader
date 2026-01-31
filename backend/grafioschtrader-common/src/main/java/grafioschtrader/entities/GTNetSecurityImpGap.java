package grafioschtrader.entities;

import grafiosch.entities.BaseID;
import grafioschtrader.types.GapCodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entity representing a gap (mismatch) identified during GTNet security import. When a security
 * from a GTNet peer cannot be fully matched to local configuration, gap records document what
 * specifically didn't match (asset class, connectors, etc.).
 */
@Schema(description = """
    Gap entity for GTNet security import. Records what didn't match when importing a security
    from a GTNet peer. Each gap represents one type of mismatch (asset class or a specific
    connector type) along with the expected configuration from the remote peer.
    """)
@Entity
@Table(name = GTNetSecurityImpGap.TABNAME)
public class GTNetSecurityImpGap extends BaseID<Integer> {

  public static final String TABNAME = "gt_net_security_imp_gap";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_gt_net_security_imp_gap")
  private Integer idGtNetSecurityImpGap;

  @Schema(description = "Reference to the parent import position.")
  @NotNull
  @Column(name = "id_gt_net_security_imp_pos")
  private Integer idGtNetSecurityImpPos;

  @Schema(description = "Reference to the GTNet peer from which the security lookup result came.")
  @NotNull
  @Column(name = "id_gt_net")
  private Integer idGtNet;

  @Schema(description = """
      Code indicating what type of gap/mismatch occurred:
      0 = ASSET_CLASS (asset class combination not matching),
      1 = INTRADAY_CONNECTOR (intraday connector not available),
      2 = HISTORY_CONNECTOR (history connector not available),
      3 = DIVIDEND_CONNECTOR (dividend connector not available),
      4 = SPLIT_CONNECTOR (split connector not available).
      """)
  @NotNull
  @Column(name = "gap_code")
  private byte gapCode;

  @Schema(description = """
      Human-readable description of the expected configuration from the remote peer.
      For asset class: 'categoryType / subCategory / specialInvestmentInstrument' (e.g., 'EQUITIES / REGIONAL / ETF').
      For connectors: the connector family name (e.g., 'yahoo', 'finnhub').
      Always in English regardless of user's language preference.
      """)
  @NotNull
  @Size(max = 1000)
  @Column(name = "gap_message")
  private String gapMessage;

  public GTNetSecurityImpGap() {
    super();
  }

  public GTNetSecurityImpGap(Integer idGtNetSecurityImpPos, Integer idGtNet, GapCodeType gapCodeType, String gapMessage) {
    super();
    this.idGtNetSecurityImpPos = idGtNetSecurityImpPos;
    this.idGtNet = idGtNet;
    this.gapCode = gapCodeType.getValue();
    this.gapMessage = gapMessage;
  }

  public Integer getIdGtNetSecurityImpGap() {
    return idGtNetSecurityImpGap;
  }

  public void setIdGtNetSecurityImpGap(Integer idGtNetSecurityImpGap) {
    this.idGtNetSecurityImpGap = idGtNetSecurityImpGap;
  }

  public Integer getIdGtNetSecurityImpPos() {
    return idGtNetSecurityImpPos;
  }

  public void setIdGtNetSecurityImpPos(Integer idGtNetSecurityImpPos) {
    this.idGtNetSecurityImpPos = idGtNetSecurityImpPos;
  }

  public Integer getIdGtNet() {
    return idGtNet;
  }

  public void setIdGtNet(Integer idGtNet) {
    this.idGtNet = idGtNet;
  }

  public byte getGapCode() {
    return gapCode;
  }

  public void setGapCode(byte gapCode) {
    this.gapCode = gapCode;
  }

  public GapCodeType getGapCodeType() {
    return GapCodeType.getGapCodeTypeByValue(this.gapCode);
  }

  public void setGapCodeType(GapCodeType gapCodeType) {
    this.gapCode = gapCodeType.getValue();
  }

  public String getGapMessage() {
    return gapMessage;
  }

  public void setGapMessage(String gapMessage) {
    this.gapMessage = gapMessage;
  }

  @Override
  public Integer getId() {
    return this.idGtNetSecurityImpGap;
  }

  @Override
  public String toString() {
    return "GTNetSecurityImpGap [idGtNetSecurityImpGap=" + idGtNetSecurityImpGap
        + ", idGtNetSecurityImpPos=" + idGtNetSecurityImpPos
        + ", idGtNet=" + idGtNet
        + ", gapCode=" + gapCode
        + ", gapMessage=" + gapMessage + "]";
  }
}
