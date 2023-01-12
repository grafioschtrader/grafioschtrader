import {Component, Input, OnInit} from '@angular/core';
import {Stockexchange} from '../../entities/stockexchange';
import {DataType} from '../../dynamic-form/models/data.type';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {StockexchangeService} from '../service/stockexchange.service';
import {AppHelper} from '../../shared/helper/app.helper';
import {HelpIds} from '../../shared/help/help.ids';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {DynamicFieldHelper, VALIDATION_SPECIAL} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {StockexchangeCallParam} from './stockexchange.call.param';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {combineLatest, Subscription} from 'rxjs';
import {Observable} from 'rxjs/internal/Observable';
import {Security} from '../../entities/security';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import * as moment from 'moment';
import {AppSettings} from '../../shared/app.settings';
import {GroupItem} from '../../dynamic-form/models/value.key.html.select.options';
import {StockexchangeMic} from '../model/stockexchange.base.data';
import {StockexchangeHelper} from './stockexchange.helper';

/**
 * Edit stockexchnage
 */
@Component({
  selector: 'stockexchange-edit',
  template: `
    <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class StockexchangeEditComponent extends SimpleEntityEditBase<Stockexchange> implements OnInit {

  @Input() callParam: StockexchangeCallParam;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;
  private readonly nameMaxLength = 32;
  private countriesAsKeyValue: { [cc: string]: string } = {};
  private onlyMainStockexchangeSubscribe: Subscription;
  private micSubscribe: Subscription;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              stockexchangeService: StockexchangeService,
              private securityService: SecurityService) {
    super(HelpIds.HELP_BASEDATA_STOCKEXCHANGE, AppSettings.STOCKEXCHANGE.toUpperCase(), translateService, gps,
      messageToastService, stockexchangeService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldCheckboxHeqF('onlyMainStockexchange',
        {defaultValue: true, disabled: !this.canAssignMic()}),
      DynamicFieldHelper.createFieldDropdownStringHeqF('mic', false,
        {groupItemUse: true, disabled: !this.canAssignMic()}),
      DynamicFieldHelper.createFieldInputStringHeqF('name', this.nameMaxLength, true, {minLength: 2}),
      DynamicFieldHelper.createFieldSelectStringHeqF('countryCode', true),
      DynamicFieldHelper.createFieldCheckboxHeqF('secondaryMarket', {defaultValue: true}),
      DynamicFieldHelper.createFieldCheckboxHeqF('noMarketValue'),
      DynamicFieldHelper.createFieldDAInputStringHeqF(DataType.TimeString, 'timeOpen', 8, true),
      DynamicFieldHelper.createFieldDAInputStringHeqF(DataType.TimeString, 'timeClose', 8, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('timeZone', true),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idIndexUpdCalendar', false),
      DynamicFieldHelper.createFieldInputStringVSHeqF('website', 128, false,
        [VALIDATION_SPECIAL.WEB_URL],),
      ...
        AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    if (this.callParam.stockexchange && !this.callParam.stockexchange.mic) {
      this.callParam.stockexchange.mic = '';
    }
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  private canAssignMic(): boolean {
    return this.callParam.stockexchange && (!this.callParam.stockexchange.mic || this.callParam.proposeChange)
      || !this.callParam.stockexchange;
  }

  protected override initialize(): void {
    const obserables: Observable<any>[] = [this.gps.getTimezones()];
    if (this.callParam.stockexchange) {
      obserables.push(this.getSecurityObservable());
    }

    combineLatest(obserables).subscribe(data => {
      this.countriesAsKeyValue = StockexchangeHelper.transform(this.callParam.countriesAsHtmlOptions);
      this.configObject.mic.groupItem = this.createMicOptions(true);
      this.configObject.timeZone.valueKeyHtmlOptions = data[0];
      this.configObject.countryCode.valueKeyHtmlOptions = this.callParam.countriesAsHtmlOptions;
      this.form.setDefaultValuesAndEnableSubmit();
      AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.gps,
        this.callParam.stockexchange, this.form, this.configObject, this.proposeChangeEntityWithEntity);
      FormHelper.disableEnableFieldConfigs(this.callParam.hasSecurity, [this.configObject.noMarketValue,
        this.configObject.timeZone]);
      this.disableEnableCountry();
      if (data.length > 1) {
        this.configObject.idIndexUpdCalendar.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
          'idSecuritycurrency', 'name', data[1], true);
      }
      if (this.canAssignMic()) {
        this.valueChangedOnOnlyMainStockexchange();
        this.valueChangedOnMic();
        (<any>this.configObject.mic.elementRef).accessibleViewChild.nativeElement.focus();
      } else {
        this.configObject.name.elementRef.nativeElement.focus();
      }
    });
  }

  private valueChangedOnOnlyMainStockexchange(): void {
    this.onlyMainStockexchangeSubscribe = this.configObject.onlyMainStockexchange.formControl.valueChanges.subscribe((oms: boolean) => {
      this.configObject.mic.groupItem = this.createMicOptions(oms);
    });
  }

  private valueChangedOnMic(): void {
    this.micSubscribe = this.configObject.mic.formControl.valueChanges.subscribe(mic => {
      const sm: StockexchangeMic = this.callParam.stockexchangeMics.find(smf => smf.mic === mic);
      this.configObject.name.formControl.setValue(sm.name.toLowerCase().substring(0, this.nameMaxLength));
      this.configObject.countryCode.formControl.setValue(sm.countryCode);
      this.configObject.website.formControl.setValue(sm.website);
      this.configObject.timeZone.formControl.setValue(sm.timeZone);
      this.disableEnableCountry();
    });
  }

  private disableEnableCountry(): void {
    FormHelper.disableEnableFieldConfigs(this.configObject.mic.formControl.value.length > 0, [this.configObject.countryCode]);
  }

  private createMicOptions(onlyMainStockexchange: boolean): GroupItem[] {
    const countryMap: { [cc: string]: GroupItem } = {};
    const emptyEntry: StockexchangeMic = {mic: '', name: '', city: '', countryCode: 'xx'};
    this.createGreopAndFristEntry(countryMap, emptyEntry, '-----');

    this.callParam.stockexchangeMics.filter(sm => (this.canAssignMic() && (onlyMainStockexchange && sm.timeZone || !onlyMainStockexchange)
        && !this.callParam.existingMic.has(sm.mic))
      || ((!this.canAssignMic() || this.callParam.proposeChange) && this.callParam.stockexchange.mic === sm.mic)).forEach(sm => {
      const existingGroup = countryMap[sm.countryCode];
      if (existingGroup) {
        this.addEntry(existingGroup, sm);
      } else {
        const country = this.countriesAsKeyValue[sm.countryCode];
        if (country) {
          this.createGreopAndFristEntry(countryMap, sm, country);
        }
      }
    });
    return Object.values(countryMap).sort((a, b) => a.optionsText.localeCompare(b.optionsText));
  }

  private createGreopAndFristEntry(countryMap: { [cc: string]: GroupItem }, sm: StockexchangeMic, country: string): void {
    const gp = new GroupItem(null, null, country, 'fi fi-' + sm.countryCode.toLowerCase());
    countryMap[sm.countryCode] = gp;
    gp.children = [];
    this.addEntry(gp, sm);
  }

  private addEntry(groupItem: GroupItem, sm: StockexchangeMic): void {
    groupItem.children.splice(this.sortedIndex(groupItem.children, sm.name), 0,
      new GroupItem(sm.mic, sm.mic, sm.name + ', ' + sm.city + ', ' + sm.mic, null));
  }


  private sortedIndex(groupItem: GroupItem[], value: string) {
    let low = 0;
    let high = groupItem.length;

    while (low < high) {
      const mid = (low + high) >>> 1; //eslint-disable-line no-bitwise
      if (groupItem[mid].optionsText < value) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }
    return low;
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Stockexchange {
    const stockexchange: Stockexchange = new Stockexchange();
    this.copyFormToPublicBusinessObject(stockexchange, this.callParam.stockexchange, this.proposeChangeEntityWithEntity);
    stockexchange.mic = (stockexchange.mic.length === 0) ? null : stockexchange.mic;
    return stockexchange;
  }

  private getSecurityObservable(): Observable<Security[]> {
    const securitycurrencySearch = new SecuritycurrencySearch();
    securitycurrencySearch.assetclassType = AssetclassType[AssetclassType.EQUITIES];
    securitycurrencySearch.specialInvestmentInstruments = SpecialInvestmentInstruments[SpecialInvestmentInstruments.NON_INVESTABLE_INDICES];
    securitycurrencySearch.activeDate = moment().format(AppSettings.FORMAT_DATE_SHORT_US);
    securitycurrencySearch.stockexchangeCounrtyCode = this.callParam.stockexchange.countryCode;
    return this.securityService.searchByCriteria(securitycurrencySearch);
  }

  override onHide(event): void {
    this.onlyMainStockexchangeSubscribe && this.onlyMainStockexchangeSubscribe.unsubscribe();
    this.micSubscribe && this.micSubscribe.unsubscribe();
    super.onHide(event);
  }

}
