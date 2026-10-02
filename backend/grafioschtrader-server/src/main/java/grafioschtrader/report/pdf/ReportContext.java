package grafioschtrader.report.pdf;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/** Authorized scope and calendar of the caller, also usable by scheduled delivery without HTTP. */
public record ReportContext(Integer idTenant, Integer idPortfolio, Locale language, Locale numberFormat,
    LocalDate today, ZoneId zone) {
  public ReportContext {
    Objects.requireNonNull(idTenant);
    Objects.requireNonNull(language);
    Objects.requireNonNull(numberFormat);
    Objects.requireNonNull(today);
    Objects.requireNonNull(zone);
  }
}
