package grafioschtrader.gtnet.handler.impl.historyquote;

import java.util.List;
import java.util.Set;

import grafioschtrader.gtnet.m2m.model.InstrumentHistoryquoteDTO;

/**
 * Strategy interface for handling historyquote queries based on server accept mode.
 *
 * Different accept modes have fundamentally different behaviors:
 * <ul>
 *   <li><strong>AC_PUSH_OPEN</strong>: Queries GTNetHistoryquote (for foreign instruments) or
 *       local historyquote table (for local instruments), returns records in requested date range</li>
 *   <li><strong>AC_OPEN</strong>: Queries local Security/Currencypair historyquote data only</li>
 * </ul>
 */
public interface HistoryquoteQueryStrategy {

  /**
   * Queries securities and returns historical price records within requested date ranges.
   *
   * @param requested list of requested securities with their date ranges
   * @param sendableIds set of instrument IDs allowed to be sent (empty means all allowed)
   * @return list of instruments with their historical price records
   */
  List<InstrumentHistoryquoteDTO> querySecurities(List<InstrumentHistoryquoteDTO> requested, Set<Integer> sendableIds);

  /**
   * Queries currency pairs and returns historical price records within requested date ranges.
   *
   * @param requested list of requested currency pairs with their date ranges
   * @param sendableIds set of instrument IDs allowed to be sent (empty means all allowed)
   * @return list of instruments with their historical price records
   */
  List<InstrumentHistoryquoteDTO> queryCurrencypairs(List<InstrumentHistoryquoteDTO> requested, Set<Integer> sendableIds);
}
