import {EntityPrepareRegistry} from '../../lib/proposechange/service/entity.prepare.registry';
import {AssetclassService} from '../../assetclass/service/assetclass.service';
import {StockexchangeService} from '../../stockexchange/service/stockexchange.service';
import {ImportTransactionPlatformService} from '../../imptranstemplate/service/import.transaction.platform.service';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {AssetclassPrepareEdit} from './assetclass.prepare.edit';
import {StockexchangePrepareEdit} from './stockexchange.prepare.edit';
import {ImportTransactionPlatformPrepareEdit} from './import.transaction.platform.prepare.edit';
import {SecurityPrepareEdit} from './security.prepare.edit';
import {HistoryquotePrepareEdit} from './historyquote.prepare.edit';
import {GeneralEntityPrepareEdit} from '../../lib/proposechange/component/general.entity.prepare.edit';
import {Currencypair} from '../../entities/currencypair';
import {Security} from '../../entities/security';
import {TradingPlatformPlan} from '../../entities/tradingplatformplan';
import {AppSettings} from '../app.settings';
import {AssetclassEditComponent} from '../../assetclass/component/assetclass-edit.component';
import {StockexchangeEditComponent} from '../../stockexchange/component/stockexchange-edit.component';
import {ImportTransactionEditPlatformComponent} from '../../imptranstemplate/component/import-transaction-edit-platform.component';
import {ImportTransactionEditTemplateComponent} from '../../imptranstemplate/component/import-transaction-edit-template.component';
import {CurrencypairEditComponent} from '../../securitycurrency/component/currencypair-edit.component';
import {SecurityEditComponent} from '../../securitycurrency/component/security-edit.component';
import {SecurityDerivedEditComponent} from '../../securitycurrency/component/security-derived-edit.component';
import {TradingPlatformPlanEditComponent} from '../../tradingplatform/component/trading-platform-plan-edit.component';
import {HistoryquoteEditComponent} from '../../historyquote/component/historyquote-edit.component';

/**
 * Sets up entity handlers for the propose change workflow.
 * This function registers all Grafioschtrader-specific entity types with their corresponding
 * preparation handlers and edit components in the EntityPrepareRegistry.
 *
 * This setup maintains the separation between the generic lib code (which doesn't know about
 * specific entities) and the application-specific entity implementations.
 *
 * @param registry - The entity prepare registry to configure
 * @param assetclassService - Service for asset class operations
 * @param stockexchangeService - Service for stock exchange operations
 * @param importTransactionPlatformService - Service for import transaction platform operations
 * @param securityService - Service for security operations
 * @param currencypairService - Service for currency pair operations
 * @param gps - Global parameter service
 */
export function setupProposeChangeEntityHandlers(
  registry: EntityPrepareRegistry,
  assetclassService: AssetclassService,
  stockexchangeService: StockexchangeService,
  importTransactionPlatformService: ImportTransactionPlatformService,
  securityService: SecurityService,
  currencypairService: CurrencypairService,
  gps: GlobalparameterService): void {

  // Register Assetclass
  registry.registerEntityHandler(
    AppSettings.ASSETCLASS,
    new AssetclassPrepareEdit(assetclassService),
    AssetclassEditComponent
  );

  // Register Stockexchange
  registry.registerEntityHandler(
    AppSettings.STOCKEXCHANGE,
    new StockexchangePrepareEdit(stockexchangeService, gps),
    StockexchangeEditComponent
  );

  // Register ImportTransactionPlatform
  registry.registerEntityHandler(
    AppSettings.IMPORT_TRANSACTION_PLATFORM,
    new ImportTransactionPlatformPrepareEdit(importTransactionPlatformService),
    ImportTransactionEditPlatformComponent
  );

  // Register ImportTransactionTemplate (uses same prepare handler as platform)
  registry.registerEntityHandler(
    AppSettings.IMPORT_TRANSACTION_TEMPLATE,
    new ImportTransactionPlatformPrepareEdit(importTransactionPlatformService),
    ImportTransactionEditTemplateComponent
  );

  // Register Currencypair
  registry.registerEntityHandler(
    AppSettings.CURRENCYPAIR,
    new GeneralEntityPrepareEdit(Currencypair),
    CurrencypairEditComponent
  );

  // Register Security
  const securityDerivedMapping = 'SecurityDerived';
  registry.registerEntityHandler(
    AppSettings.SECURITY,
    new SecurityPrepareEdit(securityDerivedMapping),
    SecurityEditComponent
  );

  // Register SecurityDerived (for derived securities like CFDs, futures)
  registry.registerEntityHandler(
    securityDerivedMapping,
    new GeneralEntityPrepareEdit(Security),
    SecurityDerivedEditComponent
  );

  // Register TradingPlatformPlan
  registry.registerEntityHandler(
    AppSettings.TRADING_PLATFORM_PLAN,
    new GeneralEntityPrepareEdit(TradingPlatformPlan),
    TradingPlatformPlanEditComponent
  );

  // Register Historyquote
  registry.registerEntityHandler(
    AppSettings.HISTORYQUOTE,
    new HistoryquotePrepareEdit(securityService, currencypairService),
    HistoryquoteEditComponent
  );
}
