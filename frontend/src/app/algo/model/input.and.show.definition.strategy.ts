import { ClassDescriptorInputAndShow } from '../../lib/dynamicfield/field.descriptor.input.and.show';

/**
 * Edit form of a simple strategy for each level of the algo hierarchy. Each level carries the fields of its model
 * class together with the cross-field constraints declared on that class.
 *
 * Counterpart of backend `grafioschtrader.algo.strategy.model.InputAndShowDefinitionStrategy`.
 */
export class InputAndShowDefinitionStrategy {
  topFormDefinition: ClassDescriptorInputAndShow;
  assetclassFormDefinition: ClassDescriptorInputAndShow;
  securityFormDefinition: ClassDescriptorInputAndShow;
  isComplexStrategy: boolean;
  defaultValues?: Record<string, number>;
}
