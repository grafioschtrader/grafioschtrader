package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.PropertyAlwaysUpdatable;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.types.SamplingPeriodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = CorrelationSet.TABNAME)
public class CorrelationSet extends TenantBaseID implements Serializable {

  private static final long serialVersionUID = 1L;

  public static final String TABNAME = "correlation_set";
  public static final String TABNAME_CORRELATION_INSTRUMENT = "correlation_instrument";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Basic(optional = false)
  @Column(name = "id_correlation_set")
  private Integer idCorrelationSet;

  @JsonIgnore
  @Column(name = "id_tenant")
  private Integer idTenant;

  @Schema(description = "The correlation set is used for the recognition of the corresponding correlation matrix by the user.")
  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 25)
  @PropertyAlwaysUpdatable
  @Column(name = "name")
  private String name;

  @Column(name = "note")
  @Size(max = GlobalConstants.FID_MAX_LETTERS)
  @PropertyAlwaysUpdatable
  private String note;

  @Schema(description = "This allows you to restrict the period with respect to the start date.")
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "date_from")
  @PropertyAlwaysUpdatable
  private LocalDate dateFrom;

  @Schema(description = "This allows you to restrict the period with respect to the to date.")
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  @Column(name = "date_to")
  @PropertyAlwaysUpdatable
  private LocalDate dateTo;

  @Schema(description = "Sampling period of returns for correlation calculations")
  @NotNull
  @Column(name = "sampling_period")
  @PropertyAlwaysUpdatable
  private byte samplingPeriod;

  @Schema(description = "Number of trading days or month used for rolling correlation calculations")
  @Column(name = "rolling")
  @PropertyAlwaysUpdatable
  private Byte rolling;

  @Schema(description = "Normalize historical prices of securities to a currency determined by the system.")
  @NotNull
  @Column(name = "adjust_currency")
  @PropertyAlwaysUpdatable
  private boolean adjustCurrency;

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @JoinTable(name = TABNAME_CORRELATION_INSTRUMENT, joinColumns = {
      @JoinColumn(name = "id_correlation_set", referencedColumnName = "id_correlation_set") }, inverseJoinColumns = {
          @JoinColumn(name = "id_securitycurrency", referencedColumnName = "id_securitycurrency") })
  @ManyToMany(fetch = FetchType.EAGER)
  private List<Securitycurrency<?>> securitycurrencyList;

  private transient boolean isSortedByNames;

  public CorrelationSet() {
  }

  public CorrelationSet(Integer idTenant, String name, Integer idCorrelationSet, LocalDate dateFrom, LocalDate dateTo,
      byte samplingPeriod, Byte rolling, boolean adjustCurrency) {
    this.name = name;
    this.idTenant = idTenant;
    this.idCorrelationSet = idCorrelationSet;
    this.dateFrom = dateFrom;
    this.dateTo = dateTo;
    this.samplingPeriod = samplingPeriod;
    this.rolling = rolling;
    this.adjustCurrency = adjustCurrency;
  }

  @JsonIgnore
  @Override
  public Integer getId() {
    return idCorrelationSet;
  }

  public SamplingPeriodType getSamplingPeriod() {
    return SamplingPeriodType.getSamplingPeriodTypeByValue(samplingPeriod);
  }

  public void setSamplingPeriod(SamplingPeriodType setSamplingPeriod) {
    this.samplingPeriod = setSamplingPeriod.getValue();
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

  public LocalDate getDateFrom() {
    return dateFrom;
  }

  public void setDateFrom(LocalDate dateFrom) {
    this.dateFrom = dateFrom;
  }

  public LocalDate getDateTo() {
    return dateTo;
  }

  public void setDateTo(LocalDate dateTo) {
    this.dateTo = dateTo;
  }

  public Byte getRolling() {
    return rolling;
  }

  public void setRolling(Byte rolling) {
    this.rolling = rolling;
  }

  public Integer getIdCorrelationSet() {
    return idCorrelationSet;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer idTenant) {
    this.idTenant = idTenant;
  }

  public List<Securitycurrency<?>> getSecuritycurrencyList() {
    if (securitycurrencyList != null && !isSortedByNames) {
      Collections.sort(securitycurrencyList, Comparator.comparing(Securitycurrency::getName));
      isSortedByNames = true;
    }
    return securitycurrencyList;
  }

  @JsonIgnore
  public List<Security> getSecurityList() {
    return securitycurrencyList == null ? Collections.emptyList()
        : securitycurrencyList.stream().filter(sc -> sc instanceof Security).map(Security.class::cast)
            .collect(Collectors.toList());
  }

  public void setSecuritycurrencyList(List<Securitycurrency<?>> securitycurrencyList) {
    this.securitycurrencyList = securitycurrencyList;
  }

  public boolean isAdjustCurrency() {
    return adjustCurrency;
  }

  public void setAdjustCurrency(boolean adjustCurrency) {
    this.adjustCurrency = adjustCurrency;
  }

  @Override
  public String toString() {
    return "CorrelationSet [idCorrelationSet=" + idCorrelationSet + ", idTenant=" + idTenant + ", name=" + name
        + ", note=" + note + ", dateFrom=" + dateFrom + ", dateTo=" + dateTo + ", samplingPeriod=" + samplingPeriod
        + ", rolling=" + rolling + ", securitycurrencyList=" + securitycurrencyList + "]";
  }

  public void removeInstrument(Integer idSecuritycurrency) {
    securitycurrencyList.removeIf(s -> s.idSecuritycurrency.equals(idSecuritycurrency));
  }

  public void validateBeforeSave() {

    switch (getSamplingPeriod()) {
    case DAILY_RETURNS:
      validateAgainstAllowedPeriodDefinition(GlobalConstants.CORR_DAILY);
      if (dateFrom != null) {
        validateFromToDateMinPeriods(dateFrom.plusDays(GlobalConstants.REQUIRED_MIN_PERIODS));
      }
      break;
    case MONTHLY_RETURNS:
      validateAgainstAllowedPeriodDefinition(GlobalConstants.CORR_MONTHLY);
      if (dateFrom != null) {
        validateFromToDateMinPeriods(dateFrom.plusMonths(GlobalConstants.REQUIRED_MIN_PERIODS));
      }
      break;
    default:
      if (dateFrom != null) {
        validateFromToDateMinPeriods(dateFrom.plusYears(GlobalConstants.REQUIRED_MIN_PERIODS));
      }
      this.rolling = null;
    }

  }

  private void validateAgainstAllowedPeriodDefinition(String stepMinMaxDefinition) {
    int[] stepMinMax = Arrays.stream(stepMinMaxDefinition.split(",")).mapToInt(Integer::parseInt).toArray();
    Set<Byte> possibleValues = new HashSet<>();
    for (int i = stepMinMax[1]; i <= stepMinMax[2]; i += stepMinMax[0]) {
      possibleValues.add((byte) i);
    }
    if (!possibleValues.contains(rolling)) {
      throw new DataViolationException("rolling", "gt.correlation.rolling", null);
    }
  }

  private void validateFromToDateMinPeriods(LocalDate minDateTo) {

    if ((dateTo != null && dateTo.isBefore(minDateTo)) || LocalDate.now().isBefore(minDateTo)) {
      throw new DataViolationException("date.to", "gt.dateto.min.period",
          new Object[] { GlobalConstants.REQUIRED_MIN_PERIODS });
    }
  }
}
