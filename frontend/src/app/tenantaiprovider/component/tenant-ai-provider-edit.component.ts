import { Component, Input, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { ClassDescriptorInputAndShow } from '../../lib/dynamicfield/field.descriptor.input.and.show';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { AppHelper } from '../../lib/helper/app.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { HelpIds } from '../../lib/help/help.ids';
import { TenantAIProvider, TenantAIProviderOptions } from '../model/tenant-ai-provider';
import { TenantAIProviderService } from '../service/tenant-ai-provider.service';

@Component({
  selector: 'tenant-ai-provider-edit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DialogModule, DynamicFormModule, TranslateModule],
  template: `<p-dialog
    [header]="'TENANT_AI_PROVIDER' | translate"
    [visible]="visibleDialog"
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
      (submitBt)="submit($event)"></dynamic-form>
  </p-dialog>`
})
export class TenantAIProviderEditComponent extends SimpleEntityEditBase<TenantAIProvider> implements OnInit, OnDestroy {
  @Input() entity: TenantAIProvider;
  @Input() options: TenantAIProviderOptions;
  @Input() formDefinition: ClassDescriptorInputAndShow;
  @Input() existingProviders: string[];
  private providerSubscription: Subscription;

  constructor(
    translate: TranslateService,
    gps: GlobalparameterService,
    messages: MessageToastService,
    service: TenantAIProviderService
  ) {
    super(HelpIds.HELP_INTRO_SETTINGS, 'TENANT_AI_PROVIDER', translate, gps, messages, service);
  }
  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      this.translateService,
      this.formDefinition,
      '',
      true
    ) as FieldConfig[];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.configObject.apiKey.maxLength = 256;
  }
  protected override initialize(): void {
    this.configObject.provider.valueKeyHtmlOptions = this.options.providers.filter(
      (p) => p.key === this.entity?.provider || !this.existingProviders.includes(String(p.key))
    );
    this.form.setDefaultValuesAndEnableSubmit();
    this.form.transferBusinessObjectToForm(this.entity ?? new TenantAIProvider());
    this.form.setValue('apiKey', null);
    // The server never returns a stored key; its ending shows that one exists and stays when the field is left empty
    this.configObject.apiKey.placeholder = this.entity?.apiKeyHint ?? '';
    if (this.entity) {
      this.configObject.provider.formControl.disable();
    }
    this.providerSubscription?.unsubscribe();
    this.providerSubscription = this.configObject.provider.formControl.valueChanges.subscribe((p) =>
      this.updateKeyInput(p)
    );
    this.updateKeyInput(this.entity?.provider);
  }
  private updateKeyInput(provider: string): void {
    const key = this.configObject.apiKey;
    const needsKey = this.options.keyProviders.includes(provider);
    key.invisible = !needsKey;
    key.formControl.setValidators(
      !this.entity && needsKey ? [Validators.required, Validators.maxLength(256)] : [Validators.maxLength(256)]
    );
    if (!needsKey) {
      key.formControl.setValue(null);
    }
    key.formControl.updateValueAndValidity();
    this.configObject.model.placeholder = this.options.defaultModels[provider] || '';
  }
  protected override getNewOrExistingInstanceBeforeSave(_: { [name: string]: any }): TenantAIProvider {
    return this.copyFormToPrivateBusinessObject(new TenantAIProvider(), this.entity);
  }
  override onHide(event: any): void {
    this.providerSubscription?.unsubscribe();
    super.onHide(event);
  }
  ngOnDestroy(): void {
    this.providerSubscription?.unsubscribe();
  }
}
