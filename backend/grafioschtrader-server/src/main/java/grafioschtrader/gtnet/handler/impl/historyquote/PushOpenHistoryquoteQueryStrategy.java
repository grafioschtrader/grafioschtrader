package grafioschtrader.gtnet.handler.impl.historyquote;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.GTNetHistoryquote;
import grafioschtrader.entities.GTNetInstrumentCurrencypair;
import grafioschtrader.entities.GTNetInstrumentSecurity;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.gtnet.m2m.model.HistoryquoteRecordDTO;
import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import grafioschtrader.repository.GTNetHistoryquoteJpaRepository;
import grafioschtrader.repository.GTNetInstrumentCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetInstrumentSecurityJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.service.GlobalparametersService;

/**
 * Strategy for AC_PUSH_OPEN mode: queries GTNetHistoryquote (for foreign instruments)
 * or local historyquote table (for local instruments).
 *
 * Behavior:
 * <ul>
 *   <li>Queries GTNetInstrumentSecurity/GTNetInstrumentCurrencypair to find instruments in the pool</li>
 *   <li>For local instruments (idSecuritycurrency != null): queries local historyquote table</li>
 *   <li>For foreign instruments (idSecuritycurrency == null): queries gt_net_historyquote table</li>
 *   <li>For instruments NOT found in the pool with historical data to share: creates new entries</li>
 *   <li>Returns historical data for all matching instruments within the requested date range</li>
 * </ul>
 */
@Component
public class PushOpenHistoryquoteQueryStrategy implements HistoryquoteQueryStrategy {

  @Autowired
  private GTNetInstrumentSecurityJpaRepository gtNetInstrumentSecurityJpaRepository;

  @Autowired
  private GTNetInstrumentCurrencypairJpaRepository gtNetInstrumentCurrencypairJpaRepository;

  @Autowired
  private GTNetHistoryquoteJpaRepository gtNetHistoryquoteJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Override
  @Transactional
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

    // Single batch query for all security instruments from pool
    List<GTNetInstrumentSecurity> instruments = gtNetInstrumentSecurityJpaRepository.findByIsinCurrencyTuples(tuples);

    // Track which instruments were found in the pool
    Set<String> foundKeys = new HashSet<>();

    // Match results with requests and query historical data
    for (GTNetInstrumentSecurity instrument : instruments) {
      String key = instrument.getIsin() + ":" + instrument.getCurrency();
      foundKeys.add(key);
      InstrumentHistoryquoteDTO req = requestMap.get(key);

      if (req != null && req.getFromDate() != null && req.getToDate() != null) {
        InstrumentHistoryquoteDTO responseDto = queryHistoryquotesForSecurity(instrument, req);
        if (responseDto != null && responseDto.getRecordCount() > 0) {
          result.add(responseDto);
        }
      }
    }

    // For instruments not found in pool, create entries if request has records to store
    Integer myGtNetId = globalparametersService.getGTNetMyEntryID();
    if (myGtNetId != null) {
      for (Map.Entry<String, InstrumentHistoryquoteDTO> entry : requestMap.entrySet()) {
        if (!foundKeys.contains(entry.getKey())) {
          InstrumentHistoryquoteDTO req = entry.getValue();
          if (req.getRecords() != null && !req.getRecords().isEmpty()) {
            // Create instrument entry in the pool
            GTNetInstrumentSecurity newInstrument = gtNetInstrumentSecurityJpaRepository
                .findOrCreateInstrument(req.getIsin(), req.getCurrency(), null, myGtNetId);

            // Store the received historical data
            storeGtNetHistoryquotes(newInstrument.getIdGtNetInstrument(), req.getRecords());
          }
        }
      }
    }

    return result;
  }

  @Override
  @Transactional
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

    // Single batch query for all currency pair instruments from pool
    List<GTNetInstrumentCurrencypair> instruments = gtNetInstrumentCurrencypairJpaRepository.findByCurrencyTuples(tuples);

    // Track which instruments were found in the pool
    Set<String> foundKeys = new HashSet<>();

    // Match results with requests and query historical data
    for (GTNetInstrumentCurrencypair instrument : instruments) {
      String key = instrument.getFromCurrency() + ":" + instrument.getToCurrency();
      foundKeys.add(key);
      InstrumentHistoryquoteDTO req = requestMap.get(key);

      if (req != null && req.getFromDate() != null && req.getToDate() != null) {
        InstrumentHistoryquoteDTO responseDto = queryHistoryquotesForCurrencypair(instrument, req);
        if (responseDto != null && responseDto.getRecordCount() > 0) {
          result.add(responseDto);
        }
      }
    }

    // For instruments not found in pool, create entries if request has records to store
    Integer myGtNetId = globalparametersService.getGTNetMyEntryID();
    if (myGtNetId != null) {
      for (Map.Entry<String, InstrumentHistoryquoteDTO> entry : requestMap.entrySet()) {
        if (!foundKeys.contains(entry.getKey())) {
          InstrumentHistoryquoteDTO req = entry.getValue();
          if (req.getRecords() != null && !req.getRecords().isEmpty()) {
            // Create instrument entry in the pool
            GTNetInstrumentCurrencypair newInstrument = gtNetInstrumentCurrencypairJpaRepository
                .findOrCreateInstrument(req.getCurrency(), req.getToCurrency(), null, myGtNetId);

            // Store the received historical data
            storeGtNetHistoryquotes(newInstrument.getIdGtNetInstrument(), req.getRecords());
          }
        }
      }
    }

    return result;
  }

  /**
   * Queries historical quotes for a security instrument.
   * Uses local historyquote table for local instruments, GTNetHistoryquote for foreign instruments.
   */
  private InstrumentHistoryquoteDTO queryHistoryquotesForSecurity(GTNetInstrumentSecurity instrument,
      InstrumentHistoryquoteDTO req) {
    List<HistoryquoteRecordDTO> records;

    if (instrument.isLocalInstrument()) {
      // Local instrument - query from historyquote table
      List<Historyquote> quotes = historyquoteJpaRepository.findByIdSecuritycurrencyAndDateBetweenOrderByDate(
          instrument.getIdSecuritycurrency(), req.getFromDate(), req.getToDate());
      records = convertLocalHistoryquotes(quotes);
    } else {
      // Foreign instrument - query from gt_net_historyquote table
      List<GTNetHistoryquote> quotes = gtNetHistoryquoteJpaRepository.findByInstrumentIdAndDateRange(
          instrument.getIdGtNetInstrument(), req.getFromDate(), req.getToDate());
      records = convertGtNetHistoryquotes(quotes);
    }

    if (records.isEmpty()) {
      return null;
    }

    InstrumentHistoryquoteDTO response = new InstrumentHistoryquoteDTO();
    response.setIsin(instrument.getIsin());
    response.setCurrency(instrument.getCurrency());
    response.setFromDate(req.getFromDate());
    response.setToDate(req.getToDate());
    response.setRecords(records);
    return response;
  }

  /**
   * Queries historical quotes for a currency pair instrument.
   * Uses local historyquote table for local instruments, GTNetHistoryquote for foreign instruments.
   */
  private InstrumentHistoryquoteDTO queryHistoryquotesForCurrencypair(GTNetInstrumentCurrencypair instrument,
      InstrumentHistoryquoteDTO req) {
    List<HistoryquoteRecordDTO> records;

    if (instrument.isLocalInstrument()) {
      // Local instrument - query from historyquote table
      List<Historyquote> quotes = historyquoteJpaRepository.findByIdSecuritycurrencyAndDateBetweenOrderByDate(
          instrument.getIdSecuritycurrency(), req.getFromDate(), req.getToDate());
      records = convertLocalHistoryquotes(quotes);
    } else {
      // Foreign instrument - query from gt_net_historyquote table
      List<GTNetHistoryquote> quotes = gtNetHistoryquoteJpaRepository.findByInstrumentIdAndDateRange(
          instrument.getIdGtNetInstrument(), req.getFromDate(), req.getToDate());
      records = convertGtNetHistoryquotes(quotes);
    }

    if (records.isEmpty()) {
      return null;
    }

    InstrumentHistoryquoteDTO response = new InstrumentHistoryquoteDTO();
    response.setCurrency(instrument.getFromCurrency());
    response.setToCurrency(instrument.getToCurrency());
    response.setFromDate(req.getFromDate());
    response.setToDate(req.getToDate());
    response.setRecords(records);
    return response;
  }

  private List<HistoryquoteRecordDTO> convertLocalHistoryquotes(List<Historyquote> quotes) {
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

  private List<HistoryquoteRecordDTO> convertGtNetHistoryquotes(List<GTNetHistoryquote> quotes) {
    List<HistoryquoteRecordDTO> records = new ArrayList<>();
    for (GTNetHistoryquote hq : quotes) {
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

  private void storeGtNetHistoryquotes(Integer idGtNetInstrument, List<HistoryquoteRecordDTO> records) {
    for (HistoryquoteRecordDTO record : records) {
      if (record.getDate() != null && record.getClose() != null) {
        // Check if entry already exists
        var existing = gtNetHistoryquoteJpaRepository
            .findByGtNetInstrumentIdGtNetInstrumentAndDate(idGtNetInstrument, record.getDate());

        if (existing.isEmpty()) {
          GTNetHistoryquote hq = new GTNetHistoryquote();
          // Need to set the instrument reference - fetch it first
          var instrument = gtNetInstrumentSecurityJpaRepository.findById(idGtNetInstrument);
          if (instrument.isEmpty()) {
            var cpInstrument = gtNetInstrumentCurrencypairJpaRepository.findById(idGtNetInstrument);
            if (cpInstrument.isPresent()) {
              hq.setGtNetInstrument(cpInstrument.get());
            }
          } else {
            hq.setGtNetInstrument(instrument.get());
          }
          hq.setDate(record.getDate());
          hq.setOpen(record.getOpen());
          hq.setHigh(record.getHigh());
          hq.setLow(record.getLow());
          hq.setClose(record.getClose());
          hq.setVolume(record.getVolume());
          gtNetHistoryquoteJpaRepository.save(hq);
        }
      }
    }
  }
}
