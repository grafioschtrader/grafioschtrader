package grafioschtrader.entities;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import grafioschtrader.dto.AlgoTopReadiness;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = """
    Top-level entry point in the algo hierarchy for a tenant. Optionally links a named configuration to a watchlist and gates
    live alarm evaluation for all alerts below it. Children are loaded separately. Simulation dates are stored with
    the simulation environment and its run because this shared hierarchy remains editable.""")
@Entity
@Table(name = AlgoTop.TABNAME)
@DiscriminatorValue(StrategyHelper.TOP_LEVEL_LETTER)
public class AlgoTop extends AlgoTopAssetSecurity {

  public static final String TABNAME = "algo_top";

  private static final long serialVersionUID = 1L;

  @Schema(description = "User-defined name of this algo hierarchy")
  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 40)
  @PropertyAlwaysUpdatable
  private String name;

  @Schema(description = """
      Optional watchlist supplying securities for watchlist-based strategies and selection.
      Hierarchies generated from portfolio holdings have no linked watchlist.""")
  @Column(name = "id_watchlist")
  private Integer idWatchlist;

  @Schema(description = """
      Date whose end-of-day holdings this allocation was generated from. A simulation of this allocation must start
      on the following calendar day and initialize by copying the portfolio or liquidating it to cash.""")
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Column(name = "reference_date")
  private LocalDate referenceDate;

  @Schema(description = "Sum of the target percentages of this hierarchy's direct children", accessMode = Schema.AccessMode.READ_ONLY)
  @Transient
  public Float addedPercentage;

  @Schema(description = """
      Whether this strategy can be used for a simulation, a replay and the rebalancing comparison, derived when the
      strategy is read. Null where the reading endpoint does not evaluate it.""", accessMode = Schema.AccessMode.READ_ONLY)
  @Transient
  public AlgoTopReadiness readiness;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getIdWatchlist() {
    return idWatchlist;
  }

  public void setIdWatchlist(Integer idWatchlist) {
    this.idWatchlist = idWatchlist;
  }

  public LocalDate getReferenceDate() {
    return referenceDate;
  }

  public void setReferenceDate(LocalDate referenceDate) {
    this.referenceDate = referenceDate;
  }

  @Override
  public String toString() {
    return "AlgoTop [name=" + name + ", idWatchlist=" + idWatchlist + ", idAlgoAssetclassSecurity=" + idAlgoAssetclassSecurity + ", idTenant=" + idTenant + ", percentage="
        + percentage + "]";
  }

}
