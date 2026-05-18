# Risk-Free Rate Mapping Feature — State & TODO

Snapshot of what's been built for the risk-free-rate currency-to-security mapping admin UI, and what still needs to be done. Intended to be re-read after a context reset so work can continue from a known baseline.

## Big picture

`risk_free_rate_mapping` links an ISO currency to a synthetic `Security` (asset class `NON_INVESTABLE_INDICES`, seeded "Risk-free rate" subcategory) whose historical close serves as that currency's risk-free interest rate. `RiskFreeRateService` consumes this mapping for Sharpe-ratio computation. An admin UI under **Base Data → Risk-free rate mapping** lets authenticated users edit the mapping.

Role matrix:

| Role | Read | Create | Update / Delete | Daily CUD limit |
|---|---|---|---|---|
| ROLE_USER | any | yes | own rows only | none |
| ROLE_LIMITEDIT | any | yes | own rows only | **2 / day** |
| ROLE_ALLEDIT | any | yes | any row | none |
| ROLE_ADMIN | any | yes | any row | none |

Frontend does **not** use `AuditHelper` — a bare `createdBy === currentUserId || hasRole(ADMIN|ALLEDIT)` check enables the per-row edit/delete buttons. Backend uses the existing `UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete` which encodes the same rule.

## Status: DONE

### Backend

| Path | Status | Notes |
|---|---|---|
| `backend/grafioschtrader-server/src/main/resources/db/migration/V0_35_5__riskfree_rate_mapping.sql` | ✅ | `DROP TABLE IF EXISTS` + `CREATE TABLE` with Integer auto-PK (`id_risk_free_rate_mapping`), `UNIQUE` on `currency`, FK cascade on `id_securitycurrency`, plus 5 Auditable columns. Stored procedure seeds the multilinguestring, assetclass ("Risk-free rate"), stockexchange ("Risk-Free Rate Sources"), 5 synthetic securities (USD/EUR/GBP/CHF/JPY → FRED series), 5 mapping rows (`created_by=0`), 5 `task_data_change` rows (`earliest_start_time = UTC_TIMESTAMP() + 1h`). UTC_TIMESTAMP throughout (not NOW). |
| `backend/grafioschtrader-common/src/main/java/grafioschtrader/entities/RiskFreeRateMapping.java` | ✅ | `extends Auditable`, Integer surrogate PK, `currency` (CHAR 3) + `idSecuritycurrency` (FK). |
| `backend/grafioschtrader-common/src/main/java/grafioschtrader/dto/RiskFreeInstrumentOption.java` | ✅ | Projection: `idSecuritycurrency`, `name`, `urlHistoryExtend`. **Missing `currency` — TODO below.** |
| `backend/grafioschtrader-server/src/main/java/grafioschtrader/repository/RiskFreeRateMappingJpaRepository.java` | ✅ | `extends JpaRepository<…,Integer>, UpdateCreateJpaRepository<…>`. `findByCurrency(String)`, `findAllRiskFreeInstruments()` (returns ALL risk-free Securities — server no longer filters out the mapped ones; client filters per row). |
| `backend/grafioschtrader-server/src/main/java/grafioschtrader/repository/RiskFreeRateMappingJpaRepositoryImpl.java` | ✅ | Required by Spring Data because `UpdateCreateJpaRepository` extends `BaseRepositoryCustom`. Extends `BaseRepositoryImpl`, implements `BaseRepositoryCustom`, delegates `saveOnlyAttributes` to `RepositoryHelper.saveOnlyAttributes`. |
| `backend/grafioschtrader-common/src/main/java/grafioschtrader/GlobalParamKeyDefault.java` | ✅ | `GLOB_KEY_LIMIT_DAY_RISKFREERATEMAPPING` constant + `defaultLimitMap.put(..., new MaxDefaultDBValue(2))`. |
| `backend/grafioschtrader-server/src/main/java/grafioschtrader/rest/RequestGTMappings.java` | ✅ | `RISKFREERATEMAPPING` / `RISKFREERATEMAPPING_MAP`. |
| `backend/grafioschtrader-server/src/main/java/grafioschtrader/rest/RiskFreeRateMappingResource.java` | ✅ | `extends UpdateCreateDeleteAuditResource<RiskFreeRateMapping>`. `GET /api/riskfreeratemapping`, `GET /api/riskfreeratemapping/instruments`. Inherits POST/PUT/DELETE. Overrides `getPrefixEntityLimit()` → `GT_LIMIT_DAY`. |
| `backend/grafioschtrader-server/src/main/java/grafioschtrader/service/RiskFreeRateService.java` | ✅ (untouched in this round) | Uses `findByCurrency` — works with both old and new entity shape. |

### Frontend

| Path | Status | Notes |
|---|---|---|
| `frontend/src/app/entities/risk.free.rate.mapping.ts` | ✅ | TS entity `extends Auditable` + `RiskFreeInstrumentOption` interface. **Missing `currency` on `RiskFreeInstrumentOption` — TODO below.** |
| `frontend/src/app/shared/app.settings.ts` | ✅ | `RISK_FREE_RATE_MAPPING_KEY = 'riskfreeratemapping'`. |
| `frontend/src/app/shared/maintree/types/tree.node.type.ts` | ✅ | `RiskFreeRateMapping` enum value. |
| `frontend/src/app/shared/contributor/basedata-main-tree.contributor.ts` | ✅ | Child node appended under Base Data; label `RISK_FREE_RATE_MAPPING`. |
| `frontend/src/app/app.routes.ts` | ✅ | Route registered (`AppSettings.RISK_FREE_RATE_MAPPING_KEY` → `RiskFreeRateMappingTableComponent`). |
| `frontend/src/app/app.module.ts` | ✅ | `RiskFreeRateMappingService` imported + listed in `providers`. |
| `frontend/src/app/shared/riskfreeratemapping/service/risk.free.rate.mapping.service.ts` | ✅ | `getAll()`, `getAllInstruments()`, `update(entity)` (PUT/POST via `updateEntity`), `deleteEntity(id)`. |
| `frontend/src/app/shared/riskfreeratemapping/component/risk.free.rate.mapping.table.component.ts` | ⚠️ partially | See **Current component shape** below. Has diagnostic logs that need removal. Still has dead context-menu code. |
| `frontend/src/assets/i18n/en.json` + `de.json` | ✅ | `RISK_FREE_RATE_MAPPING`, `RISK_FREE_RATE_MAPPING_INSTRUMENT`, `FRED_SERIES_ID`, `CREATE_NEW_RECORD`. |

### Current component shape (`risk.free.rate.mapping.table.component.ts`)

- Imports `EditableTableComponent`, `ButtonModule`, `TooltipModule`, etc.
- Template has a visible `<p-button icon="pi pi-plus" (click)="entityTable.addNewRow()">` toolbar + `<editable-table #entityTable …>`.
- Columns:
  - 1 `currency` — `DataType.String`, `optionsProviderFn` excludes currencies used in other rows, `canEditFn = !row.idRiskFreeRateMapping`.
  - 2 `idSecuritycurrency` — `DataType.String`, `optionsProviderFn` excludes ids used in other rows. `fieldValueFN: getInstrumentNameForRow` so display shows the security name (not the raw id).
  - 3 `fredSeriesId` — `DataType.String`, non-editable, `fieldValueFN: getFredSeriesForRow` derives from `instrumentsById`.
- `canEditRowFn` / `canDeleteRowFn` both bound to `canEditOrDeleteRow` (owner-or-admin-or-alledit).
- `(rowEditSave)` saves via service; `(rowDelete)` deletes via the trash button after confirmation.
- Still has (to be removed in next round): `contextMenuItems`, `resetMenu`, `onComponentClick`, `(componentClick)` binding, `[contextMenuItems]` binding, `[showContextMenu]` binding, diagnostic `console.log('[RFR] …')` in `createNewEntity` and `onRowAdded`.

## TODO (open from the last turn)

### 1. Remove context-menu code entirely

The component no longer needs a context menu (create is via the toolbar `+`, delete via the per-row trash). Remove:
- Field `contextMenuItems: MenuItem[] = []`
- Method `resetMenu()` and the call from `ngOnInit`
- Method `onComponentClick()`
- Template bindings `[contextMenuItems]="contextMenuItems"`, `[showContextMenu]="isActivated()"`, `(componentClick)="onComponentClick()"`
- Field `selectedEntity` if no longer referenced (it is — the trash-button path uses it indirectly only via `(rowDelete)$event.row`; can drop the `[(selection)]` binding if not needed)
- The unused diagnostic `console.log('[RFR] …')` lines in `createNewEntity` and `onRowAdded`.

The methods `isActivated()`, `hideContextMenu()`, `callMeDeactivate()`, `getHelpContextId()`, `IGlobalMenuAttach` interface and the `activePanelService` injection can be evaluated for removal at the same time — if the screen doesn't participate in the main-menubar's edit-menu integration, they're all dead code.

### 2. Currency-instrument coupling (new requirement)

A risk-free instrument has its own currency (the synthetic `Security.currency`). When the user picks a currency in column 1, column 2 must only offer instruments whose currency matches. If the user later changes the currency, column 2 must clear and re-evaluate.

#### 2a. Backend: expose `currency` on the instrument option

`backend/grafioschtrader-common/src/main/java/grafioschtrader/dto/RiskFreeInstrumentOption.java` — add:
```java
@Schema(description = "ISO currency code of the underlying Security.")
String getCurrency();
```

`backend/grafioschtrader-server/src/main/java/grafioschtrader/repository/RiskFreeRateMappingJpaRepository.java` — extend the `findAllRiskFreeInstruments` projection SQL to include `s.currency AS currency`.

#### 2b. Backend: server-side validation (must mirror frontend filter exactly)

`RiskFreeRateMappingResource.java` — add a single-column lookup in the repository:
```java
@Query(nativeQuery = true, value = "SELECT s.currency FROM security s WHERE s.id_securitycurrency = ?1")
String findCurrencyByIdSecuritycurrency(Integer idSecuritycurrency);
```
Override `create(@Valid @RequestBody RiskFreeRateMapping entity)` and `update(@Valid @RequestBody RiskFreeRateMapping entity)` to call `validateCurrencyMatch(entity)` first:
```java
private void validateCurrencyMatch(RiskFreeRateMapping entity) {
  String secCurrency = riskFreeRateMappingJpaRepository
      .findCurrencyByIdSecuritycurrency(entity.getIdSecuritycurrency());
  if (secCurrency == null || !secCurrency.equals(entity.getCurrency())) {
    throw new DataViolationException("currency", "gt.riskfree.currency.mismatch",
        new Object[] { entity.getCurrency(), secCurrency });
  }
}
```
Per `backend/CLAUDE.md`: the `DataViolationException` field arg is the dot-separated property-label NLS key (`currency`), and the `messageKey` is the dot-separated message key (`gt.riskfree.currency.mismatch`). Both must be translated.

NLS additions in `backend/grafioschtrader-common/src/main/resources/message/messages.properties` and `messages_de.properties` (UTF-8):
- `gt.riskfree.currency.mismatch=The selected risk-free instrument is denominated in {1}, but the mapping is for currency {0}. Pick an instrument whose currency matches.`
- DE equivalent (umlauts!).
- Verify `currency=Currency` / `currency=Währung` already exists; add if missing.

#### 2c. Frontend: TS interface

`frontend/src/app/entities/risk.free.rate.mapping.ts` — add `currency: string` to the `RiskFreeInstrumentOption` interface.

#### 2d. Frontend: dependency wiring + per-currency filter

`frontend/src/app/shared/riskfreeratemapping/component/risk.free.rate.mapping.table.component.ts`:
- After `addEditColumn(…, 'idSecuritycurrency', …)`, set `instrumentCol.cec.dependsOnField = 'currency'`. `EditableTableComponent.updateDependentFields` (in `editable-table.component.ts`) will then automatically (a) set `row.idSecuritycurrency = null` and (b) evict the cached options for that row when the currency cell changes, so the next render re-resolves the instrument dropdown.
- Update `getInstrumentOptions(row)`:
  ```ts
  private getInstrumentOptions(row: RiskFreeRateMapping): ValueKeyHtmlSelectOptions[] {
    if (!row.currency) {
      return [];
    }
    const usedIds = new Set<number>(
      this.entityList.filter(m => m !== row && m.idSecuritycurrency != null)
        .map(m => m.idSecuritycurrency));
    return this.allInstrumentOptions
      .filter(opt => opt.currency === row.currency && !usedIds.has(opt.idSecuritycurrency))
      .map(opt => new ValueKeyHtmlSelectOptions(opt.idSecuritycurrency, opt.name));
  }
  ```

### 3. Verification

1. `mvn -f backend/pom.xml -pl grafioschtrader-server -am compile` — expect BUILD SUCCESS.
2. `cd frontend && npm run build` — expect bundle generation complete, no TS errors.
3. Optional SQL dry-run against `grafioschtrader_t` after re-applying the migration. (No schema change in this round — the projection is read-only.)
4. Manual smoke: open Base Data → Risk-free rate mapping. Click `+`. Pick currency `USD` → instrument dropdown should list only USD-denominated risk-free Securities. Change currency to `EUR` → instrument cell clears, dropdown lists only EUR ones. Try to POST a bad pair via `curl` (USD + CHF instrument id) → expect 4xx with translated `gt.riskfree.currency.mismatch` message.

## Re-apply note (still applies)

Updating `V0_35_5` content invalidates the Flyway checksum on `grafioschtrader`. To re-apply:
```sql
DELETE FROM grafioschtrader.flyway_schema_history WHERE version='0.35.5';
```
then restart the backend. The migration's `DROP TABLE IF EXISTS risk_free_rate_mapping` discards any user-added mapping rows (the standard 5 are re-seeded by the stored procedure); supporting assetclass / stockexchange / multilinguestring / securitycurrency rows are preserved by the find-or-insert logic.

## Out of scope (named follow-ups)

- A "force re-seed" admin button.
- Letting non-admin users create new risk-free synthetic securities directly from this UI.
- Cross-row validation that the picked Security isn't already linked from a different row (currently enforced only by the UI dropdown filter — server enforces uniqueness via the `risk_free_rate_mapping.currency` UNIQUE constraint, not via `id_securitycurrency`).
