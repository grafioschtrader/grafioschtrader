package grafiosch.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meta-Annotation, @Transactional, @Modifying andg @Query combined.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented

@Transactional
@Modifying
@Query
public @interface UpdateQuery {

  @AliasFor(annotation = Query.class, attribute = "value")
  String value();

  @AliasFor(annotation = Query.class, attribute = "nativeQuery")
  boolean nativeQuery() default false;
}