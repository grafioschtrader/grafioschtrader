package grafiosch.dynamic.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import grafiosch.BaseConstants;
import grafiosch.common.DynamicFormField;
import grafiosch.common.DynamicFormPropertySupport;
import grafiosch.dynamic.model.ClassDescriptorInputAndShow.DateRangeClass;
import grafiosch.dynamic.model.udf.UDFDataHelper;
import grafiosch.validation.AfterEqual;
import grafiosch.validation.DateRange;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
            FieldDescriptorInputAndShow fieldDescriptorInputAndShow = createFieldDescriptor(field);
            applyFieldConstraintAnnotations(annotations, fieldDescriptorInputAndShow);
            applyNumericDefaultMax(fieldDescriptorInputAndShow);
            fieldDescriptorInputAndShowList.add(fieldDescriptorInputAndShow);
          }
        }
      }
    }
    return fieldDescriptorInputAndShowList;
  }

  /**
   * Creates a complete form definition for an entity that uses {@code @DynamicFormField} to mark its
   * input fields. Unlike {@link #getFormDefinitionOfModelClass(Class)} this walks the full class
   * hierarchy (so inherited form fields are included), keeps only the fields that belong to the
   * requested dialog, and orders them by the position encoded in {@code @DynamicFormField.uiOrder()}.
   *
   * <p>Class-level annotations such as {@code @DateRange} are processed and added as constraint
   * validators, identical to {@link #getFormDefinitionOfModelClass(Class)}.</p>
   *
   * @param entityClass the entity to analyse
   * @param dialogId    the dialog id whose fields should be returned (entities with a single form
   *                    use dialog 1)
   * @return a class descriptor whose field list contains the ordered
   *         {@link FieldDescriptorInputAndShowExtendedEntity} instances for the requested dialog
   */
  public static ClassDescriptorInputAndShow getFormDefinitionOfEntityClass(Class<?> entityClass, int dialogId) {
    ClassDescriptorInputAndShow cdiss = new ClassDescriptorInputAndShow(getEntityFormFields(entityClass, dialogId));
    for (Annotation annotation : entityClass.getDeclaredAnnotations()) {
      if (annotation.annotationType() == DateRange.class) {
        cdiss.putConstraint(ConstraintValidatorType.DateRange,
            new DateRangeClass(((DateRange) annotation).start(), ((DateRange) annotation).end()));
      }
    }
    return cdiss;
  }

  /**
   * Collects, constrains and orders the {@code @DynamicFormField} annotated fields of an entity
   * (including inherited ones) for a single dialog.
   *
   * @param entityClass the entity to analyse
   * @param dialogId    the dialog id to filter by
   * @return the ordered list of entity field descriptors
   */
  private static List<FieldDescriptorInputAndShow> getEntityFormFields(Class<?> entityClass, int dialogId) {
    String entityName = entityClass.getSimpleName();
    List<OrderedField> ordered = new ArrayList<>();
    for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        DynamicFormField dff = field.getAnnotation(DynamicFormField.class);
        if (dff == null) {
          continue;
        }
        Integer position = parsePosition(dff.uiOrder(), dialogId);
        if (position == null) {
          continue;
        }
        FieldDescriptorInputAndShow base = createFieldDescriptor(field);
        applyFieldConstraintAnnotations(field.getDeclaredAnnotations(), base);
        if (dff.helps().length > 0) {
          base.dynamicFormPropertyHelps = dff.helps();
        }
        if (dff.integerLimit() > 0) {
          base.digitsInteger = dff.integerLimit();
          base.digitsFraction = dff.fractionLimit();
        }
        applyNumericDefaultMax(base);
        String labelKey = dff.labelKey().isEmpty() ? null : dff.labelKey();
        ordered.add(new OrderedField(position,
            new FieldDescriptorInputAndShowExtendedEntity(base, entityName, dff.uiOrder(), labelKey)));
      }
    }
    ordered.sort(Comparator.comparingInt(OrderedField::position));
    return ordered.stream().map(OrderedField::descriptor).collect(Collectors.toList());
  }

  /**
   * Returns the position of a field within a dialog from a {@code uiOrder} string, or null when the
   * field does not belong to the dialog. Tokens are {@code dialogId.position}; a bare number is
   * interpreted as a position in dialog 1.
   *
   * @param uiOrder  the comma separated {@code dialogId.position} list
   * @param dialogId the dialog id to look up
   * @return the position within the dialog, or null if the field is not part of it
   */
  private static Integer parsePosition(String uiOrder, int dialogId) {
    for (String token : uiOrder.split(",")) {
      String t = token.trim();
      if (t.isEmpty()) {
        continue;
      }
      int dot = t.indexOf('.');
      try {
        if (dot < 0) {
          if (dialogId == 1) {
            return Integer.parseInt(t);
          }
        } else if (Integer.parseInt(t.substring(0, dot).trim()) == dialogId) {
          return Integer.parseInt(t.substring(dot + 1).trim());
        }
      } catch (NumberFormatException e) {
        // ignore malformed token
      }
    }
    return null;
  }

  /**
   * Extracts the supported Bean Validation and dynamic-form annotations from a field into the given
   * descriptor. Shared by the generic member analysis and the entity form producer.
   *
   * @param annotations the field's declared annotations
   * @param fd          the descriptor to populate
   */
  private static void applyFieldConstraintAnnotations(Annotation[] annotations, FieldDescriptorInputAndShow fd) {
    for (Annotation annotation : annotations) {
      if (annotation.annotationType() == NotNull.class || annotation.annotationType() == NotNull.List.class
          || annotation instanceof NotBlank) {
        fd.required = true;
      } else if (annotation instanceof Future) {
        fd.dynamicFormPropertyHelps = new DynamicFormPropertyHelps[] { DynamicFormPropertyHelps.DATE_FUTURE };
      } else if (annotation instanceof Min) {
        fd.min = (double) ((Min) annotation).value();
      } else if (annotation instanceof Max) {
        fd.max = (double) ((Max) annotation).value();
      } else if (annotation instanceof DecimalMin) {
        fd.min = Double.parseDouble(((DecimalMin) annotation).value());
      } else if (annotation instanceof DecimalMax) {
        fd.max = Double.parseDouble(((DecimalMax) annotation).value());
      } else if (annotation instanceof Digits) {
        fd.digitsInteger = ((Digits) annotation).integer();
        fd.digitsFraction = ((Digits) annotation).fraction();
      } else if (annotation instanceof Pattern) {
        fd.pattern = ((Pattern) annotation).regexp();
      } else if (annotation instanceof AfterEqual) {
        fd.dateMin = ((AfterEqual) annotation).value();
      } else if (annotation instanceof DynamicFormPropertySupport) {
        fd.dynamicFormPropertyHelps = ((DynamicFormPropertySupport) annotation).value();
      } else if (annotation instanceof Size) {
        fd.min = (double) ((Size) annotation).min();
        fd.max = (double) ((Size) annotation).max();
      }
    }
  }

  /**
   * Applies the system-wide default maximum to a numeric field that has neither an explicit maximum
   * nor a {@code @Digits} precision constraint.
   *
   * @param fd the descriptor to adjust
   */
  private static void applyNumericDefaultMax(FieldDescriptorInputAndShow fd) {
    if (fd.dataType == DataType.Numeric && fd.max == null && fd.digitsInteger == null) {
      fd.max = UDFDataHelper.getMaxDecimalValue(BaseConstants.FID_MAX_DIGITS, BaseConstants.FID_MAX_FRACTION_DIGITS);
    }
  }

  /**
   * Pairs a generated entity field descriptor with its resolved position for sorting within a dialog.
   */
  private record OrderedField(int position, FieldDescriptorInputAndShowExtendedEntity descriptor) {
  }

  /**
   * Creates a field descriptor from a Java field, handling special types like Set&lt;Enum&gt;.
   * For Set&lt;Enum&gt; fields, creates a descriptor with DataType.EnumSet and populates
   * the available enum values for multi-select input generation.
   *
   * <p>Supports both direct enum type parameters ({@code Set<MyEnum>}) and wildcard bounds
   * ({@code Set<? extends MyInterface>}). For wildcard bounds where the upper bound is an
   * interface, scans the interface's known implementing classes for an enum implementation.</p>
   *
   * @param field the Java field to analyze
   * @return a field descriptor with appropriate data type and enum values if applicable
   */
  private static FieldDescriptorInputAndShow createFieldDescriptor(Field field) {
    // Check for Set<Enum> type
    if (Set.class.isAssignableFrom(field.getType())) {
      Type genericType = field.getGenericType();
      if (genericType instanceof ParameterizedType) {
        ParameterizedType pt = (ParameterizedType) genericType;
        Type[] typeArgs = pt.getActualTypeArguments();
        if (typeArgs.length == 1) {
          Class<?> elementType = resolveTypeArgToClass(typeArgs[0]);
          if (elementType != null) {
            if (elementType.isEnum()) {
              return createEnumSetDescriptor(field.getName(), elementType);
            } else if (elementType.isInterface()) {
              // Interface bound (e.g., Set<? extends IExchangeKindType>): create EnumSet descriptor
              // with the interface name as enumType. enumValues must be populated at runtime by the
              // endpoint that serves this form definition.
              return createInterfaceSetDescriptor(field.getName(), elementType);
            }
          }
        }
      }
    }
    // Default: use standard field analysis
    return new FieldDescriptorInputAndShow(field.getName(), field.getType());
  }

  /**
   * Resolves a generic type argument to a concrete Class. Handles direct Class references
   * and WildcardType bounds (e.g., {@code ? extends SomeInterface}).
   *
   * @param typeArg the type argument to resolve
   * @return the resolved Class, or null if it cannot be determined
   */
  private static Class<?> resolveTypeArgToClass(Type typeArg) {
    if (typeArg instanceof Class) {
      return (Class<?>) typeArg;
    }
    if (typeArg instanceof WildcardType) {
      Type[] upperBounds = ((WildcardType) typeArg).getUpperBounds();
      if (upperBounds.length == 1 && upperBounds[0] instanceof Class) {
        return (Class<?>) upperBounds[0];
      }
    }
    return null;
  }

  /**
   * Creates a field descriptor for Set fields with an interface element type.
   * The descriptor uses DataType.EnumSet with the interface name as enumType,
   * but enumValues is left empty — it must be populated at runtime by the serving endpoint.
   *
   * @param fieldName the name of the field
   * @param interfaceClass the interface type contained in the Set
   * @return a field descriptor configured for EnumSet with no values yet
   */
  private static FieldDescriptorInputAndShow createInterfaceSetDescriptor(String fieldName, Class<?> interfaceClass) {
    FieldDescriptorInputAndShow fd = new FieldDescriptorInputAndShow(fieldName, DataType.EnumSet, null, null);
    fd.enumType = interfaceClass.getSimpleName();
    fd.enumValues = new String[0];
    return fd;
  }

  /**
   * Creates a field descriptor for Set&lt;Enum&gt; fields with multi-select support.
   * Extracts all enum constant names to populate the available options.
   *
   * @param fieldName the name of the field
   * @param enumClass the enum class type contained in the Set
   * @return a field descriptor configured for EnumSet data type with available enum values
   */
  private static FieldDescriptorInputAndShow createEnumSetDescriptor(String fieldName, Class<?> enumClass) {
    FieldDescriptorInputAndShow fd = new FieldDescriptorInputAndShow(fieldName, DataType.EnumSet, null, null);
    fd.enumType = enumClass.getSimpleName();

    // Populate available enum values
    Object[] enumConstants = enumClass.getEnumConstants();
    fd.enumValues = Arrays.stream(enumConstants)
        .map(e -> ((Enum<?>) e).name())
        .toArray(String[]::new);
    return fd;
  }
}
