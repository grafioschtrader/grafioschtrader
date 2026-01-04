package grafioschtrader.gtnet.handler.impl.historyquote;

import java.util.ArrayList;
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

    // Single batch query for all securities
    List<Security> securities = securityJpaRepository.findByIsinCurrencyTuples(tuples);

    for (Security security : securities) {
      String key = security.getIsin() + ":" + security.getCurrency();
      InstrumentHistoryquoteDTO req = requestMap.get(key);
      if (req == null) {
        continue;
      }

      // Check if we're allowed to send this security's data
      if (!sendableIds.isEmpty() && !sendableIds.contains(security.getIdSecuritycurrency())) {
        continue;
      }

      // Query historyquotes for this security within the requested date range
      if (req.getFromDate() != null && req.getToDate() != null) {
        List<Historyquote> quotes = historyquoteJpaRepository
            .findByIdSecuritycurrencyAndDateBetweenOrderByDate(
                security.getIdSecuritycurrency(), req.getFromDate(), req.getToDate());

        if (!quotes.isEmpty()) {
          InstrumentHistoryquoteDTO responseDto = new InstrumentHistoryquoteDTO();
          responseDto.setIsin(security.getIsin());
          responseDto.setCurrency(security.getCurrency());
          responseDto.setFromDate(req.getFromDate());
          responseDto.setToDate(req.getToDate());
          responseDto.setRecords(convertToRecords(quotes));
          result.add(responseDto);
        }
      }
    }

    return result;
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

    // Single batch query for all currency pairs
    List<Currencypair> currencypairs = currencypairJpaRepository.findByCurrencyTuples(tuples);

    for (Currencypair currencypair : currencypairs) {
      String key = currencypair.getFromCurrency() + ":" + currencypair.getToCurrency();
      InstrumentHistoryquoteDTO req = requestMap.get(key);
      if (req == null) {
        continue;
      }

      // Check if we're allowed to send this currency pair's data
      if (!sendableIds.isEmpty() && !sendableIds.contains(currencypair.getIdSecuritycurrency())) {
        continue;
      }

      // Query historyquotes for this currency pair within the requested date range
      if (req.getFromDate() != null && req.getToDate() != null) {
        List<Historyquote> quotes = historyquoteJpaRepository
            .findByIdSecuritycurrencyAndDateBetweenOrderByDate(
                currencypair.getIdSecuritycurrency(), req.getFromDate(), req.getToDate());

        if (!quotes.isEmpty()) {
          InstrumentHistoryquoteDTO responseDto = new InstrumentHistoryquoteDTO();
          responseDto.setCurrency(currencypair.getFromCurrency());
          responseDto.setToCurrency(currencypair.getToCurrency());
          responseDto.setFromDate(req.getFromDate());
          responseDto.setToDate(req.getToDate());
          responseDto.setRecords(convertToRecords(quotes));
          result.add(responseDto);
        }
      }
    }

    return result;
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
}
