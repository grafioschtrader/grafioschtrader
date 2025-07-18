import {AfterViewInit, Component, OnInit} from '@angular/core';
import {SimpleDynamicEditBase} from '../../lib/edit/simple.dynamic.edit.base';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {WatchlistService} from '../service/watchlist.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {Watchlist} from '../../entities/watchlist';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {HelpIds} from '../../shared/help/help.ids';

/**
 * Create or modify a watchlist, only the name can be edited.
 */
@Component({
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                  (submitBt)="submit($event)">
    </dynamic-form>
  `,
  standalone: false
})
export class WatchlistEditDynamicComponent extends SimpleDynamicEditBase<Watchlist> implements OnInit, AfterViewInit {
  callParam: CallParam;

  constructor(private gpsGT: GlobalparameterGTService,
    dynamicDialogConfig: DynamicDialogConfig,
    dynamicDialogRef: DynamicDialogRef,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    watchlistService: WatchlistService) {
    super(dynamicDialogConfig, dynamicDialogRef, HelpIds.HELP_WATCHLIST, translateService, gps, messageToastService, watchlistService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.callParam = this.dynamicDialogConfig.data.callParam;
    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'WATCHLIST_NAME', 25, true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngAfterViewInit(): void {
    this.form.setDefaultValuesAndEnableSubmit();
    if (this.callParam.thisObject != null) {
      this.form.transferBusinessObjectToForm(this.callParam.thisObject);
    }
    setTimeout(() => this.configObject.name.elementRef.nativeElement.focus());
  }


  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Watchlist {
    const watchlist: Watchlist = new Watchlist();
    if (this.callParam.thisObject) {
      Object.assign(watchlist, this.callParam.thisObject);
    }
    watchlist.idTenant = this.gps.getIdTenant();
    this.form.cleanMaskAndTransferValuesToBusinessObject(watchlist);
    return watchlist;
  }

}

