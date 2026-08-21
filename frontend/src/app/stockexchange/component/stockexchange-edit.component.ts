import {Component, Input, OnInit} from '@angular/core';
import {Stockexchange} from '../../entities/stockexchange';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {StockexchangeService} from '../service/stockexchange.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {HelpIds} from '../../lib/help/help.ids';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {StockexchangeCallParam} from './stockexchange.call.param';
import {FormHelper} from '../../lib/dynamic-form/components/FormHelper';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {SecuritycurrencySearch} from '../../entities/search/securitycurrency.search';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {combineLatest, Subscription} from 'rxjs';
import {Observable} from 'rxjs/internal/Observable';
import {Security} from '../../entities/security';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import moment from 'moment';
import {AppSettings} from '../../shared/app.settings';
import {GroupItem, ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {StockexchangeMic} from '../model/stockexchange.base.data';
import {StockexchangeHelper} from './stockexchange.helper';
import {BaseSettings} from '../../lib/base.settings';
import {DialogModule} from '@openng/optimus-ui/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {TradingCalendarRuleSetService} from '../service/trading.calendar.rule.set.service';

/**
 * Edit stockexchnage
 */
@Component({
  selector: 'stockexchange-edit',
  template: `
    <p-dialog header="{{i18nRecord | translate}}" [visible]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
  standalone: true,
  imports: [
    DialogModule,
    DynamicFormModule,
    TranslateModule
  ]
})
export class StockexchangeEditComponent extends SimpleEntityEditBase<Stockexchange> implements OnInit {

  @Input() callParam: StockexchangeCallParam;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;
  private readonly nameMaxLength = 32;
  private countriesAsKeyValue: { [cc: string]: string } = {};
  private onlyMainStockexchangeSubscribe: Subscription;
  private micSubscribe: Subscription;
  private countryCodeSubscribe: Subscription;

  private ruleSetOptions: ValueKeyHtmlSelectOptions[] = [];
  private ruleSetIdByMic: { [mic: string]: number } = {};
  private calendarSourceSubscriptions: Subscription[] = [];

  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    stockexchangeService: StockexchangeService,
    private tradingCalendarRuleSetService: TradingCalendarRuleSetService,
    private securityService: SecurityService) {
    super(HelpIds.HELP_BASEDATA_STOCKEXCHANGE, AppHelper.toUpperCaseWithUnderscore(AppSettings.STOCKEXCHANGE), translateService, gps,
      messageToastService, stockexchangeService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldCheckboxHeqF('onlyMainStockexchange',
        {defaultValue: true, disabled: !this.canAssignMic()}),
      DynamicFieldHelper.createFieldDropdownStringHeqF('mic', false,
        {groupItemUseOrLoading: true, disabled: !this.canAssignMic(), filter: true}),
      DynamicFieldHelper.createFieldInputStringHeqF('name', this.nameMaxLength, true, {minLength: 2}),
      DynamicFieldHelper.createFieldSelectStringHeqF('countryCode', true),
      DynamicFieldHelper.createFieldCheckboxHeqF('secondaryMarket', {defaultValue: true}),
      DynamicFieldHelper.createFieldCheckboxHeqF('noMarketValue'),
      DynamicFieldHelper.createFieldDAInputStringHeqF(DataType.TimeString, 'timeOpen', 8, true),
      DynamicFieldHelper.createFieldDAInputStringHeqF(DataType.TimeString, 'timeClose', 8, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('timeZone', true),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idTradingCalendarRuleSet', false),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idIndexUpdCalendar', false),
      DynamicFieldHelper.createFieldInputWebUrlHeqF('website',
        this.gps.getFieldSize(AppSettings.FIELD_SIZE_MAX_Stockexchange_Website), false),
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
    const observables: Observable<any>[] = [this.gps.getTimezones(),
      this.tradingCalendarRuleSetService.getRuleSetOptions(),
      this.tradingCalendarRuleSetService.getRuleSetIdByMic()];
    const securitiesIndex = observables.length;
    if (this.callParam.stockexchange) {
      observables.push(this.getSecurityObservable(this.callParam.stockexchange.countryCode));
    }
    combineLatest(observables).subscribe(data => {
      this.countriesAsKeyValue = StockexchangeHelper.transform(this.callParam.countriesAsHtmlOptions);
      this.configObject.mic.groupItem = this.createMicOptions(true);
      this.configObject.timeZone.valueKeyHtmlOptions = data[0] as ValueKeyHtmlSelectOptions[];
      this.ruleSetOptions = SelectOptionsHelper.prependEmptyOption(data[1] as ValueKeyHtmlSelectOptions[]);
      this.ruleSetIdByMic = data[2] as { [mic: string]: number };
      this.configObject.idTradingCalendarRuleSet.valueKeyHtmlOptions = this.ruleSetOptions;
      this.configObject.countryCode.valueKeyHtmlOptions = this.callParam.countriesAsHtmlOptions;
      this.form.setDefaultValuesAndEnableSubmit();
      AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.gps,
        this.callParam.stockexchange, this.form, this.configObject, this.proposeChangeEntityWithEntity);
      FormHelper.disableEnableFieldConfigs(this.callParam.hasSecurity, [this.configObject.noMarketValue,
        this.configObject.timeZone]);
      this.disableEnableCountry();
      if (data.length > securitiesIndex) {
        this.configObject.idIndexUpdCalendar.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
          'idSecuritycurrency', 'name', data[securitiesIndex] as Security[], true);
      }
      this.valueChangedOnCountryCode();
      this.watchCalendarSource();
      this.applyCalendarSourceExclusion();
      if (this.canAssignMic()) {
        this.valueChangedOnOnlyMainStockexchange();
        this.valueChangedOnMic();
        setTimeout(() => (<any>this.configObject.mic.elementRef)?.accessibleViewChild?.nativeElement?.focus());
      } else {
        setTimeout(() => this.configObject.name.elementRef?.nativeElement?.focus());
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
      // The MIC drives the defaults of this dialog, so it also decides the calendar source: the rule set written for
      // that MIC is preselected, and a MIC without one leaves the field empty rather than keeping the rule set of the
      // MIC chosen before. Setting it disables the index, which applyCalendarSourceExclusion takes care of.
      this.configObject.idTradingCalendarRuleSet.formControl.setValue(this.ruleSetIdByMic[mic] ?? null);
    });
  }

  /**
   * The trading calendar is updated either from the quotes of a reference index or from a rule set. Selecting one of
   * the two therefore clears and disables the other, mirroring the check the backend enforces on save.
   */
  private watchCalendarSource(): void {
    this.calendarSourceSubscriptions.push(
      this.configObject.idIndexUpdCalendar.formControl.valueChanges.subscribe(() =>
        this.applyCalendarSourceExclusion()),
      this.configObject.idTradingCalendarRuleSet.formControl.valueChanges.subscribe(() =>
        this.applyCalendarSourceExclusion()));
  }

  /**
   * Clears and disables whichever of the two calendar sources was not chosen. The rule set wins if both should ever
   * carry a value, which the backend prevents but an older record could still show.
   *
   * Every change here is made with {@code emitEvent: false}: Angular fires valueChanges on enable() and disable() as
   * well as on setValue(), so emitting would make the two subscriptions above trigger each other endlessly and freeze
   * the dialog. Clearing the value matters because a disabled control is still copied to the business object on save.
   */
  private applyCalendarSourceExclusion(): void {
    const ruleSetSelected = !!this.configObject.idTradingCalendarRuleSet.formControl.value;
    const indexSelected = !ruleSetSelected && !!this.configObject.idIndexUpdCalendar.formControl.value;
    this.setCalendarSourceState(this.configObject.idIndexUpdCalendar, ruleSetSelected);
    this.setCalendarSourceState(this.configObject.idTradingCalendarRuleSet, indexSelected);
  }

  private setCalendarSourceState(fieldConfig: FieldConfig, disable: boolean): void {
    if (disable) {
      if (fieldConfig.formControl.value != null) {
        fieldConfig.formControl.setValue(null, {emitEvent: false});
      }
      if (fieldConfig.formControl.enabled) {
        fieldConfig.formControl.disable({emitEvent: false});
      }
    } else if (fieldConfig.formControl.disabled) {
      fieldConfig.formControl.enable({emitEvent: false});
    }
  }

  private valueChangedOnCountryCode(): void {
    this.countryCodeSubscribe = this.configObject.countryCode.formControl.valueChanges.subscribe(countryCode => {
      this.configObject.idIndexUpdCalendar.formControl.setValue(null);
      if (countryCode) {
        this.getSecurityObservable(countryCode).subscribe(securities => {
          this.configObject.idIndexUpdCalendar.valueKeyHtmlOptions =
            SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
              'idSecuritycurrency', 'name', securities, true);
        });
      } else {
        this.configObject.idIndexUpdCalendar.valueKeyHtmlOptions = [new ValueKeyHtmlSelectOptions('', '')];
      }
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

  private createGreopAndFristEntry(countryMap: {
    [cc: string]: GroupItem
  }, sm: StockexchangeMic, country: string): void {
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

  private getSecurityObservable(countryCode: string): Observable<Security[]> {
    const securitycurrencySearch = new SecuritycurrencySearch();
    securitycurrencySearch.assetclassType = AssetclassType[AssetclassType.EQUITIES];
    securitycurrencySearch.specialInvestmentInstruments = SpecialInvestmentInstruments[SpecialInvestmentInstruments.NON_INVESTABLE_INDICES];
    securitycurrencySearch.activeDate = moment().format(BaseSettings.FORMAT_DATE_SHORT_US);
    securitycurrencySearch.stockexchangeCountryCode = countryCode;
    return this.securityService.searchByCriteria(securitycurrencySearch);
  }

  override onHide(event): void {
    this.onlyMainStockexchangeSubscribe && this.onlyMainStockexchangeSubscribe.unsubscribe();
    this.micSubscribe && this.micSubscribe.unsubscribe();
    this.countryCodeSubscribe && this.countryCodeSubscribe.unsubscribe();
    this.calendarSourceSubscriptions.forEach(subscription => subscription.unsubscribe());
    this.calendarSourceSubscriptions = [];
    super.onHide(event);
  }

}
