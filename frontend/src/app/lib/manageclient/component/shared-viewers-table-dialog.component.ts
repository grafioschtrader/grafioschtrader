import { Component, OnInit, signal, inject } from '@angular/core';

import { TranslateModule } from '@ngx-translate/core';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { TableModule } from '@openng/optimus-ui/table';
import { ButtonModule } from '@openng/optimus-ui/button';
import { TooltipModule } from '@openng/optimus-ui/tooltip';

import { TableConfigBase } from '../../datashowbase/table.config.base';
import { DataType } from '../../dynamic-form/models/data.type';
import { TranslateValue } from '../../datashowbase/column.config';
import { MessageToastService } from '../../message/message.toast.service';
import { InfoLevelType } from '../../message/info.leve.type';
import { AppHelper } from '../../helper/app.helper';
import { HelpIds } from '../../help/help.ids';
import { ManageClientService } from '../service/manage-client.service';
import { SharedViewerInfo } from '../model/shared-viewer-info';

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
      <p-table [columns]="fields" [value]="sharedViewers()" selectionMode="single" stripedRows showGridlines>
        <ng-template #header>
          <tr>
            @for (field of fields; track field) {
              <th [pSortableColumn]="field.field">
                {{ field.headerTranslated }}
                <p-sortIcon [field]="field.field" />
              </th>
            }
            <th style="width: 6rem"></th>
          </tr>
        </ng-template>
        <ng-template #body let-el>
          <tr [pSelectableRow]="el">
            @for (field of fields; track field) {
              <td>{{ getValueByPath(el, field) }}</td>
            }
            <td>
              <button
                pButton
                type="button"
                class="p-button-text p-button-danger"
                [pTooltip]="'REVOKE_READ_ACCESS' | translate"
                (click)="revoke(el)">
                <i class="fa fa-trash" pButtonIcon></i>
              </button>
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
  imports: [TranslateModule, TableModule, ButtonModule, TooltipModule]
})
export class SharedViewersTableDialogComponent extends TableConfigBase implements OnInit {
  private manageClientService: ManageClientService = inject(ManageClientService);
  private confirmationService: ConfirmationService = inject(ConfirmationService);
  private messageToastService: MessageToastService = inject(MessageToastService);

  readonly sharedViewers = signal<SharedViewerInfo[]>([]);

  constructor() {
    super();
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'email', 'CLIENT_EMAIL', true, false);
    this.addColumn(DataType.String, 'viewerType', 'SHARE_ACCESS_KIND', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.prepareTableAndTranslate();
    this.loadData();
  }

  /** Opens the manage-client help page in the gt-user-manual. */
  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_MANAGE_CLIENT);
  }

  revoke(viewer: SharedViewerInfo): void {
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_REVOKE_READ_ACCESS',
      () => {
        this.manageClientService.revokeShare(viewer.idUser).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'REVOKE_READ_ACCESS_DONE');
          this.loadData();
        });
      }
    );
  }

  private loadData(): void {
    this.manageClientService.getSharedViewers().subscribe((list) => {
      this.createTranslatedValueStore(list);
      this.sharedViewers.set(list);
    });
  }
}
