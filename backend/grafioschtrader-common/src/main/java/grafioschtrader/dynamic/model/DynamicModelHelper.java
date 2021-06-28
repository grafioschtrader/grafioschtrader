package grafioschtrader.dynamic.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import grafioschtrader.common.DynamicFormPropertySupport;

public abstract class DynamicModelHelper {

  public static List<FieldDescriptorInputAndShow> getFormDefinitionOfModelClass(Class<?> modelClass) {
    return getFormDefinitionOfModelClass(modelClass, Collections.emptySet());
  }

  public static List<FieldDescriptorInputAndShow> getFormDefinitionOfModelClass(Class<?> modelClass,
      Set<Class<? extends Annotation>> possibleAnnotationSet) {

    List<FieldDescriptorInputAndShow> fieldDescriptorInputAndShowList = new ArrayList<>();
    if (modelClass != null) {
      for (Field field : modelClass.getDeclaredFields()) {
        Annotation[] annotations = field.getDeclaredAnnotations();
        if (possibleAnnotationSet.isEmpty()
            || Arrays.stream(annotations).anyMatch(a -> possibleAnnotationSet.contains(a.annotationType()))) {
          FieldDescriptorInputAndShow fieldDescriptorInputAndShow = new FieldDescriptorInputAndShow(field.getName(),
              field.getType());
          for (Annotation annotation : annotations) {
            if (annotation.annotationType() == NotNull.class || annotation.annotationType() == NotNull.List.class) {
              fieldDescriptorInputAndShow.required = true;
            } else if (annotation instanceof Min) {
              fieldDescriptorInputAndShow.min = ((Min) annotation).value();
            } else if (annotation instanceof Max) {
              fieldDescriptorInputAndShow.max = ((Max) annotation).value();
            } else if (annotation instanceof DynamicFormPropertySupport) {
              fieldDescriptorInputAndShow.dynamicFormPropertyHelps = ((DynamicFormPropertySupport) annotation).value();
            } else if (annotation instanceof Size) {
              fieldDescriptorInputAndShow.min = (long) ((Size) annotation).min();
              fieldDescriptorInputAndShow.max = (long) ((Size) annotation).max();
            }
          }
          fieldDescriptorInputAndShowList.add(fieldDescriptorInputAndShow);
        }
      }
    }
    return fieldDescriptorInputAndShowList;
  }
}
