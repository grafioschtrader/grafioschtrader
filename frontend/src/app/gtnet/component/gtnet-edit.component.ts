import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {
  AcceptRequestTypes,
  GTNet,
  GTNetCallParam,
  GTNetEntity,
  GTNetExchangeKindType,
  GTNetServerOnlineStatusTypes,
  GTNetServerStateTypes
} from '../model/gtnet';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../lib/help/help.ids';
import {AppSettings} from '../../shared/app.settings';
import {GTNetService} from '../service/gtnet.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {Subscription} from 'rxjs';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';

/**
 * Add ar modify a GTNet entity.
 * Almost all attributes can be changed when entering data for your own server. Only certain values can be changed during the update.
 * Other changes must be made using GTNetMessage. Why GTNetMessage? So that this change is communicated to the other instances via GTNet.
 * Only domainRemoteName can be entered for a remote instance. The other values are set by the remote instance via GTNetMessage.
 */
@Component({
  selector: 'gtnet-edit',
  standalone: true,
  imports: [
    DialogModule,
    DynamicFormComponent,
    TranslateModule
  ],
  template: `
    <p-dialog header="{{'GT_NET_NET_AND_MESSAGE' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
})
export class GTNetEditComponent extends SimpleEntityEditBase<GTNet> implements OnInit {
  @Input() callParam: GTNetCallParam;
  private readonly BASE_SETTING = 'BASE_SETTING';
  private readonly HISTORICAL_PRICE = 'HISTORICAL_PRICE';
  private readonly LAST_PRICE = 'LAST_PRICE';
  private domainRemoteNameSubscribe: Subscription;


  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    gtNetService: GTNetService) {
    super(HelpIds.HELP_GT_NET, AppSettings.GT_NET.toUpperCase(), translateService, gps,
      messageToastService, gtNetService);
  }

  ngOnInit(): void {
    const isUpdate = this.callParam.gtNet && !!this.callParam?.gtNet.idGtNet;
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('domainRemoteName', 128, false, {
        fieldsetName: this.BASE_SETTING,
        disabled: isUpdate
      }),
      ...this.editOwnInstance(isUpdate),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  private editOwnInstance(isUpdate: boolean): FieldConfig[] {
    return this.callParam.isMyEntry ? [
      DynamicFieldHelper.createFieldSelectStringHeqF('timeZone', true, {fieldsetName: this.BASE_SETTING}),
      DynamicFieldHelper.createFieldCheckboxHeqF('spreadCapability', {
        defaultValue: true,
        fieldsetName: this.BASE_SETTING
      }),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'dailyRequestLimit', true, 0, 9999,
        {defaultValue: 1000, fieldsetName: this.BASE_SETTING}),
      DynamicFieldHelper.createFieldCheckboxHeqF('serverBusy', {fieldsetName: this.BASE_SETTING}),
      DynamicFieldHelper.createFieldCheckboxHeqF('allowServerCreation', {fieldsetName: this.BASE_SETTING}),
      DynamicFieldHelper.createFieldSelectStringHeqF('serverOnline', true,
        {
          defaultValue: GTNetServerOnlineStatusTypes[GTNetServerOnlineStatusTypes.SOS_UNKNOWN],
          fieldsetName: this.BASE_SETTING,
          disabled: isUpdate
        }),

      DynamicFieldHelper.createFieldSelectStringHeqF('historicalPriceRequest', true,
        {
          defaultValue: AcceptRequestTypes[AcceptRequestTypes.AC_OPEN],
          fieldsetName: this.HISTORICAL_PRICE,
          disabled: isUpdate
        }),
      DynamicFieldHelper.createFieldSelectStringHeqF('historicalPriceServerState', true,
        {
          defaultValue: GTNetServerStateTypes[GTNetServerStateTypes.SS_OPEN],
          fieldsetName: this.HISTORICAL_PRICE,
          disabled: isUpdate
        }),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'historicalMaxLimit', true, 10, 999,
        {defaultValue: 300, fieldsetName: this.HISTORICAL_PRICE}),
      DynamicFieldHelper.createFieldSelectStringHeqF('acceptLastpriceRequest', true,
        {
          defaultValue: AcceptRequestTypes[AcceptRequestTypes.AC_OPEN],
          fieldsetName: this.LAST_PRICE,
          disabled: isUpdate
        }),
      DynamicFieldHelper.createFieldSelectStringHeqF('lastpriceServerState', true,
        {
          defaultValue: GTNetServerStateTypes[GTNetServerStateTypes.SS_NONE],
          fieldsetName: this.LAST_PRICE,
          disabled: isUpdate
        }),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.NumericInteger, 'lastpriceMaxLimit', true, 10, 999,
        {defaultValue: 300, fieldsetName: this.LAST_PRICE})
    ] : [];
  }

  protected override initialize(): void {
    if (this.callParam.isMyEntry) {
      this.gps.getTimezones().subscribe((timezones: ValueKeyHtmlSelectOptions[]) => {
        this.configObject.timeZone.valueKeyHtmlOptions = timezones;
        this.configObject.historicalPriceServerState.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
          GTNetServerStateTypes);
        this.configObject.lastpriceServerState.valueKeyHtmlOptions = this.configObject.historicalPriceServerState.valueKeyHtmlOptions;
        this.configObject.serverOnline.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
          GTNetServerOnlineStatusTypes);
        // All AcceptRequestTypes options are available for both historical and last prices
        this.configObject.historicalPriceRequest.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
          this.translateService, AcceptRequestTypes);
        this.configObject.acceptLastpriceRequest.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
          this.translateService, AcceptRequestTypes);
        const gtNet = this.callParam.gtNet ?? new GTNet();
        this.form.transferBusinessObjectToForm(gtNet);
      });
    }
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): GTNet {
    const gtNet: GTNet = new GTNet();
    Object.assign(gtNet, this.callParam.gtNet);

    // Only set domainRemoteName from form value during create (field is disabled during update)
    if (value['domainRemoteName'] !== undefined) {
      gtNet.domainRemoteName = value['domainRemoteName'];
    }
    gtNet.timeZone = this.gps.getStandardTimeZone();
    if (this.callParam.isMyEntry) {
      gtNet.timeZone = value['timeZone'];
      gtNet.spreadCapability = value['spreadCapability'];
      gtNet.dailyRequestLimit = value['dailyRequestLimit'];
      gtNet.serverBusy = value['serverBusy'];
      gtNet.allowServerCreation = value['allowServerCreation'];
      gtNet.serverOnline = GTNetServerOnlineStatusTypes[value['serverOnline']] as any;

      const gtNetEntities: GTNetEntity[] = [];
      const existingEntities = this.callParam.gtNet?.gtNetEntities || [];

      const existingHistorical = existingEntities.find(e =>
        e.entityKind === GTNetExchangeKindType.HISTORICAL_PRICES ||
        e.entityKind === GTNetExchangeKindType[GTNetExchangeKindType.HISTORICAL_PRICES]);
      const historicalEntity: GTNetEntity = {
        idGtNetEntity: existingHistorical?.idGtNetEntity,
        idGtNet: gtNet.idGtNet,
        entityKind: GTNetExchangeKindType.HISTORICAL_PRICES,
        // Use form value if available, otherwise preserve existing (fields are disabled during update)
        serverState: value['historicalPriceServerState'] !== undefined
          ? GTNetServerStateTypes[value['historicalPriceServerState']] as any
          : existingHistorical?.serverState,
        acceptRequest: value['historicalPriceRequest'] !== undefined
          ? AcceptRequestTypes[value['historicalPriceRequest']] as any
          : existingHistorical?.acceptRequest,
        maxLimit: value['historicalMaxLimit'],
      };
      gtNetEntities.push(historicalEntity);

      const existingLastPrice = existingEntities.find(e =>
        e.entityKind === GTNetExchangeKindType.LAST_PRICE ||
        e.entityKind === GTNetExchangeKindType[GTNetExchangeKindType.LAST_PRICE]);
      const lastPriceEntity: GTNetEntity = {
        idGtNetEntity: existingLastPrice?.idGtNetEntity,
        idGtNet: gtNet.idGtNet,
        entityKind: GTNetExchangeKindType.LAST_PRICE,
        // Use form value if available, otherwise preserve existing (fields are disabled during update)
        serverState: value['lastpriceServerState'] !== undefined
          ? GTNetServerStateTypes[value['lastpriceServerState']] as any
          : existingLastPrice?.serverState,
        acceptRequest: value['acceptLastpriceRequest'] !== undefined
          ? AcceptRequestTypes[value['acceptLastpriceRequest']] as any
          : existingLastPrice?.acceptRequest,
        maxLimit: value['lastpriceMaxLimit'],
      };

      gtNetEntities.push(lastPriceEntity);
      gtNet.gtNetEntities = gtNetEntities;
    }
    return gtNet;
  }

  override onHide(event): void {
    this.domainRemoteNameSubscribe && this.domainRemoteNameSubscribe.unsubscribe();
    super.onHide(event);
  }


}
