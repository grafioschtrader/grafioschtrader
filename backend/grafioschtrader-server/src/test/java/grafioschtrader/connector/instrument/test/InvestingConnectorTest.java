package grafioschtrader.connector.instrument.test;

import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import grafioschtrader.connector.instrument.investing.InvestingConnector;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;

class InvestingConnectorTest {

  @Test
  void getEodCurrencyHistoryTest() {
    final DateTimeFormatter germanFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.GERMAN);
    final List<Security> securities = new ArrayList<>();

    final LocalDate from = LocalDate.parse("01.01.2003", germanFormatter);
    final LocalDate to = LocalDate.parse("10.05.2021", germanFormatter);

    final Date fromDate = Date.from(from.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final Date toDate = Date.from(to.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    final InvestingConnector investingConnector = new InvestingConnector();
    
    securities.add(createSecurity("CNY/CHF -  Chinese Yuan Swiss Franc",
        "currencies/cny-chf-historical-data,9495,111486"));
    securities.add(createSecurity("Enel", "equities/enel-historical-data,6963,1160404"));
    securities.add(createSecurity("BTC/CHF - Bitcoin Swiss Franc",
        "indices/investing.com-btc-chf-historical-data,1117720,2207960"));
    securities.add(createSecurity("S&P 500 (SPX)", "indices/us-spx-500-historical-data,166,2030167"));

    securities.add(createSecurity("USD/CHF - US Dollar Swiss Franc", "currencies/usd-chf,4,106685"));
    securities.add(createSecurity("SMI Futures - Jun 19", "indices/switzerland-20-futures,8837,500048"));
    securities.add(createSecurity("USA 30-Year Bond Yiel", "rates-bonds/u.s.-30-year-bond-yield,23706,200657"));
    

    securities.parallelStream().forEach(security -> {
      List<Historyquote> historyquote = new ArrayList<>();
      try {
        historyquote = investingConnector.getEodSecurityHistory(security, fromDate, toDate);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      assertTrue(historyquote.size() > 10);
    });
  }

  Security createSecurity(final String name, final String urlHistoryExtend) {
    final Security security = new Security();
    security.setName(name);
    security.setUrlHistoryExtend(urlHistoryExtend);
    return security;
  }

}
