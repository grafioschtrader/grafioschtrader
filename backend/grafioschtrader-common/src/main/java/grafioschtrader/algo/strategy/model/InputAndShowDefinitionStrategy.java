package grafioschtrader.algo.strategy.model;

import java.io.Serializable;
import java.util.List;

import grafioschtrader.dynamic.model.FieldDescriptorInputAndShow;

public class InputAndShowDefinitionStrategy implements Serializable {

  private static final long serialVersionUID = 1L;

  public List<FieldDescriptorInputAndShow> topFormDefintionList;
  public List<FieldDescriptorInputAndShow> assetclassFormDefintionList;
  public List<FieldDescriptorInputAndShow> securityFormDefinitionList;

  public InputAndShowDefinitionStrategy(List<FieldDescriptorInputAndShow> topFormDefintionList,
      List<FieldDescriptorInputAndShow> assetclassFormDefintionList,
      List<FieldDescriptorInputAndShow> securityFormDefinitionList) {
    super();
    this.topFormDefintionList = topFormDefintionList;
    this.assetclassFormDefintionList = assetclassFormDefintionList;
    this.securityFormDefinitionList = securityFormDefinitionList;
  }

}
