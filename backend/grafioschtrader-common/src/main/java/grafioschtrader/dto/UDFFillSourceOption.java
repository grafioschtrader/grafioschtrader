package grafioschtrader.dto;

import java.util.Set;

import grafiosch.types.UDFDataType;
import grafioschtrader.types.UDFFillSource;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    A fill source this instance offers for user-defined security fields, together with the data types it can fill.
    The edit form of a field builds its selection of fill sources from these entries only.""")
public record UDFFillSourceOption(@Schema(description = "The offered fill source") UDFFillSource fillSource,
    @Schema(description = "The data types of a field this fill source supports") Set<UDFDataType> udfDataTypes) {
}
