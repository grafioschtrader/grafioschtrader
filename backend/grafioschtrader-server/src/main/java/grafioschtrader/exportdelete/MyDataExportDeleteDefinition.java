package grafioschtrader.exportdelete;

import grafiosch.entities.UDFMetadata;
import grafiosch.exportdelete.ExportDefinition;
import grafiosch.exportdelete.ExportDefinition.TENANT_USER;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoAssetclassSecurity;
import grafioschtrader.entities.AlgoRule;
import grafioschtrader.entities.AlgoRuleStrategy;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.AlgoTopAssetSecurity;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.HistoryquotePeriod;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionPosFailed;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityDerivedLink;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitycashaccount;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.TradingDaysMinus;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.entities.Watchlist;

/**
 * Delete the user's data from this database and export his data for later import into his personal database.<br>
 *
 * <ul>
 * <li>hold_* tables are not exported they should rebuild with import</li>
 * <li>mail* tables are not exported, they have a relation to others users</li>
 * <li>{@link grafiosch.entities.TaskDataChange} is not exported
 * </ul>
 *
 *
 */
public class MyDataExportDeleteDefinition {
  private static String ALGO_RULE_PARAM_2_DEL = String.format(
      "ap FROM %s ap JOIN %s ars ON ap.id_algo_rule_strategy = ars.id_algo_rule_strategy WHERE ars.id_tenant = ?",
      AlgoRule.ALGO_RULE_PARAM2, AlgoRuleStrategy.TABNAME);
  private static String ALGO_RULE_DEL = String.format(
      "ar FROM %s ar JOIN %s ars ON ar.id_algo_rule_strategy = ars.id_algo_rule_strategy WHERE ars.id_tenant = ?",
      AlgoRuleStrategy.TABNAME, AlgoRuleStrategy.TABNAME);
  private static String ALGO_RULE_STRATEGY_PARAM_DEL = String.format(
      "ap FROM %s ap JOIN %s ars ON ap.id_algo_rule_strategy = ars.id_algo_rule_strategy WHERE ars.id_tenant = ?",
      AlgoRuleStrategy.ALGO_RULE_STRATEGY_PARAM, AlgoRuleStrategy.TABNAME);
  private static String ALGO_STRATEGY_DEL = String.format(
      "a FROM %s a JOIN %s ars ON a.id_algo_rule_strategy = ars.id_algo_rule_strategy WHERE ars.id_tenant = ?",
      AlgoStrategy.TABNAME, AlgoRuleStrategy.TABNAME);
  private static String ALGO_ASSETCLASS_SECURITY_DEL = String.format(
      "a FROM %s a JOIN %s tas ON a.id_algo_assetclass_security = tas.id_algo_assetclass_security WHERE tas.id_tenant = ?",
      AlgoAssetclassSecurity.TABNAME, AlgoTopAssetSecurity.TABNAME);
  private static String ALGO_TOP_DEL = String.format(
      "a FROM %s a JOIN %s tas ON a.id_algo_assetclass_security = tas.id_algo_assetclass_security WHERE tas.id_tenant = ?",
      AlgoTop.TABNAME, AlgoTopAssetSecurity.TABNAME);
  private static String ALGO_SECURITY_DEL = String.format(
      "s FROM %s s JOIN %s tas ON s.id_algo_assetclass_security = tas.id_algo_assetclass_security WHERE tas.id_tenant = ?",
      AlgoSecurity.TABNAME, AlgoTopAssetSecurity.TABNAME);
  private static String ALGO_ASSETCLASS_DEL = String.format(
      "a FROM %s a JOIN %s tas ON a.id_algo_assetclass_security = tas.id_algo_assetclass_security WHERE tas.id_tenant = ?",
      AlgoAssetclass.TABNAME, AlgoTopAssetSecurity.TABNAME);
  private static String CASHACCOUNT_SELDEL = String.format(
      "c.* FROM %s c, %s sc WHERE sc.id_tenant = ? AND sc.id_securitycash_account = c.id_securitycash_account",
      Cashaccount.TABNAME, Securitycashaccount.TABNAME);
  private static String CORRELATION_INSTRUMENT_SELDEL = String.format(
      "ci.* FROM %s cs, %s ci WHERE cs.id_tenant = ? AND cs.id_correlation_set = ci.id_correlation_set",
      CorrelationSet.TABNAME, CorrelationSet.TABNAME_CORRELATION_INSTRUMENT);
  private static String DIVIDEND_SELECT = String.format(
      """
          DISTINCT d.* FROM %s d JOIN %s ws ON ws.id_securitycurrency = d.id_securitycurrency JOIN watchlist w ON w.id_watchlist = ws.id_watchlist WHERE w.id_tenant = ?
          UNION SELECT d.* FROM dividend d JOIN security s ON d.id_securitycurrency = s.id_securitycurrency WHERE s.id_tenant_private = ?
          UNION SELECT DISTINCT d.* FROM transaction t JOIN dividend d ON t.id_securitycurrency = d.id_securitycurrency WHERE t.id_tenant = ?""",
      Dividend.TABNAME, Watchlist.TABNAME_SEC_CUR);
  private static String DIVIDEND_DELETE = String.format(
      "d.* FROM %s d JOIN %s s ON d.id_securitycurrency = s.id_securitycurrency WHERE s.id_tenant_private = ?",
      Dividend.TABNAME, Security.TABNAME);
  private static String SECURITYACCOUNT_SELDEL = String.format(
      "sa.* FROM %s sa, %s sc WHERE sc.id_tenant = ? AND sc.id_securitycash_account = sa.id_securitycash_account",
      Securityaccount.TABNAME, Securitycashaccount.TABNAME);
  private static String SECURITY_SELECT = """
      s.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN security s ON s.id_securitycurrency = ws.id_securitycurrency
      WHERE w.id_tenant = ? UNION SELECT s.* FROM security s WHERE s.id_tenant_private = 7 UNION SELECT DISTINCT s.* FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency
      WHERE t.id_tenant = ? UNION SELECT DISTINCT s1.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN security s ON s.id_securitycurrency = ws.id_securitycurrency
      JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN security s1 ON s1.id_securitycurrency = sdl.id_link_securitycurrency
      JOIN securitycurrency sc ON s1.id_securitycurrency = sc.id_securitycurrency WHERE w.id_tenant = ? AND sc.dtype = 'S' UNION SELECT s1.* FROM security s
      JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN security s1 ON s1.id_securitycurrency = sdl.id_link_securitycurrency
      JOIN securitycurrency sc ON s1.id_securitycurrency = sc.id_securitycurrency WHERE s.id_tenant_private = ? AND sc.dtype = 'S'
      UNION SELECT DISTINCT s1.* FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency
      JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN security s1 ON s1.id_securitycurrency = sdl.id_link_securitycurrency
      JOIN securitycurrency sc ON s1.id_securitycurrency = sc.id_securitycurrency WHERE t.id_tenant = ? AND sc.dtype = 'S' UNION SELECT s.* FROM correlation_set cs
      JOIN correlation_instrument ci ON cs.id_correlation_set = ci.id_correlation_set JOIN security s ON ci.id_securitycurrency = s.id_securitycurrency WHERE cs.id_tenant = ?""";
  private static String SECURITY_DELETE = String.format("FROM %s WHERE id_tenant_private = ?", Security.TABNAME);
  private static String SECURITY_DERIVED_LINK = String.format(
      """
          sdl.* FROM %s w JOIN %s wsc ON w.id_watchlist = wsc.id_watchlist JOIN %s s ON wsc.id_securitycurrency = s.id_securitycurrency
          JOIN security_derived_link sdl ON sdl.id_securitycurrency = s.id_securitycurrency WHERE w.id_tenant = ?
          UNION SELECT sdl.* FROM security s JOIN securitycurrency sc ON s.id_securitycurrency = sc.id_securitycurrency
          JOIN security_derived_link sdl ON sdl.id_securitycurrency = s.id_securitycurrency WHERE s.id_tenant_private = ?""",
      Watchlist.TABNAME, Watchlist.TABNAME_SEC_CUR, Security.TABNAME);
  private static String SECURITYSPLIT_SELECT = String.format(
      """
          DISTINCT ss.* FROM %s ss JOIN watchlist_sec_cur ws ON ws.id_securitycurrency = ss.id_securitycurrency JOIN %s w ON w.id_watchlist = ws.id_watchlist
          WHERE w.id_tenant = ? UNION SELECT ss.* FROM %s ss JOIN security s ON ss.id_securitycurrency = s.id_securitycurrency WHERE s.id_tenant_private = ?
          UNION SELECT DISTINCT ss.* FROM %s t JOIN %s ss ON t.id_securitycurrency = ss.id_securitycurrency WHERE t.id_tenant = ?""",
      Securitysplit.TABNAME, Watchlist.TABNAME, Securitysplit.TABNAME, Transaction.TABNAME, Securitysplit.TABNAME);
  private static String SECURITYSPLIT_DELETE = String.format(
      "ss.* FROM %s s, %s ss WHERE s.id_securitycurrency = ss.id_securitycurrency AND s.id_tenant_private = ?",
      Security.TABNAME, Securitysplit.TABNAME);
  private static String SECURITYCURRENCY_S_SELECT = """
      sc.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN securitycurrency sc ON sc.id_securitycurrency = ws.id_securitycurrency
      WHERE w.id_tenant = ? AND sc.dtype = 'S' UNION SELECT sc.* FROM security s JOIN securitycurrency sc ON s.id_securitycurrency = sc.id_securitycurrency WHERE s.id_tenant_private = ?
      UNION SELECT DISTINCT sc.* FROM transaction t JOIN securitycurrency sc ON t.id_securitycurrency = sc.id_securitycurrency WHERE t.id_tenant = ?
      UNION SELECT DISTINCT sc.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN security s
      ON s.id_securitycurrency = ws.id_securitycurrency JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency
      JOIN security s1 ON s1.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON s1.id_securitycurrency = sc.id_securitycurrency
      WHERE w.id_tenant = ? AND sc.dtype = 'S' UNION SELECT sc.* FROM security s JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency
      JOIN security s1 ON s1.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON s1.id_securitycurrency = sc.id_securitycurrency
      WHERE s.id_tenant_private = ? AND sc.dtype = 'S' UNION SELECT DISTINCT sc.* FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency
      JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN security s1 ON s1.id_securitycurrency = sdl.id_link_securitycurrency
      JOIN securitycurrency sc ON s1.id_securitycurrency = sc.id_securitycurrency WHERE t.id_tenant = ? AND sc.dtype = 'S' UNION SELECT sc.* FROM correlation_set cs
      JOIN correlation_instrument ci ON cs.id_correlation_set = ci.id_correlation_set JOIN securitycurrency sc ON ci.id_securitycurrency = sc.id_securitycurrency WHERE sc.dtype = 'S' AND cs.id_tenant = ?""";
  private static String SECURITYCURRENCY_DELETE = String.format(
      "sc.* FROM %s sc, %s s WHERE sc.id_securitycurrency = s.id_securitycurrency AND id_tenant_private = ?",
      Securitycurrency.TABNAME, Security.TABNAME);
  private static String CURRENCYPAIR_SELECT = """
      c1.* FROM currencypair c1, securitycurrency sc, (SELECT DISTINCT s.currency as fromcurrency, e.currency as tocurrency
      FROM tenant e, portfolio p, securitycashaccount sc, transaction t, security s WHERE e.id_tenant = ? AND e.id_tenant = p.id_portfolio
      AND p.id_portfolio = sc.id_portfolio AND sc.id_securitycash_account = t.id_security_account AND t.id_securitycurrency = s.id_securitycurrency) c2
      WHERE c1.from_currency = fromcurrency AND c1.to_currency = tocurrency AND c1.id_securitycurrency = sc.id_securitycurrency
      UNION SELECT DISTINCT c.* FROM tenant e, currencypair c, portfolio p, cashaccount a, securitycashaccount s, securitycurrency sc WHERE e.id_tenant = ?
      AND e.id_tenant = p.id_tenant AND c.from_currency = a.currency AND c.to_currency = e.currency AND p.id_portfolio = s.id_portfolio
      AND a.id_securitycash_account = s.id_securitycash_account AND c.id_securitycurrency = sc.id_securitycurrency UNION SELECT DISTINCT c.*
      FROM currencypair c, transaction t, portfolio p, securitycashaccount sc, cashaccount ca, securitycurrency s WHERE p.id_tenant = ?
      AND p.id_portfolio = sc.id_portfolio AND sc.id_securitycash_account = ca.id_securitycash_account AND t.id_cash_account = ca.id_securitycash_account
      AND t.id_currency_pair = c.id_securitycurrency AND c.id_securitycurrency = s.id_securitycurrency UNION SELECT DISTINCT c.*
      FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN security s ON s.id_securitycurrency = ws.id_securitycurrency
      JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN currencypair c ON c.id_securitycurrency = sdl.id_link_securitycurrency
      JOIN securitycurrency sc ON c.id_securitycurrency = sc.id_securitycurrency WHERE w.id_tenant = ? AND sc.dtype = 'C'
      UNION SELECT c.* FROM security s JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency
      JOIN currencypair c ON c.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON c.id_securitycurrency = sc.id_securitycurrency
      WHERE s.id_tenant_private = ? AND sc.dtype = 'C' UNION SELECT DISTINCT c.* FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency
      JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN currencypair c ON c.id_securitycurrency = sdl.id_link_securitycurrency
      JOIN securitycurrency sc ON c.id_securitycurrency = sc.id_securitycurrency WHERE t.id_tenant = ? AND sc.dtype = 'C' UNION SELECT c.* FROM watchlist w
      JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN currencypair c ON c.id_securitycurrency = ws.id_securitycurrency WHERE w.id_tenant = ?
      UNION SELECT cp.* FROM correlation_set cs JOIN correlation_instrument ci ON cs.id_correlation_set = ci.id_correlation_set JOIN currencypair cp
      ON ci.id_securitycurrency = cp.id_securitycurrency WHERE cs.id_tenant = ?""";
  private static String SECURITYCURRENCY_C_SELECT = """
      sc.* FROM currencypair c1, securitycurrency sc, (SELECT DISTINCT s.currency as fromcurrency, e.currency as tocurrency
      FROM tenant e, portfolio p, securitycashaccount sc, transaction t, security s WHERE e.id_tenant = ? AND e.id_tenant = p.id_portfolio
      AND p.id_portfolio = sc.id_portfolio AND sc.id_securitycash_account = t.id_security_account AND t.id_securitycurrency = s.id_securitycurrency) c2
      WHERE c1.from_currency = fromcurrency AND c1.to_currency = tocurrency AND c1.id_securitycurrency = sc.id_securitycurrency
      UNION SELECT DISTINCT sc.* FROM tenant e, currencypair c, portfolio p, cashaccount a, securitycashaccount s, securitycurrency sc WHERE e.id_tenant = ?
      AND e.id_tenant = p.id_tenant AND c.from_currency = a.currency AND c.to_currency = e.currency AND p.id_portfolio = s.id_portfolio
      AND a.id_securitycash_account = s.id_securitycash_account AND c.id_securitycurrency = sc.id_securitycurrency
      UNION SELECT DISTINCT s.* FROM currencypair c, transaction t, portfolio p, securitycashaccount sc, cashaccount ca, securitycurrency s
      WHERE p.id_tenant = ? AND p.id_portfolio = sc.id_portfolio AND sc.id_securitycash_account = ca.id_securitycash_account
      AND t.id_cash_account = ca.id_securitycash_account AND t.id_currency_pair = c.id_securitycurrency AND c.id_securitycurrency = s.id_securitycurrency
      UNION SELECT DISTINCT sc.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN security s
      ON s.id_securitycurrency = ws.id_securitycurrency JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency
      JOIN currencypair c ON c.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON c.id_securitycurrency = sc.id_securitycurrency WHERE w.id_tenant = ? AND sc.dtype = 'C'
      UNION SELECT sc.* FROM security s JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency
      JOIN currencypair c ON c.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON c.id_securitycurrency = sc.id_securitycurrency
      WHERE s.id_tenant_private = ? AND sc.dtype = 'C' UNION SELECT DISTINCT sc.* FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency
      JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN currencypair c ON c.id_securitycurrency = sdl.id_link_securitycurrency
      JOIN securitycurrency sc ON c.id_securitycurrency = sc.id_securitycurrency WHERE t.id_tenant = ? AND sc.dtype = 'C' UNION SELECT sc.* FROM watchlist w
      JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN securitycurrency sc ON sc.id_securitycurrency = ws.id_securitycurrency
      WHERE sc.dtype = 'C' AND w.id_tenant = ? UNION SELECT sc.* FROM correlation_set cs JOIN correlation_instrument ci ON cs.id_correlation_set = ci.id_correlation_set
      JOIN securitycurrency sc ON ci.id_securitycurrency = sc.id_securitycurrency WHERE sc.dtype = 'C' AND cs.id_tenant = ?""";
  private static String HISTORYQUOTE_SELECT = """
      DISTINCT h.* FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency JOIN historyquote h ON s.id_securitycurrency = h.id_securitycurrency
      WHERE t.id_tenant = ? AND s.active_to_date < now() + interval 1 month
      UNION SELECT DISTINCT h.* FROM transaction t JOIN historyquote h ON h.id_securitycurrency = t.id_currency_pair WHERE t.id_tenant = ? """;
  private static String HISTORYQUOTEPERIOD_SELECT = """
      hp.* FROM historyquote_period hp JOIN security s ON hp.id_securitycurrency = s.id_securitycurrency WHERE s.id_tenant_private = ?
      UNION SELECT DISTINCT hp.* FROM watchlist w JOIN watchlist_sec_cur wsc ON w.id_watchlist = wsc.id_watchlist JOIN historyquote_period hp ON wsc.id_securitycurrency = hp.id_securitycurrency
      WHERE w.id_tenant = ? UNION SELECT DISTINCT hp.* FROM transaction t JOIN historyquote_period hp ON t.id_securitycurrency = hp.id_securitycurrency WHERE t.id_tenant = ?""";
  private static String HISTORYQUOTEPERIOD_DELETE = String.format(
      "hp.* FROM %s hp JOIN %s s ON hp.id_securitycurrency = s.id_securitycurrency WHERE s.id_tenant_private = ?",
      HistoryquotePeriod.TABNAME, Security.TABNAME);
  private static String WATCHLIST_SEC_CUR_SELDEL = String.format(
      "ws.* FROM %s w, %s ws WHERE w.id_tenant = ? AND w.id_watchlist = ws.id_watchlist", Watchlist.TABNAME,
      Watchlist.TABNAME_SEC_CUR);
  private static String IMPORT_TRANS_FAILED_SELDEL = String.format(
      "f.* FROM %s f INNER JOIN %s p ON f.id_trans_pos = p.id_trans_pos WHERE p.id_tenant = ?",
      ImportTransactionPosFailed.TABNAME, ImportTransactionPos.TABNAME);
  private static String UDF_METADATA_SECUIRTY_SELDEL = String.format(
      " ums.* FROM %s ums JOIN %s m ON ums.id_udf_metadata = m.id_udf_metadata WHERE m.id_user = ?",
      UDFMetadataSecurity.TABNAME, UDFMetadata.TABNAME);

  /*
   * The order should only be changed carefully. Otherwise, the foreign key relationships can lead to errors.
   */
  public static ExportDefinition[] exportDefinitions = new ExportDefinition[] {
      // Export -> it runs with the first element
      // Delete it runs backwards
      new ExportDefinition(ImportTransactionPlatform.TABNAME, TENANT_USER.NONE, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.CHANGE_USER_ID_FOR_CREATED_BY),
      new ExportDefinition(ImportTransactionTemplate.TABNAME, TENANT_USER.NONE, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.CHANGE_USER_ID_FOR_CREATED_BY),
      new ExportDefinition(TradingPlatformPlan.TABNAME, TENANT_USER.NONE, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.CHANGE_USER_ID_FOR_CREATED_BY),
      // Stock exchange is fully exported but data owner must be changed too user
      new ExportDefinition(Stockexchange.TABNAME, TENANT_USER.NONE, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.CHANGE_USER_ID_FOR_CREATED_BY),
      // Asset classes is fully exported but data owner must be changed too user
      new ExportDefinition(Assetclass.TABNAME, TENANT_USER.NONE, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.CHANGE_USER_ID_FOR_CREATED_BY),

      new ExportDefinition(Portfolio.TABNAME, TENANT_USER.ID_TENANT, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(Securitycashaccount.TABNAME, TENANT_USER.ID_TENANT, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(Cashaccount.TABNAME, TENANT_USER.NONE, CASHACCOUNT_SELDEL,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(Securityaccount.TABNAME, TENANT_USER.ID_TENANT, SECURITYACCOUNT_SELDEL,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(Securitycurrency.TABNAME, TENANT_USER.ID_TENANT, SECURITYCURRENCY_C_SELECT,
          ExportDefinition.EXPORT_USE),
      new ExportDefinition(Securitycurrency.TABNAME, TENANT_USER.ID_TENANT, SECURITYCURRENCY_S_SELECT,
          ExportDefinition.EXPORT_USE),
      new ExportDefinition(Securitycurrency.TABNAME, TENANT_USER.ID_TENANT, SECURITYCURRENCY_DELETE,
          ExportDefinition.DELETE_USE | ExportDefinition.CHANGE_USER_ID_FOR_CREATED_BY),
      new ExportDefinition(Security.TABNAME, TENANT_USER.ID_TENANT, SECURITY_DELETE, ExportDefinition.DELETE_USE),
      new ExportDefinition(Security.TABNAME, TENANT_USER.ID_TENANT, SECURITY_SELECT, ExportDefinition.EXPORT_USE),
      new ExportDefinition(SecurityDerivedLink.TABNAME, TENANT_USER.ID_TENANT, SECURITY_DERIVED_LINK,
          ExportDefinition.EXPORT_USE),
      new ExportDefinition(Securitysplit.TABNAME, TENANT_USER.ID_TENANT, SECURITYSPLIT_SELECT,
          ExportDefinition.EXPORT_USE),
      new ExportDefinition(Securitysplit.TABNAME, TENANT_USER.ID_TENANT, SECURITYSPLIT_DELETE,
          ExportDefinition.DELETE_USE),
      new ExportDefinition(Dividend.TABNAME, TENANT_USER.ID_TENANT, DIVIDEND_SELECT, ExportDefinition.EXPORT_USE),
      new ExportDefinition(Dividend.TABNAME, TENANT_USER.ID_TENANT, DIVIDEND_DELETE, ExportDefinition.DELETE_USE),
      new ExportDefinition(HistoryquotePeriod.TABNAME, TENANT_USER.ID_TENANT, HISTORYQUOTEPERIOD_SELECT,
          ExportDefinition.EXPORT_USE),
      new ExportDefinition(HistoryquotePeriod.TABNAME, TENANT_USER.ID_TENANT, HISTORYQUOTEPERIOD_DELETE,
          ExportDefinition.DELETE_USE),
      new ExportDefinition(Currencypair.TABNAME, TENANT_USER.NONE, CURRENCYPAIR_SELECT, ExportDefinition.EXPORT_USE),
      new ExportDefinition(Historyquote.TABNAME, TENANT_USER.NONE, HISTORYQUOTE_SELECT, ExportDefinition.EXPORT_USE),
      new ExportDefinition(Watchlist.TABNAME, TENANT_USER.ID_TENANT, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(Watchlist.TABNAME_SEC_CUR, TENANT_USER.NONE, WATCHLIST_SEC_CUR_SELDEL,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(Transaction.TABNAME, TENANT_USER.ID_TENANT, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(ImportTransactionHead.TABNAME, TENANT_USER.ID_TENANT, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(ImportTransactionPos.TABNAME, TENANT_USER.ID_TENANT, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(ImportTransactionPosFailed.TABNAME, TENANT_USER.NONE, IMPORT_TRANS_FAILED_SELDEL,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),

      new ExportDefinition(CorrelationSet.TABNAME, TENANT_USER.ID_TENANT, null,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      new ExportDefinition(CorrelationSet.TABNAME_CORRELATION_INSTRUMENT, TENANT_USER.NONE,
          CORRELATION_INSTRUMENT_SELDEL, ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),
      // Delete all Change Limits for export user, nothing is exported
      new ExportDefinition(TradingDaysPlus.TABNAME, TENANT_USER.NONE, null, ExportDefinition.EXPORT_USE),
      new ExportDefinition(TradingDaysMinus.TABNAME, TENANT_USER.NONE, null, ExportDefinition.EXPORT_USE),
      // User defined fields for security
      new ExportDefinition(UDFMetadataSecurity.TABNAME, TENANT_USER.ID_USER, UDF_METADATA_SECUIRTY_SELDEL,
          ExportDefinition.EXPORT_USE | ExportDefinition.DELETE_USE),

      // TODO Missing Algo export ...
      new ExportDefinition(AlgoTopAssetSecurity.TABNAME, TENANT_USER.ID_TENANT, null, ExportDefinition.DELETE_USE),
      new ExportDefinition(AlgoTop.TABNAME, TENANT_USER.ID_TENANT, ALGO_TOP_DEL, ExportDefinition.DELETE_USE),
      new ExportDefinition(AlgoAssetclassSecurity.TABNAME, TENANT_USER.ID_TENANT, ALGO_ASSETCLASS_SECURITY_DEL,
          ExportDefinition.DELETE_USE),
      new ExportDefinition(AlgoAssetclass.TABNAME, TENANT_USER.ID_TENANT, ALGO_ASSETCLASS_DEL,
          ExportDefinition.DELETE_USE),
      new ExportDefinition(AlgoSecurity.TABNAME, TENANT_USER.ID_TENANT, ALGO_SECURITY_DEL, ExportDefinition.DELETE_USE),
      new ExportDefinition(AlgoRuleStrategy.TABNAME, TENANT_USER.ID_TENANT, null, ExportDefinition.DELETE_USE),
      new ExportDefinition(AlgoStrategy.TABNAME, TENANT_USER.ID_TENANT, ALGO_STRATEGY_DEL, ExportDefinition.DELETE_USE),
      new ExportDefinition(AlgoRule.TABNAME, TENANT_USER.ID_TENANT, ALGO_RULE_DEL, ExportDefinition.DELETE_USE),
      new ExportDefinition(AlgoRuleStrategy.ALGO_RULE_STRATEGY_PARAM, TENANT_USER.ID_TENANT,
          ALGO_RULE_STRATEGY_PARAM_DEL, ExportDefinition.DELETE_USE),
      new ExportDefinition(AlgoRule.ALGO_RULE_PARAM2, TENANT_USER.ID_TENANT, ALGO_RULE_PARAM_2_DEL,
          ExportDefinition.DELETE_USE)

  };

}
