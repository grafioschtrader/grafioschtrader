package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.FundamentalMetric;
import grafioschtrader.types.FundamentalSource;
import grafioschtrader.types.FundamentalSourceCheck;
import grafioschtrader.types.FundamentalState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * One fundamental base value of a direct equity as one source delivered it, with its basis and the outcome of its
 * checks. The weekly load task is the only writer: it replaces the rows of every source that answered, records the
 * source checks in {@code sourceCheck} and lets the selection grade every row in {@code state}. A watchlist indicator
 * column binds an expression variable only from the row graded {@code CONFIRMED}, {@code CHECKED} or {@code SINGLE}.
 *
 * <p>
 * The table is derived data rebuilt by the load task, so it has no export definition, and it is not user-writable, so
 * it has no limit key. It is not a {@code BaseID} entity, which keeps it out of the derived daily CUD limits.
 * </p>
 */
@Entity
@Table(name = SecurityFundamental.TABNAME)
@IdClass(SecurityFundamental.SecurityFundamentalKey.class)
@Schema(description = "A fundamental base value of a security from one source, with period, checks and grade")
public class SecurityFundamental {
  public static final String TABNAME = "security_fundamental";

  @Id
  @Column(name = "id_securitycurrency")
  private Integer idSecuritycurrency;

  @Id
  @Column(name = "metric")
  private byte metric;

  @Id
  @Column(name = "source")
  private byte source;

  @Schema(description = "The value in full units; per-share values are per share of the listing")
  @Column(name = "value")
  private double value;

  @Schema(description = "ISO currency of the value, null for the share count and for ratios")
  @Column(name = "currency")
  private String currency;

  @Schema(description = "End of the reporting period the value describes, null when the source does not say")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "period_end")
  private LocalDate periodEnd;

  @Schema(description = "UTC time at which the load task received the answer of the source")
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_SECOND)
  @Column(name = "fetched_time")
  private LocalDateTime fetchedTime;

  @Column(name = "source_check")
  private byte sourceCheck;

  @Column(name = "state")
  private byte state;

  public SecurityFundamental() {
  }

  public SecurityFundamental(Integer idSecuritycurrency, FundamentalMetric metric, FundamentalSource source,
      double value, String currency, LocalDate periodEnd, LocalDateTime fetchedTime) {
    this.idSecuritycurrency = idSecuritycurrency;
    this.metric = metric.getValue();
    this.source = source.getValue();
    this.value = value;
    this.currency = currency;
    this.periodEnd = periodEnd;
    this.fetchedTime = fetchedTime;
    this.sourceCheck = FundamentalSourceCheck.NOT_CHECKED.getValue();
    this.state = FundamentalState.CANDIDATE.getValue();
  }

  public Integer getIdSecuritycurrency() {
    return idSecuritycurrency;
  }

  public void setIdSecuritycurrency(Integer idSecuritycurrency) {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  public FundamentalMetric getMetric() {
    return FundamentalMetric.getByValue(metric);
  }

  public void setMetric(FundamentalMetric metric) {
    this.metric = metric.getValue();
  }

  public FundamentalSource getSource() {
    return FundamentalSource.getByValue(source);
  }

  public void setSource(FundamentalSource source) {
    this.source = source.getValue();
  }

  public double getValue() {
    return value;
  }

  public void setValue(double value) {
    this.value = value;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public void setPeriodEnd(LocalDate periodEnd) {
    this.periodEnd = periodEnd;
  }

  public LocalDateTime getFetchedTime() {
    return fetchedTime;
  }

  public void setFetchedTime(LocalDateTime fetchedTime) {
    this.fetchedTime = fetchedTime;
  }

  public FundamentalSourceCheck getSourceCheck() {
    return FundamentalSourceCheck.getByValue(sourceCheck);
  }

  public void setSourceCheck(FundamentalSourceCheck sourceCheck) {
    this.sourceCheck = sourceCheck.getValue();
  }

  public FundamentalState getState() {
    return FundamentalState.getByValue(state);
  }

  public void setState(FundamentalState state) {
    this.state = state.getValue();
  }

  @Override
  public String toString() {
    return "SecurityFundamental [idSecuritycurrency=" + idSecuritycurrency + ", metric=" + getMetric() + ", source="
        + getSource() + ", value=" + value + ", currency=" + currency + ", periodEnd=" + periodEnd + ", sourceCheck="
        + getSourceCheck() + ", state=" + getState() + "]";
  }

  /** Composite primary key: one row per security, metric and source. */
  public static class SecurityFundamentalKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer idSecuritycurrency;
    private byte metric;
    private byte source;

    public SecurityFundamentalKey() {
    }

    public SecurityFundamentalKey(Integer idSecuritycurrency, byte metric, byte source) {
      this.idSecuritycurrency = idSecuritycurrency;
      this.metric = metric;
      this.source = source;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof SecurityFundamentalKey k && Objects.equals(idSecuritycurrency, k.idSecuritycurrency)
          && metric == k.metric && source == k.source;
    }

    @Override
    public int hashCode() {
      return Objects.hash(idSecuritycurrency, metric, source);
    }
  }
}
