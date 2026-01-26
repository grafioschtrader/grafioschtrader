package grafiosch.rest;

import grafiosch.entities.Globalparameters;
import grafiosch.entities.User;

public class RequestMappings {
  public static final String M2M = "_m2m";
  public static final String M2M_API = "/m2m/";
  
  public static final String API = "/api/";
  public static final String GLOBALPARAMETERS_MAP = API + Globalparameters.TABNAME;

  public static final String CONNECTOR_API_KEY = "connectorapikey";
  public static final String CONNECTOR_API_KEY_MAP = API + CONNECTOR_API_KEY;

  public static final String MAIL_SEMD_RECV = "mailsendrecv";
  public static final String MAIL_SEMD_RECV_MAP = API + MAIL_SEMD_RECV;
  public static final String MAIL_SETTING_FORWARD = "mailsettingforward";
  public static final String MAIL_SETTING_FORWARD_MAP = API + MAIL_SETTING_FORWARD;

  public static final String PROPOSEUSER_TASK = "proposeusertask";
  public static final String PROPOSEUSER_TASK_MAP = API + PROPOSEUSER_TASK;

  public static final String PROPOSECHANGEENTITY = "proposechangeentity";
  public static final String PROPOSECHANGEENTITY_MAP = API + PROPOSECHANGEENTITY;

  public static final String RELEASE_NOTE = "releasenote";
  public static final String RELEASE_NOTE_MAP = API + RELEASE_NOTE;
  
  public static final String TASK_DATA_CHANGE = "taskdatachange";
  public static final String TASK_DATA_CHANGE_MAP = API + TASK_DATA_CHANGE;

  public static final String UDFDATA = "udfdata";
  public static final String UDF_DATA_MAP = API + UDFDATA;

  public static final String UDFMETADATAGENERAL = "udfmetadatageneral";
  public static final String UDF_METADATA_GENERAL_MAP = API + UDFMETADATAGENERAL;
  public static final String UDFSPECIALTYPEDISABLEUSER = "udfspecialtypedisableuser";
  public static final String UDF_SEPCIAL_TYPE_DISABLE_USER_MAP = API + UDFSPECIALTYPEDISABLEUSER;

  public static final String USER_ENTITY_CHANGE_LIMIT = "userentitychangelimit";
  public static final String USER_ENTITY_CHANGE_LIMIT_MAP = API + USER_ENTITY_CHANGE_LIMIT;

  public static final String USER_MAP = API + User.TABNAME;

  // The is no table for useradmin
  public static final String USERADMIN = "useradmin";
  public static final String USERADMIN_MAP = API + USERADMIN;
  public static final String GTNET = "gtnet";
  public static final String GTNET_MAP = API + GTNET;
  public static final String GTNET_M2M = GTNET + M2M;
  public static final String GTNET_M2M_MAP = M2M_API + GTNET;

  public static final String GTNET_MESSAGE = "gtnetmessage";
  public static final String GTNET_MESSAGE_MAP = API + GTNET_MESSAGE;

  public static final String GTNET_MESSAGE_ANSWER = "gtnetmessageanswer";
  public static final String GTNET_MESSAGE_ANSWER_MAP = API + GTNET_MESSAGE_ANSWER;

  public static final String GTNETCONFIGENTITY = "gtnetconfigentity";
  public static final String GTNETCONFIGENTITY_MAP = API + GTNETCONFIGENTITY;
  public static final String ACTUATOR_MAP = API + "actuator";
 

}
