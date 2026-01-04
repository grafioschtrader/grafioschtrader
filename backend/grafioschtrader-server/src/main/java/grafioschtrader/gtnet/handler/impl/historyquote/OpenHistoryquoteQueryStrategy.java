package grafioschtrader.gtnet.handler.impl.historyquote;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.m2m.model.HistoryquoteRecordDTO;
import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/**
 * Strategy for AC_OPEN mode: queries local Security/Currencypair historyquote data only.
 *
 * Behavior:
 * <ul>
 *   <li>Queries local Security and Currencypair entities to find matching instruments</li>
 *   <li>Queries local historyquote table for the requested date ranges</li>
 *   <li>Returns local historical data that matches the request</li>
 *   <li>For instruments where no data is available AND the server WANTS to receive data,
 *       returns a "want to receive" marker with the date from which data is desired</li>
 *   <li>Does NOT interact with GTNetHistoryquote table</li>
 * </ul>
 *
 * Unlike AC_PUSH_OPEN, this mode only shares data from instruments that exist in the local database.
 */
@Component
public class OpenHistoryquoteQueryStrategy implements HistoryquoteQueryStrategy {

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Override
  @Transactional(readOnly = true)
  public List<InstrumentHistoryquoteDTO> querySecurities(List<InstrumentHistoryquoteDTO> requested,
      Set<Integer> sendableIds) {
    List<InstrumentHistoryquoteDTO> result = new ArrayList<>();
    if (requested == null || requested.isEmpty()) {
      return result;
    }

    // Build list of valid ISIN+currency tuples
    List<String[]> tuples = new ArrayList<>();
    Map<String, InstrumentHistoryquoteDTO> requestMap = new HashMap<>();
    for (InstrumentHistoryquoteDTO req : requested) {
      if (req.getIsin() != null && req.getCurrency() != null) {
        tuples.add(new String[] { req.getIsin(), req.getCurrency() });
        requestMap.put(req.getIsin() + ":" + req.getCurrency(), req);
      }
    }

    if (tuples.isEmpty()) {
      return result;
    }

    // Get IDs of securities we want to receive historical data for
    Set<Integer> receivableIds = securityJpaRepository.findIdsWithGtNetHistoricalRecv();

    // Single batch query for all securities
    List<Security> securities = securityJpaRepository.findByIsinCurrencyTuples(tuples);

    for (Security security : securities) {
      String key = security.getIsin() + ":" + security.getCurrency();
      InstrumentHistoryquoteDTO req = requestMap.get(key);
      if (req == null) {
        continue;
      }

      Integer securityId = security.getIdSecuritycurrency();

      // Check if we're allowed to send this security's data
      boolean canSend = sendableIds.isEmpty() || sendableIds.contains(securityId);

      if (!canSend) {
        // Cannot send, but check if we WANT to receive data for this instrument
        if (receivableIds.contains(securityId)) {
          addWantToReceiveMarkerForSecurity(result, security);
        }
        continue;
      }

      // Query historyquotes for this security within the requested date range
      if (req.getFromDate() != null && req.getToDate() != null) {
        List<Historyquote> quotes = historyquoteJpaRepository
            .findByIdSecuritycurrencyAndDateBetweenOrderByDate(securityId, req.getFromDate(), req.getToDate());

        if (!quotes.isEmpty()) {
          InstrumentHistoryquoteDTO responseDto = new InstrumentHistoryquoteDTO();
          responseDto.setIsin(security.getIsin());
          responseDto.setCurrency(security.getCurrency());
          responseDto.setFromDate(req.getFromDate());
          responseDto.setToDate(req.getToDate());
          responseDto.setRecords(convertToRecords(quotes));
          result.add(responseDto);
        } else if (receivableIds.contains(securityId)) {
          // No quotes available but we want to receive data for this instrument
          addWantToReceiveMarkerForSecurity(result, security);
        }
      }
    }

    return result;
  }

  /**
   * Adds a "want to receive" marker for a security if we have any local data.
   * The marker indicates the date from which we need historical data.
   */
  private void addWantToReceiveMarkerForSecurity(List<InstrumentHistoryquoteDTO> result, Security security) {
    Date latestDate = historyquoteJpaRepository.getMaxDateByIdSecurity(security.getIdSecuritycurrency());
    if (latestDate != null) {
      Date wantsFromDate = addDays(latestDate, 1);
      result.add(InstrumentHistoryquoteDTO.forSecurityWantToReceive(
          security.getIsin(), security.getCurrency(), wantsFromDate));
    } else {
      // No local data at all - want data from the security's active date
      Date activeFrom = security.getActiveFromDate();
      if (activeFrom != null) {
        result.add(InstrumentHistoryquoteDTO.forSecurityWantToReceive(
            security.getIsin(), security.getCurrency(), activeFrom));
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<InstrumentHistoryquoteDTO> queryCurrencypairs(List<InstrumentHistoryquoteDTO> requested,
      Set<Integer> sendableIds) {
    List<InstrumentHistoryquoteDTO> result = new ArrayList<>();
    if (requested == null || requested.isEmpty()) {
      return result;
    }

    // Build list of valid fromCurrency+toCurrency tuples
    List<String[]> tuples = new ArrayList<>();
    Map<String, InstrumentHistoryquoteDTO> requestMap = new HashMap<>();
    for (InstrumentHistoryquoteDTO req : requested) {
      if (req.getCurrency() != null && req.getToCurrency() != null) {
        tuples.add(new String[] { req.getCurrency(), req.getToCurrency() });
        requestMap.put(req.getCurrency() + ":" + req.getToCurrency(), req);
      }
    }

    if (tuples.isEmpty()) {
      return result;
    }

    // Get IDs of currency pairs we want to receive historical data for
    Set<Integer> receivableIds = currencypairJpaRepository.findIdsWithGtNetHistoricalRecv();

    // Single batch query for all currency pairs
    List<Currencypair> currencypairs = currencypairJpaRepository.findByCurrencyTuples(tuples);

    for (Currencypair currencypair : currencypairs) {
      String key = currencypair.getFromCurrency() + ":" + currencypair.getToCurrency();
      InstrumentHistoryquoteDTO req = requestMap.get(key);
      if (req == null) {
        continue;
      }

      Integer pairId = currencypair.getIdSecuritycurrency();

      // Check if we're allowed to send this currency pair's data
      boolean canSend = sendableIds.isEmpty() || sendableIds.contains(pairId);

      if (!canSend) {
        // Cannot send, but check if we WANT to receive data for this instrument
        if (receivableIds.contains(pairId)) {
          addWantToReceiveMarkerForCurrencypair(result, currencypair);
        }
        continue;
      }

      // Query historyquotes for this currency pair within the requested date range
      if (req.getFromDate() != null && req.getToDate() != null) {
        List<Historyquote> quotes = historyquoteJpaRepository
            .findByIdSecuritycurrencyAndDateBetweenOrderByDate(pairId, req.getFromDate(), req.getToDate());

        if (!quotes.isEmpty()) {
          InstrumentHistoryquoteDTO responseDto = new InstrumentHistoryquoteDTO();
          responseDto.setCurrency(currencypair.getFromCurrency());
          responseDto.setToCurrency(currencypair.getToCurrency());
          responseDto.setFromDate(req.getFromDate());
          responseDto.setToDate(req.getToDate());
          responseDto.setRecords(convertToRecords(quotes));
          result.add(responseDto);
        } else if (receivableIds.contains(pairId)) {
          // No quotes available but we want to receive data for this instrument
          addWantToReceiveMarkerForCurrencypair(result, currencypair);
        }
      }
    }

    return result;
  }

  /**
   * Adds a "want to receive" marker for a currency pair if we have any local data.
   * The marker indicates the date from which we need historical data.
   */
  private void addWantToReceiveMarkerForCurrencypair(List<InstrumentHistoryquoteDTO> result, Currencypair currencypair) {
    Date latestDate = historyquoteJpaRepository.getMaxDateByIdSecurity(currencypair.getIdSecuritycurrency());
    if (latestDate != null) {
      Date wantsFromDate = addDays(latestDate, 1);
      result.add(InstrumentHistoryquoteDTO.forCurrencypairWantToReceive(
          currencypair.getFromCurrency(), currencypair.getToCurrency(), wantsFromDate));
    }
    // Note: Currency pairs don't have an activeFromDate like securities
  }

  private List<HistoryquoteRecordDTO> convertToRecords(List<Historyquote> quotes) {
    List<HistoryquoteRecordDTO> records = new ArrayList<>();
    for (Historyquote hq : quotes) {
      records.add(new HistoryquoteRecordDTO(
          hq.getDate(),
          hq.getOpen(),
          hq.getHigh(),
          hq.getLow(),
          hq.getClose(),
          hq.getVolume()));
    }
    return records;
  }

  /**
   * Adds specified number of days to a date.
   */
  private Date addDays(Date date, int days) {
    Calendar cal = Calendar.getInstance();
    cal.setTime(date);
    cal.add(Calendar.DAY_OF_MONTH, days);
    return cal.getTime();
  }
}
