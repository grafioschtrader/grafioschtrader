package grafioschtrader.report.pdf;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.ProgressStateType;
import grafioschtrader.dto.PerformanceReportRequest;
import grafioschtrader.dto.PerformanceReportSettings;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reports.PerformanceReport;
import grafioschtrader.reports.SecurityGroupByAssetclassWithCashReport;
import grafioschtrader.reportviews.performance.FirstAndMissingTradingDays;
import grafioschtrader.reportviews.performance.PerformancePeriod;
import grafioschtrader.reportviews.performance.WeekYear;
import grafioschtrader.repository.PortfolioJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.types.PerformanceReportPreset;
import grafioschtrader.types.PerformanceReportSection;
import grafioschtrader.types.TaskTypeExtended;
import io.swagger.v3.oas.annotations.media.Schema;

/** Read-only orchestration with explicit tenant, locale and calendar; all bytes are buffered before HTTP begins. */
@Service
public class PerformanceReportPdfService {
  private final TenantJpaRepository tenants;
  private final PortfolioJpaRepository portfolios;
  private final TaskDataChangeJpaRepository tasks;
  private final PerformanceReport performance;
  private final SecurityGroupByAssetclassWithCashReport holdings;
  private final BookingDataLoader bookings;
  private final GlobalparametersService parameters;
  private final MessageSource messages;
  private final PerformanceReportValidation validation;
  private final Map<PerformanceReportSection, SectionRenderer> renderers;

  public PerformanceReportPdfService(TenantJpaRepository tenants, PortfolioJpaRepository portfolios,
      TaskDataChangeJpaRepository tasks, PerformanceReport performance, GlobalparametersService parameters,
      MessageSource messages, PerformanceReportValidation validation, List<SectionRenderer> renderers,
      SecurityGroupByAssetclassWithCashReport holdings, BookingDataLoader bookings) {
    this.tenants = tenants;
    this.portfolios = portfolios;
    this.tasks = tasks;
    this.performance = performance;
    this.holdings = holdings;
    this.bookings = bookings;
    this.parameters = parameters;
    this.messages = messages;
    this.validation = validation;
    this.renderers = renderers.stream().collect(Collectors.toUnmodifiableMap(SectionRenderer::section, r -> r));
  }

  @Transactional(readOnly = true)
  public byte[] generate(ReportContext context, PerformanceReportRequest request) throws Exception {
    validation.request(context, request);
    ReportData data = load(context, request);
    Map<String, Integer> precision = parameters.getCurrencyPrecision();
    ReportFormatter formatter = new ReportFormatter(context.language(), context.numberFormat(), messages,
        currency -> precision.getOrDefault(currency, 2));
    try (PdfDocumentWriter writer = new PdfDocumentWriter(data.title(formatter),
        data.scope() + " · " + formatter.text("gt.report.valued.in", data.currency()), request.sender,
        formatter.date(data.created().toLocalDate()), formatter)) {
      if (request.sections.contains(PerformanceReportSection.COVER)) {
        renderers.get(PerformanceReportSection.COVER).render(data, writer, formatter);
      }
      ReportFacts.render(data, writer, formatter);
      // The dialog offers the comment for every selection, so it follows the mandatory block rather than a section.
      if (request.comment != null && !request.comment.isBlank()) {
        writer.heading(formatter.text("REPORT_COMMENT"));
        writer.paragraph(request.comment);
      }
      for (PerformanceReportSection section : PerformanceReportSection.values()) {
        if (section != PerformanceReportSection.COVER && request.sections.contains(section)) {
          SectionRenderer renderer = renderers.get(section);
          if (renderer == null) {
            throw PerformanceReportValidation.invalid("gt.report.section.not.available");
          }
          renderer.render(data, writer, formatter);
        }
      }
      writer.heading(formatter.text("gt.report.disclaimer.title"));
      writer.paragraph(formatter.text("gt.report.disclaimer"));
      writer.paragraph(request.additionalNotes);
      return writer.toByteArray();
    }
  }

  private ReportData load(ReportContext context, PerformanceReportRequest request) throws Exception {
    Tenant tenant = tenants.findById(context.idTenant())
        .orElseThrow(() -> new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH));
    Portfolio portfolio = resolvePortfolio(context);
    boolean pending = tasks.findByIdTaskIn(List.of(TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT.getValue()))
        .stream()
        .anyMatch(t -> (t.getIdEntity() == null || Objects.equals(t.getIdEntity(), context.idTenant()))
            && (t.getProgressStateType() == ProgressStateType.PROG_WAITING
                || t.getProgressStateType() == ProgressStateType.PROG_RUNNING));
    if (pending) {
      throw PerformanceReportValidation.invalid("gt.report.holdings.rebuild.pending");
    }
    FirstAndMissingTradingDays days = !request.hasPeriod() ? null
        : context.idPortfolio() == null
            ? performance.getFirstAndMissingTradingDaysByTenant(context.idTenant(), context.today())
            : performance.getFirstAndMissingTradingDaysByPortfolio(context.idTenant(), context.idPortfolio(),
                context.today());
    PerformancePeriod period = request.hasPeriod()
        ? period(context, request.dateFrom, request.dateTo, request.periodSplit, true)
        : null;
    PerformancePeriod annual = null;
    if (request.sections.contains(PerformanceReportSection.ANNUAL_RETURNS)) {
      LocalDate from = annualFrom(request.dateTo, request.annualYears, days);
      if (from.isBefore(request.dateTo)) {
        annual = from.equals(request.dateFrom) && request.periodSplit == WeekYear.WM_YEAR ? period
            : period(context, from, request.dateTo, WeekYear.WM_YEAR, false);
      }
    }
    HoldingsData statement = null;
    boolean incomeCosts = request.sections.contains(PerformanceReportSection.INCOME_COSTS);
    if (request.sections.contains(PerformanceReportSection.HOLDINGS)
        || request.sections.contains(PerformanceReportSection.ALLOCATION) || incomeCosts) {
      statement = valuation(context, request.valuationDate());
    }
    HoldingsData opening = incomeCosts ? valuation(context, request.dateFrom) : null;
    String currency = portfolio == null ? tenant.getCurrency() : portfolio.getCurrency();
    BookingData bookingData = incomeCosts || request.sections.contains(PerformanceReportSection.TRANSACTIONS)
        ? bookings.load(context, request.dateFrom, request.dateTo, currency, tenant.isFeeInterestFxAtCutOffDate(),
            tenant.isExcludeDivTax(), opening, statement)
        : null;
    return new ReportData(context, request,
        tenant.getTenantName() + (portfolio == null ? "" : " / " + portfolio.getName()),
        portfolio == null ? tenant.getCurrency() : portfolio.getCurrency(),
        portfolio == null
            ? tenant.getPortfolioList().stream().map(p -> p.getName() + " (" + p.getCurrency() + ")").toList()
            : List.of(),
        tenant.isExcludeDivTax(), tenant.isFeeInterestFxAtCutOffDate(), ZonedDateTime.now(context.zone()), days, period,
        annual, statement, opening, bookingData);
  }

  private HoldingsData valuation(ReportContext context, LocalDate date) throws Exception {
    return new HoldingsData(context.idPortfolio() == null
        ? holdings.getSecurityPositionGrandSummaryIdTenant(context.idTenant(), false, date, context.today())
        : holdings.getSecurityPositionGrandSummaryIdPortfolio(context.idTenant(), context.idPortfolio(), false, date,
            context.today()));
  }

  private Portfolio resolvePortfolio(ReportContext context) {
    if (context.idPortfolio() == null) {
      return null;
    }
    Portfolio portfolio = portfolios.findByIdTenantAndIdPortfolio(context.idTenant(), context.idPortfolio());
    if (portfolio == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return portfolio;
  }

  /** Safe download name, resolved with the same tenant ownership check as generation. */
  public String filename(ReportContext context, PerformanceReportRequest request) {
    Portfolio portfolio = resolvePortfolio(context);
    String suffix = portfolio == null ? "" : "_" + portfolio.getName().replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_");
    if (!request.hasPeriod()) {
      return "statement_" + request.valuationDate().format(DateTimeFormatter.BASIC_ISO_DATE) + suffix + ".pdf";
    }
    return "performance_" + request.dateFrom.format(DateTimeFormatter.BASIC_ISO_DATE) + "_"
        + request.dateTo.format(DateTimeFormatter.BASIC_ISO_DATE) + suffix + ".pdf";
  }

  private PerformancePeriod period(ReportContext context, LocalDate from, LocalDate to, WeekYear split,
      boolean validateSplit) throws Exception {
    return context.idPortfolio() == null
        ? performance.getPeriodPerformanceByTenant(context.idTenant(), context.numberFormat().toLanguageTag(),
            context.today(), from, to, split, validateSplit)
        : performance.getPeriodPerformanceByPortfolio(context.idTenant(), context.numberFormat().toLanguageTag(),
            context.today(), context.idPortfolio(), from, to, split, validateSplit);
  }

  /** Last usable year-end base, bounded by the scope's first valuation. */
  static LocalDate annualFrom(LocalDate to, int years, FirstAndMissingTradingDays days) {
    LocalDate from = LocalDate.of(to.getYear() - years, 12, 31);
    while (from.isAfter(days.firstEverTradingDay) && !usable(from, days)) {
      from = from.minusDays(1);
    }
    return from.isBefore(days.firstEverTradingDay) ? days.firstEverTradingDay : from;
  }

  static boolean usable(LocalDate date, FirstAndMissingTradingDays days) {
    return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
        && !days.isMissingQuoteDayOrHoliday(date);
  }

  @Schema(description = "Server-owned options and preset selections for the delivered report sections")
  public record Options(List<ValueKeyHtmlSelectOptions> presets,
      Map<PerformanceReportPreset, Set<PerformanceReportSection>> presetSections,
      List<ValueKeyHtmlSelectOptions> sections, List<ValueKeyHtmlSelectOptions> languages,
      List<ValueKeyHtmlSelectOptions> numberFormats, List<PerformanceReportSection> dateSections) {
  }

  public Options options() {
    List<PerformanceReportPreset> presets = Arrays.stream(PerformanceReportPreset.values())
        .filter(PerformanceReportPreset::isAvailable).toList();
    return new Options(presets.stream().map(p -> new ValueKeyHtmlSelectOptions(p.name(), p.name())).toList(),
        presets.stream().collect(Collectors.toMap(p -> p, PerformanceReportPreset::getSections)),
        Arrays.stream(PerformanceReportSection.values()).filter(PerformanceReportSection::isAvailable)
            .map(s -> new ValueKeyHtmlSelectOptions(s.name(), s.name())).toList(),
        BaseConstants.G_LANGUAGE_CODES.stream().map(l -> new ValueKeyHtmlSelectOptions(l, l)).toList(),
        PerformanceReportSettings.NUMBER_FORMATS.stream().map(l -> new ValueKeyHtmlSelectOptions(l, l)).toList(),
        Arrays.stream(PerformanceReportSection.values()).filter(s -> s.isAvailable() && !s.needsPeriod()).toList());
  }
}
