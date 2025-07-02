package grafiosch.dynamic.model;

import java.io.Serializable;
import java.util.Arrays;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    This metadata is intended for a generative input form.
    An instance corresponds to an input element. An instance may be created from the analysis of a class or via persisted metadata.
    """)
public class FieldDescriptorInputAndShow implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "The name of the field as it appears in the model class or form")
  public String fieldName;

  @Schema(description = "The data type of the field that determines the input component type and validation rules")
  public DataType dataType;
  
  @Schema(description = "The minimum value for numeric types or minimum length for string types")
  public Double min;
  
  @Schema(description = "The maximum value for numeric types or maximum length for string types")
  public Double max;
  
  @Schema(description = "The simple name of the enum class if this field represents an enum type")
  public String enumType;
  
  @Schema(description = "Whether this field is required and must have a value")
  public boolean required;
  
  @Schema(description = "Array of additional form property helpers that provide UI hints and specialized input behaviors")
  public DynamicFormPropertyHelps[] dynamicFormPropertyHelps;

  /**
   * Creates a field descriptor with explicit data type and constraints.
   * 
   * @param fieldName the name of the field
   * @param dataType the data type for the field
   * @param min the minimum value/length constraint, or null if no minimum
   * @param max the maximum value/length constraint, or null if no maximum
   */
  public FieldDescriptorInputAndShow(String fieldName, DataType dataType, Double min, Double max) {
    super();
    this.fieldName = fieldName;
    this.dataType = dataType;
    this.min = min;
    this.max = max;
  }

  /**
   * Creates a field descriptor by analyzing a Java class type.
   * The data type is automatically determined from the provided Java class.
   * 
   * @param fieldName the name of the field
   * @param type the Java class type to analyze and convert to a DataType
   */
  public FieldDescriptorInputAndShow(String fieldName, Class<?> type) {
    super();
    this.fieldName = fieldName;
    setJavaTypeToGenericType(type);
  }

  /**
   * Determines whether the provided type is an enum and sets the appropriate
   * data type and enum information accordingly.
   * 
   * @param type the Java class type to analyze
   */
  private void setJavaTypeToGenericType(Class<?> type) {
    if (type instanceof Class && ((Class<?>) type).isEnum()) {
      setForEnum(type);
    } else {
      setDataType(type);
    }
  }

  /**
   * Maps a Java class type to the corresponding DataType enum value.
   * Handles primitive types, wrapper classes, and common data types
   * used in form generation.
   * 
   * @param type the Java class type to map
   */
  private void setDataType(Class<?> type) {
    switch (DataTypeJava.fromClass(type)) {

    case Byte:
    case ByteC:
    case Integer:
    case IntegerC:
      dataType = DataType.NumericInteger;
      break;

    case Boolean:
    case BooleanC:
      dataType = DataType.Boolean;
      break;

    case Double:
    case DoubleC:
      dataType = DataType.Numeric;
      break;

    case LocalDate:
      dataType = DataType.DateString;
      break;

    case StringC:
      dataType = DataType.String;
      break;

    case LocalDateTime:
      dataType = DataType.DateTimeNumeric;
      break;

    default:
      dataType = DataType.None;
    }
  }

  private void setForEnum(Class<?> model) {
    Arrays.stream(model.getDeclaredFields()).filter(f -> f.getName().equals("value")).findFirst().ifPresent(f -> {
      setDataType(f.getType());
      enumType = model.getSimpleName();
    });
  }

}
