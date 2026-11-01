import { afterNextRender, Component, computed, inject, Injector, OnInit, input, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { disabled, metadata, SchemaPathTree } from '@angular/forms/signals';
import { Stockexchange } from '../../entities/stockexchange';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateModule } from '@ngx-translate/core';
import { StockexchangeService } from '../service/stockexchange.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { AuditHelper, AuditNoteModel } from '../../lib/helper/audit.helper';
import type { ProposeChangeEntityWithEntity } from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { StockexchangeCallParam } from './stockexchange.call.param';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { AssetclassType } from '../../shared/types/assetclass.type';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';
import { distinctUntilChanged, forkJoin, Observable, of, switchMap } from 'rxjs';
import { Security } from '../../entities/security';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import moment from 'moment';
import { AppSettings } from '../../shared/app.settings';
import { GroupItem, ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { StockexchangeMic } from '../model/stockexchange.base.data';
import { StockexchangeHelper } from './stockexchange.helper';
import { BaseSettings } from '../../lib/base.settings';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { FIELD_DISPLAY, SignalValueLink } from '../../lib/dynamic-form/signal/signal-form.model';
import { TradingCalendarRuleSetService } from '../service/trading.calendar.rule.set.service';

interface StockexchangeFormModel extends AuditNoteModel {
  onlyMainStockexchange: boolean;
  mic: string | null;
  name: string | null;
  countryCode: string | null;
  website: string | null;
  timeZone: string | null;
  noMarketValue: boolean;
  idTradingCalendarRuleSet: number | string | null;
  idIndexUpdCalendar: number | string | null;
}

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
    <signal-dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [source]="callParam().stockexchange"
      [values]="startValues()"
      [rules]="rules"
      [valueLinks]="valueLinks"
      #form="signalDynamicForm"
      (submitBt)="submit($event)" />
  </p-dialog>`,
  imports: [DialogModule, SignalDynamicFormComponent, TranslateModule]
})
export class StockexchangeEditComponent
  extends SimpleEntityEditBase<Stockexchange, SignalDynamicFormComponent<StockexchangeFormModel>>
  implements OnInit
{
  private tradingCalendarRuleSetService: TradingCalendarRuleSetService = inject(TradingCalendarRuleSetService);
  private securityService: SecurityService = inject(SecurityService);

  readonly callParam = input.required<StockexchangeCallParam>();
  readonly proposeChangeEntityWithEntity = input<ProposeChangeEntityWithEntity>();
  private readonly nameMaxLength = 32;
  private readonly countriesAsKeyValue = computed(() =>
    StockexchangeHelper.transform(this.callParam().countriesAsHtmlOptions)
  );
  private readonly options = signal<{
    timezones: ValueKeyHtmlSelectOptions[];
    ruleSets: ValueKeyHtmlSelectOptions[];
    mapping: Record<string, number>;
  } | null>(null);
  private readonly indices = signal<ValueKeyHtmlSelectOptions[]>([]);
  private readonly injector = inject(Injector);
  readonly auditMode = computed(() =>
    AuditHelper.auditEditMode(this.gps, this.callParam().stockexchange, this.proposeChangeEntityWithEntity())
  );
  readonly startValues = computed(() => ({
    ...AuditHelper.noteStartValues(this.proposeChangeEntityWithEntity()),
    mic: this.callParam().stockexchange?.mic ?? ''
  }));
  readonly rules = (p: SchemaPathTree<StockexchangeFormModel>): void => {
    AuditHelper.auditRules(p, this.config, () => ({
      mode: this.auditMode(),
      proposeChangeEntityWithEntity: this.proposeChangeEntityWithEntity()
    }));
    disabled(p.onlyMainStockexchange, { when: () => !this.canAssignMic() });
    disabled(p.mic, { when: () => !this.canAssignMic() || !this.options() });
    disabled(p.countryCode, { when: ({ valueOf }) => !!valueOf(p.mic) });
    disabled(p.noMarketValue, { when: () => this.callParam().hasSecurity });
    disabled(p.timeZone, { when: () => this.callParam().hasSecurity });
    disabled(p.idIndexUpdCalendar, { when: ({ valueOf }) => !!valueOf(p.idTradingCalendarRuleSet) });
    disabled(p.idTradingCalendarRuleSet, { when: ({ valueOf }) => !!valueOf(p.idIndexUpdCalendar) });
    metadata(p.mic, FIELD_DISPLAY, ({ valueOf }) => ({
      groupItem: this.createMicOptions(valueOf(p.onlyMainStockexchange))
    }));
    metadata(p.countryCode, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.callParam().countriesAsHtmlOptions }));
    metadata(p.timeZone, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.options()?.timezones ?? [] }));
    metadata(p.idTradingCalendarRuleSet, FIELD_DISPLAY, () => ({
      valueKeyHtmlOptions: this.options()?.ruleSets ?? []
    }));
    metadata(p.idIndexUpdCalendar, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.indices() }));
  };
  readonly valueLinks: SignalValueLink<StockexchangeFormModel>[] = [
    ...(['name', 'countryCode', 'website', 'timeZone'] as const).map(
      (target): SignalValueLink<StockexchangeFormModel> => ({
        sources: ['mic'],
        target,
        skipInitial: true,
        derive: (values, current) => {
          const mic = this.callParam().stockexchangeMics.find((entry) => entry.mic === values.mic);
          if (!this.canAssignMic() || !mic) return current;
          return target === 'name' ? mic.name.toLowerCase().substring(0, this.nameMaxLength) : (mic[target] ?? null);
        }
      })
    ),
    {
      sources: ['mic'],
      target: 'idTradingCalendarRuleSet',
      skipInitial: true,
      derive: (values, current) => (this.canAssignMic() ? (this.options()?.mapping[values.mic ?? ''] ?? null) : current)
    },
    {
      sources: ['countryCode'],
      target: 'idIndexUpdCalendar',
      skipInitial: true,
      derive: () => null
    },
    {
      sources: ['idTradingCalendarRuleSet'],
      target: 'idIndexUpdCalendar',
      derive: (values, current) => (values.idTradingCalendarRuleSet ? null : current)
    },
    {
      sources: ['idIndexUpdCalendar'],
      target: 'idTradingCalendarRuleSet',
      derive: (values, current) => (values.idIndexUpdCalendar ? null : current)
    }
  ];

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
        defaultValue: true
      }),
      DynamicFieldHelper.createFieldDropdownStringHeqF('mic', false, {
        groupItemUseOrLoading: true,
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
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this.form)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
  }

  private canAssignMic(): boolean {
    const callParam = this.callParam();
    return (
      (callParam.stockexchange && (!callParam.stockexchange.mic || callParam.proposeChange)) || !callParam.stockexchange
    );
  }

  protected override initialize(): void {
    AuditHelper.applyAuditButtons(this.form(), this.auditMode());
    this.form().setButtonState('submit', { disabled: true });
    forkJoin({
      timezones: this.gps.getTimezones(),
      ruleSets: this.tradingCalendarRuleSetService.getRuleSetOptions(),
      mapping: this.tradingCalendarRuleSetService.getRuleSetIdByMic()
    })
      .pipe(this.untilDialogClosed())
      .subscribe(({ timezones, ruleSets, mapping }) => {
        this.options.set({ timezones, ruleSets: [new ValueKeyHtmlSelectOptions('', ''), ...ruleSets], mapping });
        this.form().setButtonState('submit', { disabled: false });
        afterNextRender(
          () => {
            const focus = this.canAssignMic() ? this.configObject.mic : this.configObject.name;
            focus.baseInputComponent?.focus();
          },
          { injector: this.injector }
        );
      });
    // The form is ready at onShow. Country changes cancel the previous HTTP read; loading never resets a draft.
    toObservable(
      computed(() => this.form().value('countryCode') as string | null),
      { injector: this.injector }
    )
      .pipe(
        distinctUntilChanged(),
        switchMap((country) => {
          this.indices.set([]);
          return country ? this.getSecurityObservable(country) : of<Security[]>([]);
        }),
        this.untilDialogClosed()
      )
      .subscribe((securities) =>
        this.indices.set(
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idSecuritycurrency', 'name', securities, true)
        )
      );
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
          const country = this.countriesAsKeyValue()[sm.countryCode];
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
