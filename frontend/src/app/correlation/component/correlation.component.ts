import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, Subscription } from 'rxjs';
import {
  afterNextRender,
  AfterViewInit,
  Component,
  computed,
  effect,
  inject,
  Injector,
  OnDestroy,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { SingleRecordMasterViewBase } from '../../lib/masterdetail/component/single.record.master.view.base';
import {
  CorrelationLimit,
  CorrelationResult,
  CorrelationSet,
  SamplingPeriodType
} from '../../entities/correlation.set';
import { MenuItem } from '@openng/optimus-ui/api';
import { ActivatedRoute } from '@angular/router';
import { HelpIds } from '../../lib/help/help.ids';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { CorrelationSetService } from '../service/correlation.set.service';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { ChildToParent, CorrelationTableComponent } from './correlation-table.component';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { Securitycurrency } from '../../entities/securitycurrency';
import { Security } from '../../entities/security';
import { AppHelper } from '../../lib/helper/app.helper';
import { CorrelationEditingSupport } from './correlation.editing.support';

import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { PanelModule } from '@openng/optimus-ui/panel';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { ProgressBarModule } from '@openng/optimus-ui/progressbar';
import { httpResource } from '@angular/common/http';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { CorrelationSetEditComponent } from './correlation-set-edit.component';

/**
 * Main component for correlation set management. Supports the creation, deletion, and calculation of correlation sets
 * with financial instruments. Provides a form interface for correlation configuration and displays correlation matrices
 * and rolling correlation charts.
 */
@Component({
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      #cmDiv
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <p-panel>
        <p-header>
          <h4 class="ui-widget-header singleRowTableHeader">
            {{ 'CORRELATION_MATRIX' | translate }}
          </h4>
        </p-header>
      </p-panel>
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="saveAndCalculate($event)" />
      <p-contextMenu [target]="cmDiv" [model]="contextMenuItems" />
      <br />
      @if (calculation.isLoading()) {
        <div class="progress-bar-box">
          <h4>{{ 'LOADING' | translate }}</h4>
          <p-progressBar mode="indeterminate" [style]="{ height: '6px' }" />
        </div>
      } @else if (calculation.error()) {
        <p>{{ 'DATA_LOAD_FAILED' | translate }}</p>
      }
      @if (timePeriod()) {
        <p>
          {{ 'TIME_PERIOD' | translate }} {{ timePeriod() }}
          {{ 'INSTRUMENT_OVERLAPPING_PRICE_DATA' | translate }}
        </p>
      }
      @if (nonOverlappingDates()) {
        <p style="color:tomato;">{{ 'NON_OVERLAPPING_DATES' | translate }}</p>
      }
      @if (dividendMissingNames()) {
        <p style="color:tomato;">{{ 'CORRELATION_DIVIDEND_MISSING' | translate }} {{ dividendMissingNames() }}</p>
      }
      <correlation-table [childToParent]="this" />
    </div>
    @if (visibleEditDialog) {
      <correlation-set-edit
        [visibleDialog]="visibleEditDialog"
        [callParam]="callParam"
        [correlationLimit]="correlationLimit()"
        (closeDialog)="handleCloseEditDialog($event)" />
    }
  `,
  imports: [
    FormsModule,
    TranslateModule,
    PanelModule,
    ContextMenuModule,
    ProgressBarModule,
    ...DYNAMIC_FORM_IMPORTS,
    CorrelationTableComponent,
    CorrelationSetEditComponent
  ]
})
export class CorrelationComponent
  extends SingleRecordMasterViewBase<CorrelationSet, Securitycurrency, CallParam>
  implements AfterViewInit, OnDestroy, ChildToParent
{
  private activatedRoute: ActivatedRoute = inject(ActivatedRoute);
  private correlationSetService: CorrelationSetService = inject(CorrelationSetService);

  /** Primary key field name for correlation set selection */
  private static readonly MAIN_FIELD = 'idCorrelationSet';

  /** Reference to the correlation table child component */
  readonly correlationTableComponent = viewChild.required(CorrelationTableComponent);

  /** Formatted time period string showing date range of overlapping price data */
  readonly timePeriod = computed(() => {
    const result = this.correlationResult();
    return result?.firstAvailableDate
      ? AppHelper.getDateByFormat(this.gps, result.firstAvailableDate) +
          ' - ' +
          AppHelper.getDateByFormat(this.gps, result.lastAvailableDate)
      : null;
  });

  /** Flag indicating whether instruments have non-overlapping date ranges */
  readonly nonOverlappingDates = computed(() => {
    const result = this.correlationResult();
    return !!result && !result.firstAvailableDate;
  });

  /**
   * Names of the distributing securities without dividend data in the period, when dividends are included. Their total
   * return equals their price return, which the user is told below the time period.
   */
  readonly dividendMissingNames = computed(() => {
    const result = this.correlationResult();
    return this.selectedEntity && result ? this.getDividendMissingNames(this.selectedEntity, result) : null;
  });

  /** Field name for ticker symbol property */
  private readonly tickerSymbol = 'tickerSymbol';

  /** Correlation limits and configuration settings */
  readonly correlationLimit = signal<CorrelationLimit | null>(null);

  /**
   * Correlation set whose matrix is calculated, undefined when nothing is to be calculated. Each publication is a new
   * object, so saving or refreshing the same set calculates it again.
   */
  private readonly calculationTarget = signal<{ correlationSet: CorrelationSet; id: number } | undefined>(undefined);

  /** Matrix of the calculation target. A newer target cancels a request that is still pending. */
  readonly calculation = httpResource<CorrelationResult>(() => {
    const target = this.calculationTarget();
    return target && this.correlationSetService.getCalculationByCorrelationSetRequest(target.id);
  });

  /** Current correlation calculation results */
  private readonly correlationResult = computed(() => (this.calculation.hasValue() ? this.calculation.value() : null));
  private readonly injector = inject(Injector);
  private readSubscription?: Subscription;
  private correlationMenuSubscription?: Subscription;

  /** Helper class for correlation form configuration and value changes */
  private correlationEditingSupport: CorrelationEditingSupport = new CorrelationEditingSupport();

  /**
   * Creates a new correlation component instance with required services and configuration.
   */
  constructor() {
    super(
      HelpIds.HELP_WATCHLIST_CORRELATION,
      CorrelationComponent.MAIN_FIELD,
      'CORRELATION_SET',
      inject(CorrelationSetService)
    );
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 2, undefined, true);
    this.config = this.correlationEditingSupport.getCorrelationFieldDefinition(
      CorrelationComponent.MAIN_FIELD,
      6,
      'SAVE_AND_CALC'
    );
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.configObject.samplingPeriod.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService,
      SamplingPeriodType
    );
    reportResourceErrors(this.calculation, this.correlationSetService);
    effect(() => {
      const result = this.correlationResult();
      const target = this.calculationTarget();
      if (result && target) {
        untracked(() => this.correlationTableComponent().parentSelectionChanged(target.correlationSet, result));
      }
    });
  }

  /**
   * Angular lifecycle hook called after view initialization. Loads correlation limits and sets up form configuration.
   */
  ngAfterViewInit(): void {
    this.correlationSetService
      .getCorrelationSetLimit()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((correlationLimit: CorrelationLimit) => {
        this.correlationLimit.set(correlationLimit);
        this.readData();
        this.valueChangedMainField();
        this.correlationEditingSupport.setUpValueChange(this.configObject, correlationLimit);
      });
  }

  /**
   * Refreshes component data when correlation set is updated externally or when instruments are added/removed.
   * @param correlationSet - Updated correlation set or null to reload from service
   */
  refreshData(correlationSet: CorrelationSet | null): void {
    if (correlationSet) {
      if (this.selectedEntity?.idCorrelationSet !== correlationSet.idCorrelationSet) return;
      this.selectedEntity = Object.assign(new CorrelationSet(), this.selectedEntity, {
        securitycurrencyList: [...correlationSet.securitycurrencyList]
      });
      this.setChildDataAndCalculateWhenPossible(this.selectedEntity, true);
    } else {
      this.readData();
    }
  }

  /**
   * Gets localized period and rolling parameter strings for chart display.
   * @returns Array containing translated period and rolling parameters with prefixes
   */
  getPeriodAndRollingWithParamPrefix(): string[] {
    return this.correlationEditingSupport.getPeriodAndRollingWithParamPrefix(this.configObject);
  }

  /**
   * Loads correlation sets from the backend and updates form dropdown options.
   */
  override readData(): void {
    this.readSubscription?.unsubscribe();
    this.readSubscription = this.correlationSetService
      .getCorrelationSetByTenant()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((correlationSets: CorrelationSet[]) => {
        this.entityList = correlationSets;
        this.correlationLimit.update(
          (limit) =>
            limit && {
              ...limit,
              tenantLimit: { ...limit.tenantLimit, actual: this.entityList.length }
            }
        );
        this.configObject[CorrelationComponent.MAIN_FIELD].valueKeyHtmlOptions =
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
            CorrelationComponent.MAIN_FIELD,
            'name',
            correlationSets,
            false
          );

        afterNextRender(() => this.setFieldValues(), { injector: this.injector });
      });
  }

  /**
   * Saves correlation set configuration and triggers calculation of correlation matrix.
   * @param event - Form submission event
   */
  saveAndCalculate(event: unknown): void {
    const form = this.form();
    if (!form) return;
    const edited = Object.assign(new CorrelationSet(), this.selectedEntity);
    form.cleanMaskAndTransferValuesToBusinessObject(edited);
    this.correlationSetService
      .update(edited)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (cs) => {
          const updated = Object.assign(new CorrelationSet(), edited, cs);
          this.entityList = this.entityList.map((set) =>
            set.idCorrelationSet === updated.idCorrelationSet ? updated : set
          );
          if (this.selectedEntity?.idCorrelationSet === updated.idCorrelationSet) {
            this.selectedEntity = updated;
            this.setChildData(updated);
          }
          this.configObject.submit.disabled = false;
        },
        error: (errorBackend) => (this.configObject.submit.disabled = false)
      });
  }

  /**
   * Angular lifecycle hook for cleanup. Destroys form helper and calls parent cleanup.
   */
  ngOnDestroy(): void {
    super.destroy();
    this.correlationEditingSupport.destroy();
    this.correlationMenuSubscription?.unsubscribe();
  }

  /**
   * Updates child table component when parent correlation set selection changes.
   * @param selectedEntity - The newly selected correlation set
   */
  override setChildData(selectedEntity: CorrelationSet | null): void {
    this.setChildDataAndCalculateWhenPossible(selectedEntity, true);
  }

  /**
   * Determines if new correlation sets can be created based on tenant limits.
   * @returns True if creation is allowed, false if limit is reached
   */
  protected override canCreate(): boolean {
    const limit = this.correlationLimit()?.tenantLimit;
    return !!limit && limit.actual < limit.limit;
  }

  /**
   * Sets child data and optionally triggers correlation calculation based on instrument count and overlapping dates.
   * @param selectedEntity - The correlation set to display
   * @param calculate - Whether to perform correlation calculation
   */
  setChildDataAndCalculateWhenPossible(selectedEntity: CorrelationSet | null, calculate: boolean): void {
    this.childEntityList = selectedEntity?.securitycurrencyList ?? [];
    this.correlationTableComponent().parentSelectionChanged(selectedEntity, null);
    this.calculationTarget.set(
      selectedEntity &&
        selectedEntity.idCorrelationSet != null &&
        selectedEntity.securitycurrencyList.length >= 2 &&
        calculate
        ? { correlationSet: selectedEntity, id: selectedEntity.idCorrelationSet }
        : undefined
    );
  }

  /**
   * Joins the names of the securities the correlation result reports as lacking dividend data.
   * @param correlationSet - The calculated correlation set
   * @param correlationResult - The result of the calculation
   * @returns The comma separated names, or null when nothing is missing
   */
  private getDividendMissingNames(correlationSet: CorrelationSet, correlationResult: CorrelationResult): string | null {
    const ids = correlationResult.idsMissingDividends ?? [];
    const names = correlationSet.securitycurrencyList
      .filter((sc) => sc.idSecuritycurrency != null && ids.includes(sc.idSecuritycurrency))
      .map((sc) => (<Security>sc).name);
    return names.length > 0 ? names.join(', ') : null;
  }

  /**
   * Refreshes context menus by combining edit and show menu items from base class and child table component.
   */
  override refreshMenus(): void {
    this.correlationMenuSubscription?.unsubscribe();
    this.correlationMenuSubscription = forkJoin({
      editMenu: TranslateHelper.translateMenuItems$(this.prepareEditMenu(), this.translateService),
      showMenu: TranslateHelper.translateMenuItems$(this.prepareShowMenu(), this.translateService)
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((menus) => {
        this.contextMenuItems = [...menus.editMenu, ...menus.showMenu];
        this.activePanelService.activatePanel(this, menus);
      });
  }

  /**
   * Prepares edit menu items by combining base edit menu with correlation table-specific edit options.
   * @returns Array of translated menu items for editing operations
   */
  override prepareEditMenu(): MenuItem[] {
    const menuItems = this.getBaseEditMenu('CORRELATION_SET');
    menuItems.push(...this.correlationTableComponent().prepareEditMenu());
    return menuItems;
  }

  /**
   * Prepares show menu items from the correlation table component.
   * @returns Array of translated menu items for view operations
   */
  protected override prepareShowMenu(): MenuItem[] {
    const menuItems = this.correlationTableComponent().prepareShowMenu();
    return menuItems;
  }

  /**
   * Prepares call parameters for opening edit dialogs.
   * @param entity - The correlation set entity to edit or null for new creation
   */
  protected prepareCallParam(entity: CorrelationSet | null): void {
    this.callParam = new CallParam(null, entity);
  }
}
