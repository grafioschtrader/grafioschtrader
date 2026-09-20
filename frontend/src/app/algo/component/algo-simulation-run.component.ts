import { AppHelper } from '../../lib/helper/app.helper';
import { BusinessHelper } from '../../shared/helper/business.helper';
import { AppSettings } from '../../shared/app.settings';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { NgClass } from '@angular/common';
import { ActivatedRoute, Params } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { ButtonModule } from '@openng/optimus-ui/button';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { SingleRecordConfigBase } from '../../lib/datashowbase/single.record.config.base';
import { ColumnConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { BaseSettings } from '../../lib/base.settings';
import { SimulationContextService } from '../service/simulation.context.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { DataChangedService } from '../../lib/maintree/service/data.changed.service';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { MainTreeDynamicDialogs } from '../../dynamic-dialog/component/main.tree.dynamic.dialogs';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { TenantService } from '../../tenant/service/tenant.service';
import { AlgoSimulationRunService } from '../service/algo-simulation-run.service';
import {
  SimulationFailureMessage,
  SimulationRunEvent,
  SimulationRunResult,
  SimulationRunStatus,
  splitSimulationFailureMessage
} from '../model/simulation.run';
import { SimulationTenantInfo } from '../model/simulation.tenant';
import { AlgoSimulationRunTableComponent } from './algo-simulation-run-table.component';
import { AlgoSimulationRunStartDynamicComponent } from './algo-simulation-run-start.component';
import { TaxDetailsTableComponent } from '../../taxdata/component/tax-details-table.component';

/**
 * Watches and reports the historical replay of one simulation environment, and offers to start one.
 *
 * The panel is the main view of a simulation environment node, so a run that already exists can be read by anyone who
 * may see the environment. Starting one is another matter: the replay books its fills as the owner of the environment,
 * so the backend refuses a request that arrives with the simulation's own identity, and a read-only user must not be
 * offered an action the write endpoint would refuse. Both therefore lose the start entry of the edit menu and the
 * cancel button rather than the figures. The inputs of a run are collected by
 * {@link AlgoSimulationRunStartDynamicComponent}, opened from this panel's edit menu or from the context menu of the
 * environment node; whichever opened it, the start is announced through the data changed service and picked up here.
 *
 * While a run is executing the status is polled, because the work happens on a server worker rather than in the
 * request that started it. Every request counts against the per-user request limit of the server (30 per minute,
 * 300 per hour), and each rejected request is recorded as a violation that eventually locks the user out. The poll
 * therefore slows down once a run has been watched for a while, fetches the audit trail only when the run starts or
 * ends or the user asks for it, and stops on the first failed request instead of retrying. The assumptions the figures were produced under are listed with them rather than left
 * implicit - executed at the next close, without costs, Sharpe against a risk free rate of zero.
 *
 * What a run says falls into two kinds that are read for different reasons, so they are shown as two cards rather than
 * as one list: what the run is and how far it got, including the optional estimates it was started with, and what it
 * earned. Only the first is worth anything while a replay is still walking its days, and only the second is comparable
 * between two runs.
 */
@Component({
  selector: 'algo-simulation-run',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    NgClass,
    TranslateModule,
    ButtonModule,
    ContextMenuModule,
    AlgoSimulationRunTableComponent,
    TaxDetailsTableComponent
  ],
  template: `
    <div
      #cmDiv
      class="data-container"
      [ngClass]="{ 'active-border': isActivated(), 'passiv-border': !isActivated() }"
      (click)="onComponentClick($event)">
      <h4>{{ 'SIMULATION_RUN' | translate }}</h4>

      @if (simulation) {
        <div class="fcontainer">
          <fieldset class="out-border fbox">
            <legend class="out-border-legend">{{ 'SIMULATION_ENVIRONMENT' | translate }}</legend>
            @for (field of contextFields; track field.field) {
              <div class="row">
                <div class="col-md-5 showlabel text-end">{{ field.headerTranslated }}:</div>
                <div class="col-md-7 nopadding wrap">{{ getValueByPath(simulation, field) }}</div>
              </div>
            }
            @if (recreateRequired) {
              <div class="row">
                <div class="col-12 nopadding wrap" role="alert">{{ 'SIMULATION_RECREATE_REQUIRED' | translate }}</div>
              </div>
            }
          </fieldset>
        </div>

        @if (run) {
          <div class="fcontainer">
            @for (fieldSetName of Object.keys(fieldSetGroups); track fieldSetName) {
              <fieldset class="out-border fbox">
                <legend class="out-border-legend">{{ fieldSetName | translate }}</legend>
                @for (field of getFieldsForFieldSet(fieldSetName); track field.field) {
                  <div class="row">
                    <div class="col-md-5 showlabel text-end">{{ field.headerTranslated }}:</div>
                    <div class="col-md-7 nopadding wrap">
                      @switch (field.templateName) {
                        @case ('check') {
                          <span><i [ngClass]="{ 'fa fa-check': getValueByPath(run, field) }"></i></span>
                        }
                        @default {
                          {{ getValueByPath(run, field) }}
                        }
                      }
                    </div>
                  </div>
                }
                @if (fieldSetName === RUN_STATE) {
                  <div class="row">
                    <div class="col-12 nopadding wrap">
                      {{
                        'SIMULATION_RUN_PROGRESS'
                          | translate: { done: run.tradingDaysDone, total: run.tradingDaysTotal }
                      }}
                    </div>
                  </div>
                  @if (run.failureMessage) {
                    <div class="row">
                      <div class="col-md-5 showlabel text-end">{{ 'FAILURE_MESSAGE' | translate }}:</div>
                      <div class="col-md-7 nopadding wrap" role="alert">
                        {{ failureMessage.key | translate }}
                        @if (failureMessage.detail) {
                          : {{ failureMessage.detail }}
                        }
                      </div>
                    </div>
                  }
                }
              </fieldset>
            }
          </div>
          <p-button [label]="'SIMULATION_RUN_REFRESH' | translate" [disabled]="busy" (click)="refresh()" />
          @if (running && mayStart) {
            <p-button [label]="'SIMULATION_RUN_CANCEL' | translate" [disabled]="busy" (click)="cancel()" />
          }
          @if (conventions.length > 0) {
            <fieldset class="out-border">
              <legend class="out-border-legend">{{ 'CONVENTIONS' | translate }}</legend>
              <p>{{ 'SIMULATION_RUN_CONVENTIONS' | translate }}</p>
              <ul>
                @for (convention of conventions; track convention) {
                  <li>{{ convention | translate }}</li>
                }
              </ul>
            </fieldset>
          }
          @if (incomeSummary) {
            <fieldset class="out-border">
              <legend class="out-border-legend">{{ 'SIMULATION_INCOME_SUMMARY' | translate }}</legend>
              <tax-details-table mode="income" [rows]="incomeSummary.income || []" />
              <details>
                <summary>
                  {{ 'SIMULATION_TAX_WARNINGS' | translate }} ({{ incomeSummary.warnings?.length || 0 }})
                </summary>
                <tax-details-table mode="warnings" [rows]="incomeSummary.warnings || []" />
              </details>
            </fieldset>
          }
          <fieldset class="out-border">
            <legend class="out-border-legend">{{ 'SIMULATION_RUN_EVENTS' | translate }}</legend>
            <algo-simulation-run-table [rows]="events" />
          </fieldset>
        } @else {
          <p>{{ 'SIMULATION_RUN_NONE' | translate }}</p>
        }
      } @else if (notFound) {
        <p role="alert">{{ 'SIMULATION_ENVIRONMENT_NOT_FOUND' | translate }}</p>
      }
      <p-contextMenu [target]="cmDiv" [model]="contextMenuItems" appendTo="body" />
    </div>
  `
})
export class AlgoSimulationRunComponent extends SingleRecordConfigBase implements IGlobalMenuAttach, OnInit, OnDestroy {
  /** Poll interval while a run has only just started, so that a short replay still appears to finish promptly. */
  private static readonly POLL_FAST_MILLIS = 3000;
  /** Poll interval for a longer run. Four requests a minute stay below the hourly refill of the request limit. */
  private static readonly POLL_SLOW_MILLIS = 15000;
  /** How long after polling began the fast interval is used before switching to the slow one. */
  private static readonly POLL_FAST_PHASE_MILLIS = 30000;

  /** Card of what the run is and how far it got; the template adds the progress and the failure to it. */
  readonly RUN_STATE = 'SIMULATION_RUN_STATE';
  /** Card of what the run earned. Empty until a run completes, because only a completed run has figures. */
  readonly RUN_PERFORMANCE = 'SIMULATION_RUN_PERFORMANCE';

  /** The environment the run belongs to. Its own record, so it is not part of the grouped fields of the run. */
  contextFields: ColumnConfig[] = [];
  simulation: SimulationTenantInfo;
  run: SimulationRunResult;
  failureMessage: SimulationFailureMessage;
  events: SimulationRunEvent[] = [];
  conventions: string[] = [];
  incomeSummary: any;
  contextMenuItems: MenuItem[] = [];
  busy = false;
  notFound = false;
  private idTenant: number;
  private poll: ReturnType<typeof setTimeout>;
  /** When the current polling began; decides between the fast and the slow interval. */
  private pollStartedAt: number;
  /** Running state of the previously applied run, undefined before the first one, to detect a start or an end. */
  private wasRunning: boolean;
  private routeSubscribe: Subscription;
  private dataChangedSubscribe: Subscription;

  constructor(
    private activatedRoute: ActivatedRoute,
    private tenantService: TenantService,
    private runService: AlgoSimulationRunService,
    private activePanelService: ActivePanelService,
    private dataChangedService: DataChangedService,
    private dialogService: DialogService,
    private simulationContext: SimulationContextService,
    public override translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => {
      this.idTenant = +params['id'];
      this.stopPolling();
      this.reset();
      // A direct reload can activate this route before the navigation tree has populated its entity cache, so the
      // environment is read from the server rather than from the node it was navigated from.
      this.tenantService.getSimulationTenants().subscribe((simulations) => {
        this.simulation = simulations.find((simulation) => simulation.idTenant === this.idTenant);
        if (!this.simulation) {
          // The environment was deleted, or the route was reloaded while switched into a simulation, where the listing
          // is the one of the simulation itself rather than of the main tenant.
          this.notFound = true;
          this.updateMenu();
          return;
        }
        this.initContextFields();
        this.initRunFields();
        this.refresh();
      });
    });
    // The start dialog announces an accepted run, whether it was opened from this panel or from the tree node.
    this.dataChangedSubscribe = this.dataChangedService.dateChanged$.subscribe(
      (processedActionData: ProcessedActionData) => {
        if (this.simulation && (processedActionData.data as SimulationRunResult)?.idSimulationResult != null) {
          this.refresh();
        }
      }
    );
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.routeSubscribe?.unsubscribe();
    this.dataChangedSubscribe?.unsubscribe();
    this.activePanelService.destroyPanel(this);
  }

  /** The replay is documented with the strategy tree it belongs to, together with its simulation environments. */
  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_ALGO_TREE);
  }

  get running(): boolean {
    return this.run?.status === SimulationRunStatus.RUNNING;
  }

  /** An environment created before the opening date and policy were recorded cannot be replayed until recreated. */
  get recreateRequired(): boolean {
    return !this.simulation?.simulationStartDate || !this.simulation?.initializationMode;
  }

  /**
   * A replay is started from the main tenant by a user who may write. Inside a switched-in simulation the request
   * would carry the simulation's own identity, which the backend refuses, and a read-only user must not be offered an
   * action the write endpoint would refuse either. Both still see the figures of a run that already exists.
   */
  get mayStart(): boolean {
    return !this.gps.isReadOnlyUser() && !this.simulationContext.isInSimulation();
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(_event): void {
    this.contextMenuItems = this.getEditMenu();
    this.activePanelService.activatePanel(this, {
      showMenu: null,
      editMenu: this.contextMenuItems.length > 0 ? this.contextMenuItems : null
    });
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_ALGO_TREE;
  }

  cancel(): void {
    this.busy = true;
    this.runService
      .cancel(this.idTenant)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe(() => this.refresh());
  }

  /** An explicit refresh, by the user or after a start or cancel, which also reloads the audit trail. */
  refresh(): void {
    this.load(true);
  }

  /**
   * Reads the run and, while it is still executing, schedules the next poll only after this request has answered, so
   * a slow server cannot pile requests up. A failed request ends the polling: retrying a rejection of the request
   * limit would only add further violations.
   *
   * @param loadEvents - whether the audit trail is reloaded regardless of a change of the running state
   */
  private load(loadEvents: boolean): void {
    this.busy = true;
    this.runService
      .status(this.idTenant)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe({
        next: (run) => {
          this.apply(run, loadEvents);
          if (this.running) {
            this.scheduleNextPoll();
          }
        },
        // A refusal here can also mean that the environment has just started replaying, which makes every request of a
        // session sitting inside it fail. Returning home is the only thing left that works.
        error: () => {
          this.stopPolling();
          this.simulationContext.recoverToHome();
        }
      });
  }

  /**
   * The form definition of the request is memoised by the service, so only the first opening costs a request. It has
   * to be at hand before the dialog renders, because the dialog builds its form synchronously.
   */
  private openStartDialog(): void {
    this.gps
      .getEntityFormDefinition('SimulationRunRequestDTO')
      .subscribe((formDefinition) =>
        MainTreeDynamicDialogs.getEditDialogComponent(
          AlgoSimulationRunStartDynamicComponent,
          this.translateService,
          this.dialogService,
          new CallParam({ formDefinition } as any, this.simulation as any),
          'SIMULATION_RUN'
        )
      );
  }

  private getEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (this.simulation && this.mayStart) {
      menuItems.push({
        label: 'SIMULATION_RUN_START' + BaseSettings.DIALOG_MENU_SUFFIX,
        disabled: this.running || this.recreateRequired,
        command: () => this.openStartDialog()
      });
    }
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  /**
   * Keeps the start entry in step with the run, which polling changes behind the user's back. The menu bar is only
   * rewritten while this panel is the active one; activating it from here would take the menu away from whichever
   * panel the user is working in.
   */
  private updateMenu(): void {
    if (this.isActivated()) {
      this.onComponentClick(null);
    } else {
      this.contextMenuItems = this.getEditMenu();
    }
  }

  /**
   * Takes over a freshly read run. The audit trail grows with every replayed day but is only read when asked for or
   * when the run starts or ends; reading it on every poll would double the requests against the request limit.
   *
   * @param run - the run as reported by the server, undefined when the environment was never replayed
   * @param loadEvents - whether the audit trail is reloaded even though the running state did not change
   */
  private apply(run: SimulationRunResult, loadEvents: boolean): void {
    this.run = run;
    const runningChanged = this.wasRunning !== this.running;
    this.wasRunning = this.running;
    this.failureMessage = splitSimulationFailureMessage(run?.failureMessage);
    this.incomeSummary = run?.taxIncomeSummaryJson ? JSON.parse(run.taxIncomeSummaryJson) : null;
    this.conventions = run?.conventions ? run.conventions.split(' ').filter((key) => !!key) : [];
    if (run) {
      // The status is the one value of the run that is a key rather than a number, and polling replaces the object.
      this.createTranslatedValueStore([run]);
    }
    if (!this.running) {
      this.stopPolling();
      this.pollStartedAt = undefined;
    }
    if (run && (loadEvents || runningChanged)) {
      this.runService.events(this.idTenant, 0, 200).subscribe((page) => (this.events = page.content));
    }
    this.updateMenu();
  }

  /** Navigating from one environment to the next reuses the component, so nothing of the previous one may survive. */
  private reset(): void {
    this.simulation = undefined;
    this.run = undefined;
    this.wasRunning = undefined;
    this.pollStartedAt = undefined;
    this.failureMessage = undefined;
    this.events = [];
    this.conventions = [];
    this.notFound = false;
    this.updateMenu();
  }

  /**
   * The environment the replay runs against, read only: a run may choose its end date alone, so the opening date it
   * starts from and the opening policy it was established under have to be visible rather than remembered.
   */
  private initContextFields(): void {
    this.contextFields = [];
    this.addColumnToFields(this.contextFields, DataType.String, 'tenantName', 'NAME');
    this.addColumnToFields(this.contextFields, DataType.DateString, 'simulationStartDate', 'SIMULATION_START_DATE');
    this.addColumnToFields(
      this.contextFields,
      DataType.String,
      'initializationMode',
      'INITIALIZATION_MODE',
      true,
      false,
      { translateValues: TranslateValue.NORMAL }
    );
    this.translateHeaders(
      this.contextFields.map((field) => field.headerKey),
      this.contextFields
    );
    TranslateHelper.createTranslatedValueStore(this.translateService, this.contextFields, [this.simulation]);
  }

  /** Converts the raw ratio before locale formatting; the callback's third argument is already formatted text. */
  private percentageValue(data: SimulationRunResult, field: ColumnConfig): string {
    const ratio = data[field.field];
    return AppHelper.getValueByPathWithField(
      this.gps,
      this.translateService,
      {
        [field.field]:
          ratio == null ? null : BusinessHelper.roundNumber(ratio * 100, AppSettings.FID_PERCENTAGE_FRACTION)
      },
      field,
      field.field
    );
  }

  /**
   * The two cards of the result. Values outside a table are formatted through the same column configuration a table
   * uses, so that a return and a date read the same way here as everywhere else in the application; the fieldset name
   * of a field is what decides which card it appears in. The two optional estimates a run was started with belong to
   * the run card, since the start form no longer stays on screen to say what was chosen.
   */
  private initRunFields(): void {
    this.fields = [];
    this._fieldSetGroups = undefined;
    this.addFieldPropertyFeqH(DataType.String, 'status', {
      fieldsetName: this.RUN_STATE,
      translateValues: TranslateValue.NORMAL
    });
    this.addFieldPropertyFeqH(DataType.DateString, 'openingDate', { fieldsetName: this.RUN_STATE });
    this.addFieldPropertyFeqH(DataType.DateString, 'endDate', { fieldsetName: this.RUN_STATE });
    this.addFieldPropertyFeqH(DataType.Boolean, 'applyTaxModels', {
      fieldsetName: this.RUN_STATE,
      templateName: 'check'
    });
    this.addFieldPropertyFeqH(DataType.Boolean, 'generateBondCoupons', {
      fieldsetName: this.RUN_STATE,
      templateName: 'check'
    });
    this.addFieldPropertyFeqH(DataType.DateTimeString, 'startedAt', { fieldsetName: this.RUN_STATE });
    this.addFieldPropertyFeqH(DataType.DateTimeString, 'finishedAt', { fieldsetName: this.RUN_STATE });
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'dividendPaymentDelayDays', { fieldsetName: this.RUN_STATE });
    this.addFieldPropertyFeqH(DataType.Numeric, 'totalReturn', {
      fieldsetName: this.RUN_PERFORMANCE,
      headerSuffix: '%',
      maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION,
      fieldValueFN: this.percentageValue.bind(this)
    });
    this.addFieldPropertyFeqH(DataType.Numeric, 'annualizedReturn', {
      fieldsetName: this.RUN_PERFORMANCE,
      headerSuffix: '%',
      maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION,
      fieldValueFN: this.percentageValue.bind(this)
    });
    this.addFieldPropertyFeqH(DataType.Numeric, 'maxDrawdown', {
      fieldsetName: this.RUN_PERFORMANCE,
      headerSuffix: '%',
      maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION,
      fieldValueFN: this.percentageValue.bind(this)
    });
    this.addFieldPropertyFeqH(DataType.Numeric, 'sharpeRatio', { fieldsetName: this.RUN_PERFORMANCE });
    this.addFieldPropertyFeqH(DataType.Numeric, 'paidDividends', { fieldsetName: this.RUN_PERFORMANCE });
    this.addFieldPropertyFeqH(DataType.Numeric, 'dividendReceivables', { fieldsetName: this.RUN_PERFORMANCE });
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'totalTrades', { fieldsetName: this.RUN_PERFORMANCE });
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'winningTrades', { fieldsetName: this.RUN_PERFORMANCE });
    this.addFieldPropertyFeqH(DataType.NumericInteger, 'losingTrades', { fieldsetName: this.RUN_PERFORMANCE });
    this.translateHeadersAndColumns();
  }

  /**
   * Schedules exactly one next poll, replacing any pending one, so an explicit refresh during polling does not start a
   * second chain. The interval is fast during the first seconds of a run and slow afterwards.
   */
  private scheduleNextPoll(): void {
    this.stopPolling();
    this.pollStartedAt ??= Date.now();
    const delay =
      Date.now() - this.pollStartedAt < AlgoSimulationRunComponent.POLL_FAST_PHASE_MILLIS
        ? AlgoSimulationRunComponent.POLL_FAST_MILLIS
        : AlgoSimulationRunComponent.POLL_SLOW_MILLIS;
    this.poll = setTimeout(() => {
      this.poll = undefined;
      this.load(false);
    }, delay);
  }

  private stopPolling(): void {
    if (this.poll) {
      clearTimeout(this.poll);
      this.poll = undefined;
    }
  }
}
