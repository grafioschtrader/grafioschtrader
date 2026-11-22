package grafioschtrader.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityActionJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.CreateType;
import grafioschtrader.types.SpecialInvestmentInstruments;

class SpinOffAllocationTest {
  private static final LocalDate EX_DATE = LocalDate.of(2026, 3, 2);
  private static final LocalDate END = EX_DATE.plusDays(10);
  private final SecurityJpaRepository securities = mock(SecurityJpaRepository.class);
  private final HistoryquoteJpaRepository quotes = mock(HistoryquoteJpaRepository.class);
  private final SecuritysplitJpaRepository splits = mock(SecuritysplitJpaRepository.class);
  private final CurrencypairJpaRepository pairs = mock(CurrencypairJpaRepository.class);
  private final SpinOffActionService service = new SpinOffActionService(mock(SecurityActionJpaRepository.class), null,
      securities, null, quotes, splits, pairs, null, null, null, null);
  private final Security child = equity(2, "USD", "US02079K3059");

  @BeforeEach
  void prices() {
    when(securities.findById(1)).thenReturn(Optional.of(equity(1, "USD", "US0378331005")));
    when(securities.findById(2)).thenReturn(Optional.of(child));
    when(quotes.findByIdSecuritycurrencyAndDateBetweenOrderByDate(1, EX_DATE, END))
        .thenReturn(List.of(new Historyquote(1, EX_DATE, 64)));
  }

  @Test
  void proposesFifteenPointSeventyNinePercentInOneCurrency() {
    when(quotes.findByIdSecuritycurrencyAndDateBetweenOrderByDate(2, EX_DATE, END))
        .thenReturn(List.of(new Historyquote(2, EX_DATE, 48)));

    var result = service.proposeAllocation(1, 2, EX_DATE, 4, 1);

    assertThat(result.priceDate()).isEqualTo(EX_DATE);
    assertThat(result.fxChildToParent()).isEqualTo(1);
    assertThat(result.proposedAllocation()).isCloseTo(0.1578947368, within(1e-10));
  }

  @Test
  void usesFirstCommonDayAndRestatesBothClosesInTheExDateShareBasis() {
    child.setCurrency("CHF");
    LocalDate priceDate = EX_DATE.plusDays(2);
    when(quotes.findByIdSecuritycurrencyAndDateBetweenOrderByDate(1, EX_DATE, END))
        .thenReturn(List.of(new Historyquote(1, EX_DATE, 32), new Historyquote(3, priceDate, 32)));
    when(quotes.findByIdSecuritycurrencyAndDateBetweenOrderByDate(2, EX_DATE, END))
        .thenReturn(List.of(new Historyquote(4, priceDate, 12), new Historyquote(5, END, 32)));
    when(splits.findByIdSecuritycurrencyOrderBySplitDateAsc(1))
        .thenReturn(List.of(new Securitysplit(1, EX_DATE, 1, 3, CreateType.CONNECTOR_CREATED),
            new Securitysplit(1, EX_DATE.plusDays(1), 1, 2, CreateType.CONNECTOR_CREATED)));
    when(splits.findByIdSecuritycurrencyOrderBySplitDateAsc(2))
        .thenReturn(List.of(new Securitysplit(2, EX_DATE.plusDays(5), 1, 4, CreateType.CONNECTOR_CREATED)));
    Currencypair inversePair = new Currencypair("USD", "CHF");
    when(pairs.findByFromCurrencyAndToCurrencyOrToCurrencyAndFromCurrency("CHF", "USD"))
        .thenReturn(List.of(inversePair));
    when(pairs.getHistoricalClosePriceForDate(inversePair, priceDate)).thenReturn(0.80);

    var result = service.proposeAllocation(1, 2, EX_DATE, 4, 1);

    assertThat(result.priceDate()).isEqualTo(priceDate);
    assertThat(result.parentClose()).isEqualTo(64);
    assertThat(result.childClose()).isEqualTo(48);
    assertThat(result.fxChildToParent()).isEqualTo(1.25);
    assertThat(result.proposedAllocation()).isCloseTo(0.1898734177, within(1e-10));
  }

  @Test
  void reportsNoProposalWhenTheCurrenciesCannotBeConvertedOnTheCommonDay() {
    child.setCurrency("CHF");
    when(quotes.findByIdSecuritycurrencyAndDateBetweenOrderByDate(2, EX_DATE, END))
        .thenReturn(List.of(new Historyquote(2, EX_DATE, 48)));

    assertThat(service.proposeAllocation(1, 2, EX_DATE, 4, 1).proposedAllocation()).isNull();
  }

  @Test
  void reportsNoProposalWhenThereIsNoCommonPriceDay() {
    when(quotes.findByIdSecuritycurrencyAndDateBetweenOrderByDate(2, EX_DATE, END))
        .thenReturn(List.of(new Historyquote(2, EX_DATE.plusDays(1), 48)));

    assertThat(service.proposeAllocation(1, 2, EX_DATE, 4, 1).proposedAllocation()).isNull();
  }

  private static Security equity(int id, String currency, String isin) {
    Security security = new Security();
    security.setIdSecuritycurrency(id);
    security.setCurrency(currency);
    security.setIsin(isin);
    Assetclass assetClass = new Assetclass();
    assetClass.setCategoryType(AssetclassType.EQUITIES);
    assetClass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.DIRECT_INVESTMENT);
    security.setAssetClass(assetClass);
    return security;
  }
}
