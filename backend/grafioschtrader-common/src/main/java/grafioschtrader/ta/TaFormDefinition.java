package grafioschtrader.ta;

import java.io.Serializable;
import java.util.List;

import grafioschtrader.dynamic.model.FieldDescriptorInputAndShow;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contains a single form definition that can be used in the client.
 *
 * @author Hugo Graf
 *
 */
public class TaFormDefinition implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "A technical indicator  has one or more input parameters which can be set")
  public List<FieldDescriptorInputAndShow> taFormList;

  @Schema(description = "A technical indicator has default values, this should be displayed to the user")
  public Object defaultDataModel;

  public TaFormDefinition(List<FieldDescriptorInputAndShow> taFormList, Object defaultDataModel) {
    this.taFormList = taFormList;
    this.defaultDataModel = defaultDataModel;
  }

}
