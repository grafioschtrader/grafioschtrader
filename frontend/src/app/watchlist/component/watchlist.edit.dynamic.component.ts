import { afterNextRender, Component, inject, Injector, OnInit } from '@angular/core';

import { SimpleDynamicEditBase } from '../../lib/edit/simple.dynamic.edit.base';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { TranslateModule } from '@ngx-translate/core';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { WatchlistService } from '../service/watchlist.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { Watchlist } from '../../entities/watchlist';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';

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
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  `,
  imports: [TranslateModule, ...DYNAMIC_FORM_IMPORTS]
})
export class WatchlistEditDynamicComponent extends SimpleDynamicEditBase<Watchlist> implements OnInit {
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);

  /**
   * Call parameters containing the watchlist entity to be edited and operation context.
   */
  callParam: CallParam;
  private readonly injector = inject(Injector);

  /**
   * Creates an instance of WatchlistEditDynamicComponent.
   *
   */
  constructor() {
    super(HelpIds.HELP_WATCHLIST, inject(WatchlistService));
  }

  /**
   * Initializes the component and configures the dynamic form.
   * Sets up form configuration, extracts call parameters, and creates field definitions.
   */
  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    this.callParam = this.dynamicDialogConfig.data.callParam;
    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'WATCHLIST_NAME', 25, true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    afterNextRender(() => this.initializeForm(), { injector: this.injector });
  }

  /**
   * Completes form setup after view initialization.
   * Enables submit button, populates form with existing data if editing, and sets focus.
   */
  private initializeForm(): void {
    this.form().setDefaultValuesAndEnableSubmit();
    if (this.callParam.thisObject != null) {
      this.form().transferBusinessObjectToForm(this.callParam.thisObject);
    }
    this.configObject.name.elementRef?.nativeElement.focus();
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
    watchlist.idTenant = this.gps.requireIdTenant();
    this.form().cleanMaskAndTransferValuesToBusinessObject(watchlist);
    return watchlist;
  }
}
