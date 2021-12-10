package grafioschtrader.exportdelete;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

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
import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.HistoryquotePeriod;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPlatform;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionPosFailed;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.MailInOut;
import grafioschtrader.entities.MultilanguageString;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.ProposeChangeEntity;
import grafioschtrader.entities.ProposeChangeField;
import grafioschtrader.entities.ProposeRequest;
import grafioschtrader.entities.ProposeUserTask;
import grafioschtrader.entities.Role;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityDerivedLink;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitycashaccount;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.TradingDaysMinus;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.entities.TradingPlatformPlan;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.User;
import grafioschtrader.entities.UserEntityChangeCount;
import grafioschtrader.entities.UserEntityChangeLimit;
import grafioschtrader.entities.Watchlist;

/**
 * Delete the user's data from this database and export his data for later
 * import into his personal database.</br>
 *
 * <ul>
 * <li>hold_* tables are not exported they should rebuild with import</li>
 * <li>mail* tables are not exported, they have a relation to others users</li>
 * <li>{@link grafioschtrader.entities.TaskDataChange} is not exported
 * </ul>
 *
 * @author Hugo Graf
 *
 */
public class MyDataExportDeleteDefinition {
  protected static final int EXPORT_USE = 0x01;
  protected static final int DELETE_USE = 0x02;
  protected static final int CHANGE_USER_ID = 0x04;

  private static final String SELECT_STR = "SELECT";
  private static final String DELETE_STR = "DELETE";

  private static String ALGO_RULE_PARAM_2_DEL = "ap FROM algo_rule_param2 ap JOIN algo_rule_strategy ars ON ap.id_algo_rule_strategy = ars.id_algo_rule_strategy WHERE ars.id_tenant = ?";

  private static String ALGO_RULE_DEL = "ar FROM algo_rule ar JOIN algo_rule_strategy ars ON ar.id_algo_rule_strategy = ars.id_algo_rule_strategy WHERE ars.id_tenant = ?";

  private static String ALGO_RULE_STRATEGY_PARAM_DEL = "ap FROM algo_rule_strategy_param ap JOIN algo_rule_strategy ars ON ap.id_algo_rule_strategy = ars.id_algo_rule_strategy WHERE ars.id_tenant = ?";

  private static String ALGO_STRATEGY_DEL = "a FROM algo_strategy a JOIN algo_rule_strategy ars ON a.id_algo_rule_strategy = ars.id_algo_rule_strategy WHERE ars.id_tenant = ?";

  private static String ALGO_ASSETCLASS_SECURITY_DEL = "a FROM algo_assetclass_security a JOIN algo_top_asset_security tas ON a.id_algo_assetclass_security = tas.id_algo_assetclass_security WHERE tas.id_tenant = ?";

  private static String ALGO_TOP_DEL = "a FROM algo_top a JOIN algo_top_asset_security tas ON a.id_algo_assetclass_security = tas.id_algo_assetclass_security WHERE tas.id_tenant = ?";

  private static String ALGO_SECURITY_DEL = "s FROM algo_security s JOIN algo_top_asset_security tas ON s.id_algo_assetclass_security = tas.id_algo_assetclass_security WHERE tas.id_tenant = ?";

  private static String ALGO_ASSETCLASS_DEL = "a FROM algo_assetclass a JOIN algo_top_asset_security tas ON a.id_algo_assetclass_security = tas.id_algo_assetclass_security WHERE tas.id_tenant = ?";

  private static String CASHACCOUNT_SELDEL = "c.* FROM cashaccount c, securitycashaccount sc WHERE sc.id_tenant = ? AND sc.id_securitycash_account = c.id_securitycash_account";

  private static String CORRELATION_INSTRUMENT_SELDEL = "ci.* FROM correlation_set cs, correlation_instrument ci WHERE cs.id_tenant = ? AND cs.id_correlation_set = ci.id_correlation_set";

  private static String DIVIDEND_SELECT = "DISTINCT d.* FROM dividend d JOIN watchlist_sec_cur ws ON ws.id_securitycurrency = d.id_securitycurrency JOIN watchlist w ON w.id_watchlist = ws.id_watchlist WHERE w.id_tenant = ? UNION SELECT d.* FROM dividend d JOIN security s ON d.id_securitycurrency = s.id_securitycurrency WHERE s.id_tenant_private = ? UNION SELECT DISTINCT d.* FROM transaction t JOIN dividend d ON t.id_securitycurrency = d.id_securitycurrency WHERE t.id_tenant = ?";

  private static String DIVIDEND_DELETE = "d.* FROM dividend d JOIN security s ON d.id_securitycurrency = s.id_securitycurrency WHERE s.id_tenant_private = ?";

  private static String SECURITYACCOUNT_SELDEL = "sa.* FROM securityaccount sa, securitycashaccount sc WHERE sc.id_tenant = ? AND sc.id_securitycash_account = sa.id_securitycash_account";

  private static String SECURITY_SELECT = "s.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN security s ON s.id_securitycurrency = ws.id_securitycurrency WHERE w.id_tenant = ? UNION SELECT s.* FROM security s WHERE s.id_tenant_private = 7 UNION SELECT DISTINCT s.* FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency WHERE t.id_tenant = ? UNION SELECT DISTINCT s1.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN security s ON s.id_securitycurrency = ws.id_securitycurrency JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN security s1 ON s1.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON s1.id_securitycurrency = sc.id_securitycurrency WHERE w.id_tenant = ? AND sc.dtype = 'S' UNION SELECT s1.* FROM security s JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN security s1 ON s1.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON s1.id_securitycurrency = sc.id_securitycurrency WHERE s.id_tenant_private = ? AND sc.dtype = 'S' UNION SELECT DISTINCT s1.* FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN security s1 ON s1.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON s1.id_securitycurrency = sc.id_securitycurrency WHERE t.id_tenant = ? AND sc.dtype = 'S' UNION SELECT s.* FROM correlation_set cs JOIN correlation_instrument ci ON cs.id_correlation_set = ci.id_correlation_set JOIN security s ON ci.id_securitycurrency = s.id_securitycurrency WHERE cs.id_tenant = 7";

  private static String SECURITY_DELETE = "FROM security WHERE id_tenant_private = ?";

  private static String SECURITY_DERIVED_LINK = "sdl.* FROM watchlist w JOIN watchlist_sec_cur wsc ON w.id_watchlist = wsc.id_watchlist JOIN security s ON wsc.id_securitycurrency = s.id_securitycurrency JOIN security_derived_link sdl ON sdl.id_securitycurrency = s.id_securitycurrency WHERE w.id_tenant = ? UNION SELECT sdl.* FROM security s JOIN securitycurrency sc ON s.id_securitycurrency = sc.id_securitycurrency JOIN security_derived_link sdl ON sdl.id_securitycurrency = s.id_securitycurrency WHERE s.id_tenant_private = ?";

  private static String SECURITYSPLIT_SELECT = "DISTINCT ss.* FROM securitysplit ss JOIN watchlist_sec_cur ws ON ws.id_securitycurrency = ss.id_securitycurrency JOIN watchlist w ON w.id_watchlist = ws.id_watchlist WHERE w.id_tenant = ? UNION SELECT ss.* FROM securitysplit ss JOIN security s ON ss.id_securitycurrency = s.id_securitycurrency WHERE s.id_tenant_private = ? UNION SELECT DISTINCT ss.* FROM transaction t JOIN securitysplit ss ON t.id_securitycurrency = ss.id_securitycurrency WHERE t.id_tenant = ?";

  private static String SECURITYSPLIT_DELETE = "ss.* FROM security s, securitysplit ss WHERE s.id_securitycurrency = ss.id_securitycurrency AND s.id_tenant_private = ?";

  private static String SECURITYCURRENCY_S_SELECT = "sc.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN securitycurrency sc ON sc.id_securitycurrency = ws.id_securitycurrency WHERE w.id_tenant = ? AND sc.dtype = 'S' UNION SELECT sc.* FROM security s JOIN securitycurrency sc ON s.id_securitycurrency = sc.id_securitycurrency WHERE s.id_tenant_private = ? UNION SELECT DISTINCT sc.* FROM transaction t JOIN securitycurrency sc ON t.id_securitycurrency = sc.id_securitycurrency WHERE t.id_tenant = ? UNION SELECT DISTINCT sc.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN security s ON s.id_securitycurrency = ws.id_securitycurrency JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN security s1 ON s1.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON s1.id_securitycurrency = sc.id_securitycurrency WHERE w.id_tenant = ? AND sc.dtype = 'S' UNION SELECT sc.* FROM security s JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN security s1 ON s1.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON s1.id_securitycurrency = sc.id_securitycurrency WHERE s.id_tenant_private = ? AND sc.dtype = 'S' UNION SELECT DISTINCT sc.* FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN security s1 ON s1.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON s1.id_securitycurrency = sc.id_securitycurrency WHERE t.id_tenant = ? AND sc.dtype = 'S' UNION SELECT sc.* FROM correlation_set cs JOIN correlation_instrument ci ON cs.id_correlation_set = ci.id_correlation_set JOIN securitycurrency sc ON ci.id_securitycurrency = sc.id_securitycurrency WHERE sc.dtype = 'S' AND cs.id_tenant = ?";

  private static String SECURITYCURRENCY_DELETE = "sc.* FROM securitycurrency sc, security s WHERE sc.id_securitycurrency = s.id_securitycurrency AND id_tenant_private = ?";

  private static String CURRENCYPAIR_SELECT = "c1.* FROM currencypair c1, securitycurrency sc, (SELECT DISTINCT s.currency as fromcurrency, e.currency as tocurrency FROM tenant e, portfolio p, securitycashaccount sc, transaction t, security s WHERE e.id_tenant = ? AND e.id_tenant = p.id_portfolio AND p.id_portfolio = sc.id_portfolio AND sc.id_securitycash_account = t.id_security_account AND t.id_securitycurrency = s.id_securitycurrency) c2 WHERE c1.from_currency = fromcurrency AND c1.to_currency = tocurrency AND c1.id_securitycurrency = sc.id_securitycurrency UNION SELECT DISTINCT c.* FROM tenant e, currencypair c, portfolio p, cashaccount a, securitycashaccount s, securitycurrency sc WHERE e.id_tenant = ? AND e.id_tenant = p.id_tenant AND c.from_currency = a.currency AND c.to_currency = e.currency AND p.id_portfolio = s.id_portfolio AND a.id_securitycash_account = s.id_securitycash_account AND c.id_securitycurrency = sc.id_securitycurrency UNION SELECT DISTINCT c.* FROM currencypair c, transaction t, portfolio p, securitycashaccount sc, cashaccount ca, securitycurrency s WHERE p.id_tenant = ? AND p.id_portfolio = sc.id_portfolio AND sc.id_securitycash_account = ca.id_securitycash_account AND t.id_cash_account = ca.id_securitycash_account AND t.id_currency_pair = c.id_securitycurrency AND c.id_securitycurrency = s.id_securitycurrency UNION SELECT DISTINCT c.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN security s ON s.id_securitycurrency = ws.id_securitycurrency JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN currencypair c ON c.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON c.id_securitycurrency = sc.id_securitycurrency WHERE w.id_tenant = ? AND sc.dtype = 'C' UNION SELECT c.* FROM security s JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN currencypair c ON c.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON c.id_securitycurrency = sc.id_securitycurrency WHERE s.id_tenant_private = ? AND sc.dtype = 'C' UNION SELECT DISTINCT c.* FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN currencypair c ON c.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON c.id_securitycurrency = sc.id_securitycurrency WHERE t.id_tenant = ? AND sc.dtype = 'C' UNION SELECT c.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN currencypair c ON c.id_securitycurrency = ws.id_securitycurrency WHERE w.id_tenant = ? UNION SELECT cp.* FROM correlation_set cs JOIN correlation_instrument ci ON cs.id_correlation_set = ci.id_correlation_set JOIN currencypair cp ON ci.id_securitycurrency = cp.id_securitycurrency WHERE cs.id_tenant = ?";

  private static String SECURITYCURRENCY_C_SELECT = "sc.* FROM currencypair c1, securitycurrency sc, (SELECT DISTINCT s.currency as fromcurrency, e.currency as tocurrency FROM tenant e, portfolio p, securitycashaccount sc, transaction t, security s WHERE e.id_tenant = ? AND e.id_tenant = p.id_portfolio AND p.id_portfolio = sc.id_portfolio AND sc.id_securitycash_account = t.id_security_account AND t.id_securitycurrency = s.id_securitycurrency) c2 WHERE c1.from_currency = fromcurrency AND c1.to_currency = tocurrency AND c1.id_securitycurrency = sc.id_securitycurrency UNION SELECT DISTINCT sc.* FROM tenant e, currencypair c, portfolio p, cashaccount a, securitycashaccount s, securitycurrency sc WHERE e.id_tenant = ? AND e.id_tenant = p.id_tenant AND c.from_currency = a.currency AND c.to_currency = e.currency AND p.id_portfolio = s.id_portfolio AND a.id_securitycash_account = s.id_securitycash_account AND c.id_securitycurrency = sc.id_securitycurrency UNION SELECT DISTINCT s.* FROM currencypair c, transaction t, portfolio p, securitycashaccount sc, cashaccount ca, securitycurrency s WHERE p.id_tenant = ? AND p.id_portfolio = sc.id_portfolio AND sc.id_securitycash_account = ca.id_securitycash_account AND t.id_cash_account = ca.id_securitycash_account AND t.id_currency_pair = c.id_securitycurrency AND c.id_securitycurrency = s.id_securitycurrency UNION SELECT DISTINCT sc.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN security s ON s.id_securitycurrency = ws.id_securitycurrency JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN currencypair c ON c.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON c.id_securitycurrency = sc.id_securitycurrency WHERE w.id_tenant = ? AND sc.dtype = 'C' UNION SELECT sc.* FROM security s JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN currencypair c ON c.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON c.id_securitycurrency = sc.id_securitycurrency WHERE s.id_tenant_private = ? AND sc.dtype = 'C' UNION SELECT DISTINCT sc.* FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency JOIN security_derived_link sdl ON s.id_securitycurrency = sdl.id_securitycurrency JOIN currencypair c ON c.id_securitycurrency = sdl.id_link_securitycurrency JOIN securitycurrency sc ON c.id_securitycurrency = sc.id_securitycurrency WHERE t.id_tenant = ? AND sc.dtype = 'C' UNION SELECT sc.* FROM watchlist w JOIN watchlist_sec_cur ws ON w.id_watchlist = ws.id_watchlist JOIN securitycurrency sc ON sc.id_securitycurrency = ws.id_securitycurrency WHERE sc.dtype = 'C' AND w.id_tenant = ? UNION SELECT sc.* FROM correlation_set cs JOIN correlation_instrument ci ON cs.id_correlation_set = ci.id_correlation_set JOIN securitycurrency sc ON ci.id_securitycurrency = sc.id_securitycurrency WHERE sc.dtype = 'C' AND cs.id_tenant = ?";

  private static String HISTORYQUOTE_SELECT = "DISTINCT h.* FROM transaction t JOIN security s ON t.id_securitycurrency = s.id_securitycurrency JOIN historyquote h ON s.id_securitycurrency = h.id_securitycurrency WHERE t.id_tenant = ? AND s.active_to_date < now() + interval 1 month UNION SELECT DISTINCT h.* FROM transaction t JOIN historyquote h ON h.id_securitycurrency = t.id_currency_pair WHERE t.id_tenant = ? ";

  private static String HISTORYQUOTEPERIOD_SELECT = "hp.* FROM historyquote_period hp JOIN security s ON hp.id_securitycurrency = s.id_securitycurrency WHERE s.id_tenant_private = ? UNION SELECT DISTINCT hp.* FROM watchlist w JOIN watchlist_sec_cur wsc ON w.id_watchlist = wsc.id_watchlist JOIN historyquote_period hp ON wsc.id_securitycurrency = hp.id_securitycurrency WHERE w.id_tenant = ? UNION SELECT DISTINCT hp.* FROM transaction t JOIN historyquote_period hp ON t.id_securitycurrency = hp.id_securitycurrency WHERE t.id_tenant = ?";

  private static String HISTORYQUOTEPERIOD_DELETE = "hp.* FROM historyquote_period hp JOIN security s ON hp.id_securitycurrency = s.id_securitycurrency WHERE s.id_tenant_private = ?";

  private static String WATCHLIST_SEC_CUR_SELDEL = "ws.* FROM watchlist w, watchlist_sec_cur ws WHERE w.id_tenant = ? AND w.id_watchlist = ws.id_watchlist";

  private static String IMPORT_TRANS_FAILED_SELDEL = "f.* FROM " + ImportTransactionPosFailed.TABNAME + " f INNER JOIN "
      + ImportTransactionPos.TABNAME + " p ON f.id_trans_pos = p.id_trans_pos WHERE p.id_tenant = ?";

  private static String PROPOSE_USER_TASK_SEL = "t.* FROM propose_user_task t JOIN propose_request r ON t.id_propose_request = r.id_propose_request WHERE r.created_by = ?";

  private static String PROPOSE_CHANGE_ENTITY_SEL = "e.* FROM propose_change_entity e JOIN propose_request r ON e.id_propose_request = r.id_propose_request WHERE r.created_by = ?";

  private static String PROPOSE_CHANGE_FIELD_SELDEL = "f.* FROM propose_change_field f INNER JOIN propose_request r ON f.id_propose_request = r.id_propose_request WHERE r.created_by = ?";

  private static String MAIL_IN_OUT_DELETE = "FROM mail_in_out WHERE id_user_to = ? OR id_user_from = ?";

  protected JdbcTemplate jdbcTemplate;
  private int exportOrDelete;
  protected User user;

  /*
   * The order should only be changed carefully. Otherwise, the foreign key
   * relationships can lead to errors.
   */
  protected ExportDefinition[] exportDefinitions = new ExportDefinition[] {
      // Export -> it runs with the first element
      // Delete it runs backwards
      new ExportDefinition("flyway_schema_history", TENANT_USER.NONE, null, EXPORT_USE),
      new ExportDefinition(Globalparameters.TABNAME, TENANT_USER.NONE, null, EXPORT_USE),
      new ExportDefinition(MultilanguageString.TABNAME, TENANT_USER.NONE, null, EXPORT_USE),
      new ExportDefinition(MultilanguageString.MULTILINGUESTRINGS, TENANT_USER.NONE, null, EXPORT_USE),
      new ExportDefinition(ImportTransactionPlatform.TABNAME, TENANT_USER.NONE, null, EXPORT_USE | CHANGE_USER_ID),
      new ExportDefinition(ImportTransactionTemplate.TABNAME, TENANT_USER.NONE, null, EXPORT_USE | CHANGE_USER_ID),
      new ExportDefinition(TradingPlatformPlan.TABNAME, TENANT_USER.NONE, null, EXPORT_USE | CHANGE_USER_ID),
      // Stock exchange is fully exported but data owner must be changed too user
      new ExportDefinition(Stockexchange.TABNAME, TENANT_USER.NONE, null, EXPORT_USE | CHANGE_USER_ID),
      // Asset classes is fully exported but data owner must be changed too user
      new ExportDefinition(Assetclass.TABNAME, TENANT_USER.NONE, null, EXPORT_USE | CHANGE_USER_ID),
      new ExportDefinition(Tenant.TABNAME, TENANT_USER.ID_TENANT, null, EXPORT_USE | DELETE_USE),
      new ExportDefinition(User.TABNAME, TENANT_USER.ID_TENANT, null, EXPORT_USE | DELETE_USE),
      new ExportDefinition(Role.TABNAME, TENANT_USER.NONE, null, EXPORT_USE),
      new ExportDefinition(User.TABNAME_USER_ROLE, TENANT_USER.ID_USER, null, DELETE_USE),
      new ExportDefinition(Portfolio.TABNAME, TENANT_USER.ID_TENANT, null, EXPORT_USE | DELETE_USE),
      new ExportDefinition(Securitycashaccount.TABNAME, TENANT_USER.ID_TENANT, null, EXPORT_USE | DELETE_USE),
      new ExportDefinition(Cashaccount.TABNAME, TENANT_USER.NONE, CASHACCOUNT_SELDEL, EXPORT_USE | DELETE_USE),
      new ExportDefinition(Securityaccount.TABNAME, TENANT_USER.ID_TENANT, SECURITYACCOUNT_SELDEL,
          EXPORT_USE | DELETE_USE),
      new ExportDefinition(Securitycurrency.TABNAME, TENANT_USER.ID_TENANT, SECURITYCURRENCY_C_SELECT, EXPORT_USE),
      new ExportDefinition(Securitycurrency.TABNAME, TENANT_USER.ID_TENANT, SECURITYCURRENCY_S_SELECT, EXPORT_USE),
      new ExportDefinition(Securitycurrency.TABNAME, TENANT_USER.ID_TENANT, SECURITYCURRENCY_DELETE,
          DELETE_USE | CHANGE_USER_ID),
      new ExportDefinition(Security.TABNAME, TENANT_USER.ID_TENANT, SECURITY_DELETE, DELETE_USE),
      new ExportDefinition(Security.TABNAME, TENANT_USER.ID_TENANT, SECURITY_SELECT, EXPORT_USE),
      new ExportDefinition(SecurityDerivedLink.TABNAME, TENANT_USER.ID_TENANT, SECURITY_DERIVED_LINK, EXPORT_USE),
      new ExportDefinition(Securitysplit.TABNAME, TENANT_USER.ID_TENANT, SECURITYSPLIT_SELECT, EXPORT_USE),
      new ExportDefinition(Securitysplit.TABNAME, TENANT_USER.ID_TENANT, SECURITYSPLIT_DELETE, DELETE_USE),
      new ExportDefinition(Dividend.TABNAME, TENANT_USER.ID_TENANT, DIVIDEND_SELECT, EXPORT_USE),
      new ExportDefinition(Dividend.TABNAME, TENANT_USER.ID_TENANT, DIVIDEND_DELETE, DELETE_USE),
      new ExportDefinition(HistoryquotePeriod.TABNAME, TENANT_USER.ID_TENANT, HISTORYQUOTEPERIOD_SELECT, EXPORT_USE),
      new ExportDefinition(HistoryquotePeriod.TABNAME, TENANT_USER.ID_TENANT, HISTORYQUOTEPERIOD_DELETE, DELETE_USE),
      new ExportDefinition(Currencypair.TABNAME, TENANT_USER.NONE, CURRENCYPAIR_SELECT, EXPORT_USE),
      new ExportDefinition(Historyquote.TABNAME, TENANT_USER.NONE, HISTORYQUOTE_SELECT, EXPORT_USE),
      new ExportDefinition(Watchlist.TABNAME, TENANT_USER.ID_TENANT, null, EXPORT_USE | DELETE_USE),
      new ExportDefinition(Watchlist.TABNAME_SEC_CUR, TENANT_USER.NONE, WATCHLIST_SEC_CUR_SELDEL,
          EXPORT_USE | DELETE_USE),
      new ExportDefinition(Transaction.TABNAME, TENANT_USER.ID_TENANT, null, EXPORT_USE | DELETE_USE),
      new ExportDefinition(ImportTransactionHead.TABNAME, TENANT_USER.ID_TENANT, null, EXPORT_USE | DELETE_USE),
      new ExportDefinition(ImportTransactionPos.TABNAME, TENANT_USER.ID_TENANT, null, EXPORT_USE | DELETE_USE),
      new ExportDefinition(ImportTransactionPosFailed.TABNAME, TENANT_USER.NONE, IMPORT_TRANS_FAILED_SELDEL,
          EXPORT_USE | DELETE_USE),
      new ExportDefinition(ProposeRequest.TABNAME, TENANT_USER.CREATED_BY, null, EXPORT_USE | DELETE_USE),
      new ExportDefinition(ProposeUserTask.TABNAME, TENANT_USER.CREATED_BY, PROPOSE_USER_TASK_SEL, EXPORT_USE),
      new ExportDefinition(ProposeChangeEntity.TABNAME, TENANT_USER.CREATED_BY, PROPOSE_CHANGE_ENTITY_SEL, EXPORT_USE),
      new ExportDefinition(ProposeChangeField.TABNAME, TENANT_USER.CREATED_BY, PROPOSE_CHANGE_FIELD_SELDEL,
          EXPORT_USE | DELETE_USE),
      new ExportDefinition(CorrelationSet.TABNAME, TENANT_USER.ID_TENANT, null, EXPORT_USE | DELETE_USE),
      new ExportDefinition(CorrelationSet.TABNAME_CORRELATION_INSTRUMENT, TENANT_USER.NONE,
          CORRELATION_INSTRUMENT_SELDEL, EXPORT_USE | DELETE_USE),
      // Delete all Change Limits for export user, nothing is exported
      new ExportDefinition(UserEntityChangeLimit.TABNAME, TENANT_USER.ID_USER, null, DELETE_USE),
      new ExportDefinition(UserEntityChangeCount.TABNAME, TENANT_USER.ID_USER, null, DELETE_USE),
      new ExportDefinition(TradingDaysPlus.TABNAME, TENANT_USER.NONE, null, EXPORT_USE),
      new ExportDefinition(TradingDaysMinus.TABNAME, TENANT_USER.NONE, null, EXPORT_USE),
      // Delete all Mails of the user, nothing is exported
      new ExportDefinition(MailInOut.TABNAME, TENANT_USER.NONE, MAIL_IN_OUT_DELETE, DELETE_USE),

      // TODO Missing Algo export ...
      new ExportDefinition(AlgoTopAssetSecurity.TABNAME, TENANT_USER.ID_TENANT, null, DELETE_USE),
      new ExportDefinition(AlgoTop.TABNAME, TENANT_USER.ID_TENANT, ALGO_TOP_DEL, DELETE_USE),
      new ExportDefinition(AlgoAssetclassSecurity.TABNAME, TENANT_USER.ID_TENANT, ALGO_ASSETCLASS_SECURITY_DEL,
          DELETE_USE),
      new ExportDefinition(AlgoAssetclass.TABNAME, TENANT_USER.ID_TENANT, ALGO_ASSETCLASS_DEL, DELETE_USE),
      new ExportDefinition(AlgoSecurity.TABNAME, TENANT_USER.ID_TENANT, ALGO_SECURITY_DEL, DELETE_USE),
      new ExportDefinition(AlgoRuleStrategy.TABNAME, TENANT_USER.ID_TENANT, null, DELETE_USE),
      new ExportDefinition(AlgoStrategy.TABNAME, TENANT_USER.ID_TENANT, ALGO_STRATEGY_DEL, DELETE_USE),
      new ExportDefinition(AlgoRule.TABNAME, TENANT_USER.ID_TENANT, ALGO_RULE_DEL, DELETE_USE),
      new ExportDefinition(AlgoRuleStrategy.ALGO_RULE_STRATEGY_PARAM, TENANT_USER.ID_TENANT,
          ALGO_RULE_STRATEGY_PARAM_DEL, DELETE_USE),
      new ExportDefinition(AlgoRule.ALGO_RULE_PARAM2, TENANT_USER.ID_TENANT, ALGO_RULE_PARAM_2_DEL, DELETE_USE)

  };

  public MyDataExportDeleteDefinition(JdbcTemplate jdbcTemplate, int exportOrDelete) {
    this.jdbcTemplate = jdbcTemplate;
    this.exportOrDelete = exportOrDelete;
    user = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails());
  }

  protected String getQuery(ExportDefinition exportDefinition) {
    String query = exportDefinition.sqlStatement;
    if (query == null) {
      switch (exportDefinition.tenantUser) {
      case ID_TENANT:
        query = String.format("FROM %s WHERE id_Tenant = %d", exportDefinition.table, user.getIdTenant());
        break;
      case ID_USER:
        query = String.format("FROM %s WHERE id_USER = %d", exportDefinition.table, user.getIdUser());
        break;
      case CREATED_BY:
        query = String.format("FROM %s WHERE created_by = %d", exportDefinition.table, user.getIdUser());
        break;
      default:
        query = String.format("FROM %s", exportDefinition.table);
      }

      if ((exportOrDelete & EXPORT_USE) == EXPORT_USE) {
        query = " * " + query;
      }

    }
    return ((exportOrDelete & EXPORT_USE) == EXPORT_USE ? SELECT_STR : DELETE_STR) + " " + query;
  }

  protected Object[] getParamArrayOfWhereForIdTenant(ExportDefinition exportDefinition, String query) {
    int countIdTenant = 0;
    Matcher matcher = Pattern.compile("=\\s*\\?(\\s|$)").matcher(query);
    while (matcher.find()) {
      countIdTenant++;
    }
    Object[] idTenatArray = new Integer[countIdTenant];
    Arrays.fill(idTenatArray,
        exportDefinition.tenantUser == TENANT_USER.ID_USER || exportDefinition.tenantUser == TENANT_USER.CREATED_BY
            ? user.getIdUser()
            : user.getIdTenant());
    return idTenatArray;
  }

  static class ExportDefinition {
    public String table;
    public TENANT_USER tenantUser;
    public String sqlStatement;
    public int usage;

    public ExportDefinition(String table, TENANT_USER tenantUser, String sqlStatement, int usage) {
      this.table = table;
      this.tenantUser = tenantUser;
      this.sqlStatement = sqlStatement;
      this.usage = usage;
    }

    public boolean isExport() {
      return (usage & EXPORT_USE) == EXPORT_USE;
    }

    public boolean isDelete() {
      return (usage & DELETE_USE) == DELETE_USE;
    }

    public boolean isChangeUserId() {
      return (usage & CHANGE_USER_ID) == CHANGE_USER_ID;
    }

  }

  static enum TENANT_USER {
    // Use special SQL statment
    NONE, 
    // Create statement with where clause which uses tenant id for selection
    ID_TENANT, 
    // Create statement with where clause which uses id_user for selection
    ID_USER, 
    // Used for shared entities which were created by this user
    CREATED_BY
  }

}
