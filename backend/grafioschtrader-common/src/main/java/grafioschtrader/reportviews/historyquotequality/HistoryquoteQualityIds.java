package grafioschtrader.reportviews.historyquotequality;

import java.util.Objects;

import grafioschtrader.dto.IHistoryquoteQualityFlat;
import io.swagger.v3.oas.annotations.media.Schema;

public class HistoryquoteQualityIds {
  public String idConnectorHistory;
  public Integer idStockexchange;
  public Byte categoryType;
  public Byte specialInvestmentInstrument;

  @Schema(description = "Can be used for the UI to differentiate the individual elements.", required = true)
  public int uniqueKey;

  public void setValues(IHistoryquoteQualityFlat hqf, int groupLevel, boolean isConnectGroup) {
    switch (groupLevel) {
    case 2:
      categoryType = hqf.getCategoryType();
      specialInvestmentInstrument = hqf.getSpecialInvestmentInstrument();
    case 1:
      idConnectorHistory = hqf.getIdConnectorHistory();
      idStockexchange = hqf.getIdStockexchange();
    case 0:
      if (isConnectGroup) {
        idConnectorHistory = hqf.getIdConnectorHistory();
      } else {
        idStockexchange = hqf.getIdStockexchange();
      }
    }
    uniqueKey = Objects.hash(groupLevel, idConnectorHistory, idStockexchange, categoryType,
        specialInvestmentInstrument);
  }

  public void setIdConnectorHistory(String idConnectorHistory) {
    this.idConnectorHistory = idConnectorHistory;
  }

  public void setIdStockexchange(Integer idStockexchange) {
    this.idStockexchange = idStockexchange;
  }

  public void setCategoryType(Byte categoryType) {
    this.categoryType = categoryType;
  }

  public void setSpecialInvestmentInstrument(Byte specialInvestmentInstrument) {
    this.specialInvestmentInstrument = specialInvestmentInstrument;
  }

  public void setUniqueKey(int uniqueKey) {
    this.uniqueKey = uniqueKey;
  }

}
