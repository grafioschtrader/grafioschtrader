import {AfterViewInit, Component, OnDestroy, ViewChild} from '@angular/core';
import {SingleRecordMasterViewBase} from '../../shared/masterdetail/component/single.record.master.view.base';
import {CorrelationLimit, CorrelationResult, CorrelationSet, SamplingPeriodType} from '../../entities/correlation.set';
import {ConfirmationService, MenuItem} from 'primeng/api';
import {ActivatedRoute} from '@angular/router';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {HelpIds} from '../../lib/help/help.ids';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {CorrelationSetService} from '../service/correlation.set.service';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {ChildToParent, CorrelationTableComponent} from './correlation-table.component';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {Securitycurrency} from '../../entities/securitycurrency';
import {AppHelper} from '../../lib/helper/app.helper';
import {CorrelationEditingSupport} from './correlation.editing.support';

/**
 * Main component for correlation set management. Supports the creation, deletion, and calculation of correlation sets
 * with financial instruments. Provides a form interface for correlation configuration and displays correlation matrices
 * and rolling correlation charts.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-panel>
        <p-header>
          <h4 class="ui-widget-header singleRowTableHeader">{{ 'CORRELATION_MATRIX' | translate }}</h4>
        </p-header>
      </p-panel>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="saveAndCalculate($event)">
      </dynamic-form>
      <p-contextMenu [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
      <br/>
      @if (timePeriod) {
        <p>{{ 'TIME_PERIOD' | translate }} {{ timePeriod }} {{ 'INSTRUMENT_OVERLAPPING_PRICE_DATA' | translate }}</p>
      }
      @if (nonOverlappingDates) {
        <p style="color:tomato;">{{ 'NON_OVERLAPPING_DATES' | translate }}</p>
      }
      <correlation-table [childToParent]="this">
      </correlation-table>
    </div>
    @if (visibleEditDialog) {
      <correlation-set-edit [visibleDialog]="visibleEditDialog"
                            [callParam]="callParam"
                            [correlationLimit]="correlationLimit"
                            (closeDialog)="handleCloseEditDialog($event)">
      </correlation-set-edit>
    }
  `,
  standalone: false
})
export class CorrelationComponent extends SingleRecordMasterViewBase<CorrelationSet, Securitycurrency>
  implements AfterViewInit, OnDestroy, ChildToParent {

  /** Primary key field name for correlation set selection */
  private static readonly MAIN_FIELD = 'idCorrelationSet';

  /** Reference to the correlation table child component */
  @ViewChild(CorrelationTableComponent, {static: true}) correlationTableComponent: CorrelationTableComponent;

  /** Formatted time period string showing date range of overlapping price data */
  timePeriod: string = null;

  /** Flag indicating whether instruments have non-overlapping date ranges */
  nonOverlappingDates = false;

  /** Field name for ticker symbol property */
  private readonly tickerSymbol = 'tickerSymbol';

  /** Correlation limits and configuration settings */
  correlationLimit: CorrelationLimit;

  /** Current correlation calculation results */
  private correlationResult: CorrelationResult;

  /** Helper class for correlation form configuration and value changes */
  private correlationEditingSupport: CorrelationEditingSupport = new CorrelationEditingSupport();

  /**
   * Creates a new correlation component instance with required services and configuration.
   * @param activatedRoute - Angular activated route for parameter access
   * @param correlationSetService - Service for correlation set operations
   * @param gps - Global parameter service for user settings and configuration
   * @param confirmationService - PrimeNG confirmation dialog service
   * @param messageToastService - Service for displaying user messages
   * @param activePanelService - Service for managing active panel state
   * @param translateService - Angular translation service for internationalization
   */
  constructor(private activatedRoute: ActivatedRoute,
    private correlationSetService: CorrelationSetService,
    gps: GlobalparameterService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    translateService: TranslateService) {

    super(gps, HelpIds.HELP_WATCHLIST_CORRELATION,
      CorrelationComponent.MAIN_FIELD,
      'CORRELATION_SET', correlationSetService, confirmationService, messageToastService, activePanelService,
      translateService);
    this.formConfig = this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      2, null, true);
    this.config = this.correlationEditingSupport.getCorrelationFieldDefinition(CorrelationComponent.MAIN_FIELD, 6, 'SAVE_AND_CALC');
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.configObject.samplingPeriod.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, SamplingPeriodType, null);
  }

  /**
   * Angular lifecycle hook called after view initialization. Loads correlation limits and sets up form configuration.
   */
  ngAfterViewInit(): void {
    this.correlationSetService.getCorrelationSetLimit().subscribe((correlationLimit: CorrelationLimit) => {
      this.correlationLimit = correlationLimit;
      this.readData();
      this.valueChangedMainField();
      this.correlationEditingSupport.setUpValueChange(this.configObject, this.correlationLimit);
    });
  }

  /**
   * Refreshes component data when correlation set is updated externally or when instruments are added/removed.
   * @param correlationSet - Updated correlation set or null to reload from service
   */
  refreshData(correlationSet: CorrelationSet): void {
    if (correlationSet) {
      this.selectedEntity.securitycurrencyList = correlationSet.securitycurrencyList;
      this.setChildDataAndCalculateWhenPossible(correlationSet, this.nonOverlappingDates);
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
    this.correlationSetService.getCorrelationSetByTenant().subscribe((correlationSets: CorrelationSet[]) => {
      this.entityList = correlationSets;
      this.correlationLimit.tenantLimit.actual = this.entityList.length;
      this.configObject[CorrelationComponent.MAIN_FIELD].valueKeyHtmlOptions =
        SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(CorrelationComponent.MAIN_FIELD, 'name',
          correlationSets, false);
      this.selectedEntity && (this.selectedEntity = this.entityList.find(entity => entity[CorrelationComponent.MAIN_FIELD]
        === this.selectedEntity[CorrelationComponent.MAIN_FIELD]));
      setTimeout(() => this.setFieldValues());
    });
  }

  /**
   * Saves correlation set configuration and triggers calculation of correlation matrix.
   * @param event - Form submission event
   */
  saveAndCalculate(event): void {
    this.form.cleanMaskAndTransferValuesToBusinessObject(this.selectedEntity);
    this.correlationSetService.update(this.selectedEntity).subscribe({
      next: cs => {
        Object.assign(this.selectedEntity, cs);
        this.setChildData(this.selectedEntity);
        this.configObject.submit.disabled = false;
      }, error: errorBackend => (this.configObject.submit.disabled = false)
    });
  }

  /**
   * Angular lifecycle hook for cleanup. Destroys form helper and calls parent cleanup.
   */
  ngOnDestroy(): void {
    super.destroy();
    this.correlationEditingSupport.destroy();
  }

  /**
   * Updates child table component when parent correlation set selection changes.
   * @param selectedEntity - The newly selected correlation set
   */
  override setChildData(selectedEntity: CorrelationSet): void {
    this.setChildDataAndCalculateWhenPossible(selectedEntity, true);
  }

  /**
   * Determines if new correlation sets can be created based on tenant limits.
   * @returns True if creation is allowed, false if limit is reached
   */
  protected override canCreate(): boolean {
    return this.correlationLimit.tenantLimit.actual < this.correlationLimit.tenantLimit.limit;
  }

  /**
   * Sets child data and optionally triggers correlation calculation based on instrument count and overlapping dates.
   * @param selectedEntity - The correlation set to display
   * @param calculate - Whether to perform correlation calculation
   */
  setChildDataAndCalculateWhenPossible(selectedEntity: CorrelationSet, calculate: boolean): void {
    this.timePeriod = null;
    this.nonOverlappingDates = false;
    if (selectedEntity) {
      this.childEntityList = selectedEntity.securitycurrencyList;
      if (selectedEntity.securitycurrencyList.length >= 2 && calculate) {
        this.correlationSetService.getCalculationByCorrelationSet(selectedEntity.idCorrelationSet).subscribe(
          (correlationResult: CorrelationResult) => {
            this.correlationResult = correlationResult;
            this.correlationTableComponent.parentSelectionChanged(selectedEntity, correlationResult);
            if (correlationResult.firstAvailableDate) {
              this.timePeriod = AppHelper.getDateByFormat(this.gps, correlationResult.firstAvailableDate) +
                ' - ' + AppHelper.getDateByFormat(this.gps, correlationResult.lastAvailableDate);
              this.correlationTableComponent.refreshChartWhenCorrelationSetChanges();
            } else {
              this.nonOverlappingDates = true;
            }
          });
      } else {
        this.correlationTableComponent.parentSelectionChanged(selectedEntity, this.correlationResult);
      }
    } else {
      this.correlationTableComponent.parentSelectionChanged(selectedEntity, this.correlationResult);
    }
  }

  /**
   * Refreshes context menus by combining edit and show menu items from base class and child table component.
   */
  override refreshMenus(): void {
    const editMenu = this.prepareEditMenu();
    const showMenu = this.prepareShowMenu();
    this.contextMenuItems = [...editMenu, ...showMenu];
    this.activePanelService.activatePanel(this, {editMenu, showMenu});
  }

  /**
   * Prepares edit menu items by combining base edit menu with correlation table-specific edit options.
   * @returns Array of translated menu items for editing operations
   */
  override prepareEditMenu(): MenuItem[] {
    const menuItems = this.getBaseEditMenu('CORRELATION_SET');
    menuItems.push(...this.correlationTableComponent.prepareEditMenu());
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  /**
   * Prepares show menu items from the correlation table component.
   * @returns Array of translated menu items for view operations
   */
  protected override prepareShowMenu(): MenuItem[] {
    const menuItems = this.correlationTableComponent.prepareShowMenu();
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  /**
   * Prepares call parameters for opening edit dialogs.
   * @param entity - The correlation set entity to edit or null for new creation
   */
  protected prepareCallParam(entity: CorrelationSet): void {
    this.callParam = new CallParam(null, entity);
  }
}
