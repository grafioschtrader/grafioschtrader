package grafioschtrader.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import grafioschtrader.GlobalConstants;
import grafioschtrader.types.CreateType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.NamedStoredProcedureQuery;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureParameter;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = TradingDaysMinus.TABNAME)
@NamedStoredProcedureQuery(name = "TradingDaysMinusKey.copyTradingMinusToOtherStockexchange", procedureName = "copyTradingMinusToOtherStockexchange", parameters = {
    @StoredProcedureParameter(mode = ParameterMode.IN, name = "sourceIdStockexchange", type = Integer.class),
    @StoredProcedureParameter(mode = ParameterMode.IN, name = "targetIdStockexchange", type = Integer.class),
    @StoredProcedureParameter(mode = ParameterMode.IN, name = "dateFrom", type = LocalDate.class),
    @StoredProcedureParameter(mode = ParameterMode.IN, name = "dateTo", type = LocalDate.class) })
@NamedStoredProcedureQuery(name = "TradingDaysMinusKey.updCalendarStockexchangeByIndex", procedureName = "updCalendarStockexchangeByIndex")

public class TradingDaysMinus {

  public static final String TABNAME = "trading_days_minus";

  @JsonIgnore
  @EmbeddedId
  private TradingDaysMinusKey tradingDaysMinusKey;

  @Schema(description = "Who has crated this EOD record")
  @Column(name = "create_type")
  @NotNull
  private byte createType;

  public TradingDaysMinus() {
  }

  public TradingDaysMinus(Integer idStockexchange, LocalDate tradingDate, CreateType createType) {
    this.tradingDaysMinusKey = new TradingDaysMinusKey(idStockexchange, tradingDate);
    this.createType = createType.getValue();
  }

  public TradingDaysMinus(Integer idStockexchange, LocalDate tradingDateMinus) {
    this(idStockexchange, tradingDateMinus, CreateType.ADD_MODIFIED_USER);
  }

  public CreateType getCreateType() {
    return CreateType.getCreateType(createType);
  }

  public void setCreateType(CreateType createType) {
    this.createType = createType.getValue();
  }

  public Integer getIdStockexchange() {
    return tradingDaysMinusKey.idStockexchange;
  }

  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate getTradingDateMinus() {
    return tradingDaysMinusKey.tradingDateMinus;
  }

  public static class TradingDaysMinusKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_stockexchange")
    public Integer idStockexchange;

    @Column(name = "trading_date_minus")
    public LocalDate tradingDateMinus;

    public TradingDaysMinusKey() {
    }

    public TradingDaysMinusKey(Integer idStockexchange, LocalDate tradingDateMinus) {
      this.idStockexchange = idStockexchange;
      this.tradingDateMinus = tradingDateMinus;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      TradingDaysMinusKey that = (TradingDaysMinusKey) o;
      return Objects.equals(idStockexchange, that.idStockexchange)
          && Objects.equals(tradingDateMinus, that.tradingDateMinus);
    }

    @Override
    public int hashCode() {
      return Objects.hash(idStockexchange, tradingDateMinus);
    }

  }
}
