package grafioschtrader.algo.strategy.model;

import java.io.Serializable;

import grafiosch.dynamic.model.ClassDescriptorInputAndShow;

/**
 * Edit form of a simple strategy for each level of the algo hierarchy. Each level carries the fields of its model class
 * together with the cross-field constraints declared on that class, so the form rejects what the save would.
 */
public class InputAndShowDefinitionStrategy implements Serializable {

  private static final long serialVersionUID = 1L;

  public ClassDescriptorInputAndShow topFormDefinition;
  public ClassDescriptorInputAndShow assetclassFormDefinition;
  public ClassDescriptorInputAndShow securityFormDefinition;
  public boolean isComplexStrategy;
  public java.util.Map<String, Object> defaultValues = java.util.Map.of();

  public InputAndShowDefinitionStrategy(ClassDescriptorInputAndShow topFormDefinition,
      ClassDescriptorInputAndShow assetclassFormDefinition, ClassDescriptorInputAndShow securityFormDefinition,
      boolean isComplexStrategy) {
    super();
    this.topFormDefinition = topFormDefinition;
    this.assetclassFormDefinition = assetclassFormDefinition;
    this.securityFormDefinition = securityFormDefinition;
    this.isComplexStrategy = isComplexStrategy;
  }

}
