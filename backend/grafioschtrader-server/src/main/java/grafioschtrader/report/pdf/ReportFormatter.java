package grafioschtrader.report.pdf;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.function.Function;

import org.springframework.context.MessageSource;

/** Display precision and translation, isolated from all financial calculations. */
public record ReportFormatter(Locale language, Locale numberFormat, MessageSource messages,
    Function<String, Integer> currencyPrecision) {
  public String money(Double amount, String currency) {
    int digits = currencyPrecision.apply(currency);
    return number(amount, digits, digits);
  }

  public String units(double value) {
    int digits = 6;
    while (value != 0 && Math.abs(value) * Math.pow(10, digits) < 0.5 && digits < 10) {
      digits++;
    }
    return number(value, 0, digits);
  }

  public String price(double value) {
    return number(value, 2, 6);
  }

  public String rate(double value) {
    int digits = Math.abs(value) >= 0.1 || value == 0 ? 5
        : Math.max(5, 4 - (int) Math.floor(Math.log10(Math.abs(value))));
    return number(value, digits, digits);
  }

  public String percent(Double value) {
    return value == null || !Double.isFinite(value) ? "–" : number(value, 2, 2) + " %";
  }

  public String number(Double value, int min, int max) {
    if (value == null || !Double.isFinite(value)) {
      return "–";
    }
    NumberFormat format = NumberFormat.getNumberInstance(numberFormat);
    format.setMinimumFractionDigits(min);
    format.setMaximumFractionDigits(max);
    return format.format(value == 0 ? 0 : value);
  }

  public String date(LocalDate date) {
    return date == null ? "–"
        : date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(numberFormat));
  }

  public String text(String key, Object... args) {
    return messages.getMessage(key, args, language);
  }
}
