package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.entities.User;
import grafioschtrader.service.PerformanceBenchmarkService;
import grafioschtrader.service.PerformanceBenchmarkService.PerformanceBenchmarkView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Reads and writes the stored benchmark of the period performance comparison: the benchmark of the client view, which
 * is the default of every portfolio, and the override of a single portfolio. The tenant always comes from the
 * authenticated user; writes of a read-only tenant access are refused by the tenant read-only filter.
 */
@RestController
@RequestMapping(RequestGTMappings.PERFORMANCE_BENCHMARK_MAP)
@Tag(name = RequestGTMappings.PERFORMANCE_BENCHMARK, description = "Benchmark of the period performance comparison")
public class PerformanceBenchmarkResource {

  private final PerformanceBenchmarkService performanceBenchmarkService;

  public PerformanceBenchmarkResource(PerformanceBenchmarkService performanceBenchmarkService) {
    this.performanceBenchmarkService = performanceBenchmarkService;
  }

  @Schema(description = "Benchmark assignment of one scope of the period performance")
  public record PerformanceBenchmarkAssignment(
      @Schema(description = "The portfolio, null for the client view and default of all portfolios") Integer idPortfolio,
      @Schema(description = "The benchmark instrument, a security or a currency pair") Integer idSecuritycurrency) {
  }

  @Operation(summary = "Effective stored benchmark of the client view or of a portfolio", description = """
      Returns the benchmark of the portfolio, or the inherited one of the tenant. 204 when no readable benchmark is
      stored for the scope.""", tags = { RequestGTMappings.PERFORMANCE_BENCHMARK })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<PerformanceBenchmarkView> getBenchmark(
      @RequestParam(required = false) final Integer idPortfolio) {
    return performanceBenchmarkService.view(user(), idPortfolio).map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @Operation(summary = "Stores the benchmark of the client view or of a portfolio", tags = {
      RequestGTMappings.PERFORMANCE_BENCHMARK })
  @PutMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<PerformanceBenchmarkView> assignBenchmark(
      @RequestBody final PerformanceBenchmarkAssignment assignment) {
    return ResponseEntity.ok(performanceBenchmarkService.assign(user(), assignment.idPortfolio(),
        assignment.idSecuritycurrency()));
  }

  @Operation(summary = "Removes the benchmark of exactly one scope", description = """
      Without idPortfolio the benchmark of the client view is removed; with it the override of the portfolio, which
      then inherits the benchmark of the tenant again.""", tags = { RequestGTMappings.PERFORMANCE_BENCHMARK })
  @DeleteMapping
  public ResponseEntity<Void> clearBenchmark(@RequestParam(required = false) final Integer idPortfolio) {
    performanceBenchmarkService.clear(user(), idPortfolio);
    return ResponseEntity.noContent().build();
  }

  private static User user() {
    return (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
  }
}
