package grafioschtrader.reportviews.performance;

import java.util.List;

import grafioschtrader.types.BenchmarkReturnBasis;
import grafioschtrader.types.BenchmarkSource;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Identity, conversion and return basis of a resolved benchmark, together with the notices found while loading it.
 * Built by the loader on the server and turned into a {@link PerformanceBenchmarkResult} by the report.
 *
 * @param idSecuritycurrency the benchmark instrument
 * @param name               display name, the readable currency pair for a pair
 * @param currency           currency of its quotes
 * @param source             where the benchmark came from
 * @param mainCurrency       currency of the report
 * @param fxPair             conversion pair as stored, for example "USD/CHF", null without conversion
 * @param fxInverse          whether the stored pair is the inverse of the needed direction
 * @param returnBasis        what the benchmark return contains
 * @param notices            conditions found while loading, for example a missing currency pair
 */
@Schema(description = "Identity, conversion and return basis of a benchmark, with the notices of its comparison")
public record BenchmarkDescriptor(Integer idSecuritycurrency, String name, String currency, BenchmarkSource source,
    String mainCurrency, String fxPair, boolean fxInverse, BenchmarkReturnBasis returnBasis,
    List<BenchmarkNotice> notices) {
}
