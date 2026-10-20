package grafioschtrader.entities;

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
@Schema(description = "An indicator expression shown on all or one watchlist of its tenant")
public class WatchlistIndicatorColumn extends TenantBaseID {
  public static final String TABNAME = "watchlist_indicator_column";
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
  @PropertyAlwaysUpdatable
  @Column(name = "id_watchlist")
  @DynamicFormField(uiOrder = "3", helps = DynamicFormPropertyHelps.SELECT_OPTIONS)
  private Integer idWatchlist;
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

  public Integer getIdWatchlist() {
    return idWatchlist;
  }

  public void setIdWatchlist(Integer value) {
    idWatchlist = value;
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
