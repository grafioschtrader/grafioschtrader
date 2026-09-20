package grafioschtrader.priceupdate.historyquote;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import grafioschtrader.dto.HisotryqouteLinearFilledSummary;
import grafioschtrader.entities.BankruptSecurity;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.repository.BankruptSecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TradingDaysPlusJpaRepository;

/**
 * Keeps the price history of the instruments marked in {@code bankrupt_security} complete.
 *
 * <p>
 * The end of day task calls this after the connectors have run, so a price a provider did deliver is already in place
 * and only the days around it are created. Every created row carries
 * {@code HistoryquoteCreateType.FILLED_CLOSED_LINEAR_TRADING_DAY}, deliberately not the create type of the connector
 * gap filler: that one is removed and rewritten on every connector run, which would undo this filling daily.
 * </p>
 *
 * <p>
 * Nothing already written is ever revised. A trailing gap is filled flat with the last known close, so when the issuer
 * finally reports a much lower price, that price applies from its own date onwards and the days before it keep the
 * value every report has already shown for them.
 * </p>
 */
@Service
public class BankruptSecurityFillService {

  private static final Logger log = LoggerFactory.getLogger(BankruptSecurityFillService.class);

  private final BankruptSecurityJpaRepository bankruptSecurityJpaRepository;
  private final SecurityJpaRepository securityJpaRepository;
  private final TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;
  private final HistoryquoteQualityService historyquoteQualityService;

  public BankruptSecurityFillService(BankruptSecurityJpaRepository bankruptSecurityJpaRepository,
      SecurityJpaRepository securityJpaRepository, TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository,
      HistoryquoteQualityService historyquoteQualityService) {
    this.bankruptSecurityJpaRepository = bankruptSecurityJpaRepository;
    this.securityJpaRepository = securityJpaRepository;
    this.tradingDaysPlusJpaRepository = tradingDaysPlusJpaRepository;
    this.historyquoteQualityService = historyquoteQualityService;
  }

  /**
   * Completes the history of every marked instrument up to the last day that can be filled.
   *
   * <p>
   * Each instrument is a unit of work of its own and a failure on one is logged and stepped over. This runs inside a
   * task that also updates every price in the system and may be interrupted, so a single unreadable instrument must not
   * cost the rest of that run.
   * </p>
   *
   * @return how many closing prices were created over all marked instruments
   */
  public int fillMarkedSecurities() {
    List<BankruptSecurity> marked = bankruptSecurityJpaRepository.findAll();
    if (marked.isEmpty()) {
      return 0;
    }
    LocalDate lastCompletedTradingDay = lastCompletedTradingDay();
    int created = 0;
    for (BankruptSecurity bankruptSecurity : marked) {
      try {
        created += fillSingle(bankruptSecurity, lastCompletedTradingDay);
      } catch (Exception e) {
        log.warn("Filling the price history of marked instrument {} failed", bankruptSecurity.getIdSecuritycurrency(),
            e);
      }
    }
    return created;
  }

  private int fillSingle(BankruptSecurity bankruptSecurity, LocalDate lastCompletedTradingDay) {
    Optional<Security> securityOpt = securityJpaRepository.findById(bankruptSecurity.getIdSecuritycurrency());
    if (securityOpt.isEmpty()) {
      return 0;
    }
    Security security = securityOpt.get();
    LocalDate fillUpTo = fillUpTo(security, lastCompletedTradingDay);
    if (fillUpTo == null || fillUpTo.isBefore(security.getActiveFromDate())) {
      return 0;
    }
    // Never moves weekend rows: that repair deletes and rewrites prices a provider delivered and stays a deliberate act
    // of a user. The locale only decides the language of a summary message nobody reads on this path.
    HisotryqouteLinearFilledSummary summary = historyquoteQualityService
        .fillGapsLinearInternal(security.getIdSecuritycurrency(), fillUpTo, false, Locale.ENGLISH);
    if (summary.createdHistoryquotes > 0) {
      log.info("Created {} closing prices for marked instrument {} up to {}", summary.createdHistoryquotes,
          security.getIdSecuritycurrency(), fillUpTo);
    }
    return summary.createdHistoryquotes;
  }

  /**
   * Last day a marked instrument may receive a price: as far as the trading calendar of its exchange reaches, never
   * beyond the last completed session, and never past the day the instrument stops being active.
   *
   * <p>
   * The active to date is a ceiling here and never the target. For a bond of a bankrupt issuer it is the maturity and
   * therefore years away, and filling up to it would create prices for days no report will ever ask about.
   * </p>
   *
   * @param security                the instrument whose prices would be created
   * @param lastCompletedTradingDay newest session of the global trading calendar, null when it holds none
   * @return the last fillable day, or null when there is none
   */
  private LocalDate fillUpTo(Security security, LocalDate lastCompletedTradingDay) {
    LocalDate fillUpTo = security.getStockexchange().getCalendarKnownUntil();
    if (lastCompletedTradingDay != null && lastCompletedTradingDay.isBefore(fillUpTo)) {
      fillUpTo = lastCompletedTradingDay;
    }
    return security.getActiveToDate().isBefore(fillUpTo) ? security.getActiveToDate() : fillUpTo;
  }

  private LocalDate lastCompletedTradingDay() {
    TradingDaysPlus tradingDay = tradingDaysPlusJpaRepository
        .findTopByTradingDateLessThanOrderByTradingDateDesc(LocalDate.now());
    return tradingDay == null ? null : tradingDay.getTradingDate();
  }
}
