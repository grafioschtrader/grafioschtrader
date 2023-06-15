import {Directive} from '@angular/core';
import {SecurityPositionCurrenyGroupSummary} from '../../entities/view/security.position.curreny.group.summary';
import {SecurityaccountService} from '../service/securityaccount.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {Securityaccount} from '../../entities/securityaccount';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {ChartDataService} from '../../shared/chart/service/chart.data.service';
import {SecurityaccountGroupBase} from './securityaccount.group.base';
import {SecurityaccountCurrencyGroup} from './securityaccount.currency.group';
import {SecurityaccountAssetclassCategortypeGroup} from './securityaccount.assetclass.categortype.group';
import {SecurityPositionDynamicGroupSummary} from '../../entities/view/security.position.dynamic.group.summary';
import {SecurityaccountAssetclassSpecInvestGroup} from './securityaccount.assetclass.spec.invest.group';
import {SecurityaccountAssetclassSubCategoryGroup} from './securityaccount.assetclass.sub.category.group';
import {SecurityaccountBaseTable} from './securityaccount.base.table';
import {TimeSeriesQuotesService} from '../../historyquote/service/time.series.quotes.service';
import {ActivatedRoute, Router} from '@angular/router';
import {SecurityaccountAssetclassGroup} from './securityaccount.assetclass.group';
import {SecurityAccountGroup} from '../model/security.account.group';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {ProductIconService} from '../../securitycurrency/service/product.icon.service';
import {FilterService, SelectItem} from 'primeng/api';


/**
 * @whatItDoes It is the base class for components which shows a summary for each security account. Single row
 * representing a security. It supports the user to change the grouping of this summery.
 * @howToUse
 * {@example
 *
 * @description
 *
 *
 * See
 *
 * @stable
 */
@Directive()
export abstract class SecurityaccountTable extends SecurityaccountBaseTable {
  SpecialInvestmentInstruments: typeof SpecialInvestmentInstruments = SpecialInvestmentInstruments;

  public idTenant: number;
  public idPortfolio: number;
  groupOptions: SelectItem[] = [];
  selectedGroup: string = SecurityAccountGroup[SecurityAccountGroup.GROUP_BY_CURRENCY];
  groupMapping: Map<string, any> = new Map();
  protected securityAccount: Securityaccount;

  protected constructor(timeSeriesQuotesService: TimeSeriesQuotesService,
                        activePanelService: ActivePanelService,
                        messageToastService: MessageToastService,
                        securityaccountService: SecurityaccountService,
                        productIconService: ProductIconService,
                        activatedRoute: ActivatedRoute,
                        router: Router,
                        chartDataService: ChartDataService,
                        filterService: FilterService,
                        translateService: TranslateService,
                        gps: GlobalparameterService,
                        usersettingsService: UserSettingsService) {

    super(timeSeriesQuotesService, activePanelService, messageToastService, securityaccountService, productIconService,
      activatedRoute, router, chartDataService, filterService, translateService, gps, usersettingsService);

    this.groupMapping.set(SecurityAccountGroup[SecurityAccountGroup.GROUP_BY_CURRENCY],
      new SecurityaccountCurrencyGroup(this.translateService, this));
    this.groupMapping.set(SecurityAccountGroup[SecurityAccountGroup.GROUP_BY_ASSETCLASS],
      new SecurityaccountAssetclassCategortypeGroup(this.translateService, this));
    this.groupMapping.set(SecurityAccountGroup[SecurityAccountGroup.GROUP_BY_FINANCIAL_INSTRUMENT],
      new SecurityaccountAssetclassSpecInvestGroup(this.translateService, this));
    this.groupMapping.set(SecurityAccountGroup[SecurityAccountGroup.GROUP_BY_SUB_CATEGORY],
      new SecurityaccountAssetclassSubCategoryGroup(this.gps,
        this.translateService, this));
    this.groupMapping.set(SecurityAccountGroup[SecurityAccountGroup.GROUP_BY_ASSETCLASS_COMBINATION],
      new SecurityaccountAssetclassGroup(this.gps,
        this.translateService, this));

    SelectOptionsHelper.createSelectItemForEnum(translateService, SecurityAccountGroup, this.groupOptions);

    this.securityaccountGroupBase = new SecurityaccountCurrencyGroup(translateService, this);
    this.createColumns();
  }

  handleChangeGroup(event) {
    this.selectedGroup = event.value;
    this.changeGroupToView(this.groupMapping.get(this.selectedGroup));
  }

  protected getTitleChart(): string {
    return this.groupOptions.find(item => item.value === this.selectedGroup).label;
  }

  private changeGroupToView(securityPositionGroupSummary: SecurityaccountGroupBase<SecurityPositionDynamicGroupSummary<any>
    | SecurityPositionCurrenyGroupSummary, any>): void {
    this.securityaccountGroupBase = securityPositionGroupSummary;
    this.showTable = false;
    this.securityPositionAll = null;
    this.securityPositionSummary = null;
    this.createColumns();
    this.readData();
    this.activePanelService.activatePanel(this, {
      showMenu:
        this.getMenuShowOptionsParam(this.selectedSecurityPositionSummary
          ? this.selectedSecurityPositionSummary.security : null)
    });
  }
}

