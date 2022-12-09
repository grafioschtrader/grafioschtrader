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

import grafioschtrader.connector.instrument.stockworld.StockworldFeedConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

class StockworldFeedConnectorTest {

  private StockworldFeedConnector stockworldFeedConnector = new StockworldFeedConnector();
  
  @Test
  void getEodSecurityHistoryTest() {

    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final LocalDate from = LocalDate.parse("10.03.2018", germanFormatter);
    final LocalDate to = LocalDate.parse("21.05.2021", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

    final List<Security> securities = getStocks();

    securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = stockworldFeedConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      System.out.println(security.getName() + " Size: " + historyquote.size());
      assertThat(historyquote.size()).isEqualTo(security.getDenomination());
    });
  }
  

  private List<Security> getStocks() {
    final List<Security> securities = new ArrayList<>();
    securities.add(createSecurity("ComStage STOXXEurope 600 Food & Beverage NR UCITS ETF", "LU0378435803", "149970851", 764));
    securities.add(createSecurity("BASF", "DE000BASF111", "293", 764));
    securities.add(createSecurity("Bayerische Landesbank 2,5% 17/27", "DE000BLB4UP9", "128405128", 762));
    return securities;
  }

  private Security createSecurity(final String name, final String intraTicker, final String urlQuoteFeedExtend,
      final int expectedRows) {
    final Security security = new Security();
    security.setName(name);
    security.setUrlHistoryExtend(urlQuoteFeedExtend);
    security.setUrlIntraExtend(urlQuoteFeedExtend);
    security.setDenomination(expectedRows);
    return security;
  }

}
