package grafioschtrader.rest;

import grafiosch.entities.Globalparameters;
import grafiosch.rest.RequestMappings;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Dividend;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Stockexchange;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Watchlist;

public interface RequestGTMappings {
  public static final String M2M = "_m2m";
  public static final String M2M_API = "/m2m/";
  public static final String GT = "gt_";

  public static final String ACTUATOR_MAP = RequestMappings.API + "actuator";

  public static final String ASSETCLASS_MAP = RequestMappings.API + Assetclass.TABNAME;

  public static final String ALGOASSETCLASS = "algoassetclass";
  public static final String ALGOASSETCLASS_MAP = RequestMappings.API + ALGOASSETCLASS;

  public static final String ALGOSECURITY = "algosecurity";
  public static final String ALGOSECURITY_MAP = RequestMappings.API + ALGOSECURITY;

  public static final String ALGOTOP = "algotop";
  public static final String ALGOTOP_MAP = RequestMappings.API + ALGOTOP;

  public static final String ALGOSTRATEGY = "algostrategy";
  public static final String ALGOSTRATEGY_MAP = RequestMappings.API + ALGOSTRATEGY;

  public static final String CASHACCOUNT_MAP = RequestMappings.API + Cashaccount.TABNAME;

  public static final String CORRELATION_SET = "correlationset";
  public static final String CORRELATION_SET_MAP = RequestMappings.API + CORRELATION_SET;

  public static final String CURRENCYPAIR_MAP = RequestMappings.API + Currencypair.TABNAME;

  public static final String DIVIDEND_MAP = RequestMappings.API + Dividend.TABNAME;

  public static final String GLOBALPARAMETERS_GT_MAP = RequestMappings.API + GT + Globalparameters.TABNAME;
  
  
  public static final String GTNET = "gtnet";
  public static final String GTNET_MAP = RequestMappings.API + GTNET;

  public static final String GTNET_M2M = GTNET + M2M;
  public static final String GTNET_M2M_MAP = M2M_API + GTNET;


  public static final String GTNET_MESSAGE = "gtnetmessage";
  public static final String GTNET_MESSAGE_MAP = RequestMappings.API + GTNET_MESSAGE;

  public static final String GTNET_MESSAGE_ANSWER = "gtnetmessageanswer";
  public static final String GTNET_MESSAGE_ANSWER_MAP = RequestMappings.API + GTNET_MESSAGE_ANSWER;

  public static final String HISTORYQUOTE_MAP = RequestMappings.API + Historyquote.TABNAME;

  public static final String HISTORYQUOTE_PERIOD = "historyquoteperiod";
  public static final String HISTORYQUOTE_PERDIO_MAP = RequestMappings.API + HISTORYQUOTE_PERIOD;

  public static final String HOLDING = "holding";
  public static final String HOLDING_MAP = RequestMappings.API + HOLDING;

  public static final String IMPORTTRANSACTION_PLATFORM = "importtransactionplatform";
  public static final String IMPORTTRANSACTION_PLATFORM_MAP = RequestMappings.API + IMPORTTRANSACTION_PLATFORM;

  public static final String IMPORTTRANSACTIONHEAD = "importtransactionhead";
  public static final String IMPORTTRANSACTIONHEAD_MAP = RequestMappings.API + IMPORTTRANSACTIONHEAD;

  public static final String IMPORTTRANSACTIONPOS = "importtransactionpos";
  public static final String IMPORTTRANSACTIONPOS_MAP = RequestMappings.API + IMPORTTRANSACTIONPOS;

  public static final String IMPORTTRANSACTIONTEMPLATE = "importtransactiontemplate";
  public static final String IMPORTTRANSACTIONTEMPLATE_MAP = RequestMappings.API + IMPORTTRANSACTIONTEMPLATE;

  public static final String MULTIPLE_REQUEST_TO_ONE = "multiplerequesttoone";
  public static final String MULTIPLE_REQUEST_TO_ONE_MAP = RequestMappings.API + MULTIPLE_REQUEST_TO_ONE;

  public static final String PORTFOLIO_MAP = RequestMappings.API + Portfolio.TABNAME;

  public static final String SECURITYACCOUNT_MAP = RequestMappings.API + Securityaccount.TABNAME;

  public static final String STOCKEXCHANGE_MAP = RequestMappings.API + Stockexchange.TABNAME;

  public static final String SECURITY_MAP = RequestMappings.API + Security.TABNAME;
  public static final String SECURITY_M2M = Security.TABNAME + M2M;
  public static final String SECURITY_M2M_MAP = M2M_API + Security.TABNAME;

  public static final String SECURITYSPLIT_MAP = RequestMappings.API + Securitysplit.TABNAME;

  public static final String TRADINGDAYSMINUS = "tradingdaysminus";
  public static final String TRADINGDAYSMINUS_MAP = RequestMappings.API + TRADINGDAYSMINUS;

  public static final String TRADINGDAYSPLUS = "tradingdaysplus";
  public static final String TRADINGDAYSPLUS_MAP = RequestMappings.API + TRADINGDAYSPLUS;

  public static final String TRADINGPLATFORMPLAND = "tradingplatformplan";
  public static final String TRADINGPLATFORMPLAND_MAP = RequestMappings.API + TRADINGPLATFORMPLAND;

  public static final String TRANSACTION_MAP = RequestMappings.API + Transaction.TABNAME;

  public static final String WATCHLIST_MAP = RequestMappings.API + Watchlist.TABNAME;

  public static final String UDFMETADATASECURITY = "udfmetadatasecurity";
  public static final String UDF_METADATA_SECURITY_MAP = RequestMappings.API + UDFMETADATASECURITY;

  public static final String TENANT_MAP = RequestMappings.API + Tenant.TABNAME;

  // Used for path part
  public static final String SECURITY_DATAPROVIDER_INTRA_HISTORICAL_RESPONSE = "/dataproviderresponse/";
  
//Used for path part
 public static final String SECURITY_DATAPROVIDER_DIV_SPLIT_HISTORICAL_RESPONSE = "/dataproviderdivsplitresponse/";

}
