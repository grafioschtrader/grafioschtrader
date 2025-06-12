package grafioschtrader.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.types.TimePeriodType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Holds statistical properties grouped by time periods.")
public class StatisticsSummary {
  public final static String STANDARD_DEVIATION = "standardDeviation";
  public final static String MIN = "min";
  public final static String MAX = "max";
  @Schema(description = """
      A map where the key is the time period type (e.g., DAILY, MONTHLY, ANNUAL) and the value is a list 
      of statistical properties for that period.""")
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

  @Schema(description = "Represents a single statistical property with its value and main currency value.")
  public static class StatsProperty {
    @Schema(description = "The name of the statistical property (e.g., standardDeviation, min, max).", example = "standardDeviation")
    public String property;
    @Schema(description = "The calculated value of the statistical property.", example = "0.75")
    public double value;
    @Schema(description = "The calculated value of the statistical property in the main currency.", example = "0.78")
    public double valueMC;

    public StatsProperty(String property) {
      this.property = property;
    }

    public double getValue() {
      return DataBusinessHelper.roundStandard(value);
    }

    public double getValueMC() {
      return DataBusinessHelper.roundStandard(valueMC);
    }
  }
}
