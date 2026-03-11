package grafioschtrader.entities;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.BaseConstants;
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

/**
 * Records a tenant's application (or reversal) of a SecurityAction ISIN change. Each tenant can apply a given
 * SecurityAction at most once; this entity tracks the resulting sell/buy transactions.
 */
@Schema(description = "Tracks a tenant's application of an ISIN change event, including the generated transactions.")
@Entity
@Table(name = SecurityActionApplication.TABNAME)
public class SecurityActionApplication extends BaseID<Integer> {

  public static final String TABNAME = "security_action_application";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_security_action_app")
  private Integer idSecurityActionApp;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "id_security_action", referencedColumnName = "id_security_action")
  @NotNull
  private SecurityAction securityAction;

  @JsonIgnore
  @Column(name = "id_tenant")
  @NotNull
  private Integer idTenant;

  @Schema(description = "System-created SELL transaction for the old security")
  @Column(name = "id_transaction_sell")
  private Integer idTransactionSell;

  @Schema(description = "System-created BUY transaction for the new security")
  @Column(name = "id_transaction_buy")
  private Integer idTransactionBuy;

  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  @Column(name = "applied_time")
  @NotNull
  private LocalDateTime appliedTime;

  @Schema(description = "Whether this application has been reversed")
  @Column(name = "is_reversed", columnDefinition = "TINYINT", length = 1)
  private boolean reversed;

  @Override
  public Integer getId() {
    return idSecurityActionApp;
  }

  public Integer getIdSecurityActionApp() {
    return idSecurityActionApp;
  }

  public void setIdSecurityActionApp(Integer idSecurityActionApp) {
    this.idSecurityActionApp = idSecurityActionApp;
  }

  public SecurityAction getSecurityAction() {
    return securityAction;
  }

  public void setSecurityAction(SecurityAction securityAction) {
    this.securityAction = securityAction;
  }

  public Integer getIdTenant() {
    return idTenant;
  }

  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public Integer getIdTransactionSell() {
    return idTransactionSell;
  }

  public void setIdTransactionSell(Integer idTransactionSell) {
    this.idTransactionSell = idTransactionSell;
  }

  public Integer getIdTransactionBuy() {
    return idTransactionBuy;
  }

  public void setIdTransactionBuy(Integer idTransactionBuy) {
    this.idTransactionBuy = idTransactionBuy;
  }

  public LocalDateTime getAppliedTime() {
    return appliedTime;
  }

  public void setAppliedTime(LocalDateTime appliedTime) {
    this.appliedTime = appliedTime;
  }

  public boolean isReversed() {
    return reversed;
  }

  public void setReversed(boolean reversed) {
    this.reversed = reversed;
  }
}
