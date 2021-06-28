package grafioschtrader.entities;

import java.util.Date;

import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Auditable.class)
public abstract class Auditable_ {

  public static volatile SingularAttribute<Auditable, Date> lastModifiedTime;
  public static volatile SingularAttribute<Auditable, Date> creationTime;
  public static volatile SingularAttribute<Auditable, Integer> createdBy;
  public static volatile SingularAttribute<Auditable, Integer> lastModifiedBy;
  public static volatile SingularAttribute<Auditable, Integer> version;

}
