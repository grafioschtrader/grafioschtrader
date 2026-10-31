import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { afterNextRender, Component, inject, Injector, OnInit, input } from '@angular/core';
import { Stockexchange } from '../../entities/stockexchange';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateModule } from '@ngx-translate/core';
import { StockexchangeService } from '../service/stockexchange.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { AuditHelper } from '../../lib/helper/audit.helper';
import type { ProposeChangeEntityWithEntity } from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { StockexchangeCallParam } from './stockexchange.call.param';
import { FormHelper } from '../../lib/dynamic-form/components/FormHelper';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { AssetclassType } from '../../shared/types/assetclass.type';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';
import { distinctUntilChanged, forkJoin, Observable, of, startWith, switchMap } from 'rxjs';
import { Security } from '../../entities/security';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import moment from 'moment';
import { AppSettings } from '../../shared/app.settings';
import { GroupItem, ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { StockexchangeMic } from '../model/stockexchange.base.data';
import { StockexchangeHelper } from './stockexchange.helper';
import { BaseSettings } from '../../lib/base.settings';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { TradingCalendarRuleSetService } from '../service/trading.calendar.rule.set.service';

/**
 * Edits a stock exchange and its calendar source, with country-dependent reference indices.
 */
@Component({
  selector: 'stockexchange-edit',
  template: ` <p-dialog
    header="{{ i18nRecord | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '500px' }"
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
export class StockexchangeEditComponent extends SimpleEntityEditBase<Stockexchange> implements OnInit {
  private tradingCalendarRuleSetService: TradingCalendarRuleSetService = inject(TradingCalendarRuleSetService);
  private securityService: SecurityService = inject(SecurityService);

  readonly callParam = input.required<StockexchangeCallParam>();
  readonly proposeChangeEntityWithEntity = input<ProposeChangeEntityWithEntity>();
  private readonly nameMaxLength = 32;
  private countriesAsKeyValue: { [cc: string]: string } = {};
  private ruleSetIdByMic: { [mic: string]: number } = {};
  private readonly injector = inject(Injector);

  constructor() {
    super(
      HelpIds.HELP_BASEDATA_STOCKEXCHANGE,
      AppHelper.toUpperCaseWithUnderscore(AppSettings.STOCKEXCHANGE),
      inject(StockexchangeService)
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldCheckboxHeqF('onlyMainStockexchange', {
        defaultValue: true,
        disabled: !this.canAssignMic()
      }),
      DynamicFieldHelper.createFieldDropdownStringHeqF('mic', false, {
        groupItemUseOrLoading: true,
        disabled: !this.canAssignMic(),
        filter: true
      }),
      DynamicFieldHelper.createFieldInputStringHeqF('name', this.nameMaxLength, true, { minLength: 2 }),
      DynamicFieldHelper.createFieldSelectStringHeqF('countryCode', true),
      DynamicFieldHelper.createFieldCheckboxHeqF('secondaryMarket', {
        defaultValue: true
      }),
      DynamicFieldHelper.createFieldCheckboxHeqF('noMarketValue'),
      DynamicFieldHelper.createFieldDAInputStringHeqF(DataType.TimeString, 'timeOpen', 8, true),
      DynamicFieldHelper.createFieldDAInputStringHeqF(DataType.TimeString, 'timeClose', 8, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('timeZone', true),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idTradingCalendarRuleSet', false),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idIndexUpdCalendar', false),
      DynamicFieldHelper.createFieldInputWebUrlHeqF(
        'website',
        this.gps.getFieldSize(AppSettings.FIELD_SIZE_MAX_Stockexchange_Website),
        false
      ),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    const callParam = this.callParam();
    if (callParam.stockexchange && !callParam.stockexchange.mic) {
      callParam.stockexchange.mic = '';
    }
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  private canAssignMic(): boolean {
    const callParam = this.callParam();
    return (
      (callParam.stockexchange && (!callParam.stockexchange.mic || callParam.proposeChange)) || !callParam.stockexchange
    );
  }

  protected override initialize(): void {
    this.countriesAsKeyValue = StockexchangeHelper.transform(this.callParam().countriesAsHtmlOptions);
    this.configObject.mic.groupItem = this.createMicOptions(true);
    this.configObject.countryCode.valueKeyHtmlOptions = this.callParam().countriesAsHtmlOptions;
    this.form().setDefaultValuesAndEnableSubmit();
    const callParam = this.callParam();
    AuditHelper.transferToFormAndChangeButtonForProposaleEdit(
      this.translateService,
      this.gps,
      callParam.stockexchange,
      this.form(),
      this.configObject,
      this.proposeChangeEntityWithEntity()
    );
    FormHelper.disableEnableFieldConfigs(callParam.hasSecurity, [
      this.configObject.noMarketValue,
      this.configObject.timeZone
    ]);
    this.disableEnableCountry();
    this.valueChangedOnCountryCode();
    this.watchCalendarSource();
    this.applyCalendarSourceExclusion();
    if (this.canAssignMic()) {
      this.valueChangedOnOnlyMainStockexchange();
    }
    this.loadOptions();
  }

  /** Loads the calendar and time-zone choices once per session, preserving edits made while they are pending. */
  private loadOptions(): void {
    // A MIC needs its rule-set mapping before it can supply defaults. Loading options never resets the form.
    requireFormControl(this.configObject.mic).disable();
    this.configObject.submit.disabled = true;
    forkJoin({
      timezones: this.gps.getTimezones(),
      ruleSets: this.tradingCalendarRuleSetService.getRuleSetOptions(),
      mapping: this.tradingCalendarRuleSetService.getRuleSetIdByMic()
    })
      .pipe(this.untilDialogClosed())
      .subscribe(({ timezones, ruleSets, mapping }) => {
        this.configObject.timeZone.valueKeyHtmlOptions = timezones;
        this.configObject.idTradingCalendarRuleSet.valueKeyHtmlOptions = [
          new ValueKeyHtmlSelectOptions('', ''),
          ...ruleSets
        ];
        this.ruleSetIdByMic = mapping;
        this.configObject.submit.disabled = false;
        if (this.canAssignMic()) {
          requireFormControl(this.configObject.mic).enable();
          this.valueChangedOnMic();
        }
        afterNextRender(
          () => {
            if (this.canAssignMic()) {
              this.configObject.mic.baseInputComponent?.focus();
            } else {
              this.configObject.name.baseInputComponent?.focus();
            }
          },
          { injector: this.injector }
        );
      });
  }

  private valueChangedOnOnlyMainStockexchange(): void {
    requireFormControl(this.configObject.onlyMainStockexchange)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((oms: boolean) => {
        this.configObject.mic.groupItem = this.createMicOptions(oms);
      });
  }

  private valueChangedOnMic(): void {
    requireFormControl(this.configObject.mic)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((mic) => {
        const sm = this.callParam().stockexchangeMics.find((smf) => smf.mic === mic);
        if (sm) {
          requireFormControl(this.configObject.name).setValue(sm.name.toLowerCase().substring(0, this.nameMaxLength));
          requireFormControl(this.configObject.countryCode).setValue(sm.countryCode);
          requireFormControl(this.configObject.website).setValue(sm.website);
          requireFormControl(this.configObject.timeZone).setValue(sm.timeZone);
        }
        this.disableEnableCountry();
        // The MIC drives the defaults of this dialog, so it also decides the calendar source: the rule set written for
        // that MIC is preselected, and a MIC without one leaves the field empty rather than keeping the rule set of the
        // MIC chosen before. Setting it disables the index, which applyCalendarSourceExclusion takes care of.
        requireFormControl(this.configObject.idTradingCalendarRuleSet).setValue(this.ruleSetIdByMic[mic] ?? null);
      });
  }

  /**
   * The trading calendar is updated either from the quotes of a reference index or from a rule set. Selecting one of
   * the two therefore clears and disables the other, mirroring the check the backend enforces on save.
   */
  private watchCalendarSource(): void {
    for (const field of ['idIndexUpdCalendar', 'idTradingCalendarRuleSet']) {
      requireFormControl(this.configObject[field])
        .valueChanges.pipe(this.untilDialogClosed())
        .subscribe(() => this.applyCalendarSourceExclusion());
    }
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
    const ruleSetSelected = !!requireFormControl(this.configObject.idTradingCalendarRuleSet).value;
    const indexSelected = !ruleSetSelected && !!requireFormControl(this.configObject.idIndexUpdCalendar).value;
    this.setCalendarSourceState(this.configObject.idIndexUpdCalendar, ruleSetSelected);
    this.setCalendarSourceState(this.configObject.idTradingCalendarRuleSet, indexSelected);
    this.viewChanged();
  }

  private setCalendarSourceState(fieldConfig: FieldConfig, disable: boolean): void {
    if (disable) {
      if (requireFormControl(fieldConfig).value != null) {
        requireFormControl(fieldConfig).setValue(null, { emitEvent: false });
      }
      if (requireFormControl(fieldConfig).enabled) {
        requireFormControl(fieldConfig).disable({ emitEvent: false });
      }
    } else if (requireFormControl(fieldConfig).disabled) {
      requireFormControl(fieldConfig).enable({ emitEvent: false });
    }
  }

  private valueChangedOnCountryCode(): void {
    const country = requireFormControl(this.configObject.countryCode);
    country.valueChanges
      .pipe(
        startWith(country.value),
        distinctUntilChanged(),
        switchMap((countryCode, index) => {
          if (index > 0) {
            requireFormControl(this.configObject.idIndexUpdCalendar).setValue(null);
          }
          this.configObject.idIndexUpdCalendar.valueKeyHtmlOptions = [];
          return countryCode ? this.getSecurityObservable(countryCode) : of<Security[]>([]);
        }),
        this.untilDialogClosed()
      )
      .subscribe((securities) => {
        this.configObject.idIndexUpdCalendar.valueKeyHtmlOptions =
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idSecuritycurrency', 'name', securities, true);
      });
  }

  private disableEnableCountry(): void {
    FormHelper.disableEnableFieldConfigs(!!requireFormControl(this.configObject.mic).value, [
      this.configObject.countryCode
    ]);
  }

  private createMicOptions(onlyMainStockexchange: boolean): GroupItem[] {
    const countryMap: { [cc: string]: GroupItem } = {};
    const emptyEntry: StockexchangeMic = {
      mic: '',
      name: '',
      city: '',
      countryCode: 'xx'
    };
    this.createGreopAndFristEntry(countryMap, emptyEntry, '-----');

    this.callParam()
      .stockexchangeMics.filter((sm) => {
        const callParam = this.callParam();
        return (
          (this.canAssignMic() &&
            ((onlyMainStockexchange && sm.timeZone) || !onlyMainStockexchange) &&
            !callParam.existingMic.has(sm.mic)) ||
          ((!this.canAssignMic() || callParam.proposeChange) && callParam.stockexchange?.mic === sm.mic)
        );
      })
      .forEach((sm) => {
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

  private createGreopAndFristEntry(
    countryMap: {
      [cc: string]: GroupItem;
    },
    sm: StockexchangeMic,
    country: string
  ): void {
    const gp = new GroupItem(null, null, country, 'fi fi-' + sm.countryCode.toLowerCase());
    countryMap[sm.countryCode] = gp;
    gp.children = [];
    this.addEntry(gp, sm);
  }

  private addEntry(groupItem: GroupItem, sm: StockexchangeMic): void {
    const children = (groupItem.children ??= []);
    children.splice(
      this.sortedIndex(children, sm.name),
      0,
      new GroupItem(sm.mic, sm.mic, sm.name + ', ' + sm.city + ', ' + sm.mic, null)
    );
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
    this.copyFormToPublicBusinessObject(
      stockexchange,
      this.callParam().stockexchange,
      this.proposeChangeEntityWithEntity()
    );
    stockexchange.mic = stockexchange.mic || null;
    return stockexchange;
  }

  private getSecurityObservable(countryCode: string): Observable<Security[]> {
    const securitycurrencySearch = new SecuritycurrencySearch();
    securitycurrencySearch.assetclassType = AssetclassType[AssetclassType.EQUITIES];
    securitycurrencySearch.specialInvestmentInstruments =
      SpecialInvestmentInstruments[SpecialInvestmentInstruments.NON_INVESTABLE_INDICES];
    securitycurrencySearch.activeDate = moment().format(BaseSettings.FORMAT_DATE_SHORT_US);
    securitycurrencySearch.stockexchangeCountryCode = countryCode;
    return this.securityService.searchByCriteria(securitycurrencySearch);
  }
}
