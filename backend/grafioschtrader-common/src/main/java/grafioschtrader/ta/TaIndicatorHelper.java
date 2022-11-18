package grafioschtrader.ta;

import java.util.HashMap;
import java.util.Map;

import grafioschtrader.dynamic.model.DynamicModelHelper;
import grafioschtrader.ta.indicator.model.ShortMediumLongInputPeriod;

public abstract class TaIndicatorHelper {
  private static Map<TaIndicators, TaFormDefinition> taFormMap;

  static {
    taFormMap = new HashMap<>();
    taFormMap.put(TaIndicators.SMA,
        new TaFormDefinition(DynamicModelHelper.getFormDefinitionOfModelClassMembers(ShortMediumLongInputPeriod.class),
            new ShortMediumLongInputPeriod(20, 50, 200)));
    taFormMap.put(TaIndicators.EMA,
        new TaFormDefinition(DynamicModelHelper.getFormDefinitionOfModelClassMembers(ShortMediumLongInputPeriod.class),
            new ShortMediumLongInputPeriod(20, 50, 200)));
  }

  public static Map<TaIndicators, TaFormDefinition> getTaFormMap() {
    return taFormMap;
  }

}
