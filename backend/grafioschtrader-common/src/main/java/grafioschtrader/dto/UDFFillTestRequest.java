package grafioschtrader.dto;

import java.util.EnumSet;

import grafiosch.types.UDFDataType;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.UDFFillSource;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Draft of a fill rule of a user-defined security field, evaluated against the tenant scope of the caller without
    writing anything. It carries the field properties that influence the result, so an unsaved draft can be tested.""")
public class UDFFillTestRequest {

  @Schema(description = """
      The field being edited, or null for a field not yet saved. With an id the field must belong to the caller and
      its stored values serve as comparison data.""")
  public Integer idUDFMetadata;

  @Schema(description = "Data type of the field, used for the validation of each computed value")
  public UDFDataType udfDataType;

  @Schema(description = "Field size of the field in the form 'min,max', used for the validation of each computed value")
  public String fieldSize;

  @Schema(description = "Asset classes the field applies to")
  public EnumSet<AssetclassType> categoryTypeEnums;

  @Schema(description = "Financial instruments the field applies to")
  public EnumSet<SpecialInvestmentInstruments> specialInvestmentInstrumentEnums;

  @Schema(description = "The fill source of the draft")
  public UDFFillSource fillSource;

  @Schema(description = "The fill rule of the draft")
  public String fillRule;
}
