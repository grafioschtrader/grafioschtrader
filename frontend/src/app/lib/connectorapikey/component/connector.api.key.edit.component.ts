import { Component, Input, OnInit } from '@angular/core';

import { SimpleEntityEditBase } from '../../edit/simple.entity.edit.base';
import { ConnectorApiKey, SubscriptionTypeReadableName } from '../types/connector.api.key';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DynamicFormModule } from '../../dynamic-form/dynamic-form.module';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { MessageToastService } from '../../message/message.toast.service';
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
      (submitBt)="submit($event)">
    </dynamic-form>
  </p-dialog>`,
  standalone: true,
  imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class ConnectorApiKeyEditComponent extends SimpleEntityEditBase<ConnectorApiKey> implements OnInit {
  @Input() connectorApiKey: ConnectorApiKey;
  @Input() strn: { [id: string]: SubscriptionTypeReadableName };
  @Input() existingProviders: string[];
  @Input() subscriptionTypeOptionsMap: {
    [providerId: string]: ValueKeyHtmlSelectOptions[];
  };

  constructor(
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    connectorApiKeyService: ConnectorApiKeyService
  ) {
    super(
      HelpIds.HELP_CONNECTOR_API_KEY,
      AppHelper.toUpperCaseWithUnderscore(BaseSettings.CONNECTOR_API_KEY),
      translateService,
      gps,
      messageToastService,
      connectorApiKeyService
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
      this.strn,
      false,
      this.connectorApiKey ? [] : this.existingProviders
    );
    this.form().setDefaultValuesAndEnableSubmit();
    this.updateSubscriptionTypeVisibility(this.connectorApiKey?.idProvider);
    if (this.connectorApiKey) {
      this.form().transferBusinessObjectToForm(this.connectorApiKey);
      this.configObject.idProvider.formControl.disable();
    } else {
      this.configObject.idProvider.formControl.enable();
    }
    this.valueChangedOnIdProvider();
  }

  valueChangedOnIdProvider(): void {
    this.configObject.idProvider.formControl.valueChanges
      .pipe(this.untilDialogClosed())
      .subscribe((selection) => this.updateSubscriptionTypeVisibility(selection));
  }

  private updateSubscriptionTypeVisibility(providerId: string): void {
    const options = this.subscriptionTypeOptionsMap[providerId] ?? [];
    this.configObject.subscriptionType.valueKeyHtmlOptions = options;
    // A subscription belongs to its provider; switching providers must require a fresh selection.
    this.configObject.subscriptionType.formControl.setValue(null);
    if (options.length === 0) {
      this.configObject.subscriptionType.invisible = true;
      this.configObject.subscriptionType.formControl.clearValidators();
    } else {
      this.configObject.subscriptionType.invisible = false;
      this.configObject.subscriptionType.formControl.setValidators(Validators.required);
    }
    this.configObject.subscriptionType.formControl.updateValueAndValidity();
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): ConnectorApiKey {
    return this.copyFormToPrivateBusinessObject(new ConnectorApiKey(), this.connectorApiKey);
  }
}
