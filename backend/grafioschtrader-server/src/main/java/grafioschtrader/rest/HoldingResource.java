package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import grafiosch.common.ClientClock;
import grafiosch.entities.User;
import grafioschtrader.dto.MissingQuotesWithSecurities;
import grafioschtrader.dto.PerformanceReportRequest;
import grafioschtrader.report.pdf.PerformanceReportPdfService;
import grafioschtrader.report.pdf.ReportContext;
import grafioschtrader.reports.PerformanceReport;
import grafioschtrader.reportviews.performance.FirstAndMissingTradingDays;
import grafioschtrader.reportviews.performance.PerformancePeriod;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping(RequestGTMappings.HOLDING_MAP)
@Tag(name = RequestGTMappings.HOLDING, description = "Controller for security holdings and performance report")
public class HoldingResource {

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  private PerformanceReport performanceReport;

  @Autowired
  private PerformanceReportPdfService pdfService;

  @Operation(summary = "Options for the delivered PDF report sections")
  @GetMapping(value = "/report/options", produces = APPLICATION_JSON_VALUE)
  public PerformanceReportPdfService.Options getReportOptions() {
    return pdfService.options();
  }

  @Operation(summary = "Generate a read-only PDF of the period performance")
  @PostMapping(value = "/report/pdf", consumes = APPLICATION_JSON_VALUE)
  public void getPeriodPerformancePdf(@Valid @RequestBody PerformanceReportRequest request,
      HttpServletResponse response) throws Exception {
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    var context = new ReportContext(user.getIdTenant(), request.idPortfolio, Locale.forLanguageTag(request.language),
        Locale.forLanguageTag(request.numberFormat), ClientClock.today(), ClientClock.zone());
    byte[] bytes = pdfService.generate(context, request);
    String filename = pdfService.filename(context, request);
    String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    response.setContentType("application/pdf");
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Content-Disposition",
        "attachment; filename=\"" + filename.replaceAll("[^\\x20-\\x7E]", "_") + "\"; filename*=UTF-8''" + encoded);
    response.setContentLength(bytes.length);
    response.getOutputStream().write(bytes);
  }

  @Operation(summary = "Return of certain dates, which are used by the user to narrow down the period earnings reports.", description = "", tags = {
      RequestGTMappings.HOLDING })
  @GetMapping(value = "/getdatesforform", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<FirstAndMissingTradingDays> getFirstAndMissingTradingDays(
      @RequestParam(required = false) final Integer idPortfolio) throws InterruptedException, ExecutionException {
    if (idPortfolio != null) {
      return new ResponseEntity<>(performanceReport.getFirstAndMissingTradingDaysByPortfolio(idPortfolio),
          HttpStatus.OK);
    } else {
      return new ResponseEntity<>(performanceReport.getFirstAndMissingTradingDaysByTenant(), HttpStatus.OK);
    }
  }

  @Operation(summary = "Return of the period income report according to time period and period breakdown.", description = "", tags = {
      RequestGTMappings.HOLDING })
  @GetMapping(value = "/{dateFrom}/{dateTo}/{periodSplit}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<PerformancePeriod> getPeriodPerformance(
      @PathVariable() @DateTimeFormat(iso = ISO.DATE) final LocalDate dateFrom,
      @PathVariable() @DateTimeFormat(iso = ISO.DATE) final LocalDate dateTo,
      @PathVariable() final WeekYear periodSplit, @RequestParam(required = false) final Integer idPortfolio)
      throws Exception {
    if (idPortfolio != null) {
      return new ResponseEntity<>(
          performanceReport.getPeriodPerformanceByPortfolio(idPortfolio, dateFrom, dateTo, periodSplit), HttpStatus.OK);
    } else {
      return new ResponseEntity<>(performanceReport.getPeriodPerformanceByTenant(dateFrom, dateTo, periodSplit),
          HttpStatus.OK);
    }
  }

  @Operation(summary = "Returns the missing qoutes for securties during the holding period", description = "", tags = {
      RequestGTMappings.HOLDING })
  @GetMapping(value = "/missingquotes/{year}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<MissingQuotesWithSecurities> getMissingQuotesWithSecurities(@PathVariable() final Integer year)
      throws InterruptedException, ExecutionException {
    return new ResponseEntity<>(holdSecurityaccountSecurityRepository.getMissingQuotesWithSecurities(year),
        HttpStatus.OK);
  }

}
