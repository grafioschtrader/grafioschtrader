package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import grafioschtrader.types.AlgoSignalKind;

/**
 * Renders every signal kind against the real message bundles, so a broken MessageFormat pattern or a missing key in one
 * of the two languages fails here rather than in a delivered notification.
 */
class AlgoAlarmTextRendererTest {

  private static final Locale DE = Locale.GERMAN;
  private static final Locale EN = Locale.ENGLISH;

  private final AlgoAlarmTextRenderer renderer = new AlgoAlarmTextRenderer(bundles());

  private static ReloadableResourceBundleMessageSource bundles() {
    ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
    source.setBasenames("classpath:message/messages", "classpath:i18n/messages");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }

  private String render(AlgoSignalKind kind, String details, Locale locale) {
    return renderer.describe(kind, "Nestlé", details, locale);
  }

  @Test
  @DisplayName("Every signal kind renders a headline and a detail line in German and English")
  void everyKindRendersInBothLanguages() {
    String[][] cases = {
        { "PRICE_ALERT",
            AlgoAlarmDetails.of("bound", "lower", "threshold", 90.5, "price", 89.75, "direction", "BELOW") },
        { "ENTRY_SIGNAL",
            AlgoAlarmDetails.of("action", "ENTRY", "reason", "MEAN_REVERSION_ENTRY", "units", 12.0, "price", 45.6,
                "tranche", "", "identity", "1:2:3") },
        { "PROFIT_TAKE",
            AlgoAlarmDetails.of("action", "SCALE_OUT", "reason", "MEAN_REVERSION_ENTRY", "units", 3.0, "price", 50.0,
                "tranche", "T1", "identity", "1:2:3") },
        { "STOP_LOSS", AlgoAlarmDetails.of("reason", "MEAN_REVERSION_ENTRY", "units", 3.0, "price", 40.0) },
        { "RISK_BREACH", AlgoAlarmDetails.of("reason", "MEAN_REVERSION_ENTRY", "units", 3.0, "price", 40.0) },
        { "REBALANCE_DRIFT",
            AlgoAlarmDetails.of("trigger", "PERIODIC", "action", "REBALANCE_BUY", "target", 24.1, "actual", 20.0,
                "deviation", -4.1, "amount", 4100.0, "currency", "CHF", "reason", "REBALANCE_BELOW_TARGET") },
        { "HOLDING_GAIN_LOSS",
            AlgoAlarmDetails.of("positionGainLossPercent", 26.5, "gainThreshold", 25.0, "loseThreshold", null, "price",
                120.0) },
        { "PERIOD_PRICE_CHANGE",
            AlgoAlarmDetails.of("changePercent", -15.2, "gainThreshold", null, "loseThreshold", 15.0, "daysInPeriod",
                30, "referenceDate", "2026-08-31", "referenceClose", 100.0, "price", 84.8) },
        { "MA_CROSSING",
            AlgoAlarmDetails.of("indicatorType", "EMA", "period", 50, "maValue", 101.2345, "price", 102.0,
                "crossDirection", "ABOVE") },
        { "RSI_THRESHOLD", AlgoAlarmDetails.of("rsiValue", 28.4, "threshold", 30.0, "direction", "OVERSOLD") },
        { "ALLOCATION_BREACH",
            AlgoAlarmDetails.of("level", "A", "target", 24.1, "actual", 20.0, "measure", -17.0, "band", 5.0) },
        { "EXPRESSION",
            AlgoAlarmDetails.of("expression", "price < SMA(200) && \"x\" != 'y'", "result", true, "price", 95.0) } };
    for (String[] c : cases) {
      AlgoSignalKind kind = AlgoSignalKind.valueOf(c[0]);
      for (Locale locale : new Locale[] { DE, EN }) {
        String text = render(kind, c[1], locale);
        assertThat(text).as("%s %s", kind, locale).contains(": Nestlé\n").doesNotContain("{").doesNotContain("algo.");
      }
    }
  }

  @Test
  @DisplayName("Values that are keys are translated and numbers follow the reader's locale")
  void keysAreTranslatedAndNumbersLocalized() {
    String rebalance = AlgoAlarmDetails.of("trigger", "PERIODIC", "action", "REBALANCE_BUY", "target", 24.1, "actual",
        20.0, "deviation", -4.1, "amount", 4100.5, "currency", "CHF", "reason", "REBALANCE_BELOW_TARGET");
    assertThat(render(AlgoSignalKind.REBALANCE_DRIFT, rebalance, DE)).startsWith("Rebalancing-Abweichung erkannt")
        .contains("Kauf: Soll 24,10 %").contains("Unter dem Ziel");
    assertThat(render(AlgoSignalKind.REBALANCE_DRIFT, rebalance, EN)).contains("target 24.10 %")
        .contains("4,100.50 CHF");
    String ma = AlgoAlarmDetails.of("indicatorType", "SMA", "period", 200, "maValue", 99.5, "price", 101.0,
        "crossDirection", "BELOW");
    assertThat(render(AlgoSignalKind.MA_CROSSING, ma, DE)).contains("Einfacher gleitender Mittelwert über 200")
        .contains("Kreuzungsrichtung: Darunter");
    String holding = AlgoAlarmDetails.of("positionGainLossPercent", 26.5, "gainThreshold", 25.0, "loseThreshold", null,
        "price", 120.0);
    assertThat(render(AlgoSignalKind.HOLDING_GAIN_LOSS, holding, EN)).contains("loss threshold – %");
  }

  @Test
  @DisplayName("Details that cannot be rendered are shown as stored, so the notification still goes out")
  void unrenderableDetailsFallBackToTheStoredText() {
    String legacy = "1:2:3:0:2026-09-28:ENTRY:1 | MEAN_REVERSION_ENTRY | units=12.0 | price=45.6";
    assertThat(render(AlgoSignalKind.ENTRY_SIGNAL, legacy, DE))
        .isEqualTo("Einstiegssignal ausgelöst: Nestlé\n" + legacy);
    String incomplete = AlgoAlarmDetails.of("bound", "lower", "price", 89.0);
    assertThat(render(AlgoSignalKind.PRICE_ALERT, incomplete, EN)).endsWith("\n" + incomplete);
    assertThat(render(null, "{}", EN)).isEqualTo("Nestlé\n{}");
  }
}
