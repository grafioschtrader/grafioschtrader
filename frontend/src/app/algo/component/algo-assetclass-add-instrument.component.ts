import { SecuritycurrencySearchBase } from '../../securitycurrency/component/securitycurrency.search.base';
import { Component, EventEmitter, Input, Output, ViewChild, ChangeDetectionStrategy } from '@angular/core';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { MultipleRequestToOneService } from '../../shared/service/multiple.request.to.one.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProcessedAction } from '../../lib/types/processed.action';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';
import { AlgoAssetclassAddInstrumentTableComponent } from './algo-assetclass-add-instrument-table.component';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';

/**
 * Search dialog that adds instruments to a custom category of an algo hierarchy beyond those of its watchlist. It is
 * opened after the asset class dialog was saved with "add instruments by search" ticked. Several instruments can be
 * selected and added in one go, and the search can be repeated until the dialog is closed.
 */
@Component({
  selector: 'algo-assetclass-add-instrument',
  template: `
    <p-dialog
      header="{{ 'ADD_INSTRUMENTS_BY_SEARCH' | translate }}"
      [visible]="visibleDialog"
      [style]="{ width: '720px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <p class="big-size">{{ 'SEARCH_DIALOG_HELP' | translate }}</p>
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #dynamicFormComponent="dynamicForm"
        (submitBt)="submit($event)">
      </dynamic-form>
      <br />
      <algo-assetclass-add-instrument-table [tenantLimits]="[]"> </algo-assetclass-add-instrument-table>
    </p-dialog>
  `,
  imports: [TranslateModule, DialogModule, DynamicFormComponent, AlgoAssetclassAddInstrumentTableComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true
})
export class AlgoAssetclassAddInstrumentComponent extends SecuritycurrencySearchBase {
  /** Controls the visibility of the dialog. */
  @Input() visibleDialog: boolean;

  /** The custom category the selected instruments are added to. */
  @Input() idAlgoAssetclassSecurity: number;

  @ViewChild(AlgoAssetclassAddInstrumentTableComponent)
  aaaitc: AlgoAssetclassAddInstrumentTableComponent;

  /** Emitted when the dialog closes; the hierarchy view then reloads to show the added instruments. */
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  /**
   * Creates the search dialog for a custom category.
   *
   * @param gps - Global parameter service for application-wide settings
   * @param multipleRequestToOneService - Loads the select options of the search form
   * @param translateService - Angular translation service
   */
  constructor(
    gps: GlobalparameterService,
    multipleRequestToOneService: MultipleRequestToOneService,
    translateService: TranslateService
  ) {
    super(true, gps, multipleRequestToOneService, translateService);
  }

  /** Instruments may have been added, so the hierarchy is always reloaded. */
  onHide(event): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
  }

  override closeSearchDialog(event): void {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
  }

  childClearList(): void {
    this.aaaitc.clearList();
  }

  childLoadData(securitycurrencySearch: SecuritycurrencySearch): void {
    this.aaaitc.loadData(this.idAlgoAssetclassSecurity, securitycurrencySearch);
  }
}
