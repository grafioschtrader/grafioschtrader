import {Component, Input, OnInit} from '@angular/core';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {StockexchangeService} from '../../stockexchange/service/stockexchange.service';
import {AssetclassService} from '../../assetclass/service/assetclass.service';
import {TranslateService} from '@ngx-translate/core';
import {SecuritycurrencyEdit} from './securitycurrency.edit';
import {combineLatest, Observable, Subscription} from 'rxjs';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {FeedIdentifier, IFeedConnector} from './ifeed.connector';
import {Currencypair} from '../../entities/currencypair';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {CurrencypairService} from '../service/currencypair.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {HelpIds} from '../../lib/help/help.ids';
import {FormHelper} from '../../lib/dynamic-form/components/FormHelper';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SecurityDerived, SecurityEditSupport} from './security.edit.support';
import {AppSettings} from '../../shared/app.settings';
import {UDFMetadataHelper} from '../../lib/udfmeta/components/udf.metadata.helper';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {BaseSettings} from '../../lib/base.settings';


/**
 * Only on a new record is the source and target currency editable.
 */
@Component({
  selector: 'currencypair-edit',
  template: `
    <p-dialog header="{{'CURRENCYPAIR' | translate}}" [(visible)]="visibleEditCurrencypairDialog"
              [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
  standalone: false
})
export class CurrencypairEditComponent extends SecuritycurrencyEdit implements OnInit {

  // Input from parent component
  @Input() visibleEditCurrencypairDialog: boolean;

  existingCurrencypairs: Currencypair[];

  // Observer subscribe
  private fromCurrencyChangedSub: Subscription;

  constructor(private messageToastService: MessageToastService,
    private gpsGT: GlobalparameterGTService,
    private stockexchangeService: StockexchangeService,
    private assetclassService: AssetclassService,
    private currencypairService: CurrencypairService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));
    this.connectorPriceFieldConfig = SecurityEditSupport.getIntraHistoryFieldDefinition(
      SecurityDerived.Currencypair);

    this.config = [
      DynamicFieldHelper.createFieldSelectString('fromCurrency', 'CURRENCY_FROM', true,
        {fieldsetName: 'CURRENCY_BASE_DATA'}),
      DynamicFieldHelper.createFieldSelectString('toCurrency', 'CURRENCY_TO', true,
        {fieldsetName: 'CURRENCY_BASE_DATA'}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', BaseSettings.FID_MAX_LETTERS, false,
        {fieldsetName: 'CURRENCY_BASE_DATA'}),
      DynamicFieldHelper.createFieldInputStringHeqF('stockexchangeLink', 254, false,
        {fieldsetName: 'CURRENCY_BASE_DATA'}),
      ...this.connectorPriceFieldConfig,
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  valueChangedOnFromCurrency(): void {
    this.fromCurrencyChangedSub = this.configObject.fromCurrency.formControl.valueChanges.subscribe((data: string) => {
      const existingFromCurrencies: Currencypair[] = this.existingCurrencypairs.filter(currencypair => currencypair.fromCurrency === data);
      this.configObject.toCurrency.valueKeyHtmlOptions = this.configObject.fromCurrency.valueKeyHtmlOptions.filter(
        vKHO => existingFromCurrencies.filter(currencypair => currencypair.toCurrency === vKHO.key).length === 0);
    });
  }

  submit(value: { [name: string]: any }): void {
    const currencypair: Currencypair = new Currencypair();
    if (this.securityCurrencypairCallParam) {
      Object.assign(currencypair, this.securityCurrencypairCallParam);
    }
    UDFMetadataHelper.removeUDFPropertiesFromObject(currencypair);
    AuditHelper.copyProposeChangeEntityToEntityAfterEdit(this, currencypair, this.proposeChangeEntityWithEntity);
    this.dynamicForm.cleanMaskAndTransferValuesToBusinessObject(currencypair);

    this.currencypairService.update(currencypair).subscribe({
      next: newSecurity => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
          {i18nRecord: AppSettings.CURRENCYPAIR.toUpperCase()});
        this.closeDialog.emit(new ProcessedActionData(this.securityCurrencypairCallParam ? ProcessedAction.UPDATED
          : ProcessedAction.CREATED, newSecurity));
      }, error: () => this.configObject.submit.disabled = false
    });
  }

  override onHide(event): void {
    this.fromCurrencyChangedSub && this.fromCurrencyChangedSub.unsubscribe();
    super.onHide(event);
  }

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_WATCHLIST_CURRENCYPAIR);
  }

  protected loadHelperData(): void {
    this.hideVisibleFeedConnectorsFields(this.connectorPriceFieldConfig, false, FeedIdentifier.CURRENCY);
    const observableCurrencies: Observable<ValueKeyHtmlSelectOptions[]> = this.gpsGT.getCurrencies();
    const observableFeedConnectors: Observable<IFeedConnector[]> = this.currencypairService.getFeedConnectors();
    const observableAllCurrencypairs: Observable<Currencypair[]> = this.currencypairService.getAllCurrencypairs();
    combineLatest([observableCurrencies, observableFeedConnectors, observableAllCurrencypairs])
      .subscribe(([currencies, feedConnectors, allCurrencypairs]: [ValueKeyHtmlSelectOptions[],
        IFeedConnector[], Currencypair[]]) => {
        this.configObject.fromCurrency.valueKeyHtmlOptions = currencies;
        this.prepareFeedConnectors(feedConnectors, true);
        this.existingCurrencypairs = allCurrencypairs;
        this.removeByUpdateFormExstingCurrency();
        this.valueChangedOnFromCurrency();
        this.disableEnableInputForExisting(!!this.securityCurrencypairCallParam);
      });
  }

  private removeByUpdateFormExstingCurrency(): void {
    if (this.securityCurrencypairCallParam) {
      this.existingCurrencypairs = this.existingCurrencypairs.filter(currencypair =>
        currencypair.idSecuritycurrency !== this.securityCurrencypairCallParam.idSecuritycurrency);
    }
  }

  private disableEnableInputForExisting(disable: boolean): void {
    FormHelper.disableEnableFieldConfigs(disable, [this.configObject.fromCurrency, this.configObject.toCurrency]);
    this.prepareExistingSecuritycurrency(disable ? this.configObject.note : this.configObject.fromCurrency);
  }

}
