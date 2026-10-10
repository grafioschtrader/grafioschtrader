package grafioschtrader.connector.securitysearch;

import java.util.List;
import java.util.Map;

import grafioschtrader.GlobalConstants;

/**
 * Relation between an operating MIC, which GT uses for its stock exchanges, and the segment MICs of the same exchange
 * (ISO 10383). Providers sometimes identify a listing by its segment: OpenFIGI filters NASDAQ listings only by XNGS,
 * XNMS or XNCM, and {@code mic_provider_map} maps the Yahoo code {@code NMS} to XNMS. A segment MIC must therefore be
 * folded into its operating MIC before it is compared with the query, or a correct listing would count as a conflict.
 */
public final class MarketSegmentMics {

  private static final Map<String, List<String>> SEGMENTS_BY_OPERATING_MIC = Map
      .of(GlobalConstants.STOCK_EX_MIC_NASDAQ, List.of("XNGS", "XNMS", "XNCM"));

  private MarketSegmentMics() {
  }

  /**
   * Returns the segment MICs belonging to an operating MIC.
   *
   * @param operatingMic the operating MIC, e.g. XNAS
   * @return its segment MICs, empty when none are known
   */
  public static List<String> segmentsOf(String operatingMic) {
    return SEGMENTS_BY_OPERATING_MIC.getOrDefault(operatingMic, List.of());
  }

  /**
   * Folds a segment MIC into its operating MIC.
   *
   * @param mic a MIC, may be null
   * @return the operating MIC for a known segment MIC, otherwise the MIC unchanged
   */
  public static String toOperatingMic(String mic) {
    if (mic != null) {
      for (Map.Entry<String, List<String>> entry : SEGMENTS_BY_OPERATING_MIC.entrySet()) {
        if (entry.getValue().contains(mic)) {
          return entry.getKey();
        }
      }
    }
    return mic;
  }
}
