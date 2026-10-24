import { EntityPrepareRegistry } from '../../lib/proposechange/service/entity.prepare.registry';
import { AssetclassService } from '../../assetclass/service/assetclass.service';
import { StockexchangeService } from '../../stockexchange/service/stockexchange.service';
import { ImportTransactionPlatformService } from '../../imptranstemplate/service/import.transaction.platform.service';
import { SecurityService } from '../../securitycurrency/service/security.service';
import { CurrencypairService } from '../../securitycurrency/service/currencypair.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { AssetclassPrepareEdit } from './assetclass.prepare.edit';
import { StockexchangePrepareEdit } from './stockexchange.prepare.edit';
import { ImportTransactionPlatformPrepareEdit } from './import.transaction.platform.prepare.edit';
import { SecurityPrepareEdit } from './security.prepare.edit';
import { HistoryquotePrepareEdit } from './historyquote.prepare.edit';
import { HistoryquoteLegacyPrepareEdit } from './historyquote.legacy.prepare.edit';
import { GeneralEntityPrepareEdit } from '../../lib/proposechange/component/general.entity.prepare.edit';
import { TradingCalendarRuleSet } from '../../entities/trading.calendar.rule.set';
import { Currencypair } from '../../entities/currencypair';
import { Security } from '../../entities/security';
import { TradingPlatformPlan } from '../../entities/tradingplatformplan';
import { AppSettings } from '../app.settings';

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
  gps: GlobalparameterService
): void {
  // Register Assetclass
  registry.registerEntityHandler(AppSettings.ASSETCLASS, new AssetclassPrepareEdit(assetclassService), () =>
    import('../../assetclass/component/assetclass-edit.component').then((m) => m.AssetclassEditComponent)
  );

  // Register Stockexchange
  registry.registerEntityHandler(
    AppSettings.STOCKEXCHANGE,
    new StockexchangePrepareEdit(stockexchangeService, gps),
    () => import('../../stockexchange/component/stockexchange-edit.component').then((m) => m.StockexchangeEditComponent)
  );

  // Register ImportTransactionPlatform
  registry.registerEntityHandler(
    AppSettings.IMPORT_TRANSACTION_PLATFORM,
    new ImportTransactionPlatformPrepareEdit(importTransactionPlatformService),
    () =>
      import('../../imptranstemplate/component/import-transaction-edit-platform.component').then(
        (m) => m.ImportTransactionEditPlatformComponent
      )
  );

  // Register ImportTransactionTemplate (uses same prepare handler as platform)
  registry.registerEntityHandler(
    AppSettings.IMPORT_TRANSACTION_TEMPLATE,
    new ImportTransactionPlatformPrepareEdit(importTransactionPlatformService),
    () =>
      import('../../imptranstemplate/component/import-transaction-edit-template.component').then(
        (m) => m.ImportTransactionEditTemplateComponent
      )
  );

  // Register Currencypair
  registry.registerEntityHandler(AppSettings.CURRENCYPAIR, new GeneralEntityPrepareEdit(Currencypair), () =>
    import('../../shared/securitycurrency/currencypair-edit.component').then((m) => m.CurrencypairEditComponent)
  );

  // Register Security
  const securityDerivedMapping = 'SecurityDerived';
  registry.registerEntityHandler(AppSettings.SECURITY, new SecurityPrepareEdit(securityDerivedMapping), () =>
    import('../../shared/securitycurrency/security-edit.component').then((m) => m.SecurityEditComponent)
  );

  // Register SecurityDerived (for derived securities like CFDs, futures)
  registry.registerEntityHandler(securityDerivedMapping, new GeneralEntityPrepareEdit(Security), () =>
    import('../../securitycurrency/component/security-derived-edit.component').then(
      (m) => m.SecurityDerivedEditComponent
    )
  );

  // Register TradingPlatformPlan
  registry.registerEntityHandler(
    AppSettings.TRADING_PLATFORM_PLAN,
    new GeneralEntityPrepareEdit(TradingPlatformPlan),
    () =>
      import('../../tradingplatform/component/trading-platform-plan-edit.component').then(
        (m) => m.TradingPlatformPlanEditComponent
      )
  );

  // Register TradingCalendarRuleSet
  registry.registerEntityHandler(
    AppSettings.TRADING_CALENDAR_RULE_SET,
    new GeneralEntityPrepareEdit(TradingCalendarRuleSet),
    () =>
      import('../../stockexchange/component/trading-calendar-rule-set-edit.component').then(
        (m) => m.TradingCalendarRuleSetEditComponent
      )
  );

  // Register Historyquote
  registry.registerEntityHandler(
    AppSettings.HISTORYQUOTE,
    new HistoryquotePrepareEdit(securityService, currencypairService),
    () => import('../../historyquote/component/historyquote-edit.component').then((m) => m.HistoryquoteEditComponent)
  );

  // Register HistoryquoteLegacy (archived shadow rows)
  registry.registerEntityHandler(
    AppSettings.HISTORYQUOTE_LEGACY,
    new HistoryquoteLegacyPrepareEdit(securityService, currencypairService),
    () =>
      import('../../historyquote/component/historyquote-legacy-edit.component').then(
        (m) => m.HistoryquoteLegacyEditComponent
      )
  );
}
