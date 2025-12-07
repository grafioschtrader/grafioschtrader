import {Component, Input, OnInit} from '@angular/core';

import {SimpleEntityEditBase} from '../../edit/simple.entity.edit.base';
import {ConnectorApiKey, SubscriptionTypeReadableName} from '../types/connector.api.key';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../dynamic-form/dynamic-form.module';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {HelpIds} from '../../help/help.ids';
import {ConnectorApiKeyService} from '../service/connector.api.key.service';
import {AppHelper} from '../../helper/app.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {Subscription} from 'rxjs';
import {BaseSettings} from '../../base.settings';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';

/**
 * Create or edit a key for the connectors API. It is intended for the admin only.
 */
@Component({
    selector: 'connector-api-key-edit',
    template: `
    <p-dialog header="{{'CONNECTOR_API_KEY' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
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
  @Input() subscriptionTypeOptionsMap: { [providerId: string]: ValueKeyHtmlSelectOptions[] };

  private idProviderChangedSub: Subscription;

  constructor( translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    connectorApiKeyService: ConnectorApiKeyService) {
    super(HelpIds.HELP_CONNECTOR_API_KEY, BaseSettings.CONNECTOR_API_KEY.toUpperCase(), translateService, gps,
      messageToastService, connectorApiKeyService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('idProvider', true),
      DynamicFieldHelper.createFieldSelectStringHeqF('subscriptionType', true),
      DynamicFieldHelper.createFieldInputStringHeqF('apiKey', 64, true, {minLength: 10}),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.configObject.idProvider.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromObject
    ('readableName', this.strn, false, this.connectorApiKey? []: this.existingProviders);
    this.valueChangedOnIdProvider();
    if (this.connectorApiKey) {
      this.form.transferBusinessObjectToForm(this.connectorApiKey);
      this.configObject.idProvider.formControl.disable();
    }
  }

  valueChangedOnIdProvider(): void {
    this.idProviderChangedSub = this.configObject.idProvider.formControl.valueChanges.subscribe(selection => {
      this.configObject.subscriptionType.valueKeyHtmlOptions = this.subscriptionTypeOptionsMap[selection];
    });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): ConnectorApiKey {
    return this.copyFormToPrivateBusinessObject(new ConnectorApiKey(), this.connectorApiKey);
  }

  override onHide(event): void {
    this.idProviderChangedSub && this.idProviderChangedSub.unsubscribe();
    super.onHide(event);
  }
}
