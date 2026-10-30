import { requireFieldName, requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import {
  Component,
  OnInit,
  afterNextRender,
  AfterRenderRef,
  inject,
  Injector,
  signal,
  viewChild,
  input
} from '@angular/core';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { SimpleFormEditBase } from '../../lib/edit/simple.form.edit.base';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { HelpIds } from '../../lib/help/help.ids';
import { Security } from '../../entities/security';
import { AppHelper } from '../../lib/helper/app.helper';
import { SecurityDerived, SecurityEditSupport } from '../../shared/securitycurrency/security.edit.support';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { SecuritycurrencySearchAndSetDialogComponent } from './securitycurrency-search-and-set-dialog.component';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { combineLatest, Observable } from 'rxjs';
import { Stockexchange } from '../../entities/stockexchange';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { Assetclass } from '../../entities/assetclass';
import { IFeedConnector } from '../../shared/securitycurrency/ifeed.connector';
import { StockexchangeService } from '../../stockexchange/service/stockexchange.service';
import { AssetclassService } from '../../assetclass/service/assetclass.service';
import { DistributionFrequency } from '../../shared/types/distribution.frequency';
import { ProposeChangeEntityWithEntity } from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import { CurrencypairWatchlist } from '../../entities/view/currencypair.watchlist';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { SecurityService } from '../service/security.service';
import { AssetclassType } from '../../shared/types/assetclass.type';
import { SupplementCriteria } from '../model/supplement.criteria';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { SecurityDerivedLink } from '../../entities/security.derived.link';
import { SecurityCurrencypairDerivedLinks } from '../model/security.currencypair.derived.links';
import { AppSettings } from '../../shared/app.settings';
import { FormHelper } from '../../lib/dynamic-form/components/FormHelper';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DialogService, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';

/**
 * To create a derived instrument a base instrument is required. Additionally, a formula can be added. Prices depend on other instrument,
 * so there is no connector.
 */
@Component({
  selector: 'security-derived-edit',
  template: `
    <p-dialog
      header="{{ 'DERIVED_DATA' | translate }}"
      [visible]="visibleDialog()"
      [style]="{ width: '600px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <dynamic-form
        [config]="formFields()"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)" />
    </p-dialog>
  `,
  imports: [TranslateModule, DialogModule, DynamicFormComponent],
  providers: [DialogService]
})
export class SecurityDerivedEditComponent extends SimpleFormEditBase implements OnInit {
  public translateService: TranslateService = inject(TranslateService);
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private stockexchangeService: StockexchangeService = inject(StockexchangeService);
  private assetclassService: AssetclassService = inject(AssetclassService);
  private securityService: SecurityService = inject(SecurityService);
  private dialogService: DialogService = inject(DialogService);

  // Input from parent component
  readonly securityCallParam = input<Security>(undefined);
  readonly proposeChangeEntityWithEntity = input<ProposeChangeEntityWithEntity>(undefined);

  // Access child components
  readonly dynamicForm = viewChild.required(DynamicFormComponent);

  readonly derivedData = 'DERIVED_DATA';
  supplementCriteria: SupplementCriteria;
  readonly formFields = signal<FieldConfig[]>([]);
  private readonly injector = inject(Injector);
  private pendingRender: AfterRenderRef;
  private instrumentDialog: DynamicDialogRef;

  private baseInstrument: Security | CurrencypairWatchlist;
  private additionalInstruments: {
    [fieldName: string]: Security | CurrencypairWatchlist;
  } = {};
  private securityEditSupport: SecurityEditSupport;

  private readonly BASE_PRODUCT_NAME = 'baseProductName';

  private readonly FORMULA_PRICES = 'formulaPrices';
  private usedFormulaVars: string[];

  constructor() {
    super(HelpIds.HELP_WATCHLIST_DERIVED_INSTRUMENT);
    this.supplementCriteria = new SupplementCriteria(false, true);
    this.destroyRef.onDestroy(() => {
      this.pendingRender?.destroy();
      this.instrumentDialog?.close();
      this.securityEditSupport?.destroy();
    });
  }

  ngOnInit(): void {
    this.securityEditSupport = new SecurityEditSupport(this.translateService, this.gps, null);
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));

    const fields = [
      DynamicFieldHelper.createFieldInputButtonHeqF(
        DataType.String,
        this.BASE_PRODUCT_NAME,
        this.chooseInstrument,
        true,
        { fieldsetName: this.derivedData }
      ),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF(this.FORMULA_PRICES, 255, false, {
        fieldsetName: this.derivedData
      })
    ];

    fields.push(...SecurityEditSupport.getSecurityBaseFieldDefinition(SecurityDerived.Derived, this.gps));
    fields.push(...SecurityEditSupport.getIntraHistoryFieldDefinition(SecurityDerived.Derived, this.gps));
    fields.push(...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this));

    this.formFields.set(fields);
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, fields);
    const frequencies = Object.keys(DistributionFrequency).filter((key) => Number.isNaN(Number(key)));
    this.translateService
      .get(frequencies)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((texts) => {
        this.configObject.distributionFrequency.valueKeyHtmlOptions = frequencies
          .map((key) => new ValueKeyHtmlSelectOptions(key, texts[key]))
          .sort((a, b) => a.value.localeCompare(b.value));
      });
  }

  readonly chooseInstrument = (field: FieldConfig): void => this.handleSecurityClick(field);

  override onShow(event: unknown): void {
    this.renewDialogSession();
    this.securityEditSupport.destroy();
    this.baseInstrument = null;
    this.additionalInstruments = {};
    this.usedFormulaVars = [];
    this.addInvestmentInstrumentFieldRow();
    this.scheduleAfterRender(() => this.initialize());
  }

  private scheduleAfterRender(work: () => void): void {
    this.pendingRender?.destroy();
    this.pendingRender = afterNextRender(work, { injector: this.injector });
  }

  disableBaseInstrumentFields(): void {
    if (!AuditHelper.hasRightsForEditingOrDeleteAuditable(this.gps, this.securityCallParam())) {
      this.formFields()
        .filter((fieldConfig) => fieldConfig.fieldsetName === this.derivedData)
        .forEach((fieldConfig) => requireFormControl(fieldConfig).disable());
    }
  }

  valueChangedOnFormula(): void {
    requireFormControl(this.configObject[this.FORMULA_PRICES])
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((formulaPrices: string) => {
        if (!requireFormControl(this.configObject[this.FORMULA_PRICES]).disabled) {
          this.usedFormulaVars = [];
          if (formulaPrices) {
            let match = SecurityCurrencypairDerivedLinks.VAR_NAME_REGEX.exec(formulaPrices);
            while (match != null) {
              this.usedFormulaVars.push(match[1]);
              match = SecurityCurrencypairDerivedLinks.VAR_NAME_REGEX.exec(formulaPrices);
            }
          }
          this.usedFormulaVars = [...new Set(this.usedFormulaVars)];
          this.addInvestmentInstrumentFieldRow();
          this.reduceExpandChoosableAssetclass();
        }
      });
  }

  handleSecurityClick(fieldConfig: FieldConfig): void {
    const targetField = requireFieldName(fieldConfig);
    this.translateService
      .get('SET_SECURITY')
      .pipe(this.untilDialogClosed())
      .subscribe((title) => {
        this.instrumentDialog?.close();
        const ref = (this.instrumentDialog = this.dialogService.open(SecuritycurrencySearchAndSetDialogComponent, {
          header: title,
          width: '720px',
          resizable: false,
          modal: true,
          closable: true,
          closeOnEscape: true,
          data: { supplementCriteria: this.supplementCriteria }
        }));
        if (!ref) return;
        ref.onClose.pipe(this.untilDialogClosed()).subscribe((security: Security | CurrencypairWatchlist) => {
          if (security) {
            if (targetField === this.BASE_PRODUCT_NAME) {
              this.setSecurityBaseInstrument(security);
            } else {
              this.additionalInstruments[targetField] = security;
            }
            requireFormControl(this.configObject[targetField]).setValue(security.name);
          }
        });
      });
  }

  submit(value: { [name: string]: any }): void {
    const security = this.securityEditSupport.prepareForSave(
      this,
      this.proposeChangeEntityWithEntity(),
      this.securityCallParam(),
      this.dynamicForm(),
      value
    );
    if (this.baseInstrument) {
      security.idLinkSecuritycurrency = this.baseInstrument.idSecuritycurrency;
    }
    this.translateFormulaFromUserLanguage(security);
    this.setSecurityDerivedLinks(security);
    this.securityService
      .update(security)
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: (newSecurity) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
            i18nRecord: AppSettings.SECURITY.toUpperCase()
          });
          this.closeDialog.emit(
            new ProcessedActionData(
              this.securityCallParam() ? ProcessedAction.UPDATED : ProcessedAction.CREATED,
              newSecurity
            )
          );
        },
        error: () => (this.configObject.submit.disabled = false)
      });
  }

  override onHide(event: unknown): void {
    this.securityEditSupport.destroy();
    this.pendingRender?.destroy();
    super.onHide(event);
    this.instrumentDialog?.close();
  }

  protected initialize(): void {
    this.securityEditSupport.registerValueOnChanged(SecurityDerived.Derived, this.configObject);
    this.valueChangedOnFormula();
    const observables: Observable<
      | Stockexchange[]
      | ValueKeyHtmlSelectOptions[]
      | Assetclass[]
      | IFeedConnector[]
      | SecurityCurrencypairDerivedLinks
      | boolean
    >[] = [];
    observables.push(this.stockexchangeService.getAllStockexchanges(false));
    observables.push(this.gpsGT.getCurrencies());
    observables.push(this.assetclassService.getAllAssetclass());
    // The offer of stock exchanges is narrowed to the own kind once this instrument carries prices or transactions.
    let idxCategoryLocked = -1;
    let idxDerivedLinks = -1;
    const securityCallParam = this.securityCallParam();
    if (securityCallParam) {
      idxCategoryLocked = observables.length;
      observables.push(this.securityService.isStockexchangeCategoryLocked(securityCallParam.idSecuritycurrency));
      idxDerivedLinks = observables.length;
      observables.push(
        this.securityService.getDerivedInstrumentsLinksForSecurity(securityCallParam.idSecuritycurrency)
      );
    }

    combineLatest(observables)
      .pipe(this.untilDialogClosed())
      .subscribe((data: any[]) => {
        this.securityEditSupport.assignLoadedValues(
          this.configObject,
          <Stockexchange[]>data[0],
          <ValueKeyHtmlSelectOptions[]>data[1],
          <Assetclass[]>data[2]
        );
        const securityCallParamValue = this.securityCallParam();
        this.securityEditSupport.setStockexchangeOptions(
          this.configObject,
          idxCategoryLocked >= 0 && <boolean>data[idxCategoryLocked]
            ? securityCallParamValue.stockexchange.noMarketValue
            : null
        );
        this.form().setDefaultValuesAndEnableSubmit();
        AuditHelper.transferToFormAndChangeButtonForProposaleEdit(
          this.translateService,
          this.gps,
          securityCallParamValue,
          this.form(),
          this.configObject,
          this.proposeChangeEntityWithEntity()
        );
        securityCallParamValue &&
          this.setInstrumentsForExistingSecurity(<SecurityCurrencypairDerivedLinks>data[idxDerivedLinks]);
        this.disableEnableInputForExisting(!!securityCallParamValue);
      });
  }

  private addInvestmentInstrumentFieldRow(): void {
    const fieldConfig: FieldConfig[] = [];
    this.usedFormulaVars.forEach((ufv: string) => {
      if (
        SecurityCurrencypairDerivedLinks.ALLOWED_VAR_NAMES.indexOf(ufv) >= 0 &&
        ufv !== SecurityCurrencypairDerivedLinks.ALLOWED_VAR_NAMES[0]
      ) {
        const fieldName = SecurityCurrencypairDerivedLinks.ADDITIONAL_INSTRUMENT_NAME + '_' + ufv;

        if (!this.configObject[fieldName]) {
          fieldConfig.push(
            DynamicFieldHelper.createFieldInputButton(
              DataType.String,
              fieldName,
              'ADDITIONAL_INSTRUMENT_NAME',
              this.chooseInstrument,
              true,
              { fieldsetName: this.derivedData, labelSuffix: `(${ufv})` }
            )
          );
        }
      }
    });

    const retained = this.formFields().filter(
      (field) =>
        !field.field?.startsWith(SecurityCurrencypairDerivedLinks.ADDITIONAL_INSTRUMENT_NAME + '_') ||
        this.usedFormulaVars.includes(field.field.slice(-1))
    );
    if (fieldConfig.length || retained.length !== this.formFields().length) {
      const fields = [...retained.slice(0, 2), ...fieldConfig, ...retained.slice(2)];
      this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, fields);
      this.formFields.set(fields);
    }
  }

  private setInstrumentsForExistingSecurity(scdl: SecurityCurrencypairDerivedLinks): void {
    this.setBaseInstrumentForExistingSecurity(scdl);
    this.scheduleAfterRender(() => this.setAdditionalInstrumentsForExistingSecurity(scdl));
  }

  private setBaseInstrumentForExistingSecurity(scdl: SecurityCurrencypairDerivedLinks): void {
    this.configObject[this.BASE_PRODUCT_NAME].disabled = true;
    const idLink = this.securityCallParam().idLinkSecuritycurrency;
    if (idLink == null) return;
    const baseInstrument = SecurityCurrencypairDerivedLinks.getBaseInstrument(scdl, idLink);
    if (!baseInstrument) {
      requireFormControl(this.configObject[this.BASE_PRODUCT_NAME]).setValue(null);
      return;
    }
    this.baseInstrument = baseInstrument;
    this.reduceExpandChoosableAssetclass();
    requireFormControl(this.configObject[this.BASE_PRODUCT_NAME]).setValue(this.baseInstrument.name);
  }

  private setAdditionalInstrumentsForExistingSecurity(scdl: SecurityCurrencypairDerivedLinks): void {
    this.additionalInstruments = SecurityCurrencypairDerivedLinks.getAdditionalInstrumentsForExistingSecurity(scdl);
    for (const [fieldName, securitycurrency] of Object.entries(this.additionalInstruments)) {
      requireFormControl(this.configObject[fieldName]).setValue(securitycurrency.name);
    }
    this.disableBaseInstrumentFields();
  }

  private reduceExpandChoosableAssetclass(): void {
    const formula: string = requireFormControl(this.configObject[this.FORMULA_PRICES]).value;
    if (
      this.baseInstrument &&
      this.baseInstrument.assetClass.categoryType === AssetclassType[AssetclassType.CURRENCY_PAIR] &&
      (!formula || formula.length === 0)
    ) {
      this.securityEditSupport.filterAssetclasses(this.configObject, AssetclassType.CURRENCY_PAIR);
    } else {
      this.securityEditSupport.removeFilterAssetclass(this.configObject);
    }
  }

  private setSecurityBaseInstrument(security: Security | CurrencypairWatchlist): void {
    this.baseInstrument = security;
    if (security instanceof CurrencypairWatchlist) {
      requireFormControl(this.configObject.currency).setValue((<CurrencypairWatchlist>security).fromCurrency);
      this.reduceExpandChoosableAssetclass();
    } else {
      this.form().transferBusinessObjectToForm(security);
    }
  }

  private disableEnableInputForExisting(disable: boolean): void {
    FormHelper.disableEnableFieldConfigsWhenAlreadySet(disable, [this.configObject.currency]);
  }

  private translateFormulaFromUserLanguage(security: Security): void {
    if (this.gps.getDecimalSymbol() !== '.' && security.formulaPrices) {
      security.formulaPrices = security.formulaPrices.split(this.gps.getDecimalSymbol()).join('.');
    }
  }

  private setSecurityDerivedLinks(security: Security): void {
    security.securityDerivedLinks = [];
    if (this.usedFormulaVars) {
      this.usedFormulaVars.forEach((ufv: string) => {
        if (
          SecurityCurrencypairDerivedLinks.ALLOWED_VAR_NAMES.indexOf(ufv) >= 0 &&
          ufv !== SecurityCurrencypairDerivedLinks.ALLOWED_VAR_NAMES[0]
        ) {
          const fieldName = SecurityCurrencypairDerivedLinks.ADDITIONAL_INSTRUMENT_NAME + '_' + ufv;
          const securityCallParam = this.securityCallParam();
          security.securityDerivedLinks.push(
            new SecurityDerivedLink(
              securityCallParam ? securityCallParam.idSecuritycurrency : null,
              ufv,
              this.additionalInstruments[fieldName].idSecuritycurrency
            )
          );
        }
      });
    }
  }
}
