import {Security} from '../../entities/security';
import {
  GeneralEntityPrepareEdit,
  PrepareCallParam
} from '../../lib/proposechange/component/general.entity.prepare.edit';

/**
 * Preparation handler for Security entities in the propose change workflow.
 * Extends the generic entity preparation with security-specific logic for handling
 * derived securities (e.g., CFDs, futures) that link to underlying securities.
 */
export class SecurityPrepareEdit extends GeneralEntityPrepareEdit<Security> implements PrepareCallParam<Security> {
  /**
   * Creates a security preparation handler.
   *
   * @param derivedEntityMapping - Entity mapping identifier to use for derived securities
   */
  constructor(private derivedEntityMapping: string) {
    super(Security);
  }

  /**
   * Redirects derived securities to use a specialized entity mapping.
   * When a security has a linked underlying security (CFD, future), it should be edited
   * using the derived security editor instead of the standard security editor.
   *
   * @param proposedEntity - The security being proposed for editing
   * @returns Derived entity mapping identifier if security is derived, null otherwise
   */
  override redirectEntityMapping(proposedEntity: Security): string {
    return proposedEntity.idLinkSecuritycurrency ? this.derivedEntityMapping : null;
  }
}
