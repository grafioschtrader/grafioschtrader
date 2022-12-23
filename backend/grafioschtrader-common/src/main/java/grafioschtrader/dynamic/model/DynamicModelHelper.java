package grafioschtrader.dynamic.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import grafioschtrader.common.DynamicFormPropertySupport;
import grafioschtrader.dynamic.model.ClassDescriptorInputAndShow.DateRangeClass;
import grafioschtrader.validation.DateRange;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public abstract class DynamicModelHelper {

  public static ClassDescriptorInputAndShow getFormDefinitionOfModelClass(Class<?> modelClass) {
    ClassDescriptorInputAndShow cdiss = new ClassDescriptorInputAndShow(
        getFormDefinitionOfModelClassMembers(modelClass));
    for (Annotation annotation : modelClass.getDeclaredAnnotations()) {
      if (annotation.annotationType() == DateRange.class) {
        cdiss.putConstraint(ConstraintValidatorType.DateRange,
            new DateRangeClass(((DateRange) annotation).start(), ((DateRange) annotation).end()));
      }
    }
    return cdiss;
  }

  public static List<FieldDescriptorInputAndShow> getFormDefinitionOfModelClassMembers(Class<?> modelClass) {
    return getFormDefinitionOfModelClassMembers(modelClass, Collections.emptySet());
  }

  public static List<FieldDescriptorInputAndShow> getFormDefinitionOfModelClassMembers(Class<?> modelClass,
      Set<Class<? extends Annotation>> possibleAnnotationSet) {
    List<FieldDescriptorInputAndShow> fieldDescriptorInputAndShowList = new ArrayList<>();
    if (modelClass != null) {
      for (Field field : modelClass.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers())) {
          Annotation[] annotations = field.getDeclaredAnnotations();
          if (possibleAnnotationSet.isEmpty()
              || Arrays.stream(annotations).anyMatch(a -> possibleAnnotationSet.contains(a.annotationType()))) {
            FieldDescriptorInputAndShow fieldDescriptorInputAndShow = new FieldDescriptorInputAndShow(field.getName(),
                field.getType());
            for (Annotation annotation : annotations) {
              if (annotation.annotationType() == NotNull.class || annotation.annotationType() == NotNull.List.class) {
                fieldDescriptorInputAndShow.required = true;
              } else if (annotation instanceof Future) {
                fieldDescriptorInputAndShow.dynamicFormPropertyHelps = new DynamicFormPropertyHelps[] {
                    DynamicFormPropertyHelps.DATE_FUTURE };
              } else if (annotation instanceof Min) {
                fieldDescriptorInputAndShow.min = ((Min) annotation).value();
              } else if (annotation instanceof Max) {
                fieldDescriptorInputAndShow.max = ((Max) annotation).value();
              } else if (annotation instanceof DynamicFormPropertySupport) {
                fieldDescriptorInputAndShow.dynamicFormPropertyHelps = ((DynamicFormPropertySupport) annotation)
                    .value();
              } else if (annotation instanceof Size) {
                fieldDescriptorInputAndShow.min = (long) ((Size) annotation).min();
                fieldDescriptorInputAndShow.max = (long) ((Size) annotation).max();
              }
            }
            fieldDescriptorInputAndShowList.add(fieldDescriptorInputAndShow);
          }
        }
      }
    }
    return fieldDescriptorInputAndShowList;
  }
}
