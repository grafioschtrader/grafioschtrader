import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  OnDestroy,
  ViewChild
} from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { DashboardConfig, DashboardResult } from '../../lib/dashboard/dashboard.types';
import { DashboardService } from '../../lib/dashboard/dashboard.service';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { AppHelper } from '../../lib/helper/app.helper';
import { Helper } from '../../lib/helper/helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { HoldingMovers } from '../model/holding.movers';
import { HoldingMoversTreetableComponent } from './holding-movers-treetable.component';

/**
 * Dashboard card ranking the instruments the active client holds by how far they moved.
 *
 * <p>
 * The card shows the same instruments twice, ordered by the move of the instrument and by what that move was worth to
 * the portfolio, over three periods. The third period is chosen here rather than in the settings dialog: a reader
 * comparing sessions changes it repeatedly, and none of those changes is a layout decision worth saving. It therefore
 * travels as a setting of one read, which is why an ordinary Refresh returns the card to the default session.
 * </p>
 *
 * <p>
 * Which periods are open is remembered per card in this browser, because it is a reading habit rather than shared
 * state; the server never sees it.
 * </p>
 */
@Component({
  selector: 'holding-movers-widget',
  changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [TranslateModule, DynamicFormModule, HoldingMoversTreetableComponent],
  template: `
    @if (movers) {
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm">
      </dynamic-form>
      <section class="mb-3">
        <h3 class="h6">{{ 'DASHBOARD_MOVERS_BY_PERCENTAGE' | translate }}</h3>
        <holding-movers-treetable
          [branches]="movers.branches"
          ranking="byPercentage"
          [currency]="movers.currency"
          [expanded]="expandedBranches"
          (expandedChange)="onExpandedChange($event)" />
      </section>
      <section class="mb-3">
        <h3 class="h6">{{ 'DASHBOARD_MOVERS_BY_AMOUNT' | translate }}</h3>
        <holding-movers-treetable
          [branches]="movers.branches"
          ranking="byAmount"
          [currency]="movers.currency"
          [expanded]="expandedBranches"
          (expandedChange)="onExpandedChange($event)" />
      </section>
      @for (note of notes; track note.key) {
        <small class="d-block">{{ note.key | translate: note.params }}</small>
      }
    }
  `
})
export class HoldingMoversWidgetComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input() result: DashboardResult;

  @ViewChild('form') form: DynamicFormComponent;

  movers: HoldingMovers;
  expandedBranches: string[] = [];
  notes: { key: string; params: { count: number } }[] = [];
  config: FieldConfig[];
  configObject: { [name: string]: FieldConfig };
  formConfig: FormConfig;

  private chosenDateSubscription: Subscription;

  constructor(
    public translateService: TranslateService,
    gps: GlobalparameterService,
    private dashboardService: DashboardService,
    private userSettingsService: UserSettingsService
  ) {
    this.formConfig = AppHelper.getDefaultFormConfig(gps, 4, null, true);
    this.config = [
      DynamicFieldHelper.createFieldPcalendar(DataType.DateString, 'chosenDate', 'DASHBOARD_MOVERS_DATE', false)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngOnChanges(): void {
    this.movers = this.result?.payload?.custom as HoldingMovers;
    // A stored empty list means every period was deliberately closed, which is why the absence of the entry - and not
    // its emptiness - is what opens them all on a first visit.
    const stored = this.userSettingsService.retrieveObject(this.storeKey());
    this.expandedBranches = stored
      ? (stored.branches ?? [])
      : (this.movers?.branches ?? []).map((branch) => branch.branch);
    this.notes = this.collectNotes();
  }

  ngAfterViewInit(): void {
    this.chosenDateSubscription = this.configObject.chosenDate.formControl.valueChanges.subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.chosenDateSubscription?.unsubscribe();
  }

  /**
   * Reloads this card alone for a chosen session. A failure leaves the figures on screen untouched rather than blanking
   * a card because one date could not be read; the dashboard reports the failure through its own refresh path.
   */
  private reload(): void {
    if (!this.configObject.chosenDate.formControl.value || !this.result?.instanceId) {
      return;
    }
    // Calendar controls hold local Date objects. Convert through the field's data type so the API receives a
    // date-only string, without JSON serialization shifting midnight to the previous day in UTC.
    const configOverride: DashboardConfig = {};
    Helper.copyFormSingleFormConfigToBusinessObject(
      this.formConfig,
      this.configObject.chosenDate,
      configOverride,
      true
    );
    this.dashboardService.refresh(this.result.instanceId, configOverride).subscribe((result) => {
      const movers = result?.payload?.custom as HoldingMovers;
      if (movers) {
        this.movers = movers;
        this.notes = this.collectNotes();
      }
    });
  }

  /**
   * Collects what the card could not rank. The counts are the largest over the branches rather than their sum: the same
   * instrument is normally missing in every period, and adding those up would overstate the gap.
   */
  private collectNotes(): { key: string; params: { count: number } }[] {
    const branches = this.movers?.branches ?? [];
    const omitted = Math.max(0, ...branches.map((branch) => branch.omittedCount));
    const margin = Math.max(0, ...branches.map((branch) => branch.marginCount));
    const notes = [];
    if (omitted > 0) {
      notes.push({ key: 'DASHBOARD_MOVERS_NOTE_OMITTED', params: { count: omitted } });
    }
    if (margin > 0) {
      notes.push({ key: 'DASHBOARD_MOVERS_NOTE_MARGIN', params: { count: margin } });
    }
    return notes;
  }

  onExpandedChange(change: { branch: string; open: boolean }): void {
    const open = new Set(this.expandedBranches);
    change.open ? open.add(change.branch) : open.delete(change.branch);
    this.expandedBranches = [...open];
    this.userSettingsService.saveObject(this.storeKey(), { branches: this.expandedBranches });
  }

  /** Per card, so the winners and the losers card are opened independently. */
  private storeKey(): string {
    return `holdingmovers.${this.result?.type}.expanded`;
  }
}
