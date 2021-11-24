package grafioschtrader.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.common.DataHelper;
import grafioschtrader.types.TimePeriodType;

public class StatisticsSummary {
  public final static String STANDARD_DEVIATION = "standardDeviation";
  public final static String MIN = "min";
  public final static String MAX = "max";
  public final Map<TimePeriodType, List<StatsProperty>> statsPropertyMap = new HashMap<>();

  public void addProperties(TimePeriodType timePeriodType, String... properties) {
    List<StatsProperty> statsPropertyList = new ArrayList<>();
    for (String property : properties) {
      statsPropertyList.add(new StatsProperty(property));
    }
    statsPropertyMap.put(timePeriodType, statsPropertyList);
  }

  public List<StatsProperty> getPropertiesBySamplingPeriodType(TimePeriodType timePeriodType) {
    return statsPropertyMap.get(timePeriodType);
  }

  public void createAnnualByMonthly() {
    List<StatsProperty> monthlyProperties = statsPropertyMap.get(TimePeriodType.MONTHLY);
    List<StatsProperty> annualProperties = statsPropertyMap.get(TimePeriodType.ANNUAL);
    if (annualProperties != null && monthlyProperties != null) {
      for (StatsProperty statsProperty : annualProperties) {
        if (statsProperty.property.equals(STANDARD_DEVIATION)) {
          StatsProperty sp = getPropertyValue(monthlyProperties, STANDARD_DEVIATION);
          statsProperty.value = sp.value * Math.sqrt(12);
          statsProperty.valueMC = sp.valueMC * Math.sqrt(12);
        }
      }
    }

  }

  public StatsProperty getPropertyValue(final List<StatsProperty> properties, final String property) {
    return properties.stream().filter(p -> p.property.equals(property)).findAny().get();
  }

  public static class StatsProperty {
    public String property;
    public double value;
    public double valueMC;

    public StatsProperty(String property) {
      this.property = property;
    }

    public double getValue() {
      return DataHelper.roundStandard(value);
    }

    public double getValueMC() {
      return DataHelper.roundStandard(valueMC);
    }
  }
}
