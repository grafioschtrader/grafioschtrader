import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { forkJoin, Subscription } from 'rxjs';
import { Security, SecurityBondTerms, SecuritySimulationMetadata } from '../../entities/security';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { InputType } from '../../lib/dynamic-form/models/input.type';
import { FormBase } from '../../lib/edit/form.base';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { CouponDayCountDefaults, TaxDataService } from '../../taxdata/service/tax-data.service';

export function extractCouponRateFromSecurityName(name: string): number | null {
  const match = /^\s*(\d+(?:[.,]\d+)?)\s*%?/.exec(name ?? '');
  if (!match) {
    return null;
  }
  const couponRate = Number(match[1].replace(',', '.'));
  return Number.isFinite(couponRate) ? couponRate : null;
}

/**
 * Looks up the day-count convention the backend proposes for bonds in the given currency.
 *
 * @param defaults - The conventions per currency served by the backend, may still be unloaded
 * @param currency - ISO 4217 code of the security currency, case-insensitive
 * @returns The proposed convention, or null when the currency or the defaults are not available yet
 */
export function defaultCouponDayCount(defaults: CouponDayCountDefaults, currency: string): string | null {
  const code = currency?.trim().toUpperCase();
  if (!defaults || !code) {
    return null;
  }
  return defaults.byCurrency?.[code] ?? defaults.fallback ?? null;
}

/**
 * Simulation-only terms for a regular fixed-rate direct bond. The coupon rate is proposed from the security name and
 * the day-count convention from the security currency. A proposal only fills an empty field or replaces its own
 * earlier proposal; once the user changes a field, or it was already stored, it is left untouched.
 */
@Component({
  selector: 'security-bond-terms',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DynamicFormComponent, TranslateModule],
  template: `
    <p>{{ 'SIMULATION_BOND_TERMS_HELP' | translate }}</p>
    @if (ready) {
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" />
    }
  `
})
export class SecurityBondTermsComponent extends FormBase implements OnInit, OnChanges, OnDestroy {
  @Input() entity: Security;
  @Input() securityName: string;
  /** Currency of the edited security, the basis of the proposed day-count convention. */
  @Input() currency: string;

  ready = false;
  private dynamicForm: DynamicFormComponent;
  private couponRateSubscribe: Subscription;
  private applyingCouponRatePrefill = false;
  private couponRateOverridden = false;
  private lastCouponRatePrefill: number = null;
  private couponDayCountDefaults: CouponDayCountDefaults;
  private couponDayCountSubscribe: Subscription;
  private applyingCouponDayCountPrefill = false;
  private couponDayCountOverridden = false;
  private lastCouponDayCountPrefill: string = null;

  @ViewChild(DynamicFormComponent) set form(form: DynamicFormComponent) {
    this.dynamicForm = form;
    if (form) {
      queueMicrotask(() => this.initializeForm());
    }
  }

  constructor(
    public translateService: TranslateService,
    private gps: GlobalparameterService,
    private taxDataService: TaxDataService
  ) {
    super();
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 3, null);
    forkJoin([
      this.gps.getEntityFormDefinition('SecurityBondTerms'),
      this.taxDataService.taxOptions('coupondaycounts'),
      this.taxDataService.getCouponDayCountDefaults()
    ]).subscribe(([descriptor, conventions, couponDayCountDefaults]) => {
      this.config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
        this.translateService,
        descriptor,
        '',
        false
      ) as FieldConfig[];
      this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
      this.configObject.couponDayCount.inputType = InputType.Select;
      this.configObject.couponDayCount.valueKeyHtmlOptions =
        SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(this.translateService, conventions, true);
      this.couponDayCountDefaults = couponDayCountDefaults;
      this.ready = true;
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.entity && this.dynamicForm) {
      queueMicrotask(() => this.initializeForm());
      return;
    }
    if (changes.securityName) {
      this.prefillCouponRate();
    }
    if (changes.currency) {
      this.prefillCouponDayCount();
    }
  }

  ngOnDestroy(): void {
    this.couponRateSubscribe?.unsubscribe();
    this.couponDayCountSubscribe?.unsubscribe();
  }

  transfer(target: Security): boolean {
    if (!this.dynamicForm) {
      return false;
    }
    this.dynamicForm.form.markAllAsTouched();
    if (this.dynamicForm.form.invalid) {
      return false;
    }
    const bondTerms: SecurityBondTerms = {};
    this.dynamicForm.cleanMaskAndTransferValuesToBusinessObject(bondTerms, true);
    if (bondTerms.couponDayCount === '') {
      bondTerms.couponDayCount = null;
    }
    const hasBondTerms = bondTerms.couponRate != null || !!bondTerms.couponDayCount;
    const metadata: SecuritySimulationMetadata = { ...(target.simulationMetadata ?? {}) };
    if (hasBondTerms) {
      metadata.bondTerms = bondTerms;
    } else {
      delete metadata.bondTerms;
    }
    target.simulationMetadata = Object.keys(metadata).length ? metadata : null;
    return true;
  }

  private initializeForm(): void {
    if (!this.dynamicForm || !this.configObject) {
      return;
    }
    this.couponRateSubscribe?.unsubscribe();
    this.couponDayCountSubscribe?.unsubscribe();
    this.dynamicForm.setDefaultValuesAndEnableSubmit();
    const storedTerms = this.entity?.simulationMetadata?.bondTerms;
    if (storedTerms) {
      this.dynamicForm.transferBusinessObjectToForm(storedTerms);
    }
    this.couponRateOverridden = storedTerms?.couponRate != null;
    this.lastCouponRatePrefill = null;
    this.prefillCouponRate();
    this.couponRateSubscribe = this.configObject.couponRate.formControl.valueChanges.subscribe((couponRate) => {
      if (!this.applyingCouponRatePrefill && couponRate !== this.lastCouponRatePrefill) {
        this.couponRateOverridden = true;
      }
    });
    this.couponDayCountOverridden = !!storedTerms?.couponDayCount;
    this.lastCouponDayCountPrefill = null;
    this.prefillCouponDayCount();
    this.couponDayCountSubscribe = this.configObject.couponDayCount.formControl.valueChanges.subscribe(
      (couponDayCount) => {
        if (!this.applyingCouponDayCountPrefill && couponDayCount !== this.lastCouponDayCountPrefill) {
          this.couponDayCountOverridden = true;
        }
      }
    );
  }

  private prefillCouponRate(): void {
    const couponRateControl = this.configObject?.couponRate?.formControl;
    if (!couponRateControl || this.couponRateOverridden) {
      return;
    }
    const couponRate = extractCouponRateFromSecurityName(this.securityName);
    const currentValue = couponRateControl.value;
    if (currentValue != null && currentValue !== '' && currentValue !== this.lastCouponRatePrefill) {
      this.couponRateOverridden = true;
      return;
    }
    this.applyingCouponRatePrefill = true;
    couponRateControl.setValue(couponRate);
    this.lastCouponRatePrefill = couponRate;
    this.applyingCouponRatePrefill = false;
  }

  private prefillCouponDayCount(): void {
    const couponDayCountControl = this.configObject?.couponDayCount?.formControl;
    if (!couponDayCountControl || this.couponDayCountOverridden) {
      return;
    }
    const couponDayCount = defaultCouponDayCount(this.couponDayCountDefaults, this.currency);
    const currentValue = couponDayCountControl.value;
    if (currentValue != null && currentValue !== '' && currentValue !== this.lastCouponDayCountPrefill) {
      this.couponDayCountOverridden = true;
      return;
    }
    this.applyingCouponDayCountPrefill = true;
    couponDayCountControl.setValue(couponDayCount);
    this.lastCouponDayCountPrefill = couponDayCount;
    this.applyingCouponDayCountPrefill = false;
  }
}
