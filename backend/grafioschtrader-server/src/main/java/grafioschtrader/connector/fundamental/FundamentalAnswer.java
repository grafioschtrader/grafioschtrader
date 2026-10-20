package grafioschtrader.connector.fundamental;

import java.util.List;
import java.util.Map;

import grafioschtrader.entities.SecurityFundamental;

/**
 * Outcome of one request for fundamental data. A usable answer is HTTP 200 with a parseable body containing the
 * expected root object; its rows replace every stored row of that source for the security, so that a metric the source
 * no longer delivers disappears. A failed answer leaves the stored rows of the source untouched.
 *
 * @param rows       the values read from a usable answer, empty for a failed one
 * @param evidence   what the source checks read from the same answer without storing it, such as Eulerpool's net income
 * @param reason     why the answer is not usable, null for a usable one
 * @param httpStatus status of a failed request, 0 when no answer arrived or the answer was usable
 */
public record FundamentalAnswer(List<SecurityFundamental> rows, Map<String, Double> evidence, String reason,
    int httpStatus) {

  /** Key of the net income of the period of the fiscal year values, read by the unit check of Eulerpool. */
  public static final String EVIDENCE_NET_INCOME = "netIncome";

  public static FundamentalAnswer usable(List<SecurityFundamental> rows, Map<String, Double> evidence) {
    return new FundamentalAnswer(rows, evidence, null, 0);
  }

  public static FundamentalAnswer failed(String reason, int httpStatus) {
    return new FundamentalAnswer(List.of(), Map.of(), reason, httpStatus);
  }

  public boolean isUsable() {
    return reason == null;
  }

  /** A refused key or plan is the cause, so the source is not asked again in this run. */
  public boolean stopsSource() {
    return httpStatus == 401 || httpStatus == 403;
  }
}
