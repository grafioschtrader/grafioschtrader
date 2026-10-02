package grafioschtrader.report.pdf;

import java.util.Objects;

import org.springframework.stereotype.Component;

import grafiosch.BaseConstants;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.dto.PerformanceReportRequest;
import grafioschtrader.dto.PerformanceReportSettings;
import grafioschtrader.types.PerformanceReportPreset;
import grafioschtrader.types.PerformanceReportSection;
import jakarta.validation.Validator;

/** Enforces the same input boundary for REST and background generation. */
@Component
public class PerformanceReportValidation {
  private final Validator validator;

  public PerformanceReportValidation(Validator validator) {
    this.validator = validator;
  }

  public void settings(PerformanceReportSettings settings) {
    if (settings == null || !validator.validate(settings).isEmpty()
        || !BaseConstants.G_LANGUAGE_CODES.contains(settings.language)
        || !PerformanceReportSettings.NUMBER_FORMATS.contains(settings.numberFormat)) {
      throw invalid("gt.report.settings.invalid");
    }
    if (!settings.preset.isAvailable() || settings.sections == null || settings.sections.isEmpty()
        || settings.sections.stream().anyMatch(s -> s == null || !s.isAvailable())) {
      throw invalid("gt.report.section.not.available");
    }
    // A named preset stands for its exact sections, because it may shape their content (the short report).
    if (settings.preset != PerformanceReportPreset.CUSTOM && !settings.preset.getSections().equals(settings.sections)) {
      throw invalid("gt.report.settings.invalid");
    }
    if (settings.sections.stream().noneMatch(PerformanceReportSection::isContent)) {
      throw invalid("gt.report.content.required");
    }
  }

  public void request(ReportContext context, PerformanceReportRequest request) {
    settings(request);
    if (!Objects.equals(context.idPortfolio(), request.idPortfolio)) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    if (!context.language().getLanguage().equals(request.language)
        || !context.numberFormat().toLanguageTag().equals(request.numberFormat)) {
      throw invalid("gt.report.settings.invalid");
    }
    if (!request.hasPeriod()) {
      if (request.valuationDate() == null || request.valuationDate().isAfter(context.today())) {
        throw invalid("gt.report.date.required");
      }
      return;
    }
    if (request.dateFrom == null || request.dateTo == null || request.periodSplit == null) {
      throw invalid("gt.report.date.required");
    }
    if (!request.dateFrom.isBefore(request.dateTo) || !request.dateTo.isBefore(context.today())) {
      throw invalid("gt.report.date.required");
    }
  }

  static DataViolationException invalid(String key) {
    return new DataViolationException("report.sections", key, null);
  }
}
