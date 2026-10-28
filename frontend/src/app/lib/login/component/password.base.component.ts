import { FieldConfig } from '../../dynamic-form/models/field.config';
import { FormConfig } from '../../dynamic-form/models/form.config';
import { TranslateService } from '@ngx-translate/core';
import { ErrorMessageRules, RuleEvent } from '../../dynamic-form/error/error.message.rules';
import { FieldFormGroup } from '../../dynamic-form/models/form.group.definition';
import { DynamicFormComponent } from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import { ChangeDetectorRef, DestroyRef, Directive, inject, Injector, viewChild } from '@angular/core';
import { TranslateHelper } from '../../helper/translate.helper';
import { equalTo } from '../../validator/validator';
import { FieldDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { DynamicFieldModelHelper } from '../../helper/dynamic.field.model.helper';
import { ValidatorFn, Validators } from '@angular/forms';
import { GlobalparameterService, PasswordRegexProperties } from '../../services/globalparameter.service';

/**
 * Certain forms such as registration or password change will involve entering a password twice.
 * This could be the base class for these forms.
 */
@Directive()
export abstract class PasswordBaseComponent {
  protected gps: GlobalparameterService = inject(GlobalparameterService);
  protected translateService: TranslateService = inject(TranslateService);

  // Access child components
  readonly form = viewChild(DynamicFormComponent);

  private static readonly passwordConfirm = 'passwordConfirm';
  private static readonly password = 'password';

  // Form configuration
  formConfig: FormConfig;
  config: FieldFormGroup[] = [];
  configPassword: FieldConfig[];
  configObject: { [name: string]: FieldConfig };
  protected passwordRegexProperties: PasswordRegexProperties;

  /** Lifetime of the component, for subscriptions created outside an injection context */
  protected readonly destroyRef = inject(DestroyRef);

  protected readonly changeDetectorRef = inject(ChangeDetectorRef);

  /** Injector for afterNextRender, which is called from callbacks outside an injection context */
  protected readonly injector = inject(Injector);

  /** Validator added by {@link setValidators} per field, so a repeated call replaces it instead of adding another */
  private addedValidators: { [field: string]: ValidatorFn } = {};

  constructor() {}

  protected preparePasswordFields(): void {
    this.setValidators(PasswordBaseComponent.password, Validators.pattern(this.passwordRegexProperties.regex), {
      name: 'pattern',
      keyi18n: null,
      text: this.passwordRegexProperties.languageErrorMsgMap[this.gps.getUserLang()],
      rules: [RuleEvent.TOUCHED, 'dirty']
    });

    this.setValidators(PasswordBaseComponent.passwordConfirm, equalTo(this.configObject.password.formControl), {
      name: 'equalTo',
      keyi18n: 'equalToPassword',
      rules: [RuleEvent.TOUCHED, 'dirty']
    });
    TranslateHelper.translateMessageError(
      this.translateService,
      this.configObject[PasswordBaseComponent.passwordConfirm]
    );
  }

  /**
   * Marks this component for check. Needed when it writes its own template fields from an asynchronous callback and
   * has not moved them to signals yet; changes of a FieldConfig notify the form by themselves.
   */
  protected viewChanged(): void {
    this.changeDetectorRef.markForCheck();
  }

  /**
   * Adds the validator and its error rule to a field. Calling it again for the same field replaces both, so preparing
   * the password fields a second time does not duplicate the rules.
   */
  private setValidators(targetField: string, validator: ValidatorFn, error: ErrorMessageRules) {
    const fieldConfig = this.configObject[targetField];
    const previous = this.addedValidators[targetField];
    fieldConfig.validation = [...(fieldConfig.validation ?? []).filter((v) => v !== previous), validator];
    this.addedValidators[targetField] = validator;
    fieldConfig.errors = [...(fieldConfig.errors ?? []).filter((e) => e.name !== error.name), error];
    fieldConfig.formControl.setValidators(fieldConfig.validation);
    fieldConfig.formControl.updateValueAndValidity();
    fieldConfig.baseInputComponent?.reEvaluateRequired();
  }

  protected init(fdias: FieldDescriptorInputAndShow[], newPassword: boolean): void {
    this.configPassword = [
      DynamicFieldModelHelper.ccWithFieldsFromDescriptor(
        this.translateService,
        PasswordBaseComponent.password,
        newPassword ? 'PASSWORD_NEW' : 'PASSWORD',
        fdias
      ),
      DynamicFieldModelHelper.ccWithFieldsFromDescriptor(
        this.translateService,
        PasswordBaseComponent.password,
        newPassword ? 'PASSWORD_NEW_CONFIRM' : 'PASSWORD_CONFIRM',
        fdias,
        { targetField: PasswordBaseComponent.passwordConfirm }
      )
    ];
  }
}
