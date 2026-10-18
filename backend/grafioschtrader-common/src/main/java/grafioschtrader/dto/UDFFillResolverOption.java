package grafioschtrader.dto;

import java.util.Set;

import grafiosch.types.UDFDataType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    A resolver of the fill source RESOLVER: a built-in lookup against an external, keyed source. The fill rule of the
    field is the resolver ID optionally followed by a space and an argument.""")
public record UDFFillResolverOption(
    @Schema(description = "ID of the resolver as stored in the fill rule, also the NLS key of its name") String resolverId,
    @Schema(description = "The data types of a field this resolver can fill") Set<UDFDataType> udfDataTypes,
    @Schema(description = "The argument used when the rule names the resolver only") String defaultArgument) {
}
