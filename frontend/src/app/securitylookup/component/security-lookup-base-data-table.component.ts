import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Injector,
  Input,
  OnChanges,
  Output,
  SimpleChanges
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FilterService } from '@openng/optimus-ui/api';
import { CheckboxModule } from '@openng/optimus-ui/checkbox';
import { TooltipModule } from '@openng/optimus-ui/tooltip';

import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { ColumnConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { SecurityLookupCandidate } from '../model/security-lookup';

/**
 * First table of the security lookup dialog: one row per listing found by the data providers. A row expands to the
 * further master data and to every raw value of the provider, so the user can judge the listing before choosing it.
 */
@Component({
  selector: 'security-lookup-base-data-table',
  standalone: true,
  imports: [ConfigurableTableComponent, TranslateModule, FormsModule, CheckboxModule, TooltipModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <configurable-table
      [data]="candidates"
      [fields]="fields"
      dataKey="providerInstrumentId"
      [selectionMode]="'single'"
      [selection]="selectedCandidate"
      (selectionChange)="onSelectionChange($event)"
      [customSortFn]="customSort.bind(this)"
      [multiSortMeta]="multiSortMeta"
      [valueGetterFn]="getValueByPath.bind(this)"
      [contextMenuEnabled]="false"
      [scrollable]="false"
      [baseLocale]="baseLocale"
      [expandable]="true"
      [expandedRowTemplate]="baseDataExpansion">
      <h5 caption>{{ 'BASE_DATA' | translate }}</h5>
      <ng-template #customCell let-row let-field="field">
        @if (field.field === takeOverNameField) {
          <span (click)="$event.stopPropagation()">
            <p-checkbox
              [ngModel]="row === nameCandidate"
              [binary]="true"
              [readonly]="row === nameCandidate"
              (onChange)="onNameCandidateChange(row)"></p-checkbox>
          </span>
        } @else if (field.templateName === 'check') {
          <span><i [class.fa]="getValueByPath(row, field)" [class.fa-check]="getValueByPath(row, field)"></i></span>
        } @else {
          <span [pTooltip]="getValueByPath(row, field)" tooltipPosition="top">{{ getValueByPath(row, field) }}</span>
        }
      </ng-template>
    </configurable-table>

    <ng-template #baseDataExpansion let-candidate>
      <div class="lookup-expansion">
        @for (field of expansionFields; track field.field) {
          @if (candidate[field.field] != null) {
            <div class="lookup-row">
              <span class="lookup-label">{{ field.headerTranslated }}</span>
              <span>{{ getValueByPath(candidate, field) }}</span>
              @if (candidate.fieldSources?.[field.field]; as source) {
                <span class="lookup-source">({{ source }})</span>
              }
            </div>
          }
        }
        @if (candidate.baseDataDetail) {
          <div class="lookup-row">
            <span class="lookup-label">{{ 'PROBE_DETAIL' | translate }}</span>
            <span>{{ candidate.baseDataDetail }}</span>
          </div>
        }
        @if (hasAttributes(candidate)) {
          <h6>{{ 'PROVIDER_ATTRIBUTES' | translate }}</h6>
          @for (attribute of attributeList(candidate); track attribute[0]) {
            <div class="lookup-row">
              <span class="lookup-label">{{ attribute[0] }}</span>
              <span>{{ attribute[1] }}</span>
            </div>
          }
        }
      </div>
    </ng-template>
  `,
  styles: [
    `
      .lookup-expansion {
        padding: 0.5rem 1rem;
      }
      .lookup-row {
        display: flex;
        gap: 1rem;
      }
      .lookup-source {
        color: var(--text-color-secondary);
        font-size: 0.85rem;
      }
      .lookup-label {
        min-width: 220px;
        color: var(--text-color-secondary);
      }
    `
  ]
})
export class SecurityLookupBaseDataTableComponent extends TableConfigBase implements OnChanges {
  /** Listings found by the lookup */
  @Input() candidates: SecurityLookupCandidate[] = [];

  /** Emits the chosen listing, or null when the selection is removed */
  @Output() candidateSelected = new EventEmitter<SecurityLookupCandidate>();

  /** The chosen listing; the dialog preselects it when only one exists */
  @Input() selectedCandidate: SecurityLookupCandidate;

  /**
   * The listing whose name is taken over. It may differ from the chosen listing, because the providers spell the name
   * of the same instrument differently and the listing with the better spelling may lack the wanted connectors.
   */
  @Input() nameCandidate: SecurityLookupCandidate;

  /** Emits the listing whose name is taken over */
  @Output() nameCandidateChange = new EventEmitter<SecurityLookupCandidate>();

  /** Column with the checkbox choosing the name taken over; the candidate has no such property */
  readonly takeOverNameField = 'takeOverName';

  /**
   * Further master data shown in the expanded row. They are hidden columns of the table, so that formatting and the
   * translation of enum values go through the GT data types like every other column.
   */
  expansionFields: ColumnConfig[] = [];

  /**
   * Creates the table and the expansion fields.
   *
   * @param filterService - Optimus UI filter service
   * @param usersettingsService - Persists table settings
   * @param translateService - Translation of headers and enum values
   * @param gps - Locale and formatting settings
   * @param injector - Injector of the base class
   */
  constructor(
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector
  ) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.addColumnFeqH(DataType.Boolean, this.takeOverNameField, true, false, { width: 60 });
    this.addColumnFeqH(DataType.String, 'name', true, false, { width: 220 });
    this.addColumnFeqH(DataType.String, 'tickerSymbol', true, false, { width: 70 });
    this.addColumn(DataType.String, 'isin', 'ISIN', true, false, { width: 110 });
    this.addColumnFeqH(DataType.String, 'currency', true, false, { width: 60 });
    this.addColumnFeqH(DataType.String, 'categoryType', true, false, {
      width: 100,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', true, false, {
      width: 110,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'providerIds', 'PROVIDER_ID', true, false, { width: 80 });
    this.addColumnFeqH(DataType.String, 'baseDataStatus', true, false, {
      width: 90,
      translateValues: TranslateValue.NORMAL
    });
    this.addColumnFeqH(DataType.Boolean, 'instrumentTypeMismatch', true, false, { width: 80, templateName: 'check' });
    this.expansionFields = [
      this.addColumnFeqH(DataType.String, 'providerInstrumentId', false, true),
      this.addColumnFeqH(DataType.String, 'issuerCountry', false, true),
      this.addColumnFeqH(DataType.DateString, 'activeFromDate', false, true),
      this.addColumnFeqH(DataType.DateString, 'activeToDate', false, true),
      this.addColumnFeqH(DataType.String, 'distributionFrequency', false, true, {
        translateValues: TranslateValue.NORMAL
      }),
      this.addColumnFeqH(DataType.NumericInteger, 'denomination', false, true),
      this.addColumnFeqH(DataType.String, 'productLink', false, true),
      this.addColumnFeqH(DataType.String, 'stockexchangeLink', false, true)
    ];
    this.prepareTableAndTranslate();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['candidates'] && this.candidates) {
      this.createTranslatedValueStore(this.candidates);
    }
  }

  /**
   * Keeps the selection and reports it to the dialog, which shows the connectors of this listing.
   *
   * @param candidate - The chosen listing, or null when the selection is removed
   */
  onSelectionChange(candidate: SecurityLookupCandidate): void {
    this.selectedCandidate = candidate;
    this.candidateSelected.emit(candidate);
  }

  /**
   * Makes the listing the source of the name. The checkbox works like a radio button: exactly one listing supplies the
   * name, so the checked box is read-only.
   *
   * @param candidate - The listing whose checkbox was clicked
   */
  onNameCandidateChange(candidate: SecurityLookupCandidate): void {
    this.nameCandidate = candidate;
    this.nameCandidateChange.emit(candidate);
  }

  hasAttributes(candidate: SecurityLookupCandidate): boolean {
    return candidate.providerAttributes && Object.keys(candidate.providerAttributes).length > 0;
  }

  attributeList(candidate: SecurityLookupCandidate): [string, string][] {
    return Object.entries(candidate.providerAttributes);
  }
}
