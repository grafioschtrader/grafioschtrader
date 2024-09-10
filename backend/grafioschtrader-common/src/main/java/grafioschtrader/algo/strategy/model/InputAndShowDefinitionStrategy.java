package grafioschtrader.algo.strategy.model;

import java.io.Serializable;
import java.util.List;

import grafioschtrader.dynamic.model.FieldDescriptorInputAndShow;

public class InputAndShowDefinitionStrategy implements Serializable {

  private static final long serialVersionUID = 1L;

  public List<FieldDescriptorInputAndShow> topFormDefinitionList;
  public List<FieldDescriptorInputAndShow> assetclassFormDefinitionList;
  public List<FieldDescriptorInputAndShow> securityFormDefinitionList;

  public InputAndShowDefinitionStrategy(List<FieldDescriptorInputAndShow> topFormDefinitionList,
      List<FieldDescriptorInputAndShow> assetclassFormDefinitionList,
      List<FieldDescriptorInputAndShow> securityFormDefinitionList) {
    super();
    this.topFormDefinitionList = topFormDefinitionList;
    this.assetclassFormDefinitionList = assetclassFormDefinitionList;
    this.securityFormDefinitionList = securityFormDefinitionList;
  }

}
