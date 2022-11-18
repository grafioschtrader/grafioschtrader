package grafioschtrader.dynamic.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class ClassDescriptorInputAndShow {
  public List<FieldDescriptorInputAndShow> fieldDescriptorInputAndShows;
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
    
    public DateRangeClass(String startField, String endField) {
      this.startField = startField;
      this.endField = endField;
    }
    
  }
}
