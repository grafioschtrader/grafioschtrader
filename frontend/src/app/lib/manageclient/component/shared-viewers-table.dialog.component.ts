import {Component, Injector, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ConfirmationService, FilterService} from 'primeng/api';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';
import {TooltipModule} from 'primeng/tooltip';

import {TableConfigBase} from '../../datashowbase/table.config.base';
import {DataType} from '../../dynamic-form/models/data.type';
import {TranslateValue} from '../../datashowbase/column.config';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {UserSettingsService} from '../../services/user.settings.service';
import {MessageToastService} from '../../message/message.toast.service';
import {InfoLevelType} from '../../message/info.leve.type';
import {AppHelper} from '../../helper/app.helper';
import {HelpIds} from '../../help/help.ids';
import {ManageClientService} from '../service/manage-client.service';
import {SharedViewerInfo} from '../model/shared-viewer-info';

/**
 * Dialog listing everyone who can read the current owner's portfolio, with columns for the person's e-mail and the kind
 * of access (a registered read grant or a dedicated read-only viewer login). Each row offers a revoke action that
 * removes the grant or deletes the viewer login. Part of the manage-client library feature (g.use.manageclient).
 */
@Component({
  template: `
    <div class="datatable">
      <div style="text-align: right; margin-bottom: 0.5rem;">
        <p-button [rounded]="true" (click)="helpLink()">
          <i class="pi pi-question" pButtonIcon></i>
        </p-button>
      </div>
      <p-table [columns]="fields" [value]="sharedViewers" selectionMode="single" stripedRows showGridlines>
        <ng-template #header>
          <tr>
            @for (field of fields; track field) {
              <th [pSortableColumn]="field.field">
                {{field.headerTranslated}}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            }
            <th style="width: 6rem"></th>
          </tr>
        </ng-template>
        <ng-template #body let-el>
          <tr [pSelectableRow]="el">
            @for (field of fields; track field) {
              <td>{{getValueByPath(el, field)}}</td>
            }
            <td>
              <button pButton type="button" class="p-button-text p-button-danger"
                      [pTooltip]="'REVOKE_READ_ACCESS' | translate" (click)="revoke(el)">
                <i class="fa fa-trash" pButtonIcon></i>
              </button>
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule, TableModule, ButtonModule, TooltipModule]
})
export class SharedViewersTableDialogComponent extends TableConfigBase implements OnInit {

  sharedViewers: SharedViewerInfo[] = [];

  constructor(filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector,
    private manageClientService: ManageClientService,
    private confirmationService: ConfirmationService,
    private messageToastService: MessageToastService) {
    super(filterService, usersettingsService, translateService, gps, injector);
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'email', 'CLIENT_EMAIL', true, false);
    this.addColumn(DataType.String, 'viewerType', 'SHARE_ACCESS_KIND', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.prepareTableAndTranslate();
    this.loadData();
  }

  /** Opens the manage-client help page in the gt-user-manual. */
  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_MANAGE_CLIENT);
  }

  revoke(viewer: SharedViewerInfo): void {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_REVOKE_READ_ACCESS', () => {
        this.manageClientService.revokeShare(viewer.idUser).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'REVOKE_READ_ACCESS_DONE');
          this.loadData();
        });
      });
  }

  private loadData(): void {
    this.manageClientService.getSharedViewers().subscribe(list => {
      this.sharedViewers = list;
      this.createTranslatedValueStore(this.sharedViewers);
    });
  }
}
