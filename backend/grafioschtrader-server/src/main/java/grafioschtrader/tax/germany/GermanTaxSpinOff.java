package grafioschtrader.tax.germany;

import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.SecurityActionApplication;
import grafioschtrader.types.SpinOffTaxTreatmentDe;

/**
 * What the German tax help needs of one applied spin-off: how the tax treats it and which share of the euro cost passes
 * to the new shares. The three bookings of the spin-off carry the application id, by which the engine finds this
 * record.
 *
 * @param treatment  the German tax treatment recorded by the administrator; {@code UNDETERMINED} until one is chosen
 * @param allocation the share of the parent's cost that passes to the child under German tax law: the German
 *                   allocation of the action when one is recorded, otherwise the allocation the application calculates
 *                   with. Strictly between 0 and 1, or null when neither is known.
 */
record GermanTaxSpinOff(SpinOffTaxTreatmentDe treatment, Double allocation) {

  /**
   * Derives the record of an application of a spin-off.
   *
   * @param application the tenant's application of the spin-off action
   * @return the treatment and allocation that apply to the German tax help
   */
  static GermanTaxSpinOff of(SecurityActionApplication application) {
    final SecurityAction action = application.getSecurityAction();
    final SpinOffTaxTreatmentDe treatment = action.getTaxTreatmentDe() == null ? SpinOffTaxTreatmentDe.UNDETERMINED
        : action.getTaxTreatmentDe();
    final Double allocation = action.getCostAllocationDe() != null ? action.getCostAllocationDe()
        : application.getCostAllocation();
    return new GermanTaxSpinOff(treatment, allocation);
  }
}
