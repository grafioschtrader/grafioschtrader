package grafioschtrader.dto;

import java.util.ArrayList;
import java.util.List;

public class AnnualisedSecurityPerformance {
  public Double ytd;
  public Double ytdMC;
  public List<LastYears> lastYears = new ArrayList<>();

  public static class LastYears {
    public int year;
    public double performance;
    public double performanceMC;
    
    public LastYears(int year, double performance, double performanceMC) {
      this.year = year;
      this.performance = performance;
      this.performanceMC = performanceMC;
    }

  }
}
