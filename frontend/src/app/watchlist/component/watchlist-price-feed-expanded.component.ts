import {Component, Input, OnInit} from '@angular/core';
import {TranslateModule} from '@ngx-translate/core';
import {AccordionModule} from 'primeng/accordion';
import {SecuritycurrencyPosition} from '../../entities/view/securitycurrency.position';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {SecuritycurrencyExtendedInfoComponent} from './securitycurrency-extended-info.component';
import {
  InstrumentStatisticsResultComponent
} from '../../shared/securitycurrency/instrument-statistics-result.component';
import {WatchlistExpandedPanelStateService} from '../service/watchlist.expanded.panel.state.service';

/**
 * Content of an expanded row in the price feed watchlist. It offers the extended instrument information and the
 * return and statistical data of the instrument in two accordion panels, both closed initially.
 *
 * <p>
 * The panels are not only hidden but their content is removed from the DOM while they are closed, because a PrimeNG
 * accordion keeps its content mounted. Only this way the statistics are requested from the server when the user
 * opens the panel for the first time and not already when the row is expanded.
 * </p>
 *
 * <p>
 * Which panels the user has open is remembered per instrument in {@link WatchlistExpandedPanelStateService}, so the
 * choice survives collapsing the row and changing the watchlist or tab. The statistics themselves are cached for the
 * same period by {@code InstrumentStatisticsCacheService}, hence reopening a panel does not repeat the request.
 * </p>
 */
@Component({
  selector: 'watchlist-price-feed-expanded',
  template: `
    <p-accordion [multiple]="true" [value]="openPanels" (valueChange)="onPanelsChange($event)">
      <p-accordion-panel [value]="PANEL_INFO">
        <p-accordion-header><h5>{{ 'EXTENDED_INSTRUMENT_INFO' | translate }}</h5></p-accordion-header>
        <p-accordion-content>
          @if (isOpen(PANEL_INFO)) {
            <securitycurrency-extended-info
              [dividendUrl]="position.dividendUrl"
              [splitUrl]="position.splitUrl"
              [historicalUrl]="position.historicalUrl"
              [intradayUrl]="position.intradayUrl"
              [feedConnectorsKV]="feedConnectorsKV"
              [securitycurrency]="position.securitycurrency">
            </securitycurrency-extended-info>
          }
        </p-accordion-content>
      </p-accordion-panel>
      <p-accordion-panel [value]="PANEL_STATISTICS">
        <p-accordion-header><h5>{{ 'RETURN_STATISTICAL_DATA' | translate }}</h5></p-accordion-header>
        <p-accordion-content>
          @if (isOpen(PANEL_STATISTICS)) {
            <instrument-statistics-result [showTitle]="false"
                                          [idSecuritycurrency]="position.securitycurrency.idSecuritycurrency">
            </instrument-statistics-result>
          }
        </p-accordion-content>
      </p-accordion-panel>
    </p-accordion>
  `,
  standalone: true,
  imports: [
    TranslateModule,
    AccordionModule,
    SecuritycurrencyExtendedInfoComponent,
    InstrumentStatisticsResultComponent
  ]
})
export class WatchlistPriceFeedExpandedComponent implements OnInit {
  /** The instrument of the expanded row together with its feed URLs. */
  @Input() position: SecuritycurrencyPosition<Security | Currencypair>;
  /** Key-value mapping of feed connector IDs to human-readable names. */
  @Input() feedConnectorsKV: { [id: string]: string };

  /** Value of the panel with the extended instrument and price feed information. */
  readonly PANEL_INFO = 'info';
  /** Value of the panel with the return and statistical data. */
  readonly PANEL_STATISTICS = 'statistics';

  /** The panels the user has open, restored from the state service when the row is expanded again. */
  openPanels: string[] = [];

  /**
   * Creates an instance of WatchlistPriceFeedExpandedComponent.
   *
   * @param panelStateService Service which remembers the open panels per instrument for the watchlist session
   */
  constructor(private panelStateService: WatchlistExpandedPanelStateService) {
  }

  /**
   * Restores the panels the user had open for this instrument. Without an earlier choice both panels stay closed.
   */
  ngOnInit(): void {
    this.openPanels = this.panelStateService.getOpenPanels(this.position.securitycurrency.idSecuritycurrency);
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
   * Persists the user's choice for this instrument when a panel is opened or closed.
   *
   * @param panels the values of the panels which are now open
   */
  onPanelsChange(panels: string | string[]): void {
    this.openPanels = Array.isArray(panels) ? panels : [panels];
    this.panelStateService.setOpenPanels(this.position.securitycurrency.idSecuritycurrency, this.openPanels);
  }
}
