import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  OnInit,
  signal,
  untracked,
  viewChild,
  input
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { httpResource } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProgressBarModule } from '@openng/optimus-ui/progressbar';
import { MenuItem } from '@openng/optimus-ui/api';
import { FormBase } from '../../lib/edit/form.base';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppHelper } from '../../lib/helper/app.helper';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { SeasonalPeriodType, SeasonalReturnsResult } from '../../entities/seasonal.returns.result';
import { AppHelpIds } from '../../shared/help/help.ids';
import { SeasonalityTableComponent } from './seasonality-table.component';
import { TimeSeriesParam } from '../model/time.series.param';

/** Inputs of one seasonal return matrix: the instrument from the route and the three form controls. */
interface SeasonalReturnsParams {
  idSecuritycurrency: number;
  periodType: SeasonalPeriodType;
  includeDividends: boolean;
  inTenantCurrency: boolean;
}

/**
 * Seasonality heat map for a single security or currency pair. Peer of the EOD chart and the history-quote table in
 * the lower display area: it reads the selected instrument from the route and shows a matrix of period returns.
 *
 * The three form inputs (period granularity, include dividends, convert to main currency) reload the matrix on every
 * change. The include-dividends and currency toggles are disabled when the analysed instrument does not support them,
 * driven by the capability flags returned with the result.
 */
@Component({
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm" />

      @if (seasonalReturns.isLoading()) {
        <div class="progress-bar-box">
          <h4>{{ 'LOADING' | translate }}</h4>
          <p-progressBar mode="indeterminate" [style]="{ height: '6px' }" />
        </div>
      } @else if (seasonalReturns.error()) {
        <p>{{ 'DATA_LOAD_FAILED' | translate }}</p>
      }

      @if (result(); as result) {
        <p>
          <strong>{{ 'CURRENCY' | translate }}:</strong> {{ result.currency }}
        </p>
        @if (result.yearRows.length === 0) {
          <p>{{ 'NO_DATA_AVAILABLE' | translate }}</p>
        }
      }

      <seasonality-table [result]="result()" />
    </div>
  `,
  imports: [TranslateModule, ...DYNAMIC_FORM_IMPORTS, ProgressBarModule, SeasonalityTableComponent]
})
export class SeasonalityComponent extends FormBase implements OnInit, IGlobalMenuAttach {
  readonly allParam = input<string>();

  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private securityService: SecurityService = inject(SecurityService);
  private gps: GlobalparameterService = inject(GlobalparameterService);
  public translateService: TranslateService = inject(TranslateService);

  readonly form = viewChild.required(DynamicFormComponent);

  menuItems: MenuItem[] = [];

  /** Inputs of the displayed matrix; undefined until both the instrument and the form controls are known. */
  private readonly seasonalParams = signal<SeasonalReturnsParams | undefined>(undefined);

  /** Matrix of the current inputs. Every new inputs object cancels a request that is still pending. */
  readonly seasonalReturns = httpResource<SeasonalReturnsResult>(() => {
    const params = this.seasonalParams();
    return (
      params &&
      this.securityService.getSeasonalReturnsRequest(
        params.idSecuritycurrency,
        params.periodType,
        params.includeDividends,
        params.inTenantCurrency
      )
    );
  });

  readonly result = computed(() => (this.seasonalReturns.hasValue() ? this.seasonalReturns.value() : null));

  private idSecuritycurrency?: number;
  private formReady = false;
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    super();
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, null, false);
    this.formConfig.labelColumns = 2;
    // The dynamic-form builds its FormGroup in its own ngOnInit, so the form controls exist after the first render.
    afterNextRender(() => this.initializeControls());
    reportResourceErrors(this.seasonalReturns, this.securityService);
    effect(() => {
      const result = this.result();
      if (result) {
        untracked(() => this.applyCapabilities(result));
      }
    });

    effect(() => {
      const params: TimeSeriesParam[] = JSON.parse(this.allParam() ?? '[]');
      untracked(() => {
        this.idSecuritycurrency = params?.[0]?.idSecuritycurrency;
        this.maybeReload();
      });
    });
  }

  ngOnInit(): void {
    this.createInputFormDefinition();
  }

  private initializeControls(): void {
    requireFormControl(this.configObject.periodType).setValue(SeasonalPeriodType.MONTHLY);
    requireFormControl(this.configObject.includeDividends).setValue(false);
    requireFormControl(this.configObject.inTenantCurrency).setValue(false);
    ['periodType', 'includeDividends', 'inTenantCurrency'].forEach((name) =>
      requireFormControl(this.configObject[name])
        .valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.reload())
    );
    this.formReady = true;
    this.maybeReload();
  }

  private maybeReload(): void {
    if (this.formReady && this.idSecuritycurrency != null) {
      this.reload();
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event: PanelClickEvent): void {
    this.activePanelService.activatePanel(this, { showMenu: this.menuItems });
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return AppHelpIds.HELP_WATCHLIST_SEASONALITY_HEATMAP;
  }

  private createInputFormDefinition(): void {
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('periodType', true, {
        usedLayoutColumns: 4
      }),
      DynamicFieldHelper.createFieldCheckboxHeqF('includeDividends', {
        usedLayoutColumns: 4
      }),
      DynamicFieldHelper.createFieldCheckboxHeqF('inTenantCurrency', {
        usedLayoutColumns: 4
      })
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    // SeasonalPeriodType is a string enum; the enum→options helper only handles numeric enums, so build the two
    // options manually with the enum names as keys (which the backend expects as request values).
    this.translateService
      .get(['MONTHLY', 'QUARTERLY'])
      .subscribe(
        (t) =>
          (this.configObject.periodType.valueKeyHtmlOptions = [
            new ValueKeyHtmlSelectOptions(SeasonalPeriodType.MONTHLY, t['MONTHLY']),
            new ValueKeyHtmlSelectOptions(SeasonalPeriodType.QUARTERLY, t['QUARTERLY'])
          ])
      );
  }

  /** Publishes the current inputs as a new object, so even unchanged inputs request the matrix again. */
  private reload(): void {
    const idSecuritycurrency = this.idSecuritycurrency;
    if (idSecuritycurrency == null) {
      this.seasonalParams.set(undefined);
      return;
    }
    this.seasonalParams.set({
      idSecuritycurrency,
      periodType: requireFormControl(this.configObject.periodType).value,
      includeDividends: !!requireFormControl(this.configObject.includeDividends).value,
      inTenantCurrency: !!requireFormControl(this.configObject.inTenantCurrency).value
    });
  }

  /** Enables or disables the dividend and currency toggles based on what the instrument supports. */
  private applyCapabilities(result: SeasonalReturnsResult): void {
    this.toggleControl('includeDividends', result.dividendsAvailable);
    this.toggleControl('inTenantCurrency', result.currencyConversionAvailable);
    // The toggles change without events, which the form does not notice by itself.
    this.form().notifier.notify();
  }

  private toggleControl(name: string, enabled: boolean): void {
    const fc = requireFormControl(this.configObject[name]);
    if (enabled) {
      fc.enable({ emitEvent: false });
    } else {
      fc.setValue(false, { emitEvent: false });
      fc.disable({ emitEvent: false });
    }
  }
}
