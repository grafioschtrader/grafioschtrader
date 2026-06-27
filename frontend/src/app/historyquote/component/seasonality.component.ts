import {AfterViewInit, Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute} from '@angular/router';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ProgressBarModule} from 'primeng/progressbar';
import {MenuItem} from 'primeng/api';
import {Subscription} from 'rxjs';
import {FormBase} from '../../lib/edit/form.base';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppHelper} from '../../lib/helper/app.helper';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {SeasonalPeriodType, SeasonalReturnsResult} from '../../entities/seasonal.returns.result';
import {AppHelpIds} from '../../shared/help/help.ids';
import {SeasonalityTableComponent} from './seasonality-table.component';

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
    <div class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm">
      </dynamic-form>

      @if (loading) {
        <div class="progress-bar-box">
          <h4>{{ 'LOADING' | translate }}</h4>
          <p-progressBar mode="indeterminate" [style]="{'height': '6px'}"></p-progressBar>
        </div>
      }

      @if (result) {
        <p><strong>{{ 'CURRENCY' | translate }}:</strong> {{ result.currency }}</p>
      }

      <seasonality-table [result]="result"></seasonality-table>
    </div>
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule, DynamicFormModule, ProgressBarModule, SeasonalityTableComponent]
})
export class SeasonalityComponent extends FormBase implements OnInit, AfterViewInit, OnDestroy, IGlobalMenuAttach {

  @ViewChild(DynamicFormComponent, {static: true}) form: DynamicFormComponent;

  result: SeasonalReturnsResult = null;
  loading = false;
  menuItems: MenuItem[] = [];

  private idSecuritycurrency: number;
  private formReady = false;
  private routeSubscribe: Subscription;
  private valueChangeSubscriptions: Subscription[] = [];

  constructor(private activatedRoute: ActivatedRoute,
    private activePanelService: ActivePanelService,
    private securityService: SecurityService,
    private gps: GlobalparameterService,
    public translateService: TranslateService) {
    super();
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, null, false);
    this.formConfig.labelColumns = 2;
  }

  ngOnInit(): void {
    this.createInputFormDefinition();
    this.routeSubscribe = this.activatedRoute.paramMap.subscribe(paramMap => {
      const paramObject = AppHelper.createParamObjectFromParamMap(paramMap);
      this.idSecuritycurrency = paramObject.allParam?.[0]?.idSecuritycurrency;
      this.maybeReload();
    });
  }

  ngAfterViewInit(): void {
    // The dynamic-form builds its FormGroup in its own ngOnInit (after this component's ngOnInit), so the form
    // controls only exist now. Defer to a microtask to avoid ExpressionChangedAfterItHasBeenChecked errors.
    Promise.resolve().then(() => {
      this.configObject.periodType.formControl.setValue(SeasonalPeriodType.MONTHLY);
      this.configObject.includeDividends.formControl.setValue(false);
      this.configObject.inTenantCurrency.formControl.setValue(false);
      ['periodType', 'includeDividends', 'inTenantCurrency'].forEach(name =>
        this.valueChangeSubscriptions.push(
          this.configObject[name].formControl.valueChanges.subscribe(() => this.reload())));
      this.formReady = true;
      this.maybeReload();
    });
  }

  private maybeReload(): void {
    if (this.formReady && this.idSecuritycurrency != null) {
      this.reload();
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    this.activePanelService.activatePanel(this, {showMenu: this.menuItems});
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): string {
    return AppHelpIds.HELP_WATCHLIST_SEASONALITY_HEATMAP;
  }

  ngOnDestroy(): void {
    this.routeSubscribe && this.routeSubscribe.unsubscribe();
    this.valueChangeSubscriptions.forEach(s => s.unsubscribe());
  }

  private createInputFormDefinition(): void {
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('periodType', true, {usedLayoutColumns: 4}),
      DynamicFieldHelper.createFieldCheckboxHeqF('includeDividends', {usedLayoutColumns: 4}),
      DynamicFieldHelper.createFieldCheckboxHeqF('inTenantCurrency', {usedLayoutColumns: 4})
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    // SeasonalPeriodType is a string enum; the enum→options helper only handles numeric enums, so build the two
    // options manually with the enum names as keys (which the backend expects as request values).
    this.translateService.get(['MONTHLY', 'QUARTERLY']).subscribe(t =>
      this.configObject.periodType.valueKeyHtmlOptions = [
        new ValueKeyHtmlSelectOptions(SeasonalPeriodType.MONTHLY, t['MONTHLY']),
        new ValueKeyHtmlSelectOptions(SeasonalPeriodType.QUARTERLY, t['QUARTERLY'])
      ]);
  }

  private reload(): void {
    this.loading = true;
    const periodType: SeasonalPeriodType = this.configObject.periodType.formControl.value;
    const includeDividends = !!this.configObject.includeDividends.formControl.value;
    const inTenantCurrency = !!this.configObject.inTenantCurrency.formControl.value;
    this.securityService.getSeasonalReturns(this.idSecuritycurrency, periodType, includeDividends, inTenantCurrency)
      .subscribe(result => {
        this.result = result;
        this.applyCapabilities(result);
        this.loading = false;
      });
  }

  /** Enables or disables the dividend and currency toggles based on what the instrument supports. */
  private applyCapabilities(result: SeasonalReturnsResult): void {
    this.toggleControl('includeDividends', result.dividendsAvailable);
    this.toggleControl('inTenantCurrency', result.currencyConversionAvailable);
  }

  private toggleControl(name: string, enabled: boolean): void {
    const fc = this.configObject[name].formControl;
    if (enabled) {
      fc.enable({emitEvent: false});
    } else {
      fc.setValue(false, {emitEvent: false});
      fc.disable({emitEvent: false});
    }
  }
}
