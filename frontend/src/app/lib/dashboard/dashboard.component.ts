import { ChangeDetectionStrategy, Component, HostListener, Inject, OnDestroy, OnInit } from '@angular/core';
import { NgComponentOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from '@openng/optimus-ui/button';
import { CdkDrag, CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { Subscription } from 'rxjs';
import { ShowRecordConfigBase } from '../datashowbase/show.record.config.base';
import { ColumnConfig } from '../datashowbase/column.config';
import { GlobalparameterService } from '../services/globalparameter.service';
import { ActivePanelService } from '../mainmenubar/service/active.panel.service';
import { IGlobalMenuAttach } from '../mainmenubar/component/iglobal.menu.attach';
import { TranslateHelper } from '../helper/translate.helper';
import { DataType } from '../dynamic-form/models/data.type';
import { HelpIds } from '../help/help.ids';
import { GlobalSessionNames } from '../global.session.names';
import { DashboardService } from './dashboard.service';
import {
  DashboardCatalogue,
  DashboardDescriptor,
  DashboardDocument,
  DashboardResult,
  DashboardWidget,
  DashboardWidth,
  availableDescriptors,
  insertDashboardWidget,
  mergeDashboardResult,
  moveDashboardWidget,
  widgetFromDescriptor
} from './dashboard.types';
import { DASHBOARD_CONFIG_SUMMARIES, DASHBOARD_RENDERERS } from './dashboard-summary.component';
import { DashboardConfigComponent } from './dashboard-config.component';
import { DashboardMasonryDirective } from './dashboard-masonry.directive';
import { ProcessedActionData } from '../types/processed.action.data';
import { ProcessedAction } from '../types/processed.action';

/** Personal layout draft and independent card loading, hosted inside either application's split pane. */
@Component({
  selector: 'dashboard',
  changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [
    TranslateModule,
    FormsModule,
    ButtonModule,
    DragDropModule,
    NgComponentOutlet,
    DashboardConfigComponent,
    DashboardMasonryDirective
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent extends ShowRecordConfigBase implements OnInit, OnDestroy, IGlobalMenuAttach {
  document: DashboardDocument;
  draft: DashboardWidget[] = [];
  results: Record<string, DashboardResult> = {};
  catalogue: DashboardCatalogue;
  editing = false;
  loading = false;
  saving = false;
  error = '';
  conflict = false;
  conflictDraft: DashboardWidget[] | null = null;
  available: DashboardDescriptor[] = [];
  availableInfo = { availableCount: 0 };
  resetPending = false;
  configuring: { descriptor: DashboardDescriptor; widget: DashboardWidget } | null = null;
  refreshing = new Set<string>();
  widths: DashboardWidth[] = ['THIRD', 'HALF', 'TWO_THIRDS', 'FULL'];
  readonly spans = { THIRD: 4, HALF: 6, TWO_THIRDS: 8, FULL: 12 };
  readonly timeField = ShowRecordConfigBase.createColumnConfig(DataType.DateTimeString, 'dataAsOf', '');
  readonly generatedField = ShowRecordConfigBase.createColumnConfig(DataType.DateTimeString, 'generatedAt', '');
  readonly tenantField = ShowRecordConfigBase.createColumnConfig(DataType.NumericInteger, 'activeTenantId', '');
  readonly rowLimitField = ShowRecordConfigBase.createColumnConfig(
    DataType.NumericInteger,
    'maxRows',
    'DASHBOARD_MAX_ROWS'
  );
  readonly availableCountField = ShowRecordConfigBase.createColumnConfig(DataType.NumericInteger, 'availableCount', '');
  private removedWidgets = new Map<string, DashboardWidget>();
  private subscriptions = new Subscription();
  private generation = 0;
  private catalogueLoading = false;

  constructor(
    translate: TranslateService,
    gps: GlobalparameterService,
    private service: DashboardService,
    private activePanel: ActivePanelService,
    @Inject(DASHBOARD_RENDERERS) public renderers: Record<string, any>,
    @Inject(DASHBOARD_CONFIG_SUMMARIES) private configSummaries: Record<string, ColumnConfig>
  ) {
    super(translate, gps);
  }
  ngOnInit(): void {
    this.activePanel.registerPanel(this);
    this.onComponentClick(null);
    this.load();
  }
  ngOnDestroy(): void {
    this.generation++;
    this.subscriptions.unsubscribe();
    this.activePanel.destroyPanel(this);
  }
  get dirty(): boolean {
    return this.editing && (this.resetPending || JSON.stringify(this.draft) !== JSON.stringify(this.document?.widgets));
  }
  get canEdit(): boolean {
    return this.document?.schemaVersion === 1 && !this.loading && !this.saving;
  }
  get visibleWidgets(): DashboardWidget[] {
    return this.editing ? this.draft : (this.document?.widgets ?? []);
  }
  /** A setting absent from this widget must not leave an empty label in its header or conflict preview. */
  configSummaryField(widget: DashboardWidget): ColumnConfig | null {
    const field = this.configSummaries[widget.type] ?? this.rowLimitField;
    return widget.config[field.field] == null ? null : field;
  }
  get remainingCapacity(): number {
    return this.resetPending
      ? 24 - this.draft.length
      : (this.document?.remainingCapacity ?? 0) + (this.document?.widgets.length ?? 0) - this.draft.length;
  }
  get simulation(): boolean {
    return !!sessionStorage.getItem(GlobalSessionNames.MAIN_ID_TENANT);
  }
  private context(): string {
    return (
      sessionStorage.getItem(GlobalSessionNames.ID_USER) + ':' + sessionStorage.getItem(GlobalSessionNames.ID_TENANT)
    );
  }
  private current(generation: number, context: string): boolean {
    if (generation !== this.generation) return false;
    if (context !== this.context()) {
      this.document = null;
      this.results = {};
      this.catalogue = null;
      this.loading = false;
      this.editing = false;
      this.refreshing.clear();
      this.load();
      return false;
    }
    return true;
  }
  load(): void {
    if (this.loading || this.saving) return;
    this.loading = true;
    this.error = '';
    const generation = ++this.generation,
      context = this.context();
    this.subscriptions.add(
      this.service.load().subscribe({
        next: (document) => {
          if (!this.current(generation, context)) return;
          const previous = this.document?.activeTenantId === document.activeTenantId ? this.results : {};
          this.results = Object.fromEntries(
            document.results.map((r) => [r.instanceId, mergeDashboardResult(previous[r.instanceId], r)])
          );
          this.document = document;
          this.loading = false;
          this.onComponentClick(null);
          if (document.schemaVersion !== 1) this.error = 'DASHBOARD_SCHEMA';
        },
        error: (error) => {
          if (!this.current(generation, context)) return;
          this.loading = false;
          this.error = error.status === 403 ? 'DASHBOARD_DISABLED' : 'DASHBOARD_LOAD_ERROR';
          this.results = Object.fromEntries(Object.entries(this.results).map(([id, r]) => [id, { ...r, stale: true }]));
          if (error.status === 401 || error.status === 403) {
            this.results = {};
            this.document = null;
          }
        }
      })
    );
  }
  edit(): void {
    if (!this.canEdit) return;
    this.draft = structuredClone(this.document.widgets);
    this.editing = true;
    this.resetPending = false;
    this.conflict = false;
    this.catalogue = null;
    this.available = [];
    this.removedWidgets.clear();
    this.fetchCatalogue(() => {});
    this.onComponentClick(null);
  }
  cancel(): void {
    this.editing = false;
    this.draft = [];
    this.resetPending = false;
    this.catalogue = null;
    this.available = [];
    this.removedWidgets.clear();
    this.configuring = null;
    this.conflict = false;
    this.error = '';
    this.onComponentClick(null);
  }
  save(): void {
    if (!this.canEdit || !this.editing) return;
    this.saving = true;
    this.error = '';
    const generation = this.generation,
      context = this.context();
    this.subscriptions.add(
      this.service.save(this.document.revision, this.draft, this.resetPending).subscribe({
        next: (document) => {
          this.saving = false;
          if (!this.current(generation, context)) return;
          this.document = document;
          this.results = {};
          this.cancel();
          this.load();
        },
        error: (error) => {
          this.saving = false;
          if (!this.current(generation, context)) return;
          this.conflict = error.status === 409;
          this.error = this.conflict
            ? 'DASHBOARD_CONFLICT'
            : error.status === 403
              ? 'DASHBOARD_UNAVAILABLE'
              : 'DASHBOARD_SAVE_ERROR';
        }
      })
    );
  }
  reloadConflict(): void {
    this.conflictDraft = structuredClone(this.draft);
    this.cancel();
    this.load();
  }
  private fetchCatalogue(action: () => void): void {
    if (this.catalogue) {
      action();
      return;
    }
    if (this.catalogueLoading) return;
    this.catalogueLoading = true;
    const generation = this.generation,
      context = this.context();
    this.subscriptions.add(
      this.service.catalogue().subscribe({
        next: (catalogue) => {
          this.catalogueLoading = false;
          if (this.current(generation, context) && this.editing) {
            this.catalogue = catalogue;
            this.refreshAvailable();
            action();
          }
        },
        error: () => {
          this.catalogueLoading = false;
          if (this.current(generation, context)) this.error = 'DASHBOARD_LOAD_ERROR';
        }
      })
    );
  }
  reset(): void {
    this.fetchCatalogue(() => {
      this.draft = structuredClone(this.catalogue.defaults);
      this.resetPending = true;
      this.refreshAvailable();
    });
  }
  /** Adding never opens the configuration dialog, so dragging and the keyboard button behave alike. */
  add(descriptor: DashboardDescriptor, to: number = this.draft.length): void {
    if (this.remainingCapacity <= 0 || this.resetPending || this.draft.some((w) => w.type === descriptor.type)) return;
    const widget = widgetFromDescriptor(descriptor, this.removedWidgets.get(descriptor.type));
    this.removedWidgets.delete(descriptor.type);
    this.draft = insertDashboardWidget(this.draft, widget, to);
    this.refreshAvailable();
  }
  /** A type without a form definition has no settings, so its card offers nothing to configure. */
  configurable(widget: DashboardWidget): boolean {
    return !!this.catalogue?.descriptors.find((d) => d.type === widget.type)?.formDefinition;
  }
  configure(widget: DashboardWidget): void {
    this.fetchCatalogue(() => {
      const descriptor = this.catalogue.descriptors.find((d) => d.type === widget.type);
      if (descriptor) this.configuring = { descriptor, widget };
    });
  }
  configured(action: ProcessedActionData): void {
    if (action.action === ProcessedAction.UPDATED) {
      this.draft = this.draft.map((w) =>
        w.instanceId === this.configuring.widget.instanceId ? { ...w, config: action.data } : w
      );
    }
    this.configuring = null;
  }
  remove(index: number): void {
    const widget = this.draft[index];
    if (!widget) return;
    this.removedWidgets.set(widget.type, widget);
    this.draft = this.draft.filter((_, i) => i !== index);
    this.refreshAvailable();
  }
  move(from: number, to: number): void {
    this.draft = moveDashboardWidget(this.draft, from, to);
  }
  /** The dragged payload tells the two areas apart: cards carry a widget, tray entries a descriptor. */
  drop(event: CdkDragDrop<DashboardWidget[] | DashboardDescriptor[]>): void {
    const dragged = event.item.data as DashboardWidget | DashboardDescriptor;
    if (event.previousContainer === event.container) {
      if ('instanceId' in dragged) this.move(event.previousIndex, event.currentIndex);
    } else if ('instanceId' in dragged) {
      this.remove(event.previousIndex);
    } else {
      this.add(dragged, event.currentIndex);
    }
  }
  private refreshAvailable(): void {
    this.available = availableDescriptors(this.catalogue?.descriptors ?? [], this.draft);
    this.availableInfo = { availableCount: this.available.length };
  }
  /** A tray entry may only enter the grid while capacity is left; cards are always allowed back in. */
  readonly canEnterGrid = (drag: CdkDrag): boolean =>
    !drag.data || 'instanceId' in drag.data || this.remainingCapacity > 0;
  refresh(widget: DashboardWidget): void {
    if (this.refreshing.has(widget.instanceId) || this.loading) return;
    this.refreshing.add(widget.instanceId);
    const generation = this.generation,
      context = this.context();
    const receive = (result: DashboardResult) => {
      this.refreshing.delete(widget.instanceId);
      if (this.current(generation, context))
        this.results[widget.instanceId] = mergeDashboardResult(this.results[widget.instanceId], result);
    };
    this.subscriptions.add(
      this.service.refresh(widget.instanceId).subscribe({
        next: receive,
        error: (error) =>
          receive({
            instanceId: widget.instanceId,
            type: widget.type,
            status: error.status === 403 || error.status === 404 ? 'UNAVAILABLE' : 'ERROR',
            payload: null,
            dataAsOf: '',
            errorCode: error.status === 403 || error.status === 404 ? 'DASHBOARD_UNAVAILABLE' : 'DASHBOARD_LOAD_ERROR'
          })
      })
    );
  }
  canLeave(): boolean {
    return !this.saving && (!this.dirty || window.confirm(this.translateService.instant('DASHBOARD_DISCARD')));
  }
  @HostListener('window:beforeunload', ['$event']) beforeUnload(event: BeforeUnloadEvent): void {
    if (this.dirty || this.saving) {
      event.preventDefault();
      event.returnValue = '';
    }
  }
  getHelpContextId(): string {
    return HelpIds.HELP_DASHBOARD;
  }
  isActivated(): boolean {
    return this.activePanel.isActivated(this);
  }
  hideContextMenu(): void {}
  callMeDeactivate(): void {}
  onComponentClick(event: any): void {
    if (event?.consumedGT) return;
    const items = this.editing
      ? [
          { label: 'DASHBOARD_SAVE', command: () => this.save() },
          { label: 'DASHBOARD_CANCEL', command: () => this.cancel() }
        ]
      : [{ label: 'DASHBOARD_EDIT', command: () => this.edit(), disabled: !this.canEdit }];
    TranslateHelper.translateMenuItems(items, this.translateService);
    this.activePanel.activatePanel(this, { editMenu: items, showMenu: null });
  }
}
