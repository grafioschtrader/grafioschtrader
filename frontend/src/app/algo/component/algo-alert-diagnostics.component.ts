import {
  Component,
  EventEmitter,
  Output,
  OnInit,
  AfterViewInit,
  OnDestroy,
  ViewChild,
  ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { TabsModule } from '@openng/optimus-ui/tabs';
import { ButtonModule } from '@openng/optimus-ui/button';
import { finalize } from 'rxjs/operators';
import { AlgoAlertService, AlertEvaluation, AlertNotification } from '../service/algo-alert.service';
import { AlgoAlertDiagnosticsTableComponent } from './algo-alert-diagnostics-table.component';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';

/** Live alert health and explicit recovery controls. Tables own their formatting and selection. */
@Component({
  selector: 'algo-alert-diagnostics',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    CommonModule,
    DynamicFormModule,
    TranslateModule,
    DialogModule,
    TabsModule,
    ButtonModule,
    AlgoAlertDiagnosticsTableComponent
  ],
  template: `<p-dialog
    [header]="'ALERT_DIAGNOSTICS' | translate"
    [visible]="true"
    [modal]="true"
    [closable]="true"
    [closeOnEscape]="true"
    [style]="{ width: '95vw' }"
    (onHide)="closed.emit()">
    <p-button [label]="'ALERT_REFRESH' | translate" [disabled]="busy" (click)="refresh()" />
    @if (!readOnly) {
      <p-button [label]="'ALERT_EVALUATE_NOW' | translate" [disabled]="busy" (click)="evaluate()" />
    }
    <p>{{ 'ALERT_DELIVERY_EXPLANATION' | translate }}</p>
    <p-tabs value="evaluation"
      ><p-tablist>
        <p-tab value="evaluation">{{ 'ALERT_EVALUATION' | translate }}</p-tab>
        <p-tab value="trading">{{ 'TRADING_DECISIONS' | translate }}</p-tab>
        <p-tab value="notifications">{{ 'ALERT_NOTIFICATIONS' | translate }}</p-tab> </p-tablist
      ><p-tabpanels>
        <p-tabpanel value="evaluation"><algo-alert-diagnostics-table [rows]="evaluations" /></p-tabpanel>
        <p-tabpanel value="trading"><algo-alert-diagnostics-table [rows]="trading" [trading]="true" /></p-tabpanel>
        <p-tabpanel value="notifications">
          <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translate" />
          @if (!readOnly) {
            <p-button [label]="'ALERT_RETRY_SELECTED' | translate" [disabled]="busy || !canRetry()" (click)="retry()" />
          }
          <algo-alert-diagnostics-table [rows]="notifications" [notification]="true" (selected)="selected = $event" />
          <p-button
            [label]="'ALERT_PREVIOUS' | translate"
            [disabled]="busy || page === 0"
            (click)="page = page - 1; loadNotifications()" />
          <p-button
            [label]="'ALERT_NEXT' | translate"
            [disabled]="busy || (page + 1) * 25 >= total"
            (click)="page = page + 1; loadNotifications()" />
        </p-tabpanel> </p-tabpanels
    ></p-tabs>
  </p-dialog>`
})
export class AlgoAlertDiagnosticsComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;
  config: FieldConfig[];
  formConfig: FormConfig;
  private filterSubscription: Subscription;
  @Output() closed = new EventEmitter<void>();
  evaluations: AlertEvaluation[] = [];
  trading: any[] = [];
  notifications: AlertNotification[] = [];
  selected: AlertNotification;
  statuses: { label: string; value: string }[] = [];
  status = '';
  page = 0;
  total = 0;
  busy = false;
  readOnly: boolean;
  constructor(
    private service: AlgoAlertService,
    gps: GlobalparameterService,
    public translate: TranslateService
  ) {
    this.readOnly = gps.isReadOnlyUser();
    // The form carries the user's locale, so a date or number added to this filter later is formatted like the rest of
    // the application instead of falling back to the en-US default of the widget.
    this.formConfig = AppHelper.getDefaultFormConfig(gps, 3, null, true);
    this.config = [DynamicFieldHelper.createFieldSelectString('status', 'DELIVERY_STATUS', false)];
    TranslateHelper.prepareFieldsAndErrors(translate, this.config);
  }
  ngOnInit(): void {
    this.service.statuses().subscribe((values) => {
      this.config[0].valueKeyHtmlOptions = [
        new ValueKeyHtmlSelectOptions('', this.translate.instant('ALERT_ALL_STATUSES')),
        ...values.map((value) => new ValueKeyHtmlSelectOptions(value, this.translate.instant(value)))
      ];
    });
    this.refresh();
  }
  ngAfterViewInit(): void {
    this.filterSubscription = this.form.changes.subscribe((values) => {
      this.status = values.status || '';
      this.page = 0;
      this.loadNotifications();
    });
  }
  ngOnDestroy(): void {
    this.filterSubscription?.unsubscribe();
  }
  refresh(): void {
    this.service.trading().subscribe((rows) => (this.trading = rows));
    this.service.evaluations().subscribe((rows) => (this.evaluations = rows));
    this.loadNotifications();
  }
  loadNotifications(): void {
    this.busy = true;
    this.selected = null;
    this.service
      .notifications(this.page, this.status)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe((result) => {
        this.notifications = result.content;
        this.total = result.totalElements;
      });
  }
  evaluate(): void {
    this.busy = true;
    this.service
      .evaluate()
      .pipe(finalize(() => (this.busy = false)))
      .subscribe(() => this.refresh());
  }
  canRetry(): boolean {
    return !!this.selected && ['FAILED', 'REVIEW_REQUIRED'].includes(this.selected.deliveryStatus);
  }
  retry(): void {
    if (!this.canRetry()) return;
    this.busy = true;
    this.service
      .retry(this.selected.id)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe(() => this.loadNotifications());
  }
}
