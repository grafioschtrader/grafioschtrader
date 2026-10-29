import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { Component, OnInit, input, inject } from '@angular/core';
import { Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { ClassDescriptorInputAndShow } from '../../lib/dynamicfield/field.descriptor.input.and.show';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { AppHelper } from '../../lib/helper/app.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { TenantAIProvider, TenantAIProviderOptions } from '../model/tenant-ai-provider';
import { TenantAIProviderService } from '../service/tenant-ai-provider.service';

@Component({
  selector: 'tenant-ai-provider-edit',
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule],
  template: `<p-dialog
    [header]="'TENANT_AI_PROVIDER' | translate"
    [visible]="visibleDialog()"
    [style]="{ width: '550px' }"
    [modal]="true"
    appendTo="body"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)">
    <p>{{ 'AI_KEY_KEEP_HINT' | translate }}</p>
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  </p-dialog>`
})
export class TenantAIProviderEditComponent extends SimpleEntityEditBase<TenantAIProvider> implements OnInit {
  readonly entity = input<TenantAIProvider>(undefined);
  readonly options = input<TenantAIProviderOptions>(undefined);
  readonly formDefinition = input<ClassDescriptorInputAndShow>(undefined);
  readonly existingProviders = input<string[]>(undefined);

  constructor() {
    super(HelpIds.HELP_INTRO_SETTINGS, 'TENANT_AI_PROVIDER', inject(TenantAIProviderService));
  }
  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      this.translateService,
      this.formDefinition(),
      '',
      true
    ) as FieldConfig[];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.configObject.apiKey.maxLength = 256;
  }
  protected override initialize(): void {
    this.configObject.provider.valueKeyHtmlOptions = this.options().providers.filter(
      (p) => p.key === this.entity()?.provider || !this.existingProviders().includes(String(p.key))
    );
    this.form().setDefaultValuesAndEnableSubmit();
    this.form().transferBusinessObjectToForm(this.entity() ?? new TenantAIProvider());
    this.form().setValue('apiKey', null);
    // The server never returns a stored key; its ending shows that one exists and stays when the field is left empty
    this.configObject.apiKey.placeholder = this.entity()?.apiKeyHint ?? '';
    const entity = this.entity();
    if (entity) {
      requireFormControl(this.configObject.provider).disable();
    }
    requireFormControl(this.configObject.provider)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((p) => this.updateKeyInput(p));
    this.updateKeyInput(entity?.provider);
  }
  private updateKeyInput(provider: string): void {
    const key = this.configObject.apiKey;
    const needsKey = this.options().keyProviders.includes(provider);
    const required = !this.entity() && needsKey;
    key.invisible = !needsKey;
    if (!needsKey) {
      requireFormControl(key).setValue(null);
    }
    DynamicFieldHelper.resetValidator(
      key,
      required ? [Validators.required, Validators.maxLength(256)] : [Validators.maxLength(256)],
      (key.errors ?? []).filter((e) => e.name !== 'required')
    );
    if (required) {
      DynamicFieldHelper.addErrorRule(this.translateService, key, { ...DynamicFieldHelper.RULE_REQUIRED_TOUCHED });
    }
    this.configObject.model.placeholder = this.options().defaultModels[provider] || '';
  }
  protected override getNewOrExistingInstanceBeforeSave(_: { [name: string]: any }): TenantAIProvider {
    return this.copyFormToPrivateBusinessObject(new TenantAIProvider(), this.entity());
  }
}
