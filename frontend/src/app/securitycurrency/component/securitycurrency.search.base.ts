import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {
  afterNextRender,
  DestroyRef,
  Directive,
  inject,
  Injector,
  OnInit,
  viewChild,
  input,
  linkedSignal,
  signal
} from '@angular/core';
import { Subscription } from 'rxjs';
import { FormBase } from '../../lib/edit/form.base';
import type { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { AppHelper } from '../../lib/helper/app.helper';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { TranslateService } from '@ngx-translate/core';
import { HelpIds } from '../../lib/help/help.ids';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { AssetclassType } from '../../shared/types/assetclass.type';
import { atLeastOneFieldValidator } from '../../lib/validator/validator';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';
import { Security } from '../../entities/security';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { SupplementCriteria } from '../model/supplement.criteria';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';
import { Helper } from '../../lib/helper/helper';
import {
  AdvancedSearchCriterion,
  AdvancedSearchField,
  AdvancedSearchValueType
} from '../../entities/search/advanced.search';
import { AdvancedSearchOperator } from '../../shared/types/advanced.search.operator';
import {
  ADVANCED_SEARCH_MAX_CRITERIA,
  ADVANCED_SEARCH_MAX_TEXT_LENGTH,
  ADVANCED_SLOT_CONTROLS,
  AdvancedSlotControl,
  AdvancedSlotValues,
  criteriaOfSlots,
  visibleSlotControls
} from './advanced.search.helper';
import {
  DataForCurrencySecuritySearch,
  MultipleRequestToOneService
} from '../../shared/service/multiple.request.to.one.service';

/**
 * Base class for a search dialog for security or currency pair.
 */
@Directive()
export abstract class SecuritycurrencySearchBase extends FormBase implements OnInit {
  protected gps: GlobalparameterService = inject(GlobalparameterService);
  protected multipleRequestToOneService: MultipleRequestToOneService = inject(MultipleRequestToOneService);
  public translateService: TranslateService = inject(TranslateService);

  /**
   * Field which turns the name into a regular expression. It only modifies how 'name' is matched, it is not a search
   * criterion of its own, which is why it is excluded from the emptiness check of the second group.
   */
  private static readonly NAME_REGEX = 'nameRegex';

  // Access child components
  readonly dynamicFormComponent = viewChild.required(DynamicFormComponent);
  readonly supplementCriteriaInput = input<SupplementCriteria>(undefined, { alias: 'supplementCriteria' });
  readonly supplementCriteria = linkedSignal(() => this.supplementCriteriaInput());

  // Groups are manly for the handling of inputs visibility
  readonly firstGroup = 'G1';
  readonly secondGroup = 'G2';
  /** The slots of the advanced search; kept apart from the second group, whose visibility follows the ISIN. */
  readonly advancedGroup = 'G3';

  /** The fields the user can choose in a criterion, from the base data of the dialog; the result table labels with them. */
  readonly advancedSearchFields = signal<AdvancedSearchField[]>([]);
  private advancedFieldOfKey = new Map<string, AdvancedSearchField>();
  /** Slots, counted from 1, that the user has opened. */
  private readonly openAdvancedSlots = new Set<number>();
  /** The field of each slot when the block was last updated, to notice a change of field. */
  private readonly advancedSlotField: (string | null)[] = Array(ADVANCED_SEARCH_MAX_CRITERIA).fill(null);
  /** The options of each field select as last assigned, so unchanged options are not assigned again. */
  private readonly advancedOptionSignature: string[] = Array(ADVANCED_SEARCH_MAX_CRITERIA).fill('');

  private monitor = false;
  private readonly injector = inject(Injector);
  /** Value subscription of the current opening; a new opening replaces it, so openings do not accumulate. */
  private formValueSubscription?: Subscription;

  protected constructor(protected multiplyAddClose: boolean) {
    super();
    inject(DestroyRef).onDestroy(() => this.formValueSubscription?.unsubscribe());
  }

  abstract childClearList(): void;

  abstract childLoadData(securitycurrencySearch: SecuritycurrencySearch): void;

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 3, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputStringVSHeqF('isin', 12, false, ['ISIN'], {
        userDefinedValue: this.firstGroup
      }),
      DynamicFieldHelper.createFieldCheckboxHeqF('withHoldings', { userDefinedValue: this.secondGroup }),
      DynamicFieldHelper.createFieldInputStringHeqF('name', 80, false, { userDefinedValue: this.secondGroup }),
      DynamicFieldHelper.createFieldCheckboxHeqF(SecuritycurrencySearchBase.NAME_REGEX, {
        userDefinedValue: this.secondGroup
      }),
      DynamicFieldHelper.createFieldInputStringHeqF(
        'tickerSymbol',
        this.gps.getFieldSize(AppSettings.FIELD_SIZE_MAX_Security_Ticker_Symbol),
        false,
        {
          upperCase: true,
          userDefinedValue: this.secondGroup
        }
      ),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateStringShortUS, 'activeDate', false, {
        userDefinedValue: <string>this.secondGroup
      }),
      DynamicFieldHelper.createFieldTriStateCheckbox('onlyTenantPrivate', 'PRIVATE_SECURITY', {
        userDefinedValue: this.secondGroup
      }),
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.Numeric, 'leverageFactor', false, -9.99, 9.99, {
        userDefinedValue: this.secondGroup
      }),
      DynamicFieldHelper.createFieldSelectString('assetclassType', AppSettings.ASSETCLASS.toUpperCase(), false, {
        userDefinedValue: this.secondGroup
      }),
      DynamicFieldHelper.createFieldSelectString('subCategoryNLS', 'SUB_ASSETCLASS', false, {
        userDefinedValue: this.secondGroup
      }),
      DynamicFieldHelper.createFieldSelectString('specialInvestmentInstruments', 'FINANCIAL_INSTRUMENT', false, {
        userDefinedValue: this.secondGroup
      }),
      DynamicFieldHelper.createFieldSelectString('idConnectorHistory', 'HISTORY_DATA_PROVIDER', false, {
        userDefinedValue: this.secondGroup
      }),
      DynamicFieldHelper.createFieldSelectString('idConnectorIntra', 'INTRA_DATA_PROVIDER', false, {
        userDefinedValue: this.secondGroup
      }),
      DynamicFieldHelper.createFieldSelectNumber('idStockexchange', AppSettings.STOCKEXCHANGE.toUpperCase(), false, {
        userDefinedValue: this.secondGroup
      }),
      DynamicFieldHelper.createFieldSelectStringHeqF('currency', false, { userDefinedValue: this.secondGroup }),
      ...this.createAdvancedSlotFields()
    ];
    this.config.push(
      DynamicFieldHelper.createFunctionButtonFieldName('advAdd', 'ADVANCED_SEARCH_ADD', () =>
        this.openNextAdvancedSlot()
      )
    );
    if (this.multiplyAddClose) {
      this.config.push(DynamicFieldHelper.createFunctionButton('EXIT', (e) => this.closeSearchDialog(e)));
    }
    this.config.push(DynamicFieldHelper.createSubmitButton('SEARCH'));
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  onShow(event: unknown) {
    // The form controls of a dialog that was just made visible exist after the next render.
    afterNextRender(() => this.initialize(), { injector: this.injector });
  }

  submit(value: { [name: string]: any }): void {
    const securitycurrencySearch: SecuritycurrencySearch = new SecuritycurrencySearch();
    this.dynamicFormComponent().cleanMaskAndTransferValuesToBusinessObject(securitycurrencySearch);
    securitycurrencySearch.advancedCriteria = this.collectAdvancedCriteria();
    this.childLoadData(securitycurrencySearch);
    this.configObject.submit.disabled = false;
  }

  closeSearchDialog(event: unknown): void {}

  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_WATCHLIST_SEARCHDIALOG);
  }

  protected initialize(): void {
    this.dynamicFormComponent().form.setValidators(atLeastOneFieldValidator);
    const denyAssetClass = [AssetclassType.CURRENCY_CASH, AssetclassType.CURRENCY_FOREIGN];
    if (this.supplementCriteria()?.onlySecurity) {
      denyAssetClass.push(AssetclassType.CURRENCY_PAIR);
    }

    this.configObject.assetclassType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnumAddEmpty(
      this.translateService,
      AssetclassType,
      [AssetclassType.CURRENCY_CASH, AssetclassType.CURRENCY_FOREIGN],
      true
    );

    this.configObject.specialInvestmentInstruments.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnumAddEmpty(this.translateService, SpecialInvestmentInstruments);

    this.setAdvancedStaticOptions();
    this.childClearList();

    this.multipleRequestToOneService
      .getDataForCurrencySecuritySearch()
      .subscribe((dfcss: DataForCurrencySecuritySearch) => {
        this.setValueKeyHtmlOptions(this.configObject.currency, dfcss.currencies);
        this.setValueKeyHtmlOptions(this.configObject.subCategoryNLS, dfcss.assetclasses);
        this.setValueKeyHtmlOptions(this.configObject.idConnectorHistory, dfcss.feedConnectorsHistory);
        this.setValueKeyHtmlOptions(this.configObject.idConnectorIntra, dfcss.feedConnectorsIntra);
        this.configObject.idStockexchange.valueKeyHtmlOptions =
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
            'idStockexchange',
            'name',
            dfcss.stockexchanges,
            true
          );
        this.dynamicFormComponent().setDefaultValuesAndEnableSubmit();
        this.valueChangedOnForm();
        this.resetAdvanced(dfcss.advancedSearchFields ?? []);
        this.configObject.isin.elementRef?.nativeElement.focus();
      });
  }

  private setValueKeyHtmlOptions(fieldConfig: FieldConfig, values: ValueKeyHtmlSelectOptions[]): void {
    fieldConfig.valueKeyHtmlOptions = [new ValueKeyHtmlSelectOptions('', ''), ...values];
  }

  private valueChangedOnForm(): void {
    this.formValueSubscription?.unsubscribe();
    this.formValueSubscription = this.dynamicFormComponent().form.valueChanges.subscribe((security: Security) => {
      if (!this.monitor) {
        this.monitor = true;
        this.dynamicFormComponent()
          .controls.filter((fieldConfig) => fieldConfig.userDefinedValue === this.secondGroup)
          .forEach((fieldConfig) => AppHelper.invisibleAndHide(fieldConfig, !!security.isin && security.isin !== ''));

        if (!security.isin || security.isin === '') {
          const secondGroupIsEmpty = this.isSecondGroupEmpty();
          this.dynamicFormComponent()
            .controls.filter((fieldConfig) => fieldConfig.userDefinedValue === this.firstGroup)
            .forEach((fieldConfig) => AppHelper.invisibleAndHide(fieldConfig, !secondGroupIsEmpty));
          AppHelper.invisibleAndHide(
            this.configObject.name,
            this.configObject.name.invisible || this.isExactCurrency()
          );
          // The regular expression is only supported for securities, the currency pair search matches the currency codes
          AppHelper.invisibleAndHide(
            this.configObject.nameRegex,
            this.configObject.name.invisible || this.isCurrency()
          );
          AppHelper.invisibleAndHide(this.configObject.tickerSymbol, this.isCurrency());
          AppHelper.invisibleAndHide(this.configObject.onlyTenantPrivate, this.isCurrency());
          AppHelper.invisibleAndHide(this.configObject.leverageFactor, this.isCurrency());
          AppHelper.invisibleAndHide(this.configObject.activeDate, this.isCurrency());
          AppHelper.invisibleAndHide(this.configObject.idStockexchange, this.isCurrency());
          AppHelper.invisibleAndHide(this.configObject.specialInvestmentInstruments, this.isCurrency());
          AppHelper.invisibleAndHide(this.configObject.subCategoryNLS, this.isCurrency());
        }
        this.updateAdvancedVisibility();
        this.monitor = false;
      }
    });
  }

  /** The name of an input of a slot, for example `advNumberTo2`. */
  private slotFieldName(control: AdvancedSlotControl, slot: number): string {
    return `adv${control.charAt(0).toUpperCase()}${control.slice(1)}${slot}`;
  }

  private slotConfig(control: AdvancedSlotControl, slot: number): FieldConfig {
    return this.configObject[this.slotFieldName(control, slot)];
  }

  /**
   * Creates the inputs of the criteria slots of the advanced search. They stay hidden until the user opens a slot, and
   * every input but the field select is required while it is shown; a hidden input is disabled and therefore not part
   * of the validation.
   */
  private createAdvancedSlotFields(): FieldConfig[] {
    const fields: FieldConfig[] = [];
    for (let n = 1; n <= ADVANCED_SEARCH_MAX_CRITERIA; n++) {
      const options = { userDefinedValue: this.advancedGroup, invisible: true };
      fields.push(
        DynamicFieldHelper.createFieldSelectString(`advField${n}`, 'ADVANCED_SEARCH_FIELD', false, options),
        DynamicFieldHelper.createFieldSelectString(
          this.slotFieldName('operator', n),
          'ADVANCED_SEARCH_OPERATOR',
          true,
          options
        ),
        DynamicFieldHelper.createFieldInputNumber(
          this.slotFieldName('number', n),
          'ADVANCED_SEARCH_VALUE',
          true,
          12,
          6,
          true,
          options
        ),
        DynamicFieldHelper.createFieldInputNumber(
          this.slotFieldName('numberTo', n),
          'ADVANCED_SEARCH_VALUE',
          true,
          12,
          6,
          true,
          options
        ),
        DynamicFieldHelper.createFieldPcalendar(
          DataType.DateStringShortUS,
          this.slotFieldName('date', n),
          'ADVANCED_SEARCH_VALUE',
          true,
          options
        ),
        DynamicFieldHelper.createFieldPcalendar(
          DataType.DateStringShortUS,
          this.slotFieldName('dateTo', n),
          'ADVANCED_SEARCH_VALUE',
          true,
          options
        ),
        DynamicFieldHelper.createFieldInputString(
          this.slotFieldName('text', n),
          'ADVANCED_SEARCH_VALUE',
          ADVANCED_SEARCH_MAX_TEXT_LENGTH,
          true,
          options
        ),
        DynamicFieldHelper.createFieldSelectString(
          this.slotFieldName('flag', n),
          'ADVANCED_SEARCH_VALUE',
          true,
          options
        ),
        DynamicFieldHelper.createFunctionButtonFieldName(
          `advRemove${n}`,
          'ADVANCED_SEARCH_REMOVE',
          () => this.removeAdvancedSlot(n),
          options
        )
      );
    }
    return fields;
  }

  /** Assigns the options of the operator and the yes/no selects, which do not depend on the data of the user. */
  private setAdvancedStaticOptions(): void {
    const operators = SelectOptionsHelper.translateArrayKeyEqualValue(
      this.translateService,
      Object.values(AdvancedSearchOperator)
    );
    const flags = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(this.translateService, [
      new ValueKeyHtmlSelectOptions('', ''),
      new ValueKeyHtmlSelectOptions('true', 'YES'),
      new ValueKeyHtmlSelectOptions('false', 'NO')
    ]);
    for (let n = 1; n <= ADVANCED_SEARCH_MAX_CRITERIA; n++) {
      this.slotConfig('operator', n).valueKeyHtmlOptions = operators;
      this.slotConfig('flag', n).valueKeyHtmlOptions = flags;
    }
  }

  /** Takes over the fields of a new opening of the dialog and closes every slot. */
  private resetAdvanced(fields: AdvancedSearchField[]): void {
    this.advancedSearchFields.set(fields);
    this.advancedFieldOfKey = new Map(fields.map((field) => [field.key, field]));
    this.openAdvancedSlots.clear();
    this.advancedSlotField.fill(null);
    this.advancedOptionSignature.fill('');
    this.refreshAdvanced();
  }

  /** Opens the lowest closed slot. */
  private openNextAdvancedSlot(): void {
    for (let n = 1; n <= ADVANCED_SEARCH_MAX_CRITERIA; n++) {
      if (!this.openAdvancedSlots.has(n)) {
        this.openAdvancedSlots.add(n);
        break;
      }
    }
    this.refreshAdvanced();
  }

  /** Clears a slot and closes it; the other slots keep their place and the freed one can be opened again. */
  private removeAdvancedSlot(slot: number): void {
    this.setDefaultValueOf(this.configObject[`advField${slot}`]);
    this.clearAdvancedValues(slot);
    this.openAdvancedSlots.delete(slot);
    this.advancedSlotField[slot - 1] = null;
    this.refreshAdvanced();
  }

  /** Updates the slots outside of the value change handler, whose guard keeps the changes it causes from recursing. */
  private refreshAdvanced(): void {
    if (this.monitor) {
      return;
    }
    this.monitor = true;
    try {
      this.updateAdvancedVisibility();
    } finally {
      this.monitor = false;
    }
  }

  /**
   * Shows or hides the slots and their inputs after every change of the form: the open slots with the inputs of the
   * type of their field, the operator and the second value only for BETWEEN. An ISIN, the asset class currency pair
   * and an empty list of fields hide all of them; the values stay, so they are there again when that ends. Choosing
   * another field clears the values of the former one.
   */
  private updateAdvancedVisibility(): void {
    const blocked = this.isAdvancedBlocked();
    const chosen = this.chosenAdvancedFields();
    for (let n = 1; n <= ADVANCED_SEARCH_MAX_CRITERIA; n++) {
      const show = this.openAdvancedSlots.has(n) && !blocked;
      const key = chosen.get(n) ?? null;
      if (show && key !== this.advancedSlotField[n - 1]) {
        this.clearAdvancedValues(n);
      }
      if (show) {
        this.advancedSlotField[n - 1] = key;
      }
      const field = key ? this.advancedFieldOfKey.get(key) : undefined;
      const operator = this.advancedControlValue('operator', n) as AdvancedSearchOperator | '' | null;
      const visible = show ? visibleSlotControls(field?.valueType, operator || null) : [];
      const fieldSelect = this.configObject[`advField${n}`];
      this.setHidden(fieldSelect, !show);
      // A heading is drawn even when its field is hidden, so it is set only while the slot is shown.
      fieldSelect.fieldHeader = show ? 'ADVANCED_SEARCH_CRITERION' : null;
      this.updateAdvancedSlotControls(n, show, visible, field);
      this.updateAdvancedFieldOptions(n, chosen);
    }
    const allOpen = this.openAdvancedSlots.size >= ADVANCED_SEARCH_MAX_CRITERIA;
    this.setHidden(this.configObject['advAdd'], blocked || allOpen);
  }

  private updateAdvancedSlotControls(
    slot: number,
    show: boolean,
    visible: AdvancedSlotControl[],
    field: AdvancedSearchField | undefined
  ): void {
    for (const control of ADVANCED_SLOT_CONTROLS) {
      const fieldConfig = this.slotConfig(control, slot);
      const isVisible = visible.includes(control);
      if (show && !isVisible) {
        this.setDefaultValueOf(fieldConfig);
      }
      this.setHidden(fieldConfig, !isVisible);
      if (isVisible && control !== 'operator' && field) {
        const labelKey = this.advancedValueLabel(control, field);
        if (fieldConfig.labelKey !== labelKey) {
          fieldConfig.labelKey = labelKey;
        }
      }
    }
    const remove = this.configObject[`advRemove${slot}`];
    this.setHidden(remove, !show);
    remove.fieldHeader = show && field?.candidateLimited ? 'ADVANCED_SEARCH_LIMIT_HINT' : null;
  }

  /** A literal label of a value input (the leading star suppresses translation): the text of the chosen field. */
  private advancedValueLabel(control: AdvancedSlotControl, field: AdvancedSearchField): string {
    const second = control === 'numberTo' || control === 'dateTo';
    return second ? `*${field.label} (${this.translateService.instant('ADVANCED_SEARCH_TO')})` : `*${field.label}`;
  }

  /** Offers each select the fields that no other slot has chosen. */
  private updateAdvancedFieldOptions(slot: number, chosen: Map<number, string>): void {
    const own = chosen.get(slot);
    const takenElsewhere = new Set([...chosen].filter(([other]) => other !== slot).map(([, key]) => key));
    const options = this.advancedSearchFields().filter((field) => field.key === own || !takenElsewhere.has(field.key));
    const signature = options.map((field) => field.key).join('|');
    if (signature !== this.advancedOptionSignature[slot - 1]) {
      this.advancedOptionSignature[slot - 1] = signature;
      this.configObject[`advField${slot}`].valueKeyHtmlOptions = [
        new ValueKeyHtmlSelectOptions('', ''),
        ...options.map((field) => new ValueKeyHtmlSelectOptions(field.key, field.label))
      ];
    }
  }

  /** The field chosen in each open slot, by slot. */
  private chosenAdvancedFields(): Map<number, string> {
    const chosen = new Map<number, string>();
    for (const n of this.openAdvancedSlots) {
      const key = requireFormControl(this.configObject[`advField${n}`]).value;
      if (key) {
        chosen.set(n, String(key));
      }
    }
    return chosen;
  }

  private hasChosenAdvancedField(): boolean {
    for (let n = 1; n <= ADVANCED_SEARCH_MAX_CRITERIA; n++) {
      if (requireFormControl(this.configObject[`advField${n}`]).value) {
        return true;
      }
    }
    return false;
  }

  /** The block is unavailable with an ISIN, for currency pairs, and when the user has no field to choose. */
  private isAdvancedBlocked(): boolean {
    return (
      !!requireFormControl(this.configObject.isin).value ||
      this.isCurrency() ||
      this.advancedSearchFields().length === 0
    );
  }

  private advancedControlValue(control: AdvancedSlotControl, slot: number): unknown {
    return requireFormControl(this.slotConfig(control, slot)).value;
  }

  private clearAdvancedValues(slot: number): void {
    for (const control of ADVANCED_SLOT_CONTROLS) {
      this.setDefaultValueOf(this.slotConfig(control, slot));
    }
  }

  private setDefaultValueOf(fieldConfig: FieldConfig): void {
    const control = requireFormControl(fieldConfig);
    const value = fieldConfig.defaultValue ?? null;
    if (control.value !== value) {
      control.setValue(value, { emitEvent: false });
    }
  }

  /** Hides and disables, or shows and enables, an input; nothing is touched when it is already in that state. */
  private setHidden(fieldConfig: FieldConfig, hide: boolean): void {
    const control = fieldConfig.formControl;
    const disabled = control ? control.disabled : !!fieldConfig.disabled;
    if (!!fieldConfig.invisible !== hide || disabled !== hide) {
      AppHelper.invisibleAndHide(fieldConfig, hide);
    }
  }

  private slotValues(slot: number): AdvancedSlotValues {
    const value = (control: AdvancedSlotControl) => this.advancedControlValue(control, slot);
    const number = (control: AdvancedSlotControl) => {
      const entered = value(control);
      return entered === null || entered === undefined || entered === '' ? null : +(entered as number);
    };
    const date = (control: AdvancedSlotControl) =>
      Helper.formatDateValueAsString(value(control), BaseSettings.FORMAT_DATE_SHORT_US);
    const flag = value('flag');
    return {
      field: String(requireFormControl(this.configObject[`advField${slot}`]).value || '') || null,
      operator: (value('operator') as AdvancedSearchOperator | '' | null) || null,
      number: number('number'),
      numberTo: number('numberTo'),
      date: date('date'),
      dateTo: date('dateTo'),
      text: (value('text') as string | null) || null,
      flag: flag === 'true' ? true : flag === 'false' ? false : null
    };
  }

  /** The complete criteria of the slots, none while the block is unavailable. */
  private collectAdvancedCriteria(): AdvancedSearchCriterion[] {
    if (this.isAdvancedBlocked()) {
      return [];
    }
    const slots: { values: AdvancedSlotValues; valueType: AdvancedSearchValueType | undefined }[] = [];
    for (let n = 1; n <= ADVANCED_SEARCH_MAX_CRITERIA; n++) {
      const values = this.slotValues(n);
      slots.push({
        values,
        valueType: values.field ? this.advancedFieldOfKey.get(values.field)?.valueType : undefined
      });
    }
    return criteriaOfSlots(slots).criteria;
  }

  private isSecondGroupEmpty(): boolean {
    const foundFieldConfig = this.dynamicFormComponent()
      .controls.filter(
        (fieldConfig) =>
          fieldConfig.userDefinedValue === this.secondGroup &&
          fieldConfig.field !== SecuritycurrencySearchBase.NAME_REGEX
      )
      .find(
        (fieldConfig) =>
          (fieldConfig.dataType === DataType.Boolean && requireFormControl(fieldConfig).value) ||
          (fieldConfig.dataType !== DataType.Boolean &&
            requireFormControl(fieldConfig).value &&
            requireFormControl(fieldConfig).value !== '')
      );
    return !foundFieldConfig && !this.hasChosenAdvancedField();
  }

  private isCurrency(): boolean {
    return requireFormControl(this.configObject.assetclassType).value === AssetclassType[AssetclassType.CURRENCY_PAIR];
  }

  private isExactCurrency(): boolean {
    return this.isCurrency() && !!requireFormControl(this.configObject.currency).value;
  }
}
