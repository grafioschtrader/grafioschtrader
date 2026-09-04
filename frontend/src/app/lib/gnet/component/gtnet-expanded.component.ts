import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { AccordionModule } from '@openng/optimus-ui/accordion';
import { ClassDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { ProcessedActionData } from '../../types/processed.action.data';
import { ExchangeKindTypeInfo, GTNet, GTNetMaintenanceWindow } from '../model/gtnet';
import { GTNetMessage } from '../model/gtnet.message';
import { GTNetConfigEntityTableComponent } from './gtnet-config-entity-table.component';
import { GTNetMaintenanceWindowTableComponent } from './gtnet-maintenance-window-table.component';
import { GTNetMessageTreeTableComponent } from './gtnet-message-treetable.component';
import { GTNetMessageAttemptView } from '../model/gtnet-message-attempt';
import { GTNetMessageAttemptTableComponent } from './gtnet-message-attempt-table.component';

/**
 * Content of an expanded row in the GTNet setup table. It offers the peer's entity configuration, messages,
 * administrator-visible delivery attempts and announced maintenance windows in accordion panels, all closed initially.
 *
 * <p>
 * Every panel header states how many records its panel holds, so whether there is anything to see is answered without
 * opening it. A panel with nothing in it is disabled rather than hidden, which keeps the panels in a stable order.
 * </p>
 *
 * <p>
 * The content of a panel is removed from the DOM while the panel is closed, because an Optimus accordion keeps its
 * content mounted. Only this way are the three tables built when the user opens a panel, and not all at once the
 * moment the row is expanded.
 * </p>
 */
@Component({
  selector: 'gtnet-expanded',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    AccordionModule,
    GTNetConfigEntityTableComponent,
    GTNetMaintenanceWindowTableComponent,
    GTNetMessageTreeTableComponent,
    GTNetMessageAttemptTableComponent
  ],
  template: `
    <p-accordion [multiple]="true" [value]="openPanels" (valueChange)="onPanelsChange($event)">
      <p-accordion-panel [value]="PANEL_ENTITIES" [disabled]="entityCount === 0">
        <p-accordion-header>
          <h5>{{ 'GT_NET_CONFIG_ENTITY_TABLE' | translate }} ({{ entityCount }})</h5>
        </p-accordion-header>
        <p-accordion-content>
          @if (isOpen(PANEL_ENTITIES)) {
            <gtnet-config-entity-table
              [gtNetEntities]="gtNet.gtNetEntities"
              [exchangeKindTypes]="exchangeKindTypes"
              (dataChanged)="configEntityDataChanged.emit($event)">
            </gtnet-config-entity-table>
          }
        </p-accordion-content>
      </p-accordion-panel>

      <p-accordion-panel [value]="PANEL_MESSAGES" [disabled]="messageCount === 0">
        <p-accordion-header>
          <h5>{{ 'GT_NET_MESSAGE' | translate }} ({{ messageCount }})</h5>
        </p-accordion-header>
        <p-accordion-content>
          @if (isOpen(PANEL_MESSAGES)) {
            @if (loading) {
              <div style="padding: 1rem; text-align: center;">
                <i class="fa fa-spinner fa-spin"></i> {{ 'LOADING' | translate }}...
              </div>
            } @else if (gtNetMessages?.length) {
              <gtnet-message-treetable
                [gtNetMessages]="gtNetMessages"
                [incomingPendingIds]="incomingPendingIds"
                [outgoingPendingIds]="outgoingPendingIds"
                [formDefinitions]="formDefinitions"
                (dataChanged)="messageDataChanged.emit($event)">
              </gtnet-message-treetable>
            }
          }
        </p-accordion-content>
      </p-accordion-panel>

      @if (canAdministerGTNet) {
        <p-accordion-panel [value]="PANEL_ATTEMPTS" [disabled]="messageAttemptCount === 0">
          <p-accordion-header>
            <h5>{{ 'DELIVERY_ATTEMPTS' | translate }} ({{ messageAttemptCount }})</h5>
          </p-accordion-header>
          <p-accordion-content>
            @if (isOpen(PANEL_ATTEMPTS)) {
              @if (loading) {
                <div style="padding: 1rem; text-align: center;">
                  <i class="fa fa-spinner fa-spin"></i> {{ 'LOADING' | translate }}...
                </div>
              } @else if (messageAttempts?.length) {
                <gtnet-message-attempt-table [messageAttempts]="messageAttempts"> </gtnet-message-attempt-table>
              }
            }
          </p-accordion-content>
        </p-accordion-panel>
      }

      <p-accordion-panel [value]="PANEL_WINDOWS" [disabled]="maintenanceWindowCount === 0">
        <p-accordion-header>
          <h5>{{ 'GT_NET_MAINTENANCE_WINDOW' | translate }} ({{ maintenanceWindowCount }})</h5>
        </p-accordion-header>
        <p-accordion-content>
          @if (isOpen(PANEL_WINDOWS)) {
            @if (loading) {
              <div style="padding: 1rem; text-align: center;">
                <i class="fa fa-spinner fa-spin"></i> {{ 'LOADING' | translate }}...
              </div>
            } @else if (maintenanceWindows?.length) {
              <gtnet-maintenance-window-table [maintenanceWindows]="maintenanceWindows">
              </gtnet-maintenance-window-table>
            }
          }
        </p-accordion-content>
      </p-accordion-panel>
    </p-accordion>
  `,
  changeDetection: ChangeDetectionStrategy.Eager
})
export class GTNetExpandedComponent {
  /** The peer of the expanded row. */
  @Input() gtNet: GTNet;
  @Input() exchangeKindTypes: ExchangeKindTypeInfo[] = [];
  /** Messages of this peer, loaded by the parent when the row was expanded. */
  @Input() gtNetMessages: GTNetMessage[] = [];
  /** Maintenance windows of this peer, loaded by the parent when the row was expanded. */
  @Input() maintenanceWindows: GTNetMaintenanceWindow[] = [];
  /** Per-target outcomes of outgoing messages stored under this row. */
  @Input() messageAttempts: GTNetMessageAttemptView[] = [];
  /** Number of messages, known before they are loaded so the header can show it. */
  @Input() messageCount = 0;
  /** Number of announced windows, likewise known in advance. */
  @Input() maintenanceWindowCount = 0;
  @Input() messageAttemptCount = 0;
  @Input() canAdministerGTNet = false;
  @Input() incomingPendingIds: number[] = [];
  @Input() outgoingPendingIds: number[] = [];
  @Input() formDefinitions: { [type: string]: ClassDescriptorInputAndShow };
  /** True while the parent is still fetching the lazily loaded panels. */
  @Input() loading = false;

  @Output() configEntityDataChanged = new EventEmitter<ProcessedActionData>();
  @Output() messageDataChanged = new EventEmitter<ProcessedActionData>();

  /**
   * The message tree table, present only while its panel is open. The setup table cannot query it directly — a
   * ViewChildren query does not reach across a component boundary — so the reset is delegated through here.
   */
  @ViewChild(GTNetMessageTreeTableComponent) messageTreeTable: GTNetMessageTreeTableComponent;

  readonly PANEL_ENTITIES = 'entities';
  readonly PANEL_MESSAGES = 'messages';
  readonly PANEL_ATTEMPTS = 'attempts';
  readonly PANEL_WINDOWS = 'windows';

  /** The panels the user has opened. All panels start closed. */
  openPanels: string[] = [];

  /** Number of entity configurations of this peer. */
  get entityCount(): number {
    return this.gtNet?.gtNetEntities?.filter((entity) => entity.gtNetConfigEntity != null).length ?? 0;
  }

  /**
   * Determines whether a panel is currently open. Used to create the panel content only on demand.
   *
   * @param panel the value of the panel in question
   * @returns true when the panel is open and its content must be rendered
   */
  isOpen(panel: string): boolean {
    return this.openPanels.includes(panel);
  }

  /**
   * Records which panels are open after the user toggled one.
   *
   * @param panels the values of the panels which are now open
   */
  onPanelsChange(panels: string | string[]): void {
    this.openPanels = Array.isArray(panels) ? panels : [panels];
  }

  /** Clears the selection of the message tree table, if that panel is open at all. */
  clearMessageSelection(): void {
    this.messageTreeTable?.clearSelection();
  }
}
