package grafioschtrader.entities;

import java.time.LocalDate;

import javax.annotation.Generated;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(CorrelationSet.class)
public abstract class CorrelationSet_ {

  public static volatile SingularAttribute<CorrelationSet, Integer> idTenant;
  public static volatile SingularAttribute<CorrelationSet, String> note;
  public static volatile SingularAttribute<CorrelationSet, Byte> rolling;
  public static volatile SingularAttribute<CorrelationSet, String> name;
  public static volatile SingularAttribute<CorrelationSet, Byte> samplingPeriod;
  public static volatile SingularAttribute<CorrelationSet, Integer> idCorrelationSet;
  public static volatile SingularAttribute<CorrelationSet, LocalDate> fromDate;
  public static volatile SingularAttribute<CorrelationSet, LocalDate> toDate;
  public static volatile ListAttribute<CorrelationSet, Securitycurrency<?>> securitycurrencyList;

  public static final String ID_TENANT = "idTenant";
  public static final String NOTE = "note";
  public static final String ROLLING = "rolling";
  public static final String NAME = "name";
  public static final String SAMPLING_PERIOD = "samplingPeriod";
  public static final String ID_CORRELATION_SET = "idCorrelationSet";
  public static final String DATE_FROM = "dateFrom";
  public static final String DATE_TO = "dateTo";
  public static final String SECURITYCURRENCY_LIST = "securitycurrencyList";

}
