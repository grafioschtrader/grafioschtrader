package grafioschtrader.entities;

import java.time.LocalDate;

import grafiosch.common.PropertyAlwaysUpdatable;
import grafioschtrader.algo.strategy.model.StrategyHelper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Top-level entry point in the algo hierarchy for a tenant. Links a named algo configuration to a watchlist and
 * defines the simulation date range. The {@code activatable} flag controls whether the configuration is eligible
 * for live alarm evaluation. Children (asset-class and security levels) are loaded separately.
 */
@Schema(description = """
    Top-level algo configuration for a tenant. Links a named algo setup to a watchlist and defines simulation
    date range. Does not include depending children (asset class / security levels).""")
@Entity
@Table(name = AlgoTop.TABNAME)
@DiscriminatorValue(StrategyHelper.TOP_LEVEL_LETTER)
public class AlgoTop extends AlgoTopAssetSecurity {

  public static final String TABNAME = "algo_top";

  private static final long serialVersionUID = 1L;

  @Basic(optional = false)
  @NotNull
  @Size(min = 1, max = 40)
  @PropertyAlwaysUpdatable
  private String name;

//	@JoinColumn(name = "id_algo_assetclass_parent")
//	@OneToMany(fetch = FetchType.LAZY)
//	private List<AlgoAssetclass> algoAssetclassList;

  @Schema(description = """
      For the simulation, a watchlist must be linked to the top level.
      The corresponding securities can then be selected from this list.""")
  @Basic(optional = false)
  @Column(name = "id_watchlist")
  private Integer idWatchlist;

  @Schema(description = """
      A strategy or simulation must be checked for completeness before it is used.""")
  @Column(name = "activatable")
  private boolean activatable;

  @Schema(description = "Reference date from UC6 portfolio strategy creation. Used as transaction cutoff for simulation copies.")
  @Column(name = "reference_date")
  private LocalDate referenceDate;

  @Schema(description = "Start date of the simulation date range for backtesting.")
  @Column(name = "simulation_start_date")
  private LocalDate simulationStartDate;

  @Schema(description = "End date of the simulation date range for backtesting.")
  @Column(name = "simulation_end_date")
  private LocalDate simulationEndDate;

  @Transient
  public Float addedPercentage;

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

  public boolean isActivatable() {
    return activatable;
  }

  public void setActivatable(boolean activatable) {
    this.activatable = activatable;
  }

  public LocalDate getReferenceDate() {
    return referenceDate;
  }

  public void setReferenceDate(LocalDate referenceDate) {
    this.referenceDate = referenceDate;
  }

  public LocalDate getSimulationStartDate() {
    return simulationStartDate;
  }

  public void setSimulationStartDate(LocalDate simulationStartDate) {
    this.simulationStartDate = simulationStartDate;
  }

  public LocalDate getSimulationEndDate() {
    return simulationEndDate;
  }

  public void setSimulationEndDate(LocalDate simulationEndDate) {
    this.simulationEndDate = simulationEndDate;
  }

  @Override
  public String toString() {
    return "AlgoTop [name=" + name + ", idWatchlist=" + idWatchlist
        + ", activatable=" + activatable + ", idAlgoAssetclassSecurity=" + idAlgoAssetclassSecurity + ", idTenant="
        + idTenant + ", percentage=" + percentage + "]";
  }

}
