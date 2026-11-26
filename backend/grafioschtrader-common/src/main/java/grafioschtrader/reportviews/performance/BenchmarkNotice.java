package grafioschtrader.reportviews.performance;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import grafioschtrader.types.BenchmarkNoticeCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    A condition of the benchmark comparison the reader must know about, decided on the server so that every client shows
    the same notices. The code is the NLS key, the parameters fill its named placeholders. Dates are ISO strings
    (yyyy-MM-dd) and are formatted by the client.""")
public record BenchmarkNotice(@Schema(description = "NLS key of the notice") BenchmarkNoticeCode code,
    @Schema(description = "Named placeholder values of the notice") Map<String, String> parameters) {

  /** Parameter value naming the instrument leg of the benchmark, an NLS key itself. */
  public static final String LEG_INSTRUMENT = "BENCHMARK_LEG_INSTRUMENT";
  /** Parameter value naming the currency conversion leg of the benchmark, an NLS key itself. */
  public static final String LEG_RATE = "BENCHMARK_LEG_RATE";

  /**
   * Creates a notice from alternating names and values. A {@link LocalDate} value is written as an ISO string, null
   * values are left out.
   *
   * @param code          the condition
   * @param namesAndValues alternating parameter names and values
   * @return the notice
   */
  public static BenchmarkNotice of(BenchmarkNoticeCode code, Object... namesAndValues) {
    Map<String, String> parameters = new LinkedHashMap<>();
    for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
      Object value = namesAndValues[i + 1];
      if (value != null) {
        parameters.put(String.valueOf(namesAndValues[i]), value.toString());
      }
    }
    return new BenchmarkNotice(code, Collections.unmodifiableMap(parameters));
  }
}
