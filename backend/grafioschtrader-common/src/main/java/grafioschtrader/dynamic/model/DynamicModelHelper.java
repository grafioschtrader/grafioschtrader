package grafioschtrader.dynamic.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import grafioschtrader.common.DynamicFormPropertySupport;

public abstract class DynamicModelHelper {

  public static List<FieldDescriptorInputAndShow> getFormDefinitionOfModelClass(Class<?> modelClass) {

    List<FieldDescriptorInputAndShow> fieldDescriptorInputAndShowList = new ArrayList<>();
    if (modelClass != null) {
      for (Field field : modelClass.getDeclaredFields()) {
        Annotation[] annotations = field.getDeclaredAnnotations();

        FieldDescriptorInputAndShow fieldDescriptorInputAndShow = new FieldDescriptorInputAndShow(field.getName(),
            field.getType());
        for (Annotation annotation : annotations) {
          if (annotation instanceof NotNull) {
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
    return fieldDescriptorInputAndShowList;
  }
}
