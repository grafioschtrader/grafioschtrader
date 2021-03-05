package grafioschtrader.algo;

import java.util.List;

import grafioschtrader.entities.AlgoTop;

public class AlgoTopCreate extends AlgoTop {

  private static final long serialVersionUID = 1L;
  public List<AssetclassPercentage> assetclassPercentageList;

  public static class AssetclassPercentage {
    public Integer idAssetclass;
    public Float percentage;

    @Override
    public String toString() {
      return "AssetclassPercentage [idAssetclass=" + idAssetclass + ", percentage=" + percentage + "]";
    }
  }
}
