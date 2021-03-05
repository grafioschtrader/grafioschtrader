package grafioschtrader.transform;

import java.util.Objects;

public class HistoryquoteQualityIds {

  public String idConnectorHistory;
  public Integer idStockexchange;
  public Byte categoryType;
  public Byte specialInvestmentInstrument;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HistoryquoteQualityIds that = (HistoryquoteQualityIds) o;
    return idConnectorHistory.equals(that.idConnectorHistory) &&
            idStockexchange.equals(that.idStockexchange) &&
            categoryType.equals(that.categoryType) &&
            specialInvestmentInstrument.equals(that.specialInvestmentInstrument);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idConnectorHistory, idStockexchange, categoryType, specialInvestmentInstrument);
  }
}
