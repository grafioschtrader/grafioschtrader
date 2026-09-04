package grafioschtrader.repository.dataverification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository;
import grafioschtrader.repository.HoldSecurityaccountSecurityJpaRepository.DateSecurityQuoteMissing;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.test.start.GTforTest;

/**
 * Reports the days on which a holding or a cash balance cannot be converted into the currency of its client, because
 * the currency pair carries no rate for that day.
 *
 * <p>
 * Such a day is dropped from the period performance report, which is correct but invisible: the report simply has no
 * row for it. This diagnostic makes the drop countable. It is the exchange rate half of the baseline that
 * {@code scripts/check-hold-tables.mjs} takes for the holding tables themselves, and it distinguishes the two causes
 * that need different remedies - a currency pair with a hole in its history, which the daily gap filling closes, and a
 * currency pair without any price at all, which needs a working data source before any day of its hold period can be
 * valued.
 * </p>
 *
 * <p>
 * Like its neighbours in this package the test is disabled and reads the production database. Enable it, run it alone,
 * and read the output; it writes nothing.
 * </p>
 */
@Transactional
@SpringBootTest(classes = GTforTest.class)
@ActiveProfiles("prod")
class MissingExchangeRateHoldDaysTest {

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Test
  @Disabled
  void reportMissingExchangeRatesOfAllTenants() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    int tenantsAffected = 0;

    for (Tenant tenant : tenantJpaRepository.findAll()) {
      Integer idTenant = tenant.getIdTenant();
      LocalDate firstHoldDate = holdSecurityaccountSecurityRepository.findByIdTenantMinFromHoldDate(idTenant);
      if (firstHoldDate == null || !firstHoldDate.isBefore(yesterday)) {
        continue;
      }
      List<String> lines = collectMissingRates(idTenant, firstHoldDate, yesterday);
      List<String> withoutAnyRate = holdSecurityaccountSecurityRepository
          .getCurrencypairsWithoutAnyQuoteByTenant(idTenant);
      if (lines.isEmpty() && withoutAnyRate.isEmpty()) {
        continue;
      }
      tenantsAffected++;
      System.out.println("Client " + idTenant + " (" + tenant.getCurrency() + "), holdings since " + firstHoldDate);
      lines.forEach(line -> System.out.println("  " + line));
      if (!withoutAnyRate.isEmpty()) {
        System.out.println("  without any price at all: " + String.join(", ", withoutAnyRate));
      }
    }
    System.out.println(tenantsAffected == 0 ? "No client has a hold day without an exchange rate."
        : tenantsAffected + " client(s) have hold days without an exchange rate.");
  }

  /**
   * Splits the mixed result of the missing quote query into its two kinds and returns one line per affected currency
   * pair. The securities are dropped here, they are the subject of the missing end-of-day price view of the client.
   */
  private List<String> collectMissingRates(Integer idTenant, LocalDate fromDate, LocalDate toDate) {
    List<DateSecurityQuoteMissing> missingList = holdSecurityaccountSecurityRepository
        .getMissingQuotesForSecurityByTenantAndPeriod(idTenant, fromDate, toDate);
    if (missingList.isEmpty()) {
      return Collections.emptyList();
    }
    Set<Integer> ids = new HashSet<>();
    missingList.forEach(missing -> ids.add(missing.getIdSecuritycurrency()));
    // A securitycurrency id identifies either a security or a currency pair, so what the security repository does not
    // return is a currency pair. The production code resolves the same result in this way.
    Set<Integer> idsCurrencypair = new HashSet<>(ids);
    for (Security security : securityJpaRepository.findByIdSecuritycurrencyInOrderByName(ids)) {
      idsCurrencypair.remove(security.getIdSecuritycurrency());
    }
    if (idsCurrencypair.isEmpty()) {
      return Collections.emptyList();
    }

    Map<Integer, String> nameById = new TreeMap<>();
    for (Currencypair currencypair : currencypairJpaRepository.findAllById(idsCurrencypair)) {
      nameById.put(currencypair.getIdSecuritycurrency(), currencypair.getName());
    }
    Map<Integer, List<LocalDate>> datesById = new TreeMap<>();
    for (DateSecurityQuoteMissing missing : missingList) {
      if (idsCurrencypair.contains(missing.getIdSecuritycurrency())) {
        datesById.computeIfAbsent(missing.getIdSecuritycurrency(), _ -> new ArrayList<>())
            .add(missing.getTradingDate());
      }
    }

    List<String> lines = new ArrayList<>();
    datesById.forEach((id, dates) -> {
      Collections.sort(dates);
      lines.add(nameById.getOrDefault(id, "id " + id) + ": " + dates.size() + " day(s), " + dates.getFirst() + " to "
          + dates.getLast());
    });
    return lines;
  }
}
