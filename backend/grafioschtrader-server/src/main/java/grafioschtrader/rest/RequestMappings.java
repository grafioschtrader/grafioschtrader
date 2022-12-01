package grafioschtrader.rest;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.User;
import grafioschtrader.entities.Watchlist;

public interface RequestMappings {
  public static final String API = "/api/";
  public static final String M2M = "_m2m";
  public static final String M2M_API = "/m2m/";

  public static final String ACTUATOR_MAP = API + "actuator";
  
  public static final String ASSETCLASS_MAP = API + Assetclass.TABNAME;

  public static final String ALGOASSETCLASS = "algoassetclass";
  public static final String ALGOASSETCLASS_MAP = API + ALGOASSETCLASS;

  public static final String ALGOSECURITY = "algosecurity";
  public static final String ALGOSECURITY_MAP = API + ALGOSECURITY;

  public static final String ALGOTOP = "algotop";
  public static final String ALGOTOP_MAP = API + ALGOTOP;

  public static final String ALGOSTRATEGY = "algostrategy";
  public static final String ALGOSTRATEGY_MAP = API + ALGOSTRATEGY;

  public static final String CASHACCOUNT_MAP = API + Cashaccount.TABNAME;

  public static final String CONNECTOR_API_KEY = "connectorapikey";
  public static final String CONNECTOR_API_KEY_MAP = API + CONNECTOR_API_KEY;
  
  public static final String CORRELATION_SET = "correlationset";
  public static final String CORRELATION_SET_MAP = API + CORRELATION_SET;

  public static final String CURRENCYPAIR_MAP = API + Currencypair.TABNAME;

  public static final String DIVIDEND_MAP = API + Dividend.TABNAME;

  public static final String GLOBALPARAMETERS_MAP = API + Globalparameters.TABNAME;

  public static final String GTNET = "gtnet";
  public static final String GTNET_MAP = API + GTNET;
  
  public static final String GTNET_M2M = GTNET + M2M;
  public static final String GTNET_M2M_MAP = M2M_API + GTNET;
 
  
  public static final String GTNET_MESSAGE = "gtnetmessage";
  public static final String GTNET_MESSAGE_MAP = API + GTNET_MESSAGE;
  
  public static final String GTNET_MESSAGE_ANSWER = "gtnetmessageanswer";
  public static final String GTNET_MESSAGE_ANSWER_MAP = API + GTNET_MESSAGE_ANSWER;
  
  public static final String HISTORYQUOTE_MAP = API + Historyquote.TABNAME;

  public static final String HISTORYQUOTE_PERIOD = "historyquoteperiod";
  public static final String HISTORYQUOTE_PERDIO_MAP = API + HISTORYQUOTE_PERIOD;

  public static final String HOLDING = "holding";
  public static final String HOLDING_MAP = API + HOLDING;

  public static final String IMPORTTRANSACTION_PLATFORM = "importtransactionplatform";
  public static final String IMPORTTRANSACTION_PLATFORM_MAP = API + IMPORTTRANSACTION_PLATFORM;

  public static final String IMPORTTRANSACTIONHEAD = "importtransactionhead";
  public static final String IMPORTTRANSACTIONHEAD_MAP = API + IMPORTTRANSACTIONHEAD;

  public static final String IMPORTTRANSACTIONPOS = "importtransactionpos";
  public static final String IMPORTTRANSACTIONPOS_MAP = API + IMPORTTRANSACTIONPOS;

  public static final String IMPORTTRANSACTIONTEMPLATE = "importtransactiontemplate";
  public static final String IMPORTTRANSACTIONTEMPLATE_MAP = API + IMPORTTRANSACTIONTEMPLATE;

  public static final String MAIL_INBOX = "mailinbox";
  public static final String MAIL_INBOX_MAP = API + MAIL_INBOX;

  public static final String MAIL_SENDBOX = "mailsendbox";
  public static final String MAIL_SENDBOX_MAP = API + MAIL_SENDBOX;

  public static final String MULTIPLE_REQUEST_TO_ONE = "multiplerequesttoone";
  public static final String MULTIPLE_REQUEST_TO_ONE_MAP = API + MULTIPLE_REQUEST_TO_ONE;

  public static final String PORTFOLIO_MAP = API + Portfolio.TABNAME;

  public static final String PROPOSECHANGEENTITY = "proposechangeentity";
  public static final String PROPOSECHANGEENTITY_MAP = API + PROPOSECHANGEENTITY;

  public static final String PROPOSEUSER_TASK = "proposeusertask";
  public static final String PROPOSEUSER_TASK_MAP = API + PROPOSEUSER_TASK;

  public static final String SECURITYACCOUNT_MAP = API + Securityaccount.TABNAME;

  public static final String STOCKEXCHANGE_MAP = API + Stockexchange.TABNAME;

  public static final String SECURITY_MAP = API + Security.TABNAME;
  public static final String SECURITY_M2M = Security.TABNAME + M2M;
  public static final String SECURITY_M2M_MAP = M2M_API + Security.TABNAME;

  public static final String SECURITYSPLIT_MAP = API + Securitysplit.TABNAME;

  public static final String TASK_DATA_CHANGE = "taskdatachange";
  public static final String TASK_DATA_CHANGE_MAP = API + TASK_DATA_CHANGE;

  public static final String TRADINGDAYSMINUS = "tradingdaysminus";
  public static final String TRADINGDAYSMINUS_MAP = API + TRADINGDAYSMINUS;

  public static final String TRADINGDAYSPLUS = "tradingdaysplus";
  public static final String TRADINGDAYSPLUS_MAP = API + TRADINGDAYSPLUS;

  public static final String TRADINGPLATFORMPLAND = "tradingplatformplan";
  public static final String TRADINGPLATFORMPLAND_MAP = API + TRADINGPLATFORMPLAND;

  public static final String TRANSACTION_MAP = API + Transaction.TABNAME;

  public static final String WATCHLIST_MAP = API + Watchlist.TABNAME;

  public static final String USER_MAP = API + User.TABNAME;

  // The is no table for useradmin
  public static final String USERADMIN = "useradmin";
  public static final String USERADMIN_MAP = API + USERADMIN;

  public static final String TENANT_MAP = API + Tenant.TABNAME;

  public static final String USER_ENTITY_CHANGE_LIMIT = "userentitychangelimit";
  public static final String USER_ENTITY_CHANGE_LIMIT_MAP = API + USER_ENTITY_CHANGE_LIMIT;

}
