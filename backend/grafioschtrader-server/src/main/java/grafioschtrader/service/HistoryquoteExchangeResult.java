package grafioschtrader.service;

import java.util.List;
import java.util.Map;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;
import grafioschtrader.priceupdate.historyquote.SecurityCurrencyMaxHistoryquoteData;

/**
 * Container for GTNet historyquote exchange results.
 *
 * This class holds the results of querying GTNet servers for historical quotes, separating
 * instruments into those that were successfully filled and those that need fallback to connectors.
 * It also carries "want to receive" markers for pushing connector-fetched data back to suppliers.
 *
 * @param <S> Security or Currencypair
 */
public class HistoryquoteExchangeResult<S extends Securitycurrency<S>> {

  private final List<SecurityCurrencyMaxHistoryquoteData<S>> remainingForConnector;
  private final List<SecurityCurrencyMaxHistoryquoteData<S>> filledByGTNet;
  private final Map<GTNet, List<InstrumentHistoryquoteDTO>> wantToReceiveMap;
  private final Map<String, InstrumentHistoryquoteDTO> receivedHistoryquotes;

  /**
   * Creates a new result container.
   *
   * @param remainingForConnector instruments that were not filled by GTNet and need connector fallback
   * @param filledByGTNet instruments that were successfully filled by GTNet
   * @param wantToReceiveMap map of suppliers that want data pushed back, with their requested instruments
   * @param receivedHistoryquotes map of received historyquote data keyed by instrument identifier
   */
  public HistoryquoteExchangeResult(
      List<SecurityCurrencyMaxHistoryquoteData<S>> remainingForConnector,
      List<SecurityCurrencyMaxHistoryquoteData<S>> filledByGTNet,
      Map<GTNet, List<InstrumentHistoryquoteDTO>> wantToReceiveMap,
      Map<String, InstrumentHistoryquoteDTO> receivedHistoryquotes) {
    this.remainingForConnector = remainingForConnector;
    this.filledByGTNet = filledByGTNet;
    this.wantToReceiveMap = wantToReceiveMap;
    this.receivedHistoryquotes = receivedHistoryquotes;
  }

  /**
   * Returns instruments that need connector fallback.
   * These are instruments that were not filled by any GTNet server.
   */
  public List<SecurityCurrencyMaxHistoryquoteData<S>> getRemainingForConnector() {
    return remainingForConnector;
  }

  /**
   * Returns instruments that were successfully filled by GTNet.
   */
  public List<SecurityCurrencyMaxHistoryquoteData<S>> getFilledByGTNet() {
    return filledByGTNet;
  }

  /**
   * Returns the map of suppliers that expressed interest in receiving data.
   * Key is the GTNet supplier, value is the list of instruments they want data for.
   */
  public Map<GTNet, List<InstrumentHistoryquoteDTO>> getWantToReceiveMap() {
    return wantToReceiveMap;
  }

  /**
   * Checks if there are any instruments remaining for connector fallback.
   */
  public boolean hasRemainingForConnector() {
    return remainingForConnector != null && !remainingForConnector.isEmpty();
  }

  /**
   * Checks if any suppliers want to receive data.
   */
  public boolean hasWantToReceive() {
    return wantToReceiveMap != null && !wantToReceiveMap.isEmpty();
  }

  /**
   * Returns the received historyquote data keyed by instrument identifier.
   * Key format: "ISIN:Currency" for securities, "FromCurrency:ToCurrency" for pairs.
   */
  public Map<String, InstrumentHistoryquoteDTO> getReceivedHistoryquotes() {
    return receivedHistoryquotes;
  }

  /**
   * Gets the received historyquote DTO for a specific instrument.
   *
   * @param securitycurrency the security or currency pair
   * @return the received DTO with historyquote records, or null if not received
   */
  public InstrumentHistoryquoteDTO getReceivedDataFor(S securitycurrency) {
    String key = buildKey(securitycurrency);
    return key != null ? receivedHistoryquotes.get(key) : null;
  }

  private String buildKey(S securitycurrency) {
    if (securitycurrency instanceof Security security) {
      return security.getIsin() != null ? security.getIsin() + ":" + security.getCurrency() : null;
    } else if (securitycurrency instanceof Currencypair pair) {
      return pair.getFromCurrency() + ":" + pair.getToCurrency();
    }
    return null;
  }

  /**
   * Creates an empty result indicating GTNet is disabled or no instruments to process.
   * The original list is passed through as remaining for connector.
   */
  public static <S extends Securitycurrency<S>> HistoryquoteExchangeResult<S> passthrough(
      List<SecurityCurrencyMaxHistoryquoteData<S>> allInstruments) {
    return new HistoryquoteExchangeResult<>(allInstruments, List.of(), Map.of(), Map.of());
  }
}
