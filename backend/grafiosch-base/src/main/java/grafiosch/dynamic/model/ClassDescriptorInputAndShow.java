package grafiosch.dynamic.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
Represents the complete metadata definition for a dynamic form class.
Contains both the individual field descriptors for form elements and class-level 
constraint validators that apply across multiple fields (such as date range validation).
This metadata is used to generate dynamic input forms and validation rules.
""")
public class ClassDescriptorInputAndShow {
  
  @Schema(description = "List of field descriptors that define the individual input elements and their properties for the dynamic form")
  public List<FieldDescriptorInputAndShow> fieldDescriptorInputAndShows;
  
  @Schema(description = "Map of class-level constraint validators that apply validation rules across multiple fields, keyed by constraint type")
  public Map<ConstraintValidatorType, Object> constraintValidatorMap = new HashMap<>();

  public ClassDescriptorInputAndShow(List<FieldDescriptorInputAndShow> fieldDescriptorInputAndShows) {
    this.fieldDescriptorInputAndShows = fieldDescriptorInputAndShows;
  }

  public void putConstraint(ConstraintValidatorType ContraintValidatorType, Object constraintValidator) {
    constraintValidatorMap.put(ContraintValidatorType, constraintValidator);
  }

  
  public static class DateRangeClass {
    public String startField;
    public String endField;

    /**
     * Creates a new date range constraint configuration.
     * 
     * @param startField the name of the field containing the start date
     * @param endField the name of the field containing the end date
     */
    public DateRangeClass(String startField, String endField) {
      this.startField = startField;
      this.endField = endField;
    }

  }
}
