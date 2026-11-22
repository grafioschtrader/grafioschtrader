import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { ButtonModule } from '@openng/optimus-ui/button';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { finalize, forkJoin, merge } from 'rxjs';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { Helper } from '../../lib/helper/helper';
import { BaseSettings } from '../../lib/base.settings';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { SecurityActionService } from '../service/security-action.service';
import {
  SecurityAction,
  SecurityActionType,
  SpinOffAllocationProposal,
  SpinOffHistoryBasisSetting,
  SpinOffPriceStep,
  SpinOffProviderSplits,
  SpinOffTaxTreatmentDe
} from '../model/security-action.model';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { AppHelper } from '../../lib/helper/app.helper';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { AppSettings } from '../../shared/app.settings';
import { HelpIds } from '../../lib/help/help.ids';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { SimpleFormEditBase } from '../../lib/edit/simple.form.edit.base';
import { Security } from '../../entities/security';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { SupplementCriteria } from '../../securitycurrency/model/supplement.criteria';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { SecuritycurrencySearchAndSetDialogComponent } from '../../securitycurrency/component/securitycurrency-search-and-set-dialog.component';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';

interface SecurityActionFormValue {
  isinOld?: string;
  isinNew?: string;
  fromFactor?: number;
  toFactor?: number;
  note?: string;
  costAllocation?: number;
  costAllocationSource?: string;
  taxTreatmentDe?: SpinOffTaxTreatmentDe;
  costAllocationDe?: number;
  historyBasis?: SpinOffHistoryBasisSetting;
  historyFactor?: number;
}

/** Administrator dialog for recording a system action or correcting a spin-off allocation. */
@Component({
  selector: 'security-action-create',
  template: `
    <p-dialog
      [header]="titleKey | translate"
      [visible]="visibleDialog()"
      [style]="{ width: '600px', maxWidth: '95vw' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        (submitBt)="submit($event)" />
      @if (busy()) {
        <p role="status">{{ 'SPIN_OFF_LOADING' | translate }}</p>
      }
      @if (lookupChild(); as child) {
        <p role="status">
          <strong>{{ child.name }}</strong>
          {{ child.isin }} · {{ child.currency }}
          @if (child.tickerSymbol) {
            · {{ child.tickerSymbol }}
          }
        </p>
        @if (child.idSecuritycurrency == null) {
          <dl>
            <dt>{{ 'SPIN_OFF_CHILD_CONNECTORS' | translate }}</dt>
            <dd>{{ connectorNames(child) || ('SPIN_OFF_CHILD_NO_CONNECTORS' | translate) }}</dd>
          </dl>
        }
      }
      @if (allocationProposal(); as proposal) {
        @if (proposalDisplay(); as shown) {
          <p role="status">
            {{ 'SPIN_OFF_ALLOCATION_PRICES' | translate: shown }}
            <strong>{{ shown.allocation }}</strong>
          </p>
          <p-button [label]="'SPIN_OFF_USE_ALLOCATION' | translate" (onClick)="useProposal(proposal)" />
        } @else {
          <p role="status">{{ 'SPIN_OFF_ALLOCATION_EMPTY' | translate }}</p>
        }
      }
      @if (providerSplitsDisplay(); as shown) {
        <dl>
          <dt>{{ 'SPIN_OFF_PROVIDER_SPLITS' | translate }}</dt>
          <dd>
            @for (split of shown.splits; track split.date) {
              <div>
                {{ split.date }}: {{ split.ratio }}
                @if (split.dropped) {
                  ({{ 'SPIN_OFF_SPLIT_DROPPED' | translate }})
                }
              </div>
            } @empty {
              {{ shown.splitsMessage | translate }}
            }
          </dd>
          @if (shown.providerSplitFactor) {
            <dt>{{ 'PROVIDER_SPLIT_FACTOR' | translate }}</dt>
            <dd>{{ shown.providerSplitFactor }}</dd>
            <dt>{{ 'SPIN_OFF_FACTOR' | translate }}</dt>
            <dd>{{ shown.spinOffFactor }}</dd>
          }
          @if (shown.impliedFactor) {
            <dt>{{ 'SPIN_OFF_FACTOR_IMPLIED' | translate }}</dt>
            <dd>{{ shown.impliedFactor }}</dd>
          }
          @if (shown.historyBasis) {
            <dt>{{ 'HISTORY_BASIS' | translate }}</dt>
            <dd>
              {{ shown.historyBasis }} {{ shown.historyFactor }}
              @if (shown.historyBasisOrigin) {
                · {{ shown.historyBasisOrigin }}
              }
            </dd>
            @if (shown.historySource) {
              <dt>{{ 'SPIN_OFF_HISTORY_SOURCE' | translate }}</dt>
              <dd>
                {{ shown.historySource }}
                @if (!shown.currentSource) {
                  ({{ 'SPIN_OFF_HISTORY_SOURCE_OTHER' | translate }})
                }
              </dd>
            }
          }
          <dt>{{ 'SPIN_OFF_STEP_STORED' | translate }}</dt>
          <dd>{{ shown.storedStep }}</dd>
          <dt>{{ 'SPIN_OFF_STEP_DELIVERED' | translate }}</dt>
          <dd>{{ shown.deliveredStep }}</dd>
        </dl>
      }
    </p-dialog>
  `,
  imports: [ButtonModule, DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule],
  providers: [DialogService]
})
export class SecurityActionCreateDialogComponent extends SimpleFormEditBase implements OnInit {
  readonly initialType = input<SecurityActionType>('ISIN_CHANGE');
  readonly actionToEdit = input<SecurityAction | null>(null);
  readonly busy = signal(false);
  readonly lookupChild = signal<Security | null>(null);
  readonly allocationProposal = signal<SpinOffAllocationProposal | null>(null);
  readonly providerSplits = signal<SpinOffProviderSplits | null>(null);

  readonly translateService = inject(TranslateService);

  // The proposal is shown with the data types of GT: the price day in the user's date format, prices and the exchange
  // rate with the precision of the transaction tables, the allocation as percentage with its standard fraction digits.
  private readonly priceDateField = ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'priceDate', null);
  private readonly priceField = (field: string) =>
    ShowRecordConfigBase.createColumnConfig(DataType.Numeric, field, null, true, false, {
      maxFractionDigits: this.gps.getMaxFractionDigits()
    });
  private readonly parentCloseField = this.priceField('parentClose');
  private readonly childCloseField = this.priceField('childClose');
  private readonly fxChildToParentField = this.priceField('fxChildToParent');
  private readonly percentField = ShowRecordConfigBase.createColumnConfig(
    DataType.Numeric,
    'percent',
    null,
    true,
    false,
    {
      fixedFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION
    }
  );

  /** The values of the allocation proposal formatted for display, null while there is no proposed allocation. */
  readonly proposalDisplay = computed(() => {
    const proposal = this.allocationProposal();
    if (proposal?.proposedAllocation == null) {
      return null;
    }
    const format = (data: object, field: ColumnConfig): string =>
      AppHelper.getValueByPathWithField(this.gps, this.translateService, data, field, field.field);
    return {
      priceDate: format(proposal, this.priceDateField),
      parentClose: format(proposal, this.parentCloseField),
      childClose: format(proposal, this.childCloseField),
      fxChildToParent: format(proposal, this.fxChildToParentField),
      allocation: format({ percent: proposal.proposedAllocation * 100 }, this.percentField) + ' %'
    };
  });
  private readonly factorField = ShowRecordConfigBase.createColumnConfig(
    DataType.Numeric,
    'factor',
    null,
    true,
    false,
    {
      maxFractionDigits: 6
    }
  );
  private readonly stepField = ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'step', null, true, false, {
    fixedFractionDigits: 4
  });
  private readonly splitDateField = ShowRecordConfigBase.createColumnConfig(DataType.DateString, 'date', null);

  /** Provider splits, factors, history basis and price steps formatted for display, null until they are loaded. */
  readonly providerSplitsDisplay = computed(() => {
    const ps = this.providerSplits();
    if (ps == null) {
      return null;
    }
    const format = (data: object, field: ColumnConfig): string =>
      AppHelper.getValueByPathWithField(this.gps, this.translateService, data, field, field.field);
    const factor = (value: number | null): string | null =>
      value == null ? null : format({ factor: value }, this.factorField);
    const step = (value: SpinOffPriceStep | null): string => {
      if (value == null) {
        return this.translateService.instant('SPIN_OFF_STEP_NONE');
      }
      const number = (v: number | null): string => (v == null ? '–' : format({ step: v }, this.stepField));
      return this.translateService.instant('SPIN_OFF_STEP_VALUES', {
        x: number(value.x),
        mAdj: number(value.mAdj),
        mRaw: number(value.mRaw)
      });
    };
    const proposal = this.allocationProposal()?.proposedAllocation;
    return {
      splits: ps.splits.map((s) => ({
        date: format({ date: s.splitDate }, this.splitDateField),
        ratio: s.fromFactor + ' : ' + s.toFactor,
        dropped: s.dropped
      })),
      splitsMessage: ps.splitsUnavailable ? 'SPIN_OFF_PROVIDER_SPLITS_UNAVAILABLE' : 'SPIN_OFF_PROVIDER_SPLITS_NONE',
      providerSplitFactor: factor(ps.providerSplitFactor),
      spinOffFactor: factor(ps.spinOffFactor),
      // f = 1 / (1 - q) of the closes, only a display aid beside the provider factor
      impliedFactor: proposal == null || proposal >= 1 ? null : factor(1 / (1 - proposal)),
      historyBasis: ps.historyBasis == null ? null : this.translateService.instant(ps.historyBasis),
      historyFactor: factor(ps.historyFactor),
      historyBasisOrigin: ps.historyBasisOrigin == null ? null : this.translateService.instant(ps.historyBasisOrigin),
      historySource: ps.historyConnector == null ? null : [ps.historyConnector, ps.historyUrlExtend].join(' · '),
      currentSource: ps.currentSource,
      storedStep: step(ps.storedStep),
      deliveredStep: step(ps.deliveredStep)
    };
  });
  private readonly messageToastService = inject(MessageToastService);
  private readonly securityActionService = inject(SecurityActionService);
  private readonly dialogService = inject(DialogService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly currencies = inject(GlobalparameterGTService);
  private readonly supplementCriteria = new SupplementCriteria(true, false);
  private readonly actionType = signal<SecurityActionType>('ISIN_CHANGE');
  private selectedParent: Security | null = null;
  private selectedChild: Security | null = null;
  private lookupRevision = 0;
  private priceRevision = 0;

  constructor() {
    super(HelpIds.HELP_BASEDATA_SECURITY_ACTION_ISIN_RENAME);
  }

  override helpLink(): void {
    if (this.actionType() === 'SPIN_OFF') {
      this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_BASEDATA_SECURITY_ACTION_SPIN_OFF);
    } else {
      super.helpLink();
    }
  }

  get titleKey(): string {
    return this.actionToEdit()
      ? 'EDIT_SPIN_OFF'
      : this.actionType() === 'SPIN_OFF'
        ? 'CREATE_SPIN_OFF'
        : 'CREATE_ISIN_CHANGE';
  }

  ngOnInit(): void {
    const editing = this.actionToEdit() !== null;
    this.actionType.set(this.actionToEdit()?.actionType ?? this.initialType());
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('actionType', true, {
        defaultValue: this.actionType(),
        disabled: editing
      }),
      DynamicFieldHelper.createFieldInputButtonHeqF(DataType.String, 'isinOld', () => this.selectSecurity(false), true),
      DynamicFieldHelper.createFieldInputStringVSHeqF('isinNew', 12, true, ['ISIN']),
      DynamicFieldHelper.createFieldInputButtonHeqF(
        DataType.String,
        'securityOld',
        () => this.selectSecurity(false),
        true,
        { disabled: editing }
      ),
      DynamicFieldHelper.createFieldInputButtonHeqF(
        DataType.String,
        'securityNew',
        () => this.selectSecurity(true),
        false,
        { disabled: editing }
      ),
      DynamicFieldHelper.createFieldInputStringVSHeqF('childIsin', 12, false, ['ISIN'], { upperCase: true }),
      DynamicFieldHelper.createFieldSelectStringHeqF('childCurrency', false),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'actionDate', true, { disabled: editing }),
      DynamicFieldHelper.createFieldInputNumberHeqF('fromFactor', false, 10, 0, false, { disabled: editing }),
      DynamicFieldHelper.createFieldInputNumberHeqF('toFactor', false, 10, 0, false, { disabled: editing }),
      DynamicFieldHelper.createFieldInputNumberHeqF('costAllocation', true, 2, 8, false, {
        fieldSuffix: '%',
        labelHelpText: 'SPIN_OFF_ALLOCATION_HELP'
      }),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('costAllocationSource', 255, false),
      DynamicFieldHelper.createFieldSelectStringHeqF('taxTreatmentDe', true),
      DynamicFieldHelper.createFieldInputNumberHeqF('costAllocationDe', false, 2, 8, false, { fieldSuffix: '%' }),
      DynamicFieldHelper.createFieldSelectStringHeqF('historyBasis', false, {
        labelHelpText: 'SPIN_OFF_HISTORY_HELP'
      }),
      DynamicFieldHelper.createFieldInputNumberHeqF('historyFactor', false, 3, 8, false),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', 1024, false),
      DynamicFieldHelper.createFunctionButtonFieldName('lookup', 'SPIN_OFF_LOOKUP_CHILD', () => this.previewChild()),
      DynamicFieldHelper.createFunctionButtonFieldName('allocation', 'SPIN_OFF_PROPOSE_ALLOCATION', () =>
        this.proposeAllocation()
      ),
      DynamicFieldHelper.createFunctionButtonFieldName('providerSplits', 'SPIN_OFF_PROVIDER_SPLITS', () =>
        this.loadProviderSplits()
      ),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    const existing = this.actionToEdit();
    this.selectedParent = existing?.securityOld ?? null;
    this.selectedChild = existing?.securityNew ?? null;
    this.busy.set(false);
    this.form().setDefaultValuesAndEnableSubmit();
    if (existing) {
      this.form().transferBusinessObjectToForm({
        ...existing,
        securityOld: this.securityLabel(existing.securityOld),
        securityNew: this.securityLabel(existing.securityNew),
        costAllocation: existing.costAllocation == null ? null : existing.costAllocation * 100,
        costAllocationDe: existing.costAllocationDe == null ? null : existing.costAllocationDe * 100,
        historyBasis: existing.historyBasisOrigin === 'ADMINISTRATOR' ? existing.historyBasis : 'AUTOMATIC',
        historyFactor: existing.historyBasisOrigin === 'ADMINISTRATOR' ? existing.historyFactor : null
      });
    }
    this.updateFields();
    this.control('historyBasis')
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe(() => this.updateHistoryFactor());
    this.control('actionType')
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((type: SecurityActionType) => {
        this.actionType.set(type);
        this.invalidateLookup();
        this.updateFields();
      });
    merge(
      ...['securityOld', 'childIsin', 'childCurrency', 'actionDate'].map((field) => this.control(field).valueChanges)
    )
      .pipe(this.untilDialogClosed())
      .subscribe(() => this.invalidateLookup());
    merge(...['securityNew', 'fromFactor', 'toFactor'].map((field) => this.control(field).valueChanges))
      .pipe(this.untilDialogClosed())
      .subscribe(() => this.invalidatePrices());
    forkJoin({
      types: this.securityActionService.getActionTypes(),
      taxTreatments: this.securityActionService.getSpinOffTaxTreatments(),
      historyBases: this.securityActionService.getSpinOffHistoryBases(),
      currencies: this.currencies.getCurrencies()
    })
      .pipe(this.untilDialogClosed())
      .subscribe(({ types, taxTreatments, historyBases, currencies }) => {
        this.configObject.actionType.valueKeyHtmlOptions = this.translatedOptions(types);
        this.configObject.taxTreatmentDe.valueKeyHtmlOptions = this.translatedOptions(taxTreatments);
        this.configObject.historyBasis.valueKeyHtmlOptions = this.translatedOptions(historyBases);
        this.configObject.childCurrency.valueKeyHtmlOptions = currencies;
      });
    if (existing?.actionType === 'SPIN_OFF') {
      this.loadProviderSplits();
    }
  }

  /** The history factor is entered only for an adjusted history basis set by the administrator. */
  private updateHistoryFactor(): void {
    const adjusted =
      this.actionToEdit()?.actionType === 'SPIN_OFF' && this.control('historyBasis').value === 'ADJUSTED';
    const field = this.configObject.historyFactor;
    if (!adjusted) {
      this.control('historyFactor').setValue(null, { emitEvent: false });
    }
    this.control('historyFactor')[adjusted ? 'enable' : 'disable']({ emitEvent: false });
    DynamicFieldHelper.resetValidator(field, adjusted ? [Validators.required, Validators.min(1.000001)] : []);
    this.viewChanged();
  }

  /** Reads the provider splits and the history basis of the parent around the ex-date from the server. */
  private loadProviderSplits(): void {
    if (this.busy()) return;
    const idSecurityOld = this.selectedParent?.idSecuritycurrency;
    const actionDate = this.actionDate();
    if (idSecurityOld == null || !actionDate) {
      this.showInfo('SPIN_OFF_PROVIDER_SPLITS_INPUT');
      return;
    }
    const revision = this.lookupRevision;
    this.busy.set(true);
    this.securityActionService
      .getSpinOffProviderSplits(idSecurityOld, actionDate)
      .pipe(
        this.untilDialogClosed(),
        finalize(() => this.busy.set(false))
      )
      .subscribe({
        next: (providerSplits) => {
          if (revision === this.lookupRevision) this.providerSplits.set(providerSplits);
        },
        error: () => {}
      });
  }

  private translatedOptions(options: ValueKeyHtmlSelectOptions[]): ValueKeyHtmlSelectOptions[] {
    return options.map(({ key, value }) => ({ key, value: this.translateService.instant(value) }));
  }

  private control(field: string) {
    return requireFormControl(this.configObject[field]);
  }

  private updateFields(): void {
    const spinOff = this.actionType() === 'SPIN_OFF';
    const editing = this.actionToEdit() !== null;
    for (const name of ['isinOld', 'isinNew']) {
      this.configObject[name].invisible = spinOff;
      this.control(name)[spinOff ? 'disable' : 'enable']({ emitEvent: false });
    }
    for (const name of [
      'securityOld',
      'securityNew',
      'childIsin',
      'childCurrency',
      'costAllocation',
      'costAllocationSource',
      'taxTreatmentDe',
      'costAllocationDe'
    ]) {
      const lookupField = name === 'childIsin' || name === 'childCurrency';
      const instrumentField = name === 'securityOld' || name === 'securityNew';
      const visible = spinOff && (!lookupField || !editing);
      this.configObject[name].invisible = !visible;
      this.control(name)[visible && !(editing && instrumentField) ? 'enable' : 'disable']({ emitEvent: false });
    }
    this.configObject.lookup.invisible = !spinOff || editing;
    this.configObject.allocation.invisible = !spinOff;
    this.configObject.providerSplits.invisible = !spinOff;
    const historyVisible = spinOff && editing;
    this.configObject.historyBasis.invisible = !historyVisible;
    this.configObject.historyFactor.invisible = !historyVisible;
    this.control('historyBasis')[historyVisible ? 'enable' : 'disable']({ emitEvent: false });
    this.updateHistoryFactor();
    const ratioHelp = spinOff ? this.translateService.instant('SPIN_OFF_RATIO_HELP') : null;
    for (const name of ['fromFactor', 'toFactor']) {
      const field = this.configObject[name];
      field.labelHelpText = ratioHelp;
      DynamicFieldHelper.resetValidator(field, spinOff ? [Validators.required, Validators.min(1)] : []);
    }
    this.viewChanged();
  }

  private invalidateLookup(): void {
    this.lookupRevision++;
    this.lookupChild.set(null);
    this.providerSplits.set(null);
    this.invalidatePrices();
  }

  private invalidatePrices(): void {
    this.priceRevision++;
    this.allocationProposal.set(null);
  }

  private securityLabel(security: Security | null): string {
    return security ? [security.name, security.isin, security.currency].filter(Boolean).join(' · ') : '';
  }

  private selectSecurity(child: boolean): void {
    const ref = this.dialogService.open(SecuritycurrencySearchAndSetDialogComponent, {
      header: this.translateService.instant('SET_SECURITY'),
      width: '720px',
      resizable: false,
      closable: true,
      closeOnEscape: true,
      focusOnShow: false,
      data: { supplementCriteria: this.supplementCriteria }
    });
    ref?.onClose.pipe(this.untilDialogClosed()).subscribe((security: Security | null) => {
      if (!security) return;
      if (child) {
        this.selectedChild = security;
        this.control('childIsin').setValue(null);
        this.control('childCurrency').setValue('');
        this.control('securityNew').setValue(this.securityLabel(security));
        this.invalidateLookup();
        this.updateFields();
      } else {
        this.selectedParent = security;
        this.control('isinOld').setValue(security.isin);
        this.control('securityOld').setValue(this.securityLabel(security));
      }
    });
  }

  private actionDate(): string | null {
    return Helper.formatDateStringAsString(this.configObject.actionDate, BaseSettings.FORMAT_DATE_SHORT_NATIVE);
  }

  private showInfo(key: string): void {
    this.messageToastService.showMessageI18n(InfoLevelType.INFO, key);
  }

  /** Whether parent, ex-date, a valid child ISIN and its trading currency are given for the child lookup. */
  private childLookupInputComplete(): boolean {
    return (
      this.selectedParent?.idSecuritycurrency != null &&
      !!this.actionDate() &&
      !!this.control('childIsin').value &&
      !!this.control('childCurrency').value &&
      !this.control('childIsin').invalid
    );
  }

  /**
   * Looks up the child by ISIN and trading currency, as the lookup button does. The server returns a complete
   * instrument, either the existing one or the proposal from the data providers.
   *
   * @param onFound continues with the found child, used by a save without a preceding lookup; omitted for the button
   * @param onFailed called when the input is incomplete or the lookup fails
   */
  private previewChild(onFound?: (child: Security) => void, onFailed?: () => void): void {
    if (this.busy()) return;
    const parentId = this.selectedParent?.idSecuritycurrency;
    const date = this.actionDate();
    const isin: string | null = this.control('childIsin').value;
    const currency: string | null = this.control('childCurrency').value;
    if (parentId == null || !date || !isin || !currency || this.control('childIsin').invalid) {
      this.showInfo('SPIN_OFF_CHILD_LOOKUP_INPUT');
      onFailed?.();
      return;
    }
    const revision = this.lookupRevision;
    this.busy.set(true);
    this.securityActionService
      .previewSpinOffChild(parentId, isin.trim().toUpperCase(), currency, date)
      .pipe(
        this.untilDialogClosed(),
        finalize(() => this.busy.set(false))
      )
      .subscribe({
        next: (child) => {
          if (revision === this.lookupRevision) {
            this.selectedChild = null;
            this.control('securityNew').setValue(null, { emitEvent: false });
            this.invalidatePrices();
            this.lookupChild.set(child);
            // finalize clears busy only after this handler; the continuation must not see the lookup as running
            this.busy.set(false);
            onFound?.(child);
          } else {
            onFailed?.();
          }
        },
        error: () => onFailed?.()
      });
  }

  connectorNames(child: Security): string {
    return [
      ...new Set(
        [child.idConnectorHistory, child.idConnectorIntra, child.idConnectorDividend, child.idConnectorSplit].filter(
          Boolean
        )
      )
    ].join(', ');
  }

  private proposeAllocation(): void {
    if (this.busy()) return;
    const idSecurityOld = this.selectedParent?.idSecuritycurrency;
    const idSecurityNew = (this.selectedChild ?? this.lookupChild())?.idSecuritycurrency;
    const actionDate = this.actionDate();
    const fromFactor = this.control('fromFactor').value;
    const toFactor = this.control('toFactor').value;
    if (idSecurityOld == null || idSecurityNew == null || !actionDate || !(fromFactor > 0 && toFactor > 0)) {
      this.showInfo('SPIN_OFF_ALLOCATION_INPUT');
      return;
    }
    const revision = this.priceRevision;
    this.busy.set(true);
    this.securityActionService
      .proposeSpinOffAllocation({ idSecurityOld, idSecurityNew, actionDate, fromFactor, toFactor })
      .pipe(
        this.untilDialogClosed(),
        finalize(() => this.busy.set(false))
      )
      .subscribe({
        next: (proposal) => {
          if (revision === this.priceRevision) this.allocationProposal.set(proposal);
        },
        error: () => {}
      });
  }

  useProposal(proposal: SpinOffAllocationProposal): void {
    if (proposal.proposedAllocation == null) return;
    this.control('costAllocation').setValue(proposal.proposedAllocation * 100);
    this.control('costAllocationSource').setValue(
      'Closes of ' +
        proposal.priceDate +
        ': ' +
        proposal.parentClose +
        ' / ' +
        proposal.childClose +
        ', FX ' +
        proposal.fxChildToParent
    );
  }

  private allocationFraction(percent: number | undefined, stored?: number | null): number | null {
    if (percent == null) return null;
    return stored != null && percent === stored * 100 ? stored : percent / 100;
  }

  submit(value: SecurityActionFormValue): void {
    const editing = this.actionToEdit();
    const spinOff = this.actionType() === 'SPIN_OFF';
    const actionDate = this.actionDate();
    if (
      !editing &&
      spinOff &&
      !this.busy() &&
      !this.selectedChild &&
      !this.lookupChild() &&
      this.childLookupInputComplete()
    ) {
      // Only ISIN and trading currency are given: look the child up first, then save with the complete instrument
      this.previewChild(
        () => this.submit(value),
        () => (this.configObject.submit.disabled = false)
      );
      return;
    }
    const child = this.selectedChild ?? this.lookupChild();
    if (!actionDate || this.busy() || (spinOff && !child)) {
      if (spinOff && !child) this.showInfo('SPIN_OFF_CHILD_REQUIRED');
      this.configObject.submit.disabled = false;
      return;
    }
    const allocation = {
      costAllocation: this.allocationFraction(value.costAllocation, editing?.costAllocation),
      costAllocationSource: value.costAllocationSource || null,
      taxTreatmentDe: value.taxTreatmentDe || null,
      costAllocationDe: this.allocationFraction(value.costAllocationDe, editing?.costAllocationDe),
      note: value.note || null
    };
    const action: SecurityAction = editing
      ? { ...editing, ...allocation, ...this.historyBasisRequest(editing, value) }
      : {
          idSecurityAction: null,
          actionType: this.actionType(),
          securityOld: this.selectedParent,
          securityNew: spinOff ? child : null,
          isinOld: spinOff ? (this.selectedParent?.isin ?? null) : (value.isinOld ?? null),
          isinNew: spinOff ? (child?.isin ?? null) : (value.isinNew ?? null),
          actionDate,
          fromFactor: value.fromFactor ?? null,
          toFactor: value.toFactor ?? null,
          affectedCount: 0,
          appliedCount: 0,
          createdBy: null,
          creationTime: null,
          ...allocation
        };
    if (editing) {
      this.confirmationService.confirm({
        header: this.translateService.instant('EDIT_SPIN_OFF'),
        message: this.translateService.instant('SPIN_OFF_CORRECTION_CONFIRM'),
        accept: () => this.save(action, ProcessedAction.UPDATED),
        reject: () => (this.configObject.submit.disabled = false)
      });
    } else {
      this.save(action, ProcessedAction.CREATED);
    }
  }

  /**
   * Translates the history basis setting into the fields the server evaluates: a setting is sent with origin
   * ADMINISTRATOR, automatic with the stored automatic origin, or without origin when the administrator's setting is
   * to be withdrawn.
   */
  private historyBasisRequest(
    editing: SecurityAction,
    value: SecurityActionFormValue
  ): Pick<SecurityAction, 'historyBasis' | 'historyFactor' | 'historyBasisOrigin'> {
    const setting = value.historyBasis ?? 'AUTOMATIC';
    if (setting === 'AUTOMATIC') {
      return {
        historyBasis: editing.historyBasis ?? null,
        historyFactor: editing.historyFactor ?? null,
        historyBasisOrigin: editing.historyBasisOrigin === 'ADMINISTRATOR' ? null : (editing.historyBasisOrigin ?? null)
      };
    }
    return {
      historyBasis: setting,
      historyFactor: setting === 'ADJUSTED' ? (value.historyFactor ?? null) : null,
      historyBasisOrigin: 'ADMINISTRATOR'
    };
  }

  private save(action: SecurityAction, processed: ProcessedAction): void {
    // Saving a newly proposed child repeats its lookup at the data providers, which can take several seconds
    this.busy.set(true);
    this.securityActionService
      .update(action)
      .pipe(
        this.untilDialogClosed(),
        finalize(() => this.busy.set(false))
      )
      .subscribe({
        next: () => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
            i18nRecord: this.titleKey
          });
          this.closeDialog.emit(new ProcessedActionData(processed));
        },
        error: () => (this.configObject.submit.disabled = false)
      });
  }
}
