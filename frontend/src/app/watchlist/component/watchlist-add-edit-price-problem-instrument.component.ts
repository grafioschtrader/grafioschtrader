import {Component, Input, OnInit} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {HelpIds} from '../../shared/help/help.ids';
import {WatchlistService} from '../service/watchlist.service';
import {SimpleEditBase} from '../../shared/edit/simple.edit.base';
import {AppHelper} from '../../shared/helper/app.helper';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {IntraHistoricalWatchlistProblem} from '../model/intra.historical.watchlist.problem';
import {DataType} from '../../dynamic-form/models/data.type';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {atLeastOneFieldValidator} from '../../shared/validator/validator';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';

/**
 * Dialog for adding securities and currencies to an empty watchlist whose repeat counter has reached its limit.
 * It can be selected if intraday and/or historical problem cases are added.
 * TODO: This dialog could well be transformed into a dialog for dynamic inputs.
 */
@Component({
  selector: 'watchlist-add-edit-price-problem-instrument',
  template: `
    <p-dialog header="{{'WATCHLIST_ADD_PROBLEM_INSTRUMENT' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '450px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class WatchlistAddEditPriceProblemInstrumentComponent extends SimpleEditBase implements OnInit {
  @Input() idWatchlist: number;

  constructor(public translateService: TranslateService,
              private messageToastService: MessageToastService,
              private watchlistService: WatchlistService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_WATCHLIST_PRICE_FEED, gps);
  }

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
