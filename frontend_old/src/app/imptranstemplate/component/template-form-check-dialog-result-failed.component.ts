import {Component, Input, OnInit} from '@angular/core';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {DataType} from '../../dynamic-form/models/data.type';
import {FailedParsedTemplateState} from './failed.parsed.template.state';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {FilterService} from 'primeng/api';

/**
 * Display if the import template was not completely recognized. The last successfully recognized field per import
 * template is displayed.
 */
@Component({
  selector: 'template-form-check-dialog-result-failed',
  template: `
    <div class="datatable">
      <p-table [columns]="fields" [value]="failedParsedTemplateStateList" selectionMode="single"
               styleClass="sticky-table p-datatable-striped p-datatable-gridlines"
               responsiveLayout="scroll" sortField="security.name">
        <ng-template pTemplate="caption">
          <h4>{{'IMPORT_POS_CHECK_FAILED' | translate}}</h4>
        </ng-template>
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th *ngFor="let field of fields" [pSortableColumn]="field.field"
                [pTooltip]="field.headerTooltipTranslated" [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
              {{field.headerTranslated}}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td *ngFor="let field of fields" [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}"
                [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-right': ''">
              {{getValueByPath(el, field)}}
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `
})
export class TemplateFormCheckDialogResultFailedComponent extends TableConfigBase implements OnInit {
  @Input() failedParsedTemplateStateList: FailedParsedTemplateState[];

  constructor(filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);
  }

  ngOnInit(): void {
    this.addColumnFeqH(DataType.String, 'templatePurpose', true, false, {width: 250});
    this.addColumnFeqH(DataType.DateString, 'validSince', true, false, {width: 70});
    this.addColumnFeqH(DataType.String, 'localeStr', true, false, {width: 70});
    this.addColumnFeqH(DataType.String, 'lastMatchingProperty', true, false, {width: 100});
    this.addColumn(DataType.String, 'errorMessage', 'ERROR', true, false);

    this.prepareTableAndTranslate();
  }
}
