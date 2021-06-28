package grafioschtrader.entities;

import java.util.Date;

import javax.annotation.Generated;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Watchlist.class)
public abstract class Watchlist_ {

  public static volatile SingularAttribute<Watchlist, Integer> idTenant;
  public static volatile SingularAttribute<Watchlist, Integer> idWatchlist;
  public static volatile SingularAttribute<Watchlist, Date> lastTimestamp;
  public static volatile SingularAttribute<Watchlist, String> name;
  public static volatile ListAttribute<Watchlist, Securitycurrency<?>> securitycurrencyList;

  public static final String ID_TENANT = "idTenant";
  public static final String ID_WATCHLIST = "idWatchlist";
  public static final String LAST_TIMESTAMP = "lastTimestamp";
  public static final String NAME = "name";
  public static final String SECURITYCURRENCY_LIST = "securitycurrencyList";

}
