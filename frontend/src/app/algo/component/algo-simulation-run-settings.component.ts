import { httpResource } from '@angular/common/http';
import { Component, OnInit, computed, effect, inject, input, numberAttribute, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { ConfigurableTreeTableComponent } from '../../lib/datashowbase/configurable-tree-table.component';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { AppHelper } from '../../lib/helper/app.helper';
import { reportResourceErrors } from '../../lib/login/service/resource.error.report';
import { AppSettings } from '../../shared/app.settings';
import { AlgoAssetclass } from '../model/algo.assetclass';
import { AlgoSecurity } from '../model/algo.security';
import { AlgoTop } from '../model/algo.top';
import { SimulationRunSettings } from '../model/simulation.run';
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
  imports: [
    TranslateModule,
    ConfigurableTreeTableComponent,
    StrategyDetailComponent,
    AngularSvgIconModule,
    TooltipModule
  ],
  template: `
    <div class="data-container">
      @if (settings() === null) {
        <p>{{ 'SIMULATION_RUN_NONE' | translate }}</p>
      } @else if (settings()) {
        <div class="row">
          <div class="col-md-2 showlabel text-end">{{ submittedAtField.headerTranslated }}:</div>
          <div class="col-md-10 nopadding wrap">{{ getValueByPath(settings(), submittedAtField) }}</div>
        </div>
        @if (treeNodes()) {
          <configurable-tree-table
            [data]="treeNodes()"
            [fields]="fields"
            [fieldsRevision]="fieldsRevision()"
            dataKey="idTree"
            [(selection)]="selectedNode"
            (nodeSelect)="onNodeSelect($event)"
            (nodeUnselect)="onNodeUnselect($event)"
            [showContextMenu]="false"
            [valueGetterFn]="valueGetter"
            [baseLocale]="baseLocale"
            [rowClassFn]="getAlgoRowClassCallback"
            [enableSort]="false">
            <h4 caption>{{ 'SIMULATION_RUN_SETTINGS' | translate }}</h4>
            <ng-template #iconCell let-row let-value="value">
              @if (getDistributionIcon(row); as iconName) {
                <svg-icon
                  [name]="iconName"
                  [pTooltip]="'DISTRIBUTION_HEADER_TOOLTIP' | translate"
                  tooltipPosition="top"
                  [svgStyle]="{ 'width.px': 14, 'height.px': 14 }" />
              } @else {
                <span>{{ value }}</span>
              }
            </ng-template>
          </configurable-tree-table>
          @if (algoStrategyShowParamCall().algoStrategy) {
            <strategy-detail [algoStrategyParamCall]="algoStrategyShowParamCall()" />
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
export class AlgoSimulationRunSettingsComponent extends AlgoTreeViewBase implements OnInit {
  readonly id = input.required({ transform: numberAttribute });

  private runService: AlgoSimulationRunService = inject(AlgoSimulationRunService);

  readonly getAlgoRowClassCallback = (rowNode: any, rowData: any) => this.getAlgoRowClass(rowNode, rowData);
  /** Undefined while loading, null when the environment has never been replayed. */
  readonly settings = computed(() => (this.settingsResource.hasValue() ? this.settingsResource.value() : undefined));
  submittedAtField: ColumnConfig;
  private readonly allocation = computed(() => this.settings()?.allocation);

  readonly settingsResource = httpResource<SimulationRunSettings>(() => this.runService.getSettingsRequest(this.id()));
  constructor() {
    super();
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

    effect(() => {
      this.id();
      untracked(() => {
        this.algoTop.set(null);
        this.selectedNode.set(null);
        this.onNodeUnselect(null);
      });
    });

    reportResourceErrors(this.settingsResource, this.runService);
    effect(() => {
      const settings = this.settings();
      if (settings?.hierarchy)
        untracked(() => this.buildTree(settings.hierarchy.algoTop, settings.hierarchy.algoAssetclassList));
    });
  }

  ngOnInit(): void {
    this.translateHeadersAndColumns();
    this.translateService
      .get(this.submittedAtField.headerKey)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((text) => {
        this.submittedAtField.headerTranslated = text;
        this.notifyViewChanged();
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
    const a = this.allocation();
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
