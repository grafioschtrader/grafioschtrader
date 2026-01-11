package grafioschtrader.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import grafioschtrader.gtnet.model.msg.HistoryquoteExchangeMsg;
import grafioschtrader.priceupdate.historyquote.SecurityCurrencyMaxHistoryquoteData;

/**
 * Tracks instruments during GTNet historyquote exchange, maintaining state of which have been filled.
 *
 * This helper class manages the intersection of BaseHistoryquoteThru instruments with GTNet-enabled
 * instruments and tracks which ones have received historical quotes from remote servers. It supports:
 * <ul>
 *   <li>Building the initial set from SecurityCurrencyMaxHistoryquoteData</li>
 *   <li>Creating request DTOs with date ranges</li>
 *   <li>Marking instruments as filled when historyquotes are received</li>
 *   <li>Tracking "want to receive" markers from suppliers for push-back</li>
 *   <li>Returning unfilled instruments for fallback to connectors</li>
 * </ul>
 *
 * @param <S> Security or Currencypair
 */
public class HistoryquoteExchangeSet<S extends Securitycurrency<S>> {

  private final Map<String, SecurityCurrencyMaxHistoryquoteData<S>> instruments = new HashMap<>();
  private final Map<String, Date> fromDates = new HashMap<>();
  private final Map<String, Date> toDates = new HashMap<>();
  private final Set<String> filledKeys = new HashSet<>();
  private final Map<GTNet, List<InstrumentHistoryquoteDTO>> wantToReceiveBySupplier = new HashMap<>();
  private final Map<String, InstrumentHistoryquoteDTO> receivedData = new HashMap<>();

  /**
   * Adds an instrument to the exchange set with its date range.
   *
   * @param data the SecurityCurrencyMaxHistoryquoteData from BaseHistoryquoteThru
   * @param fromDate the start date for historyquote request
   * @param toDate the end date for historyquote request
   */
  public void addInstrument(SecurityCurrencyMaxHistoryquoteData<S> data, Date fromDate, Date toDate) {
    String key = buildKey(data.getSecurityCurrency());
    if (key != null) {
      instruments.put(key, data);
      fromDates.put(key, fromDate);
      toDates.put(key, toDate);
    }
  }

  /**
   * Builds the instrument key based on type.
   */
  private String buildKey(S securitycurrency) {
    if (securitycurrency instanceof Security security) {
      if (security.getIsin() != null) {
        return security.getIsin() + ":" + security.getCurrency();
      }
    } else if (securitycurrency instanceof Currencypair pair) {
      return pair.getFromCurrency() + ":" + pair.getToCurrency();
    }
    return null;
  }

  /**
   * Marks an instrument as filled with historical data.
   */
  public void markAsFilled(String key) {
    filledKeys.add(key);
  }

  /**
   * Processes a response from a remote server and tracks "want to receive" markers.
   *
   * @param response the response containing historical quotes and/or want-to-receive markers
   * @param supplier the GTNet supplier that sent this response
   * @return the number of instruments that were marked as filled
   */
  public int processResponse(HistoryquoteExchangeMsg response, GTNet supplier) {
    int filledCount = 0;

    if (response == null) {
      return 0;
    }

    // Process securities with data
    if (response.securities != null) {
      for (InstrumentHistoryquoteDTO dto : response.securities) {
        if (dto.getRecords() != null && !dto.getRecords().isEmpty()) {
          String key = dto.getIsin() + ":" + dto.getCurrency();
          if (instruments.containsKey(key) && !filledKeys.contains(key)) {
            markAsFilled(key);
            receivedData.put(key, dto);
            filledCount++;
          }
        }
      }
    }

    // Process currency pairs with data
    if (response.currencypairs != null) {
      for (InstrumentHistoryquoteDTO dto : response.currencypairs) {
        if (dto.getRecords() != null && !dto.getRecords().isEmpty()) {
          String key = dto.getCurrency() + ":" + dto.getToCurrency();
          if (instruments.containsKey(key) && !filledKeys.contains(key)) {
            markAsFilled(key);
            receivedData.put(key, dto);
            filledCount++;
          }
        }
      }
    }

    // Track "want to receive" markers
    List<InstrumentHistoryquoteDTO> wantToReceive = new ArrayList<>();
    wantToReceive.addAll(response.getSecuritiesWantingData());
    wantToReceive.addAll(response.getCurrencypairsWantingData());
    if (!wantToReceive.isEmpty()) {
      wantToReceiveBySupplier.put(supplier, wantToReceive);
    }

    return filledCount;
  }

  /**
   * Creates request DTOs for unfilled securities with their date ranges.
   */
  public List<InstrumentHistoryquoteDTO> getUnfilledSecurityDTOs() {
    List<InstrumentHistoryquoteDTO> result = new ArrayList<>();
    for (Map.Entry<String, SecurityCurrencyMaxHistoryquoteData<S>> entry : instruments.entrySet()) {
      String key = entry.getKey();
      if (!filledKeys.contains(key)) {
        S sc = entry.getValue().getSecurityCurrency();
        if (sc instanceof Security security) {
          result.add(InstrumentHistoryquoteDTO.forSecurityRequest(
              security.getIsin(),
              security.getCurrency(),
              fromDates.get(key),
              toDates.get(key)));
        }
      }
    }
    return result;
  }

  /**
   * Creates request DTOs for unfilled currency pairs with their date ranges.
   */
  public List<InstrumentHistoryquoteDTO> getUnfilledCurrencypairDTOs() {
    List<InstrumentHistoryquoteDTO> result = new ArrayList<>();
    for (Map.Entry<String, SecurityCurrencyMaxHistoryquoteData<S>> entry : instruments.entrySet()) {
      String key = entry.getKey();
      if (!filledKeys.contains(key)) {
        S sc = entry.getValue().getSecurityCurrency();
        if (sc instanceof Currencypair pair) {
          result.add(InstrumentHistoryquoteDTO.forCurrencypairRequest(
              pair.getFromCurrency(),
              pair.getToCurrency(),
              fromDates.get(key),
              toDates.get(key)));
        }
      }
    }
    return result;
  }

  /**
   * Returns unfilled instruments for fallback to connectors.
   */
  public List<SecurityCurrencyMaxHistoryquoteData<S>> getUnfilledInstruments() {
    List<SecurityCurrencyMaxHistoryquoteData<S>> result = new ArrayList<>();
    for (Map.Entry<String, SecurityCurrencyMaxHistoryquoteData<S>> entry : instruments.entrySet()) {
      if (!filledKeys.contains(entry.getKey())) {
        result.add(entry.getValue());
      }
    }
    return result;
  }

  /**
   * Returns filled instruments.
   */
  public List<SecurityCurrencyMaxHistoryquoteData<S>> getFilledInstruments() {
    List<SecurityCurrencyMaxHistoryquoteData<S>> result = new ArrayList<>();
    for (Map.Entry<String, SecurityCurrencyMaxHistoryquoteData<S>> entry : instruments.entrySet()) {
      if (filledKeys.contains(entry.getKey())) {
        result.add(entry.getValue());
      }
    }
    return result;
  }

  /**
   * Returns the "want to receive" map for push-back to interested suppliers.
   */
  public Map<GTNet, List<InstrumentHistoryquoteDTO>> getWantToReceiveMap() {
    return new HashMap<>(wantToReceiveBySupplier);
  }

  /**
   * Checks if all instruments have been filled.
   */
  public boolean allFilled() {
    return filledKeys.size() >= instruments.size();
  }

  /**
   * Checks if this set is empty.
   */
  public boolean isEmpty() {
    return instruments.isEmpty();
  }

  /**
   * Returns the total count of instruments in this set.
   */
  public int getTotalCount() {
    return instruments.size();
  }

  /**
   * Returns the count of unfilled instruments.
   */
  public int getUnfilledCount() {
    return instruments.size() - filledKeys.size();
  }

  /**
   * Returns the received historyquote DTO for a specific instrument.
   *
   * @param securitycurrency the security or currency pair
   * @return the received DTO with historyquote records, or null if not received
   */
  public InstrumentHistoryquoteDTO getReceivedData(S securitycurrency) {
    String key = buildKey(securitycurrency);
    return key != null ? receivedData.get(key) : null;
  }

  /**
   * Returns all received historyquote data keyed by instrument identifier.
   * Key format: "ISIN:Currency" for securities, "FromCurrency:ToCurrency" for pairs.
   */
  public Map<String, InstrumentHistoryquoteDTO> getAllReceivedData() {
    return new HashMap<>(receivedData);
  }
}
