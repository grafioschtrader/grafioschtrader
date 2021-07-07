package grafioschtrader.reportviews.historyquotequality;

import java.util.ArrayList;
import java.util.List;

import grafioschtrader.common.DataHelper;
import grafioschtrader.dto.IHistoryquoteQualityFlat;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Group by connector, stock exchange, asset class (equities, bond, ...),
 * special investment like (ETF, Direct investment, ...)
 *
 * @author Hugo Graf
 */
public class HistoryquoteQualityGroup extends HistoryquoteQualityIds {

  @Schema(description = "May be differe for head and how it is grouped", required = true)
  public String name;
  public int numberOfSecurities;
  public int activeNowSecurities;
  public int connectorCreated;
  public int manualImported;
  public int filledLinear;
  private double qualityPercentage;
  private int averageCounter;
  public List<HistoryquoteQualityGroup> childrendHqg = new ArrayList<>();

  public HistoryquoteQualityGroup(String name) {
    this.name = name;
  }

  public void addHistoryquoteQualityFlat(IHistoryquoteQualityFlat hqf, String[] groupValues, int groupLevel,
      boolean isConnectGroup) {
    HistoryquoteQualityGroup historyquoteQualityGroup;
    if (!childrendHqg.isEmpty() && childrendHqg.get(childrendHqg.size() - 1).name.equals(groupValues[groupLevel])) {
      // Group value on this level is unchanged
      historyquoteQualityGroup = childrendHqg.get(childrendHqg.size() - 1);
    } else {
      // Group value on this level has changed
      historyquoteQualityGroup = new HistoryquoteQualityGroup(groupValues[groupLevel]);
      historyquoteQualityGroup.setValues(hqf, groupLevel, isConnectGroup);
      childrendHqg.add(historyquoteQualityGroup);
    }
    if (groupLevel == 0) {
      sum(this, hqf);
    }
    sum(historyquoteQualityGroup, hqf);

    if (groupLevel < groupValues.length - 1) {
      historyquoteQualityGroup.addHistoryquoteQualityFlat(hqf, groupValues, ++groupLevel, isConnectGroup);
    }

  }

  public void sum(HistoryquoteQualityGroup hqg, IHistoryquoteQualityFlat hqf) {
    hqg.numberOfSecurities += hqf.getNumberOfSecurities();
    hqg.activeNowSecurities += hqf.getActiveNowSecurities();
    hqg.connectorCreated += hqf.getConnectorCreated();
    hqg.manualImported += hqf.getManualImported();
    hqg.filledLinear += hqf.getFilledLinear();
    hqg.qualityPercentage += hqf.getQualityPercentage();
    hqg.averageCounter++;
  }

  public double getQualityPercentage() {
    return DataHelper.roundStandard(averageCounter > 0 ? qualityPercentage / averageCounter : qualityPercentage);
  }

}
