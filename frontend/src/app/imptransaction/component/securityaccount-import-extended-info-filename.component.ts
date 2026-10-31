import { Component, OnInit, input } from '@angular/core';

import { TranslateModule } from '@ngx-translate/core';
import { AngularSvgIconModule } from 'angular-svg-icon';

import type { CombineTemplateAndImpTransPos } from '../../securityaccount/component/combine.template.and.imp.trans.pos';
import { SingleRecordConfigBase } from '../../lib/datashowbase/single.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ImportSettings } from './import.settings';
import { AppSettings } from '../../shared/app.settings';

/**
 * Show the file name of an imported transaction file.
 */
@Component({
  selector: 'securityaccount-import-extended-info-filename',
  templateUrl: '../../securityaccount/view/securityaccount.import.extended.info.html',
  imports: [TranslateModule, AngularSvgIconModule]
})
export class SecurityaccountImportExtendedInfoFilenameComponent extends SingleRecordConfigBase implements OnInit {
  readonly combineTemplateAndImpTransPos = input.required<CombineTemplateAndImpTransPos>();

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.addFieldPropertyFeqH(DataType.String, ImportSettings.IMPORT_TRANSACTION_POS + 'fileNameOriginal', {
      fieldsetName: AppSettings.IMPORT_TRANSACTION_TEMPLATE.toUpperCase()
    });
    this.translateHeadersAndColumns();
  }
}
