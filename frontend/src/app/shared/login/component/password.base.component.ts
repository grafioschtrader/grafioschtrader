import {FieldConfig} from '../../../dynamic-form/models/field.config';
import {FormConfig} from '../../../dynamic-form/models/form.config';
import {TranslateService} from '@ngx-translate/core';
import {RuleEvent} from '../../../dynamic-form/error/error.message.rules';
import {FieldFormGroup} from '../../../dynamic-form/models/form.group.definition';
import {DynamicFormComponent} from '../../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {Directive, ViewChild} from '@angular/core';
import {TranslateHelper} from '../../helper/translate.helper';
import {equalTo} from '../../validator/validator';
import {FieldDescriptorInputAndShow} from '../../dynamicfield/field.descriptor.input.and.show';
import {DynamicFieldModelHelper} from '../../helper/dynamic.field.model.helper';
import {Validators} from '@angular/forms';

/**
 * Certain forms such as registration or password change will involve entering a password twice.
 * This could be the base class for these forms.
 */
@Directive()
export abstract class PasswordBaseComponent {

  // Access child components
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  private static readonly passwordConfirm = 'passwordConfirm';
  private static readonly password = 'password';

  // Form configuration
  formConfig: FormConfig;
  config: FieldFormGroup[] = [];
  configPassword: FieldConfig[];
  configObject: { [name: string]: FieldConfig };

  protected constructor(public translateService: TranslateService) {
  }

  afterViewInit(): void {
    this.configObject[PasswordBaseComponent.passwordConfirm].errors.push({
      name: 'equalTo',
      keyi18n: 'equalToPassword',
      rules: [RuleEvent.TOUCHED, 'dirty']
    });
    this.configObject[PasswordBaseComponent.password].validation.push( Validators.pattern('^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$'));
    this.configObject[PasswordBaseComponent.password].errors.push({
      name: 'pattern',
      keyi18n: null,
      text: 'Es muss eine Zahl und ein Buchstabe',
      rules: [RuleEvent.TOUCHED, 'dirty']
    });
    this.configObject[PasswordBaseComponent.password].formControl.setValidators(this.configObject.password.validation);
    this.configObject[PasswordBaseComponent.password].formControl.updateValueAndValidity();


    TranslateHelper.translateMessageError(this.translateService, this.configObject[PasswordBaseComponent.passwordConfirm]);
    this.configObject[PasswordBaseComponent.passwordConfirm].validation.push(equalTo(this.configObject.password.formControl));
    this.configObject[PasswordBaseComponent.passwordConfirm].formControl.setValidators(this.configObject.passwordConfirm.validation);
    this.configObject[PasswordBaseComponent.passwordConfirm].formControl.updateValueAndValidity();
  }

  protected init(fdias: FieldDescriptorInputAndShow[], newPassword: boolean): void {
    this.configPassword = [
      // TODO Should only accept better passwords
      DynamicFieldModelHelper.ccWithFieldsFromDescriptor(PasswordBaseComponent.password,
        (newPassword) ? 'PASSWORD_NEW' : 'PASSWORD', fdias),
      DynamicFieldModelHelper.ccWithFieldsFromDescriptor(PasswordBaseComponent.password,
        (newPassword) ? 'PASSWORD_NEW_CONFIRM' : 'PASSWORD_CONFIRM', fdias,
        {targetField: PasswordBaseComponent.passwordConfirm}),
    ];
  }

}
