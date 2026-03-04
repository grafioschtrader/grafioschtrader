package grafiosch.entities;

import java.time.LocalDateTime;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Auditable.class)
public abstract class Auditable_ {

  public static volatile SingularAttribute<Auditable, LocalDateTime> lastModifiedTime;
  public static volatile SingularAttribute<Auditable, LocalDateTime> creationTime;
  public static volatile SingularAttribute<Auditable, Integer> createdBy;
  public static volatile SingularAttribute<Auditable, Integer> lastModifiedBy;
  public static volatile SingularAttribute<Auditable, Integer> version;

}
