package grafioschtrader.gtnet.handler.impl.lastprice;

import java.util.List;
import java.util.Set;

import grafioschtrader.gtnet.m2m.model.InstrumentPriceDTO;

/**
 * Strategy interface for handling lastprice queries based on server accept mode.
 *
 * Different accept modes have fundamentally different behaviors:
 * <ul>
 *   <li><strong>AC_PUSH_OPEN</strong>: Queries GTNetLastprice* tables (shared pool), returns "final price" for
 *       instruments not found if request has non-zero last price</li>
 *   <li><strong>AC_OPEN</strong>: Queries local Security/Currencypair entities, updates own instruments
 *       if incoming prices are newer</li>
 * </ul>
 */
public interface LastpriceQueryStrategy {

  /**
   * Queries securities and returns prices that are newer than requested.
   *
   * @param requested list of requested securities with their current timestamps
   * @param sendableIds set of instrument IDs allowed to be sent (empty means all allowed)
   * @return list of prices to return to the requester
   */
  List<InstrumentPriceDTO> querySecurities(List<InstrumentPriceDTO> requested, Set<Integer> sendableIds);

  /**
   * Queries currency pairs and returns prices that are newer than requested.
   *
   * @param requested list of requested currency pairs with their current timestamps
   * @param sendableIds set of instrument IDs allowed to be sent (empty means all allowed)
   * @return list of prices to return to the requester
   */
  List<InstrumentPriceDTO> queryCurrencypairs(List<InstrumentPriceDTO> requested, Set<Integer> sendableIds);
}
