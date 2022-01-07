package strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import grafioschtrader.dto.HistoryquoteDateClose;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.security.UserAuthentication;
import grafioschtrader.service.UserService;
import grafioschtrader.ta.TaIndicatorData;
import grafioschtrader.ta.indicator.calc.ExponentialMovingAverage;
import grafioschtrader.test.start.GTforTest;

/**
 * We will compare the calculated values of GT technical indicators with the
 * ta4j.
 * 
 * TODO Integrate with integration tests.
 *
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = GTforTest.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class Ta4jTest {

  @Autowired
  HistoryquoteJpaRepository historyquoteJpaRepository;
  @Autowired
  UserService userService;

  @Disabled
  @Test
  void timeSeriesTest() {

    UserDetails authenticatedUser = userService.loadUserByUsername("hg@hugograf.com");
    final UserAuthentication userAuthentication = new UserAuthentication(authenticatedUser);
    SecurityContextHolder.getContext().setAuthentication(userAuthentication);

    List<HistoryquoteDateClose> historyquotes = historyquoteJpaRepository
        .findDateCloseByIdSecuritycurrencyAndCreateTypeFalseOrderByDateAsc(1965);

    ExponentialMovingAverage simpleMovingAverage = new ExponentialMovingAverage(200, historyquotes.size());
    historyquotes.forEach(historyquote -> simpleMovingAverage.addData(historyquote.getDate(), historyquote.getClose()));
    TaIndicatorData[] taIndicatorData = simpleMovingAverage.getTaIndicatorData();

    BarSeries series = new BaseBarSeriesBuilder().withName("IShare SLI").build();
    historyquotes.forEach(historyquote -> {
      ZonedDateTime zonedDateTime = ZonedDateTime.of(historyquote.getDate().atStartOfDay(), ZoneId.systemDefault());

      series.addBar(zonedDateTime, historyquote.getClose(), historyquote.getClose(), historyquote.getClose(),
          historyquote.getClose(), 0);
    });


    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    EMAIndicator shortSma = new EMAIndicator(closePrice, 200);

    int l = 0;
    for (int i = 0; i < historyquotes.size(); i++) {
      if (taIndicatorData[l].date.equals(historyquotes.get(i).getDate())) {
        assertThat(Math.abs(shortSma.getValue(i).doubleValue() - taIndicatorData[l++].value) < 0.01);
      }
    }

  }

}
