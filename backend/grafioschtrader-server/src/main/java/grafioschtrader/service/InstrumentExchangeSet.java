package grafioschtrader.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;

/**
 * Tracks instruments during GTNet price exchange, maintaining state of which have been filled with prices.
 *
 * This helper class manages the intersection of watchlist instruments with GTNet-enabled instruments
 * and tracks which ones have received price updates from remote servers. It supports:
 * <ul>
 *   <li>Building the initial set from securities and currency pairs</li>
 *   <li>Creating request DTOs with current timestamps</li>
 *   <li>Marking instruments as filled when prices are received</li>
 *   <li>Returning unfilled instruments for fallback to connectors</li>
 * </ul>
 */
public class InstrumentExchangeSet {

  private final Map<String, Security> securities = new HashMap<>();
  private final Map<String, Currencypair> currencypairs = new HashMap<>();
  private final Set<String> filledKeys = new HashSet<>();

  /**
   * Adds a security to the exchange set.
   *
   * @param security the security to add
   */
  public void addSecurity(Security security) {
    if (security.getIsin() != null) {
      String key = buildSecurityKey(security.getIsin(), security.getCurrency());
      securities.put(key, security);
    }
  }

  /**
   * Adds a currency pair to the exchange set.
   *
   * @param currencypair the currency pair to add
   */
  public void addCurrencypair(Currencypair currencypair) {
    String key = buildCurrencypairKey(currencypair.getFromCurrency(), currencypair.getToCurrency());
    currencypairs.put(key, currencypair);
  }

  /**
   * Marks an instrument as filled with price data.
   *
   * @param key the instrument key (ISIN:currency or from:to)
   */
  public void markAsFilled(String key) {
    filledKeys.add(key);
  }

  /**
   * Processes a response from a remote server, updating local entities and marking as filled.
   *
   * @param responseSecurities list of security price DTOs from the response
   * @param responseCurrencypairs list of currency pair price DTOs from the response
   * @return the number of entities that were successfully updated with newer data
   */
  public int processResponse(List<InstrumentPriceDTO> responseSecurities,
      List<InstrumentPriceDTO> responseCurrencypairs) {
    int updatedCount = 0;
    if (responseSecurities != null) {
      for (InstrumentPriceDTO dto : responseSecurities) {
        String key = dto.getKey();
        Security security = securities.get(key);
        if (security != null && isNewer(dto.getTimestamp(), security.getSTimestamp())) {
          updateSecurityFromDTO(security, dto);
          markAsFilled(key);
          updatedCount++;
        }
      }
    }
    if (responseCurrencypairs != null) {
      for (InstrumentPriceDTO dto : responseCurrencypairs) {
        String key = dto.getKey();
        Currencypair currencypair = currencypairs.get(key);
        if (currencypair != null && isNewer(dto.getTimestamp(), currencypair.getSTimestamp())) {
          updateCurrencypairFromDTO(currencypair, dto);
          markAsFilled(key);
          updatedCount++;
        }
      }
    }
    return updatedCount;
  }

  /**
   * Creates request DTOs for unfilled securities with current timestamps.
   *
   * @return list of InstrumentPriceDTO for securities that haven't been filled
   */
  public List<InstrumentPriceDTO> getUnfilledSecurityDTOs() {
    List<InstrumentPriceDTO> result = new ArrayList<>();
    for (Map.Entry<String, Security> entry : securities.entrySet()) {
      if (!filledKeys.contains(entry.getKey())) {
        result.add(InstrumentPriceDTO.fromSecurity(entry.getValue()));
      }
    }
    return result;
  }

  /**
   * Creates request DTOs for unfilled currency pairs with current timestamps.
   *
   * @return list of InstrumentPriceDTO for currency pairs that haven't been filled
   */
  public List<InstrumentPriceDTO> getUnfilledCurrencypairDTOs() {
    List<InstrumentPriceDTO> result = new ArrayList<>();
    for (Map.Entry<String, Currencypair> entry : currencypairs.entrySet()) {
      if (!filledKeys.contains(entry.getKey())) {
        result.add(InstrumentPriceDTO.fromCurrencypair(entry.getValue()));
      }
    }
    return result;
  }

  /**
   * Returns securities that haven't been filled (for fallback to connectors).
   *
   * @return list of unfilled Security entities
   */
  public List<Security> getUnfilledSecurities() {
    List<Security> result = new ArrayList<>();
    for (Map.Entry<String, Security> entry : securities.entrySet()) {
      if (!filledKeys.contains(entry.getKey())) {
        result.add(entry.getValue());
      }
    }
    return result;
  }

  /**
   * Returns currency pairs that haven't been filled (for fallback to connectors).
   *
   * @return list of unfilled Currencypair entities
   */
  public List<Currencypair> getUnfilledCurrencypairs() {
    List<Currencypair> result = new ArrayList<>();
    for (Map.Entry<String, Currencypair> entry : currencypairs.entrySet()) {
      if (!filledKeys.contains(entry.getKey())) {
        result.add(entry.getValue());
      }
    }
    return result;
  }

  /**
   * Returns all securities (both filled and unfilled).
   *
   * @return list of all Security entities in this set
   */
  public List<Security> getAllSecurities() {
    return new ArrayList<>(securities.values());
  }

  /**
   * Returns all currency pairs (both filled and unfilled).
   *
   * @return list of all Currencypair entities in this set
   */
  public List<Currencypair> getAllCurrencypairs() {
    return new ArrayList<>(currencypairs.values());
  }

  /**
   * Checks if all instruments have been filled.
   *
   * @return true if no unfilled instruments remain
   */
  public boolean allFilled() {
    return filledKeys.size() >= (securities.size() + currencypairs.size());
  }

  /**
   * Returns the count of unfilled instruments.
   *
   * @return number of instruments still needing prices
   */
  public int getUnfilledCount() {
    return (securities.size() + currencypairs.size()) - filledKeys.size();
  }

  /**
   * Returns the total count of instruments in this set.
   *
   * @return total number of securities + currency pairs
   */
  public int getTotalCount() {
    return securities.size() + currencypairs.size();
  }

  /**
   * Checks if this set is empty.
   *
   * @return true if no instruments have been added
   */
  public boolean isEmpty() {
    return securities.isEmpty() && currencypairs.isEmpty();
  }

  // Helper methods

  private static String buildSecurityKey(String isin, String currency) {
    return isin + ":" + currency;
  }

  private static String buildCurrencypairKey(String fromCurrency, String toCurrency) {
    return fromCurrency + ":" + toCurrency;
  }

  private static boolean isNewer(Date incoming, Date existing) {
    if (existing == null) {
      return incoming != null;
    }
    if (incoming == null) {
      return false;
    }
    return incoming.after(existing);
  }

  private void updateSecurityFromDTO(Security security, InstrumentPriceDTO dto) {
    security.setSTimestamp(dto.getTimestamp());
    if (dto.getOpen() != null) {
      security.setSOpen(dto.getOpen());
    }
    if (dto.getHigh() != null) {
      security.setSHigh(dto.getHigh());
    }
    if (dto.getLow() != null) {
      security.setSLow(dto.getLow());
    }
    if (dto.getLast() != null) {
      security.setSLast(dto.getLast());
    }
    if (dto.getVolume() != null) {
      security.setSVolume(dto.getVolume());
    }
  }

  private void updateCurrencypairFromDTO(Currencypair currencypair, InstrumentPriceDTO dto) {
    currencypair.setSTimestamp(dto.getTimestamp());
    if (dto.getOpen() != null) {
      currencypair.setSOpen(dto.getOpen());
    }
    if (dto.getHigh() != null) {
      currencypair.setSHigh(dto.getHigh());
    }
    if (dto.getLow() != null) {
      currencypair.setSLow(dto.getLow());
    }
    if (dto.getLast() != null) {
      currencypair.setSLast(dto.getLast());
    }
    // Note: Currencypair entities don't have volume field
  }
}
