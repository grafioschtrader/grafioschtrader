import { Component, EventEmitter, Output, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { TabsModule } from '@openng/optimus-ui/tabs';
import { ButtonModule } from '@openng/optimus-ui/button';
import { finalize } from 'rxjs/operators';
import { AlgoAlertService, AlertEvaluation, AlertNotification } from '../service/algo-alert.service';
import { AlgoAlertDiagnosticsTableComponent } from './algo-alert-diagnostics-table.component';
import { AlgoAlertNotificationTableComponent } from './algo-alert-notification-table.component';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { HelpIds } from '../../lib/help/help.ids';
import { AppHelper } from '../../lib/helper/app.helper';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';

/**
 * Live alert health and explicit recovery controls. The tables own their formatting, paging, filtering and, for the
 * notifications, the selection and the retry and delete entries of the context menu; this dialog loads the data and
 * runs the actions.
 */
@Component({
  selector: 'algo-alert-diagnostics',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    CommonModule,
    TranslateModule,
    DialogModule,
    TabsModule,
    ButtonModule,
    AlgoAlertDiagnosticsTableComponent,
    AlgoAlertNotificationTableComponent
  ],
  template: `<p-dialog
    [header]="'ALERT_DIAGNOSTICS' | translate"
    [visible]="true"
    [modal]="true"
    [closable]="true"
    [closeOnEscape]="true"
    [style]="{ width: '95vw' }"
    (onHide)="closed.emit()">
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
          <algo-alert-notification-table
            [rows]="notifications"
            (retry)="retry($event)"
            (delete)="deleteNotifications($event)" />
        </p-tabpanel> </p-tabpanels
    ></p-tabs>
    <ng-template #footer>
      <div class="d-flex align-items-center w-100">
        <p-button [rounded]="true" (click)="helpLink()">
          <i class="pi pi-question" pButtonIcon></i>
        </p-button>
        <div class="ms-auto">
          <p-button [label]="'ALERT_REFRESH' | translate" [disabled]="busy" (click)="refresh()" />
          @if (!readOnly) {
            <p-button class="ms-1" [label]="'ALERT_EVALUATE_NOW' | translate" [disabled]="busy" (click)="evaluate()" />
          }
        </div>
      </div>
    </ng-template>
  </p-dialog>`
})
export class AlgoAlertDiagnosticsDialogComponent implements OnInit {
  @Output() closed = new EventEmitter<void>();
  evaluations: AlertEvaluation[] = [];
  trading: any[] = [];
  notifications: AlertNotification[] = [];
  busy = false;
  readOnly: boolean;
  constructor(
    private service: AlgoAlertService,
    private gps: GlobalparameterService,
    private translateService: TranslateService,
    private confirmationService: ConfirmationService,
    private messageToastService: MessageToastService
  ) {
    this.readOnly = gps.isReadOnlyUser();
  }
  ngOnInit(): void {
    this.refresh();
  }
  refresh(): void {
    this.service.trading().subscribe((rows) => (this.trading = rows));
    this.service.evaluations().subscribe((rows) => (this.evaluations = rows));
    this.loadNotifications();
  }
  loadNotifications(): void {
    this.busy = true;
    this.service
      .notifications()
      .pipe(finalize(() => (this.busy = false)))
      .subscribe((rows) => (this.notifications = rows));
  }
  evaluate(): void {
    this.busy = true;
    this.service
      .evaluate()
      .pipe(finalize(() => (this.busy = false)))
      .subscribe(() => this.refresh());
  }
  /** Opens the user manual page on alerts, which also explains evaluation outcomes and notification delivery. */
  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_ALGO_ALERT);
  }
  /** Queues the delivery of one failed notification again; the table has already checked that it is retryable. */
  retry(idNotification: number): void {
    this.busy = true;
    this.service
      .retry(idNotification)
      .pipe(finalize(() => (this.busy = false)))
      .subscribe(() => this.loadNotifications());
  }
  /**
   * Deletes the selected notifications after a confirmation; the table has already checked that all are deletable.
   *
   * @param ids - The ids of the selected notifications
   */
  deleteNotifications(ids: number[]): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORDS|ALERT_NOTIFICATIONS',
      () => {
        this.busy = true;
        this.service
          .deleteNotifications(ids)
          .pipe(finalize(() => (this.busy = false)))
          .subscribe(() => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORDS', {
              i18nRecord: 'ALERT_NOTIFICATIONS'
            });
            this.loadNotifications();
          });
      }
    );
  }
}
