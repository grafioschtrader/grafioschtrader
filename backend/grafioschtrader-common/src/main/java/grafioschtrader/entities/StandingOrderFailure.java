package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

/**
 * Records a single execution failure of a {@link StandingOrder}. Business errors (no price, zero units) go into
 * {@code businessError}; unexpected runtime exceptions go into {@code unexpectedError} (truncated to
 * {@value #MAX_SIZE_UNEXPECTED_ERROR} characters). Tenant scoping is implicit via FK to standing_order.
 */
@Schema(description = """
    Persisted failure record for a standing order execution attempt. Contains either a business-level error message
    or a truncated stack trace from an unexpected runtime exception.""")
@Entity
@Table(name = StandingOrderFailure.TABNAME)
public class StandingOrderFailure implements Serializable {

  public static final String TABNAME = "standing_order_failure";
  public static final int MAX_SIZE_UNEXPECTED_ERROR = 4096;

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_standing_order_failure")
  private Integer idStandingOrderFailure;

  @Column(name = "id_standing_order", nullable = false)
  private Integer idStandingOrder;

  @Schema(description = "The date the standing order was scheduled to execute on")
  @Column(name = "execution_date", nullable = false)
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  private LocalDate executionDate;

  @Schema(description = "Expected business error (no price available, zero units, negative balance)")
  @Column(name = "business_error")
  @Size(max = 2000)
  private String businessError;

  @Schema(description = "Unexpected runtime exception stack trace, truncated to 4096 characters")
  @Column(name = "unexpected_error")
  @Size(max = MAX_SIZE_UNEXPECTED_ERROR)
  private String unexpectedError;

  @Column(name = "created_at", insertable = false, updatable = false)
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
  private LocalDateTime createdAt;

  public StandingOrderFailure() {
  }

  public StandingOrderFailure(Integer idStandingOrder, LocalDate executionDate, String businessError,
      String unexpectedError) {
    this.idStandingOrder = idStandingOrder;
    this.executionDate = executionDate;
    this.businessError = businessError;
    this.unexpectedError = unexpectedError != null
        ? unexpectedError.substring(0, Math.min(unexpectedError.length(), MAX_SIZE_UNEXPECTED_ERROR))
        : null;
  }

  public Integer getIdStandingOrderFailure() {
    return idStandingOrderFailure;
  }

  public void setIdStandingOrderFailure(Integer idStandingOrderFailure) {
    this.idStandingOrderFailure = idStandingOrderFailure;
  }

  public Integer getIdStandingOrder() {
    return idStandingOrder;
  }

  public void setIdStandingOrder(Integer idStandingOrder) {
    this.idStandingOrder = idStandingOrder;
  }

  public LocalDate getExecutionDate() {
    return executionDate;
  }

  public void setExecutionDate(LocalDate executionDate) {
    this.executionDate = executionDate;
  }

  public String getBusinessError() {
    return businessError;
  }

  public void setBusinessError(String businessError) {
    this.businessError = businessError;
  }

  public String getUnexpectedError() {
    return unexpectedError;
  }

  public void setUnexpectedError(String unexpectedError) {
    this.unexpectedError = unexpectedError;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
