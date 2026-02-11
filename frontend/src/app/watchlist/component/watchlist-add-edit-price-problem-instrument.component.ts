import {Component, Input, OnInit} from '@angular/core';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {HelpIds} from '../../lib/help/help.ids';
import {WatchlistService} from '../service/watchlist.service';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {IntraHistoricalWatchlistProblem} from '../model/intra.historical.watchlist.problem';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {atLeastOneFieldValidator} from '../../lib/validator/validator';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

/**
 * Dialog component for adding securities and currencies to an empty watchlist whose repeat counter has reached its limit.
 * Allows users to select whether intraday and/or historical problem cases should be added, along with configuring the
 * number of days since last work parameter. The dialog provides checkboxes for intraday/historical selection and a
 * numeric input for the days threshold.
 *
 * TODO: This dialog could well be transformed into a dialog for dynamic inputs.
 */
@Component({
    selector: 'watchlist-add-edit-price-problem-instrument',
    template: `
    <p-dialog header="{{'WATCHLIST_ADD_PROBLEM_INSTRUMENT' | translate}}" [visible]="visibleDialog"
              [style]="{width: '450px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: true,
    imports: [
      TranslateModule,
      DialogModule,
      DynamicFormModule
    ]
})
export class WatchlistAddEditPriceProblemInstrumentComponent extends SimpleEditBase implements OnInit {
  /** The unique identifier of the watchlist to add problem instruments to */
  @Input() idWatchlist: number;

  /**
   * Creates a new instance of the watchlist add/edit price problem instrument dialog component.
   *
   * @param translateService Service for handling internationalization and text translation
   * @param watchlistService Service for performing watchlist-related operations including adding problem instruments
   * @param gps Global parameter service for accessing application-wide configuration and user settings
   */
  constructor(public translateService: TranslateService,
              private watchlistService: WatchlistService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_WATCHLIST_PRICE_FEED, gps);
  }

  /** Initializes the component by setting up form configuration, field definitions, and validation rules */
  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      6, this.helpLink.bind(this));
    const addGroupConfig: FieldConfig[] = [
      DynamicFieldHelper.createFieldCheckboxHeqF('addIntraday', {defaultValue: true}),
      DynamicFieldHelper.createFieldCheckboxHeqF('addHistorical', {defaultValue: true})
    ];
    this.config = [
      {formGroupName: 'addGroup', fieldConfig: addGroupConfig},
      DynamicFieldHelper.createFieldMinMaxNumberHeqF(DataType.Numeric, 'daysSinceLastWork',
        true, 2, 90, {defaultValue: 60}),
      DynamicFieldHelper.createSubmitButton('APPLY')
    ] as any[];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  /**
   * Initializes form validation by setting up the at-least-one-field validator for the add group and configuring
   * error messages to ensure at least one of the intraday or historical checkboxes is selected.
   */
  protected override initialize(): void {
    this.configObject.addGroup.validation = [atLeastOneFieldValidator];
    this.configObject.addGroup.errors = [{
      name: 'required',
      keyi18n: 'required',
      rules: ['dirty']
    }];
    TranslateHelper.translateMessageError(this.translateService, this.configObject.addGroup);
    this.configObject.addGroup.formControl.setValidators(atLeastOneFieldValidator);
  }

  /**
   * Handles form submission by creating an IntraHistoricalWatchlistProblem object from form values and sending it
   * to the backend service to add problem instruments to the specified watchlist.
   *
   * @param value Object containing the form values including addIntraday, addHistorical, and daysSinceLastWork properties
   */
  submit(value: { [value: string]: any }): void {
    const ihwp = new IntraHistoricalWatchlistProblem();
    this.form.cleanMaskAndTransferValuesToBusinessObject(ihwp);

    this.watchlistService.addInstrumentsWithPriceDataProblems(this.idWatchlist, ihwp).subscribe({
      next: watchlist =>
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED)),
      error: () => this.configObject.submit.disabled = false
    });
  }

}
