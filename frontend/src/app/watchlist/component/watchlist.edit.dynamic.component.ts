import {AfterViewInit, Component, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {SimpleDynamicEditBase} from '../../lib/edit/simple.dynamic.edit.base';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {WatchlistService} from '../service/watchlist.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {Watchlist} from '../../entities/watchlist';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {HelpIds} from '../../lib/help/help.ids';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

/**
 * Angular component for creating or modifying watchlists in a dynamic dialog.
 *
 * Provides a simple form interface that allows users to edit only the name
 * property of a watchlist entity. Extends SimpleDynamicEditBase for common
 * CRUD functionality and dynamic form generation.
 */
@Component({
  // Selector is not used
  selector: 'watchlist-edit-dynamic',
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                  (submitBt)="submit($event)">
    </dynamic-form>
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule, DynamicFormModule]
})
export class WatchlistEditDynamicComponent extends SimpleDynamicEditBase<Watchlist> implements OnInit, AfterViewInit {
  /**
   * Call parameters containing the watchlist entity to be edited and operation context.
   */
  callParam: CallParam;

  /**
   * Creates an instance of WatchlistEditDynamicComponent.
   *
   * @param {GlobalparameterGTService} gpsGT - GT-specific global parameters service
   * @param {DynamicDialogConfig} dynamicDialogConfig - PrimeNG dialog configuration
   * @param {DynamicDialogRef} dynamicDialogRef - PrimeNG dialog reference
   * @param {TranslateService} translateService - Angular translation service
   * @param {GlobalparameterService} gps - Global parameters service
   * @param {MessageToastService} messageToastService - Toast notification service
   * @param {WatchlistService} watchlistService - Watchlist CRUD service
   */
  constructor(private gpsGT: GlobalparameterGTService,
    dynamicDialogConfig: DynamicDialogConfig,
    dynamicDialogRef: DynamicDialogRef,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    watchlistService: WatchlistService) {
    super(dynamicDialogConfig, dynamicDialogRef, HelpIds.HELP_WATCHLIST, translateService, gps, messageToastService, watchlistService);
  }

  /**
   * Initializes the component and configures the dynamic form.
   * Sets up form configuration, extracts call parameters, and creates field definitions.
   */
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

  /**
   * Completes form setup after view initialization.
   * Enables submit button, populates form with existing data if editing, and sets focus.
   */
  ngAfterViewInit(): void {
    this.form.setDefaultValuesAndEnableSubmit();
    if (this.callParam.thisObject != null) {
      this.form.transferBusinessObjectToForm(this.callParam.thisObject);
    }
    setTimeout(() => this.configObject.name.elementRef.nativeElement.focus());
  }


  /**
   * Creates or updates a watchlist entity before saving.
   * Handles both new watchlist creation and existing watchlist updates.
   *
   * @param {Object} value - Form field values
   * @returns {Watchlist} The prepared watchlist entity for backend persistence
   */
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

