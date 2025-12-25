import {FieldConfig} from '../../dynamic-form/models/field.config';
import {FormConfig} from '../../dynamic-form/models/form.config';
import {TranslateService} from '@ngx-translate/core';
import {ErrorMessageRules, RuleEvent} from '../../dynamic-form/error/error.message.rules';
import {FieldFormGroup} from '../../dynamic-form/models/form.group.definition';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {Directive, ViewChild} from '@angular/core';
import {TranslateHelper} from '../../helper/translate.helper';
import {equalTo} from '../../validator/validator';
import {FieldDescriptorInputAndShow} from '../../dynamicfield/field.descriptor.input.and.show';
import {DynamicFieldModelHelper} from '../../helper/dynamic.field.model.helper';
import {ValidatorFn, Validators} from '@angular/forms';
import {GlobalparameterService, PasswordRegexProperties} from '../../services/globalparameter.service';

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
  protected passwordRegexProperties: PasswordRegexProperties;


  protected constructor(protected gps: GlobalparameterService, protected translateService: TranslateService) {
  }

  protected preparePasswordFields(): void {
    this.setValidators(PasswordBaseComponent.password, Validators.pattern(this.passwordRegexProperties.regex), {
      name: 'pattern',
      keyi18n: null,
      text: this.passwordRegexProperties.languageErrorMsgMap[this.gps.getUserLang()],
      rules: [RuleEvent.TOUCHED, 'dirty']
    });

    this.setValidators(PasswordBaseComponent.passwordConfirm, equalTo(this.configObject.password.formControl),
      {
        name: 'equalTo',
        keyi18n: 'equalToPassword',
        rules: [RuleEvent.TOUCHED, 'dirty']
      });
    TranslateHelper.translateMessageError(this.translateService, this.configObject[PasswordBaseComponent.passwordConfirm]);
  }

  private setValidators(targetField: string, validators: ValidatorFn, error: ErrorMessageRules) {
    this.configObject[targetField].validation.push(validators);
    this.configObject[targetField].errors.push(error);
    this.configObject[targetField].formControl.setValidators(this.configObject[targetField].validation);
    this.configObject[targetField].formControl.updateValueAndValidity();
  }


  protected init(fdias: FieldDescriptorInputAndShow[], newPassword: boolean): void {
    this.configPassword = [
      DynamicFieldModelHelper.ccWithFieldsFromDescriptor(this.translateService, PasswordBaseComponent.password,
        (newPassword) ? 'PASSWORD_NEW' : 'PASSWORD', fdias),
      DynamicFieldModelHelper.ccWithFieldsFromDescriptor(this.translateService, PasswordBaseComponent.password,
        (newPassword) ? 'PASSWORD_NEW_CONFIRM' : 'PASSWORD_CONFIRM', fdias,
        {targetField: PasswordBaseComponent.passwordConfirm}),
    ];
  }

}
