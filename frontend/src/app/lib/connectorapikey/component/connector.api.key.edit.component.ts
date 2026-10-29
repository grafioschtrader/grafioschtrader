import { requireFormControl } from '../../dynamic-form/models/form-control-access';
import { Component, OnInit, input, inject } from '@angular/core';

import { SimpleEntityEditBase } from '../../edit/simple.entity.edit.base';
import { ConnectorApiKey, SubscriptionTypeReadableName } from '../types/connector.api.key';
import { TranslateModule } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../dynamic-form/dynamic-form.imports';
import { HelpIds } from '../../help/help.ids';
import { ConnectorApiKeyService } from '../service/connector.api.key.service';
import { AppHelper } from '../../helper/app.helper';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { SelectOptionsHelper } from '../../helper/select.options.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { Validators } from '@angular/forms';
import { BaseSettings } from '../../base.settings';
import { ValueKeyHtmlSelectOptions } from '../../dynamic-form/models/value.key.html.select.options';

/**
 * Create or edit a key for the connectors API. It is intended for the admin only.
 */
@Component({
  selector: 'connector-api-key-edit',
  template: ` <p-dialog
    header="{{ 'CONNECTOR_API_KEY' | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '600px' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  </p-dialog>`,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class ConnectorApiKeyEditComponent extends SimpleEntityEditBase<ConnectorApiKey> implements OnInit {
  readonly connectorApiKey = input<ConnectorApiKey>(undefined);
  readonly strn = input<{
    [id: string]: SubscriptionTypeReadableName;
  }>(undefined);
  readonly existingProviders = input<string[]>(undefined);
  readonly subscriptionTypeOptionsMap = input<{
    [providerId: string]: ValueKeyHtmlSelectOptions[];
  }>(undefined);

  constructor() {
    super(
      HelpIds.HELP_CONNECTOR_API_KEY,
      AppHelper.toUpperCaseWithUnderscore(BaseSettings.CONNECTOR_API_KEY),
      inject(ConnectorApiKeyService)
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('idProvider', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('subscriptionType', true),
      // A browser cuts a longer pasted key silently; 256 covers OpenAI project keys and fits the encrypted column
      DynamicFieldHelper.createFieldInputStringHeqF('apiKey', 256, true, {
        minLength: 10
      }),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.configObject.idProvider.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromObject(
      'readableName',
      this.strn(),
      false,
      this.connectorApiKey() ? [] : this.existingProviders()
    );
    this.form().setDefaultValuesAndEnableSubmit();
    const connectorApiKey = this.connectorApiKey();
    this.updateSubscriptionTypeVisibility(connectorApiKey?.idProvider);
    if (connectorApiKey) {
      this.form().transferBusinessObjectToForm(connectorApiKey);
      requireFormControl(this.configObject.idProvider).disable();
    } else {
      requireFormControl(this.configObject.idProvider).enable();
    }
    this.valueChangedOnIdProvider();
  }

  valueChangedOnIdProvider(): void {
    requireFormControl(this.configObject.idProvider)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((selection) => this.updateSubscriptionTypeVisibility(selection));
  }

  private updateSubscriptionTypeVisibility(providerId: string | null | undefined): void {
    const options = providerId == null ? [] : (this.subscriptionTypeOptionsMap()[providerId] ?? []);
    this.configObject.subscriptionType.valueKeyHtmlOptions = options;
    // A subscription belongs to its provider; switching providers must require a fresh selection.
    requireFormControl(this.configObject.subscriptionType).setValue(null);
    if (options.length === 0) {
      this.configObject.subscriptionType.invisible = true;
      requireFormControl(this.configObject.subscriptionType).clearValidators();
    } else {
      this.configObject.subscriptionType.invisible = false;
      requireFormControl(this.configObject.subscriptionType).setValidators(Validators.required);
    }
    requireFormControl(this.configObject.subscriptionType).updateValueAndValidity();
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): ConnectorApiKey {
    return this.copyFormToPrivateBusinessObject(new ConnectorApiKey(), this.connectorApiKey());
  }
}
