import {BaseUDFDataEdit} from './base.udf.data.edit';
import {Component, OnInit} from '@angular/core';
import {MessageToastService} from '../../message/message.toast.service';
import {UDFDataService} from '../service/udf.data.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {HelpIds} from '../../help/help.ids';
import {AppHelper} from '../../helper/app.helper';
import {UDFMetadataHelper} from './udf.metadata.helper';

/**
 * Component for editing user-defined field data values for general entities.
 * Provides a dynamic form dialog for entering or updating UDF field values on entities like portfolios,
 * watchlists, and other general information classes without entity-specific extensions.
 * The form fields are generated dynamically based on UDF metadata definitions for the entity type.
 */
@Component({
    selector: 'udf-general-edit',
    templateUrl: '../view/general.udf.data.edit.html',
    standalone: false
})
export class UDFGeneralEditComponent extends BaseUDFDataEdit implements OnInit {

  /**
   * Creates the UDF general edit component.
   *
   * @param messageToastService - Service for displaying user notifications
   * @param uDFDataService - Service for UDF data CRUD operations
   * @param translateService - Translation service for i18n support
   * @param gps - Global parameter service providing user settings and system configuration
   */
  constructor(
    messageToastService: MessageToastService,
    uDFDataService: UDFDataService,
    translateService: TranslateService,
    gps: GlobalparameterService) {
    super(messageToastService, uDFDataService, translateService, HelpIds.HELP_WATCHLIST_UDF, gps);

    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));
  }

  /**
   * Initializes the component by loading UDF field descriptors for the entity type.
   * Retrieves cached descriptors from session storage and configures the dynamic form.
   */
  ngOnInit(): void {
    super.baseInit(UDFMetadataHelper.getFieldDescriptorByEntity(this.uDFGeneralCallParam.entityName));
  }

}
