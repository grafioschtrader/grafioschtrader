package grafioschtrader.connector.instrument.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import grafioschtrader.connector.instrument.alphavantage.AlphaVantageFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.test.start.GTforTest;

@SpringBootTest(classes = GTforTest.class)
class AlphaVantageFeedConnectorTest {

  @Autowired
  private AlphaVantageFeedConnector alphaVantageConnector;

  
  /**
   * NEEDS Premium Membership!
   */
  @Test
  void getEodSecurityHistoryTest() {

    final List<Security> securities = new ArrayList<>();

    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("02.09.2019", germanFormatter);
    final LocalDate to = LocalDate.parse("25.10.2019", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

  
    securities.add(createSecurity("AAPL"));
    securities.add(createSecurity("MSFT"));
    securities.add(createSecurity("NESN.SW"));
    securities.parallelStream().forEach(security -> {

      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = alphaVantageConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertThat(historyquote.size()).isEqualTo(39);
    });
  }

  @Test
  void updateSecurityLastPriceTest() {
    final List<Security> securities = new ArrayList<>();

    securities.add(createSecurity("AAPL"));
    securities.add(createSecurity("MSFT"));
    securities.add(createSecurity("DAI.FRK"));
    // securities.add(createSecurity("NESN.SW"));
    securities.add(createSecurity("^DJI"));
    securities.parallelStream().forEach(security -> {
      try {
        alphaVantageConnector.updateSecurityLastPrice(security);
      } catch (final Exception e) {
        e.printStackTrace();
      }

      if (security.getSTimestamp() != null) {
        if (security.getSLow() != null && security.getSHigh() != null) {
          assertThat(security.getSLast()).isGreaterThanOrEqualTo(security.getSLow())
              .isLessThanOrEqualTo(security.getSHigh());
        } else {
          assertThat(security.getSLast()).isNotNull().isGreaterThan(0.0);
        }
      }
    });
  }

  Security createSecurity(final String ticker) {
    final Security security = new Security();
    security.setUrlIntraExtend(ticker);
    security.setUrlHistoryExtend(ticker);
    return security;
  }

}
