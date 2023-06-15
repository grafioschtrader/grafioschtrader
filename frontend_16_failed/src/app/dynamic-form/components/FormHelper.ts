import {FieldConfig} from '../models/field.config';
import {FieldFormGroup, FormGroupDefinition} from '../models/form.group.definition';
import {InputType} from '../models/input.type';

export abstract class FormHelper {

  public static flattenConfigMap(fieldFormGroups: FieldFormGroup[]): FieldConfig[] {
    return fieldFormGroups.reduce((explored, toExplore) =>
      explored.concat((FormHelper.isFieldConfig(toExplore)) ? toExplore :
        FormHelper.flattenConfigMap((<FormGroupDefinition>toExplore).fieldConfig)), []);
  }

  public static getFormGroupDefinition(fieldFormGroups: FieldFormGroup[]): FormGroupDefinition[] {
    return fieldFormGroups.filter(fieldConfig => !FormHelper.isFieldConfig(fieldConfig)) as FormGroupDefinition[];
  }

  /**
   * Return FieldConfig without FieldConfig of children.
   */
  public static getFieldConfigs(fieldFormGroups: FieldFormGroup[]): FieldConfig[] {
    return fieldFormGroups.filter(fieldConfig => FormHelper.isFieldConfig(fieldConfig)) as FieldConfig[];
  }

  public static isFieldConfig(fieldFormGroup: FieldFormGroup): boolean {
    return fieldFormGroup.hasOwnProperty('inputType');
  }

  public static hideVisibleFieldSet(invisible: boolean, fieldSetName: string, fieldConfigs: FieldConfig[]): void {
    fieldConfigs.filter(fieldConfig => fieldConfig.fieldsetName && fieldConfig.fieldsetName === fieldSetName)
      .forEach(fieldConfig => fieldConfig.invisible = invisible);
  }

  public static hideVisibleFieldConfigs(invisible: boolean, fieldConfigs: FieldConfig[]): void {
    fieldConfigs.forEach(fieldConfig => fieldConfig.invisible = invisible);
  }

  public static disableEnableFieldConfigs(disable: boolean, fieldConfigs: FieldConfig[]): void {
    fieldConfigs.forEach(fieldConfig => {
      if (fieldConfig.inputType !== InputType.Button && fieldConfig.inputType !== InputType.Pbutton) {
        fieldConfig.invisible = false;
        if (disable) {
          fieldConfig.formControl.disable();
        } else {
          fieldConfig.formControl.enable();
        }
      }
    });
  }

  public static disableEnableFieldConfigsWhenAlreadySet(disable: boolean, fieldConfigs: FieldConfig[]): void {
    fieldConfigs.forEach(fieldConfig => {
      if (fieldConfig.inputType !== InputType.Button && fieldConfig.inputType !== InputType.Pbutton) {
        const saveVisibleState = fieldConfig.invisible;
        fieldConfig.invisible = false;
        if (disable && fieldConfig.formControl.value != null) {
          fieldConfig.formControl.disable();
        } else {
          fieldConfig.formControl.enable();
        }
        fieldConfig.invisible = saveVisibleState;
      }
    });
  }

}
