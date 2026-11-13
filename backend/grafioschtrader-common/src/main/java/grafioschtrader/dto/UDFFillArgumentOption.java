package grafioschtrader.dto;

import java.util.Set;

import grafiosch.types.UDFDataType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    One of the arguments a resolver of the fill source RESOLVER accepts. A resolver that lists such arguments is edited
    with a selection instead of a free text, and only the arguments that fit the data type of the field are offered.""")
public record UDFFillArgumentOption(
    @Schema(description = """
        The argument as stored in the fill rule. The text of the selection is the NLS key resolverId_argument""")
    String argument,
    @Schema(description = "The data types of a field this argument can fill") Set<UDFDataType> udfDataTypes) {
}
