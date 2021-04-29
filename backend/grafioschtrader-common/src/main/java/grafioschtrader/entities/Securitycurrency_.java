package grafioschtrader.entities;

import java.util.Date;

import javax.annotation.Generated;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Securitycurrency.class)
@SuppressWarnings("rawtypes")
public abstract class Securitycurrency_ {

  public static volatile SingularAttribute<Securitycurrency, String> idConnectorIntra;
  public static volatile SingularAttribute<Securitycurrency, String> note;
  public static volatile SingularAttribute<Securitycurrency, Short> retryIntraLoad;
  public static volatile SingularAttribute<Securitycurrency, Double> sHigh;
  public static volatile SingularAttribute<Securitycurrency, Short> retryHistoryLoad;
  public static volatile SingularAttribute<Securitycurrency, Double> sOpen;
  public static volatile SingularAttribute<Securitycurrency, Double> sPrevClose;
  public static volatile SingularAttribute<Securitycurrency, Integer> idSecuritycurrency;
  public static volatile ListAttribute<Securitycurrency, Historyquote> historyquoteList;
  public static volatile SingularAttribute<Securitycurrency, String> idConnectorHistory;
  public static volatile SingularAttribute<Securitycurrency, Double> sLast;
  public static volatile SingularAttribute<Securitycurrency, Double> sChangePercentage;
  public static volatile SingularAttribute<Securitycurrency, Double> sLow;
  public static volatile SingularAttribute<Securitycurrency, String> stockexchangeLink;
  public static volatile SingularAttribute<Securitycurrency, Date> sTimestamp;
  public static volatile SingularAttribute<Securitycurrency, Date> fullLoadTimestamp;
  public static volatile SingularAttribute<Security, String> urlHistoryExtend;
  public static volatile SingularAttribute<Security, String> urlIntraExtend;
  // public static volatile SingularAttribute<Securitycurrency, Integer>
  // createUserId;

}
