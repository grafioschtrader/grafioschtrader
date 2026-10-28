import { BaseUDFDataEdit } from './base.udf.data.edit';
import { Component, OnInit } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { HelpIds } from '../../help/help.ids';
import { AppHelper } from '../../helper/app.helper';
import { UDFMetadataHelper } from './udf.metadata.helper';

import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../dynamic-form/dynamic-form.imports';

/**
 * Component for editing user-defined field data values for general entities.
 * Provides a dynamic form dialog for entering or updating UDF field values on entities like portfolios,
 * watchlists, and other general information classes without entity-specific extensions.
 * The form fields are generated dynamically based on UDF metadata definitions for the entity type.
 */
@Component({
  selector: 'udf-general-edit',
  templateUrl: '../view/general.udf.data.edit.html',
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class UDFGeneralEditComponent extends BaseUDFDataEdit implements OnInit {
  /**
   * Creates the UDF general edit component.
   *
   */
  constructor() {
    super(HelpIds.HELP_WATCHLIST_UDF);

    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
  }

  /**
   * Initializes the component by loading UDF field descriptors for the entity type.
   * Retrieves cached descriptors from session storage and configures the dynamic form.
   */
  ngOnInit(): void {
    super.baseInit(UDFMetadataHelper.getFieldDescriptorByEntity(this.uDFGeneralCallParam().entityName));
  }
}
