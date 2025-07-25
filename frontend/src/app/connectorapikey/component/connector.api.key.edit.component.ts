import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {ConnectorApiKey, SubscriptionTypeReadableName} from '../../entities/connector.api.key';
import {ImportTransactionPlatformService} from '../../imptranstemplate/service/import.transaction.platform.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {AppSettings} from '../../shared/app.settings';
import {ConnectorApiKeyService} from '../service/connector.api.key.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {Subscription} from 'rxjs';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {SubscriptionType} from '../../shared/types/subscription.type';

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
    standalone: false
})
export class ConnectorApiKeyEditComponent extends SimpleEntityEditBase<ConnectorApiKey> implements OnInit {
  @Input() callParam: CallParam;
  @Input() strn: { [id: string]: SubscriptionTypeReadableName };
  @Input() existingProviders: string[];

  private idProviderChangedSub: Subscription;

  constructor(private importTransactionPlatformService: ImportTransactionPlatformService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    connectorApiKeyService: ConnectorApiKeyService) {
    super(HelpIds.HELP_CONNECTOR_API_KEY, AppSettings.CONNECTOR_API_KEY.toUpperCase(), translateService, gps,
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
    ('readableName', this.strn, false, this.callParam.thisObject? []: this.existingProviders);
    this.valueChangedOnIdProvider();
    if (this.callParam.thisObject) {
      this.form.transferBusinessObjectToForm(this.callParam.thisObject);
      this.configObject.idProvider.formControl.disable();
    }
  }

  valueChangedOnIdProvider(): void {
    this.idProviderChangedSub = this.configObject.idProvider.formControl.valueChanges.subscribe(selection => {
      const subscriptionTypes = this.strn[selection].subscriptionTypes.map((st: string) => SubscriptionType[st]);
      this.configObject.subscriptionType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
        this.translateService, SubscriptionType, subscriptionTypes, false);
    });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): ConnectorApiKey {
    return this.copyFormToPrivateBusinessObject(new ConnectorApiKey(), <ConnectorApiKey>this.callParam.thisObject);
  }

  override onHide(event): void {
    this.idProviderChangedSub && this.idProviderChangedSub.unsubscribe();
    super.onHide(event);
  }
}
