import {Component, Input, OnInit} from '@angular/core';
import {CombineTemplateAndImpTransPos} from './combine.template.and.imp.trans.pos';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {SingleRecordConfigBase} from '../../shared/datashowbase/single.record.config.base';
import {DataType} from '../../dynamic-form/models/data.type';
import {ImportSettings} from './import.settings';
import {AppSettings} from '../../shared/app.settings';

/**
 * Show the file name of an imported transaction file.
 */
@Component({
  selector: 'securityaccount-import-extended-info-filename',
  templateUrl: '../view/securityaccount.import.extended.info.html'
})
export class SecurityaccountImportExtendedInfoFilenameComponent extends SingleRecordConfigBase implements OnInit {

  @Input() combineTemplateAndImpTransPos: CombineTemplateAndImpTransPos;

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addFieldPropertyFeqH(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'fileNameOriginal',
      {fieldsetName: AppSettings.IMPORT_TRANSACTION_TEMPLATE.toUpperCase()});
    this.translateHeadersAndColumns();
  }
}
