import {
  afterNextRender,
  AfterRenderRef,
  DestroyRef,
  effect,
  inject,
  Injector,
  signal,
  Component,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  untracked,
  viewChild
} from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { forkJoin, map, Subscription, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Security, SecurityBondTerms, SecuritySimulationMetadata } from '../../entities/security';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { InputType } from '../../lib/dynamic-form/models/input.type';
import { FormBase } from '../../lib/edit/form.base';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { CouponDayCountDefaults, TaxDataService } from '../../taxdata/service/tax-data.service';

/**
 * Reads the annual coupon rate from the name of a bond or convertible bond. A number marked with a percent sign wins
 * wherever it stands, e.g. "voestalpine3.75%GreenN.24-29/1" or "0% Sonova Holding AG 19-29". Without a percent sign
 * only a number at the start of the name counts, e.g. "2.875 CFFL 10-30", because an unmarked number further on is
 * usually part of the term, e.g. "Royal Bank 0.20 21-31". Mirrors SecurityBondTerms.couponRateFromName of the backend,
 * which fills the yield-to-maturity field; keep both in step.
 *
 * @param name - The security name, may be null
 * @returns The coupon rate in percent, e.g. 3.75, or null when the name carries none
 */
export function extractCouponRateFromSecurityName(name: string): number | null {
  const match =
    /(?<![\d.,])(\d{1,4}(?:[.,]\d+)?)\s*%/.exec(name ?? '') ?? /^\s*(\d{1,4}(?:[.,]\d+)?)(?=\s|%|$)/.exec(name ?? '');
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
  imports: [DynamicFormComponent, TranslateModule],
  template: `
    <p>{{ 'SIMULATION_BOND_TERMS_HELP' | translate }}</p>
    @if (ready()) {
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" />
    }
  `
})
export class SecurityBondTermsComponent extends FormBase implements OnInit, OnChanges, OnDestroy {
  @Input() entity: Security;
  @Input() securityName: string;
  /** Currency of the edited security, the basis of the proposed day-count convention. */
  @Input() currency: string;

  readonly ready = signal(false);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private initializeAfterRender: AfterRenderRef;
  readonly form = viewChild(DynamicFormComponent);
  private couponRateSubscribe: Subscription;
  private applyingCouponRatePrefill = false;
  private couponRateOverridden = false;
  private lastCouponRatePrefill: number = null;
  private couponDayCountDefaults: CouponDayCountDefaults;
  private couponDayCountSubscribe: Subscription;
  private applyingCouponDayCountPrefill = false;
  private couponDayCountOverridden = false;
  private lastCouponDayCountPrefill: string = null;

  constructor(
    public translateService: TranslateService,
    private gps: GlobalparameterService,
    private taxDataService: TaxDataService
  ) {
    super();
    effect(() => {
      if (this.form()) {
        untracked(() => this.scheduleInitialization());
      }
    });
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 3, null);
    forkJoin([
      this.gps.getEntityFormDefinition('SecurityBondTerms'),
      this.taxDataService
        .taxOptions('coupondaycounts')
        .pipe(
          switchMap((options) =>
            this.translateService
              .get(options.map((option) => option.value))
              .pipe(
                map((texts) => [
                  new ValueKeyHtmlSelectOptions('', ''),
                  ...options.map((option) => new ValueKeyHtmlSelectOptions(option.key, texts[option.value]))
                ])
              )
          )
        ),
      this.taxDataService.getCouponDayCountDefaults()
    ])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([descriptor, conventions, couponDayCountDefaults]) => {
        this.config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
          this.translateService,
          descriptor,
          '',
          false
        ) as FieldConfig[];
        this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
        this.configObject.couponDayCount.inputType = InputType.Select;
        this.configObject.couponDayCount.valueKeyHtmlOptions = [...conventions].sort((a, b) =>
          a.value.localeCompare(b.value)
        );
        this.couponDayCountDefaults = couponDayCountDefaults;
        this.ready.set(true);
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.entity && this.form()) {
      this.scheduleInitialization();
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
    this.initializeAfterRender?.destroy();
    this.couponRateSubscribe?.unsubscribe();
    this.couponDayCountSubscribe?.unsubscribe();
  }

  transfer(target: Security): boolean {
    if (!this.form()) {
      return false;
    }
    this.form().form.markAllAsTouched();
    if (this.form().form.invalid) {
      return false;
    }
    const bondTerms: SecurityBondTerms = {};
    this.form().cleanMaskAndTransferValuesToBusinessObject(bondTerms, true);
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

  /** Replaces pending form initialization when inputs or the rendered form change. */
  private scheduleInitialization(): void {
    this.initializeAfterRender?.destroy();
    this.initializeAfterRender = afterNextRender(() => this.initializeForm(), { injector: this.injector });
  }

  private initializeForm(): void {
    if (!this.form() || !this.configObject) {
      return;
    }
    this.couponRateSubscribe?.unsubscribe();
    this.couponDayCountSubscribe?.unsubscribe();
    this.form().setDefaultValuesAndEnableSubmit();
    const storedTerms = this.entity?.simulationMetadata?.bondTerms;
    if (storedTerms) {
      this.form().transferBusinessObjectToForm(storedTerms);
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
