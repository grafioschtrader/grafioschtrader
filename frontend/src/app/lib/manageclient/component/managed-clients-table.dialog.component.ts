import {Component, Injector, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ConfirmationService, FilterService} from 'primeng/api';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';
import {TooltipModule} from 'primeng/tooltip';

import {TableConfigBase} from '../../datashowbase/table.config.base';
import {DataType} from '../../dynamic-form/models/data.type';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {UserSettingsService} from '../../services/user.settings.service';
import {MessageToastService} from '../../message/message.toast.service';
import {InfoLevelType} from '../../message/info.leve.type';
import {AppHelper} from '../../helper/app.helper';
import {HelpIds} from '../../help/help.ids';
import {ManageClientService} from '../service/manage-client.service';
import {TenantAccessInfo} from '../model/tenant-access-info';

/**
 * Dialog listing the clients an advisor manages, with columns for the client's e-mail and the client (tenant) name.
 * Each row offers two actions: switch into that client, or delete the client entirely (its tenant, all data and the
 * read-only client user). Part of the manage-client library feature (g.use.manageclient).
 */
@Component({
  template: `
    <div class="datatable">
      <div style="text-align: right; margin-bottom: 0.5rem;">
        <p-button [rounded]="true" (click)="helpLink()">
          <i class="pi pi-question" pButtonIcon></i>
        </p-button>
      </div>
      <p-table [columns]="fields" [value]="managedClients" selectionMode="single" stripedRows showGridlines>
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
              <button pButton type="button" class="p-button-text"
                      [pTooltip]="'SWITCH_TO_CLIENT' | translate" (click)="switchTo(el)">
                <i class="fa fa-sign-in" pButtonIcon></i>
              </button>
              @if (el.accessLevel === 'MANAGE') {
                <button pButton type="button" class="p-button-text p-button-danger"
                        [pTooltip]="'DELETE' | translate" [disabled]="el.idTenant === currentIdTenant"
                        (click)="deleteClient(el)">
                  <i class="fa fa-trash" pButtonIcon></i>
                </button>
              }
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule, TableModule, ButtonModule, TooltipModule]
})
export class ManagedClientsTableDialogComponent extends TableConfigBase implements OnInit {

  managedClients: TenantAccessInfo[] = [];
  currentIdTenant: number;

  constructor(filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    injector: Injector,
    private manageClientService: ManageClientService,
    private confirmationService: ConfirmationService,
    private messageToastService: MessageToastService) {
    super(filterService, usersettingsService, translateService, gps, injector);
    this.currentIdTenant = gps.getIdTenant();
  }

  ngOnInit(): void {
    this.addColumn(DataType.String, 'email', 'CLIENT_EMAIL', true, false);
    this.addColumn(DataType.String, 'tenantName', 'CLIENT_NAME', true, false);
    this.prepareTableAndTranslate();
    this.loadData();
  }

  /** Opens the manage-client help page in the gt-user-manual. */
  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_MANAGE_CLIENT);
  }

  switchTo(client: TenantAccessInfo): void {
    this.manageClientService.switchAndReload(client.idTenant, false);
  }

  deleteClient(client: TenantAccessInfo): void {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_CLIENT', () => {
        this.manageClientService.deleteClient(client.idTenant).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'CLIENT_DELETED');
          this.loadData();
        });
      });
  }

  private loadData(): void {
    // Exclude the home tenant and the client the advisor is currently in (no point switching to where you already are).
    this.manageClientService.getAccessibleTenants().subscribe(list => {
      this.managedClients = list.filter(t => !t.home && t.idTenant !== this.currentIdTenant);
    });
  }
}
