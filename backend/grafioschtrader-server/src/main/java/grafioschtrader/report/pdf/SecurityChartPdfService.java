package grafioschtrader.report.pdf;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.dto.PerformanceReportSettings;
import grafioschtrader.dto.SecurityChartPdfRequest;
import grafioschtrader.dto.SecurityChartPdfRequest.TransactionNumber;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.report.pdf.ChartSvgSanitizer.ChartSvg;
import grafioschtrader.report.pdf.SecurityChartPdfComposer.Content;
import grafioschtrader.report.pdf.SecurityChartPdfComposer.Row;
import grafioschtrader.reports.SecruityTransactionsReport;
import grafioschtrader.reports.SecruityTransactionsReport.SecruityTransactionsReportOptions;
import grafioschtrader.reportviews.transaction.SecurityTransactionSummary;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.ChartPdfPaper;

/**
 * Makes the PDF of a time-series chart. The browser delivers the picture of the chart and the numbers at its markers;
 * everything that is a statement about the user's data is read here: the transactions are loaded by the same report
 * methods and in the same scope as for the chart, so the table cannot disagree with the markers, and a transaction
 * outside the user's tenant never appears whatever the request names.
 */
@Service
public class SecurityChartPdfService {

  /** The finished document and the name under which it is offered for download. */
  public record Result(byte[] bytes, String filename) {
  }

  /**
   * The choices of the dialog, owned by the server so that a value outside them is refused here as well.
   *
   * @param papers        paper formats with their description as label
   * @param languages     languages of the texts
   * @param numberFormats locale tags for dates and numbers
   */
  public record Options(List<ValueKeyHtmlSelectOptions> papers, List<ValueKeyHtmlSelectOptions> languages,
      List<ValueKeyHtmlSelectOptions> numberFormats) {
  }

  private final SecruityTransactionsReport transactionsReport;
  private final SecurityJpaRepository securities;
  private final PortfolioJpaRepository portfolios;
  private final SecurityaccountJpaRepository securityaccounts;
  private final GlobalparametersService parameters;
  private final MessageSource messages;
  private final SvgPdfConverter converter;
  private final SecurityChartPdfComposer composer;

  public SecurityChartPdfService(SecruityTransactionsReport transactionsReport, SecurityJpaRepository securities,
      PortfolioJpaRepository portfolios, SecurityaccountJpaRepository securityaccounts,
      GlobalparametersService parameters, MessageSource messages, SvgPdfConverter converter,
      SecurityChartPdfComposer composer) {
    this.transactionsReport = transactionsReport;
    this.securities = securities;
    this.portfolios = portfolios;
    this.securityaccounts = securityaccounts;
    this.parameters = parameters;
    this.messages = messages;
    this.converter = converter;
    this.composer = composer;
  }

  /**
   * Lists what the dialog may offer.
   *
   * @return paper formats, languages and number formats
   */
  public Options options() {
    return new Options(
        List.of(new ValueKeyHtmlSelectOptions(ChartPdfPaper.A4.name(), "A4 (210 × 297 mm)"),
            new ValueKeyHtmlSelectOptions(ChartPdfPaper.LETTER.name(), "Letter (8.5 × 11 in)")),
        BaseConstants.G_LANGUAGE_CODES.stream().map(l -> new ValueKeyHtmlSelectOptions(l, l)).toList(),
        PerformanceReportSettings.NUMBER_FORMATS.stream().map(l -> new ValueKeyHtmlSelectOptions(l, l)).toList());
  }

  /**
   * Generates the document. Must run on the request thread, because the transaction reports identify the user through
   * the security context.
   *
   * @param idTenant the tenant of the signed-in user
   * @param request  the chart and what it shows
   * @param today    the day of the user, printed as creation date and used as cutoff of the transactions
   * @return the PDF with a file name
   */
  @Transactional(readOnly = true)
  public Result generate(Integer idTenant, SecurityChartPdfRequest request, LocalDate today) throws Exception {
    validate(request);
    ChartSvg chart = ChartSvgSanitizer.sanitize(request.svg);
    Security security = securities.findById(request.idSecuritycurrency)
        .orElseThrow(() -> new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH));
    Map<String, Integer> precision = parameters.getCurrencyPrecision();
    ReportFormatter formatter = new ReportFormatter(Locale.forLanguageTag(request.language),
        Locale.forLanguageTag(request.numberFormat), messages, currency -> precision.getOrDefault(currency, 2));
    String scope = scope(idTenant, request, formatter);
    List<Row> rows = request.includeTransactions ? rows(idTenant, request, today) : List.of();

    byte[] pdf = composer.compose(
        new Content(security.getName(), security.getCurrency(), scope, converter.convert(chart), rows, today),
        formatter, paper(request.paper));
    return new Result(pdf, filename(security.getName(), today));
  }

  private void validate(SecurityChartPdfRequest request) {
    if (!BaseConstants.G_LANGUAGE_CODES.contains(request.language)
        || !PerformanceReportSettings.NUMBER_FORMATS.contains(request.numberFormat)) {
      throw PerformanceReportValidation.invalid("gt.report.settings.invalid");
    }
    if (request.idPortfolio != null && hasAccounts(request)) {
      throw new DataViolationException("report.chart.scope", "gt.report.chart.scope.invalid", null);
    }
  }

  private static boolean hasAccounts(SecurityChartPdfRequest request) {
    return request.idsSecurityaccount != null && !request.idsSecurityaccount.isEmpty();
  }

  /** Describes where the transactions come from; a portfolio or account of another tenant is a security breach. */
  private String scope(Integer idTenant, SecurityChartPdfRequest request, ReportFormatter formatter) {
    if (hasAccounts(request)) {
      List<String> names = new ArrayList<>();
      for (Integer id : request.idsSecurityaccount) {
        Securityaccount account = securityaccounts.findByIdSecuritycashAccountAndIdTenant(id, idTenant);
        if (account == null) {
          throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
        }
        names.add(account.getName());
      }
      return formatter.text("gt.report.chart.scope.securityaccounts", String.join(", ", names));
    }
    if (request.idPortfolio != null) {
      Portfolio portfolio = portfolios.findByIdTenantAndIdPortfolio(idTenant, request.idPortfolio);
      if (portfolio == null) {
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
      }
      return formatter.text("gt.report.chart.scope.portfolio", portfolio.getName());
    }
    return formatter.text("gt.report.chart.scope.tenant");
  }

  /**
   * Loads the transactions in the scope of the chart and keeps those the chart marks, numbered as in the chart, with
   * the hypothetical sale of the open position. The numbers come from the browser, but they only label rows: which
   * rows exist is decided by the database.
   */
  private List<Row> rows(Integer idTenant, SecurityChartPdfRequest request, LocalDate today) {
    Map<Integer, Integer> numbers = new HashMap<>();
    if (request.transactionNumbers != null) {
      for (TransactionNumber transactionNumber : request.transactionNumbers) {
        numbers.putIfAbsent(transactionNumber.idTransaction, transactionNumber.number);
      }
    }
    return SecurityChartPdfRows.of(load(idTenant, request, today).transactionPositionList, numbers);
  }

  /** The same three scopes as the chart's own requests; the report methods add the tenant check themselves. */
  private SecurityTransactionSummary load(Integer idTenant, SecurityChartPdfRequest request, LocalDate today) {
    Set<SecruityTransactionsReportOptions> options = EnumSet
        .of(SecruityTransactionsReportOptions.QUTATION_SPLIT_CORRECTION);
    if (hasAccounts(request)) {
      return transactionsReport.getTransactionsByIdSecurityaccountsAndIdSecurityAndClearSecurity(
          request.idsSecurityaccount, request.idSecuritycurrency, today, options);
    }
    if (request.idPortfolio != null) {
      return transactionsReport.getTransactionsByIdPortfolioAndIdSecurityAndClearSecurity(request.idPortfolio,
          request.idSecuritycurrency, today, options);
    }
    return transactionsReport.getTransactionsByIdTenantAndIdSecurityAndClearSecurity(idTenant,
        request.idSecuritycurrency, today, options);
  }

  private static PDRectangle paper(ChartPdfPaper paper) {
    return paper == ChartPdfPaper.LETTER ? PDRectangle.LETTER : PDRectangle.A4;
  }

  private static String filename(String securityName, LocalDate today) {
    String name = securityName == null ? "" : securityName.replaceAll("[^A-Za-z0-9._-]+", "_");
    return "Chart_" + (name.isBlank() ? "security" : name) + "_" + today + ".pdf";
  }
}
