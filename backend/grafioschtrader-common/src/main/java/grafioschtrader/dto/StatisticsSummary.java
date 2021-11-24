package grafioschtrader.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.types.SamplingPeriodType;

public class StatisticsSummary {
  public final static String STANDARD_DEVIATION = "standardDeviation";
  public final static String MIN = "min";
  public final static String MAX = "max";
  public final Map<SamplingPeriodType, List<StatsProperty>> statsPropertyMap = new HashMap<>();

  public void addProperties(SamplingPeriodType samplingPeriodType, String... properties) {
    List<StatsProperty> statsPropertyList = new ArrayList<>();
    for (String property : properties) {
      statsPropertyList.add(new StatsProperty(property));
    }
    statsPropertyMap.put(samplingPeriodType, statsPropertyList);
  }

  public List<StatsProperty> getPropertiesBySamplingPeriodType(SamplingPeriodType samplingPeriodType) {
    return statsPropertyMap.get(samplingPeriodType);
  }

  public void createAnnualByMonthly() {
    List<StatsProperty> monthlyProperties = statsPropertyMap.get(SamplingPeriodType.MONTHLY_RETURNS);
    List<StatsProperty> annualProperties = statsPropertyMap.get(SamplingPeriodType.ANNUAL_RETURNS);
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
  }
}
