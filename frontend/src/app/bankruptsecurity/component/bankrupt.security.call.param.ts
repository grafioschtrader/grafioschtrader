import { ClassDescriptorInputAndShow } from '../../lib/dynamicfield/field.descriptor.input.and.show';
import { BankruptSecurity } from '../../entities/bankrupt.security';

/**
 * What the marker dialog needs from its opener.
 *
 * <p>
 * The form definition is fetched before the dialog is shown rather than inside it: the dialog is mounted lazily and
 * builds its form during its own initialization, so a definition arriving later would leave an empty form. The lookup
 * is memoised, so only the first open costs a request.
 * </p>
 */
export class BankruptSecurityCallParam {
  constructor(
    public formDefinition: ClassDescriptorInputAndShow,
    /** The marker being edited, null when one is created. */
    public bankruptSecurity: BankruptSecurity = null,
    /** Name of the instrument, shown in the read-only field; empty while it has still to be searched. */
    public securityName: string = null
  ) {}
}
