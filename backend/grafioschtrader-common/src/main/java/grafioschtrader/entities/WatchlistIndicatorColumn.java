package grafioschtrader.entities;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.DynamicFormField;
import grafiosch.common.PropertyAlwaysUpdatable;
import grafiosch.dynamic.model.DynamicFormPropertyHelps;
import grafiosch.entities.TenantBaseID;
import grafioschtrader.types.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

/** Tenant-owned expression and presentation settings; computed cell values are never persisted. */
@Entity
@Table(name = WatchlistIndicatorColumn.TABNAME)
@Schema(description = """
    An indicator expression shown on all or selected watchlists of its tenant, as a table column of the indicator tab,
    in its expanded row, or both""")
public class WatchlistIndicatorColumn extends TenantBaseID {
  public static final String TABNAME = "watchlist_indicator_column";
  public static final String TABNAME_WATCHLIST = "watchlist_indicator_column_watchlist";
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_watchlist_indicator_column")
  private Integer idWatchlistIndicatorColumn;
  @JsonIgnore
  @Column(name = "id_tenant")
  private Integer idTenant;
  @Column(name = "result_type")
  private byte resultType;
  @Column(name = "ui_order")
  private short uiOrder;
  @PropertyAlwaysUpdatable
  @Column(name = "mark_colors")
  @DynamicFormField(uiOrder = "8", helps = DynamicFormPropertyHelps.SELECT_OPTIONS)
  private byte markColors;
  @Schema(description = """
      The watchlists on which the column is shown. An empty set means all watchlists of the tenant, including those
      created later.""")
  @PropertyAlwaysUpdatable
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = TABNAME_WATCHLIST, joinColumns = @JoinColumn(name = "id_watchlist_indicator_column"))
  @Column(name = "id_watchlist")
  private Set<Integer> idWatchlists = new HashSet<>();
  @PropertyAlwaysUpdatable
  @Column(name = "label")
  @DynamicFormField(uiOrder = "1", labelKey = "WIC_LABEL")
  @NotBlank
  @Size(max = 40)
  private String label;
  @PropertyAlwaysUpdatable
  @Column(name = "expression")
  @DynamicFormField(uiOrder = "2")
  @NotBlank
  @Size(max = 500)
  private String expression;
  @PropertyAlwaysUpdatable
  @Column(name = "id_benchmark")
  @DynamicFormField(uiOrder = "4")
  private Integer idBenchmark;
  @PropertyAlwaysUpdatable
  @Column(name = "decimals")
  @DynamicFormField(uiOrder = "5")
  @Min(0)
  @Max(6)
  private byte decimals = 2;
  @PropertyAlwaysUpdatable
  @Column(name = "lower_mark")
  @DynamicFormField(uiOrder = "6", fractionLimit = 6)
  private Double lowerMark;
  @PropertyAlwaysUpdatable
  @Column(name = "upper_mark")
  @DynamicFormField(uiOrder = "7", fractionLimit = 6)
  private Double upperMark;
  @Schema(description = "Whether the indicator tab shows the value as a table column")
  @PropertyAlwaysUpdatable
  @Column(name = "show_in_table")
  @DynamicFormField(uiOrder = "9")
  private boolean showInTable = true;
  @Schema(description = """
      Whether the indicator tab shows the value in the expanded row of an instrument, grouped into technical and
      fundamental indicators. At least one of showInTable and showInExpansion is set.""")
  @PropertyAlwaysUpdatable
  @Column(name = "show_in_expansion")
  @DynamicFormField(uiOrder = "10")
  private boolean showInExpansion;

  @Override
  @JsonIgnore
  public Integer getId() {
    return idWatchlistIndicatorColumn;
  }

  public Integer getIdWatchlistIndicatorColumn() {
    return idWatchlistIndicatorColumn;
  }

  public void setIdWatchlistIndicatorColumn(Integer id) {
    idWatchlistIndicatorColumn = id;
  }

  @Override
  public Integer getIdTenant() {
    return idTenant;
  }

  @Override
  public void setIdTenant(Integer id) {
    idTenant = id;
  }

  public IndicatorResultType getResultType() {
    return IndicatorResultType.getByValue(resultType);
  }

  public void setResultType(IndicatorResultType type) {
    resultType = type.getValue();
  }

  public IndicatorMarkColors getMarkColors() {
    return IndicatorMarkColors.getByValue(markColors);
  }

  public void setMarkColors(IndicatorMarkColors colors) {
    markColors = colors.getValue();
  }

  public short getUiOrder() {
    return uiOrder;
  }

  public void setUiOrder(short order) {
    uiOrder = order;
  }

  public Set<Integer> getIdWatchlists() {
    return idWatchlists;
  }

  public void setIdWatchlists(Set<Integer> value) {
    idWatchlists = value == null ? new HashSet<>() : new HashSet<>(value);
  }

  public boolean isShowInTable() {
    return showInTable;
  }

  public void setShowInTable(boolean value) {
    showInTable = value;
  }

  public boolean isShowInExpansion() {
    return showInExpansion;
  }

  public void setShowInExpansion(boolean value) {
    showInExpansion = value;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String value) {
    label = value;
  }

  public String getExpression() {
    return expression;
  }

  public void setExpression(String value) {
    expression = value;
  }

  public Integer getIdBenchmark() {
    return idBenchmark;
  }

  public void setIdBenchmark(Integer value) {
    idBenchmark = value;
  }

  public byte getDecimals() {
    return decimals;
  }

  public void setDecimals(byte value) {
    decimals = value;
  }

  public Double getLowerMark() {
    return lowerMark;
  }

  public void setLowerMark(Double value) {
    lowerMark = value;
  }

  public Double getUpperMark() {
    return upperMark;
  }

  public void setUpperMark(Double value) {
    upperMark = value;
  }
}
