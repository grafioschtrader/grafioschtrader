import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Params } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { ConfigurableTreeTableComponent } from '../../lib/datashowbase/configurable-tree-table.component';
import { AppSettings } from '../../shared/app.settings';
import { AppHelper } from '../../lib/helper/app.helper';
import { AlgoTop } from '../model/algo.top';
import { AlgoAssetclass } from '../model/algo.assetclass';
import { AlgoSecurity } from '../model/algo.security';
import { SimulationRunAllocation, SimulationRunSettings } from '../model/simulation.run';
import { AlgoStrategyService } from '../service/algo.strategy.service';
import { AlgoSimulationRunService } from '../service/algo-simulation-run.service';
import { AlgoTreeViewBase } from './algo.tree.view.base';
import { StrategyDetailComponent } from './strategy-detail.component';

/**
 * Shows the strategy hierarchy the latest historical replay of a simulation environment was submitted with, laid out
 * like the live hierarchy view but read only. The hierarchy is frozen with the run, so it still answers what the run
 * was based on after the shared hierarchy has been edited. Next to the weight each node had, the used percentage shows
 * the weight the replay actually allocated with; the two differ where instruments were excluded from trading and their
 * weight was redistributed within their class. Selecting a strategy shows its parameters below the tree.
 */
@Component({
  selector: 'algo-simulation-run-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslateModule, ConfigurableTreeTableComponent, StrategyDetailComponent],
  template: `
    <div class="data-container">
      @if (settings === null) {
        <p>{{ 'SIMULATION_RUN_NONE' | translate }}</p>
      } @else if (settings) {
        <div class="row">
          <div class="col-md-2 showlabel text-end">{{ submittedAtField.headerTranslated }}:</div>
          <div class="col-md-10 nopadding wrap">{{ getValueByPath(settings, submittedAtField) }}</div>
        </div>
        @if (treeNodes) {
          <configurable-tree-table
            [data]="treeNodes"
            [fields]="fields"
            dataKey="idTree"
            [(selection)]="selectedNode"
            (nodeSelect)="onNodeSelect($event)"
            (nodeUnselect)="onNodeUnselect($event)"
            [showContextMenu]="false"
            [valueGetterFn]="getValueByPath.bind(this)"
            [baseLocale]="baseLocale"
            [rowClassFn]="getAlgoRowClass.bind(this)"
            [enableSort]="false">
            <h4 caption>{{ 'SIMULATION_RUN_SETTINGS' | translate }}</h4>
          </configurable-tree-table>
          @if (algoStrategyShowParamCall.algoStrategy) {
            <strategy-detail [algoStrategyParamCall]="algoStrategyShowParamCall"> </strategy-detail>
          }
        } @else {
          <p>{{ 'SIMULATION_RUN_SETTINGS_NONE' | translate }}</p>
        }
      }
    </div>
  `,
  styles: [
    `
      .kb-row {
        font-weight: 700 !important;
      }
    `
  ]
})
export class AlgoSimulationRunSettingsComponent extends AlgoTreeViewBase implements OnInit, OnDestroy {
  /** Undefined while loading, null when the environment has never been replayed. */
  settings: SimulationRunSettings | null;
  submittedAtField: ColumnConfig;
  private allocation: SimulationRunAllocation;
  private routeSubscribe: Subscription;

  /**
   * @param activatedRoute - Carries the tenant id of the simulation environment
   * @param runService - Loads the settings of the environment's latest replay
   * @param algoStrategyService - Loads the form definition of a strategy implementation for the strategy detail
   * @param translateService - Angular translation service for internationalization support
   * @param gps - Global parameter service providing user locale and formatting preferences
   */
  constructor(
    private activatedRoute: ActivatedRoute,
    private runService: AlgoSimulationRunService,
    algoStrategyService: AlgoStrategyService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(algoStrategyService, translateService, gps);
    this.addNameAndPercentageColumns();
    this.addColumnFeqH(DataType.NumericShowZero, 'usedPercentage', true, false, {
      maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION,
      fieldValueFN: this.getUsedPercentage.bind(this)
    });
    this.addTotalDateAndIdColumns();
    this.submittedAtField = ShowRecordConfigBase.createColumnConfig(
      DataType.DateTimeString,
      'submittedAt',
      'STARTED_AT'
    );
  }

  ngOnInit(): void {
    this.translateHeadersAndColumns();
    this.translateService.get(this.submittedAtField.headerKey).subscribe((text) => {
      this.submittedAtField.headerTranslated = text;
    });
    this.routeSubscribe = this.activatedRoute.params.subscribe((params: Params) => this.readSettings(+params['id']));
  }

  ngOnDestroy(): void {
    this.routeSubscribe?.unsubscribe();
  }

  private readSettings(idTenant: number): void {
    this.settings = undefined;
    this.treeNodes = null;
    this.selectedNode = null;
    this.algoStrategyShowParamCall.algoStrategy = null;
    this.runService.settings(idTenant).subscribe((settings) => {
      this.settings = settings ?? null;
      this.allocation = settings?.allocation;
      if (settings?.hierarchy) {
        this.buildTree(settings.hierarchy.algoTop, settings.hierarchy.algoAssetclassList);
      }
    });
  }

  /**
   * Formats the weight the replay allocated a node with. The column has no property of its own, so the looked-up weight
   * goes through the same formatting as any numeric column.
   */
  private getUsedPercentage(dataobject: any, field: ColumnConfig, valueField: any): string {
    const usedPercentage = this.lookupUsedPercentage(dataobject);
    return usedPercentage == null
      ? null
      : AppHelper.getValueByPathWithField(this.gps, this.translateService, { usedPercentage }, field, field.field);
  }

  /**
   * Looks up the weight the replay allocated a node with. An excluded instrument, or a class left without a permitted
   * instrument, was recorded with its original weight but received none, so it shows zero.
   */
  private lookupUsedPercentage(dataobject: any): number {
    const a = this.allocation;
    if (!a) {
      return null;
    }
    const id = dataobject.idAlgoAssetclassSecurity;
    if (dataobject instanceof AlgoTop) {
      return a.topPercentage;
    } else if (dataobject instanceof AlgoAssetclass) {
      return a.classes[id] ?? (a.originalClasses[id] != null ? 0 : null);
    } else if (dataobject instanceof AlgoSecurity) {
      return a.members[id] ?? (a.originalMembers[id] != null ? 0 : null);
    }
    return null;
  }
}
