package grafiosch.dynamic.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import grafiosch.BaseConstants;
import grafiosch.common.DynamicFormPropertySupport;
import grafiosch.dynamic.model.ClassDescriptorInputAndShow.DateRangeClass;
import grafiosch.dynamic.model.udf.UDFDataHelper;
import grafiosch.validation.DateRange;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Utility class that provides methods for analyzing Java model classes and generating
 * corresponding dynamic form metadata. This class uses reflection to examine class
 * fields and their annotations to create {@link FieldDescriptorInputAndShow} and
 * {@link ClassDescriptorInputAndShow} instances for dynamic form generation.
 * 
 * <p>The helper processes standard Jakarta validation annotations and custom annotations
 * to extract form configuration including field types, constraints, validation rules,
 * and UI hints.</p>
 * 
 * <h3>Supported Annotations:</h3>
 * <ul>
 * <li><strong>Field-level annotations:</strong>
 *   <ul>
 *     <li>{@code @NotNull} - Marks field as required</li>
 *     <li>{@code @Future} - Adds DATE_FUTURE property helper for date validation</li>
 *     <li>{@code @Min} - Sets minimum value constraint</li>
 *     <li>{@code @Max} - Sets maximum value constraint</li>
 *     <li>{@code @Size} - Sets minimum and maximum length/size constraints</li>
 *     <li>{@code @DynamicFormPropertySupport} - Adds custom form property helpers</li>
 *   </ul>
 * </li>
 * <li><strong>Class-level annotations:</strong>
 *   <ul>
 *     <li>{@code @DateRange} - Adds date range validation constraint</li>
 *   </ul>
 * </li>
 * </ul>
 */
public abstract class DynamicModelHelper {

  /**
   * Creates a complete form definition from a model class, including both field
   * descriptors and class-level constraints. This method analyzes all non-static
   * fields of the provided class and processes supported annotations.
   * 
   * <p>Class-level annotations such as {@code @DateRange} are processed and added
   * as constraint validators to the resulting descriptor.</p>
   * 
   * @param modelClass the Java class to analyze for form generation
   * @return a complete class descriptor containing field metadata and class constraints,
   *         or a descriptor with empty field list if modelClass is null
   */
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

  /**
   * Creates field descriptors for all non-static fields in the specified model class.
   * This is a convenience method that processes all fields without annotation filtering.
   * 
   * @param modelClass the Java class to analyze
   * @return a list of field descriptors for all eligible fields in the class
   * @see #getFormDefinitionOfModelClassMembers(Class, Set)
   */
  public static List<FieldDescriptorInputAndShow> getFormDefinitionOfModelClassMembers(Class<?> modelClass) {
    return getFormDefinitionOfModelClassMembers(modelClass, Collections.emptySet());
  }

  /**
   * Creates field descriptors for model class fields, with optional annotation filtering.
   * This method uses reflection to examine each non-static field and processes supported
   * annotations to build comprehensive field metadata for dynamic form generation.
   * 
   * <p>The method processes various validation and form annotations to set field properties:</p>
   * <ul>
   * <li>Field data types are automatically determined from Java types</li>
   * <li>Validation constraints are extracted from annotations</li>
   * <li>UI hints and behaviors are derived from property helper annotations</li>
   * <li>Special handling for numeric fields ensures appropriate maximum values</li>
   * </ul>
   * 
   * <p>For numeric fields without an explicit maximum constraint, a default maximum
   * value is calculated based on system-wide digit and fraction digit limits.</p>
   * 
   * @param modelClass the Java class to analyze for field definitions
   * @param possibleAnnotationSet a set of annotation types to filter by; if empty,
   *                             all fields are processed regardless of annotations
   * @return a list of field descriptors containing metadata for form generation,
   *         empty list if modelClass is null
   */ 
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
                fieldDescriptorInputAndShow.min = (double) ((Min) annotation).value();
              } else if (annotation instanceof Max) {
                fieldDescriptorInputAndShow.max = (double) ((Max) annotation).value();
              } else if (annotation instanceof DynamicFormPropertySupport) {
                fieldDescriptorInputAndShow.dynamicFormPropertyHelps = ((DynamicFormPropertySupport) annotation)
                    .value();
              } else if (annotation instanceof Size) {
                fieldDescriptorInputAndShow.min = (double) ((Size) annotation).min();
                fieldDescriptorInputAndShow.max = (double) ((Size) annotation).max();
              }
            }
            if (fieldDescriptorInputAndShow.dataType == DataType.Numeric && fieldDescriptorInputAndShow.max == null) {
              fieldDescriptorInputAndShow.max = UDFDataHelper.getMaxDecimalValue(BaseConstants.FID_MAX_DIGITS,
                  BaseConstants.FID_MAX_FRACTION_DIGITS);
            }

            fieldDescriptorInputAndShowList.add(fieldDescriptorInputAndShow);
          }
        }
      }
    }
    return fieldDescriptorInputAndShowList;
  }
}
