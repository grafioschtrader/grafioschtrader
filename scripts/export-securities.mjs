#!/usr/bin/env node
/**
 * Exports the securities recreated by the backend and Playwright tests as a JSON fixture.
 *
 * The output contains create-time business values and natural references only. Database IDs,
 * audit data, quotes, retry counters, timestamps and GTNet runtime settings are deliberately
 * omitted. Property names follow the Security REST representation where possible; the three
 * natural-reference properties identify the stock exchange and asset class in another database.
 *
 * Environment override:
 *   GT_MYSQL_BIN   Path to the mariadb/mysql CLI (default: PATH, then XAMPP on Windows).
 */

import {spawnSync} from 'node:child_process';
import {existsSync, mkdirSync, writeFileSync} from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const IS_WIN = process.platform === 'win32';

const DISTRIBUTION_FREQUENCY = {
  0: 'DF_NONE', 1: 'DF_YEAR', 2: 'DF_SEMI_ANNUAL', 4: 'DF_QUARTERLY', 12: 'DF_MONTHLY', 99: 'DF_AD_HOC'
};
const ASSETCLASS_TYPE = {
  0: 'EQUITIES', 1: 'FIXED_INCOME', 2: 'MONEY_MARKET', 3: 'COMMODITIES', 4: 'REAL_ESTATE',
  5: 'MULTI_ASSET', 6: 'CONVERTIBLE_BOND', 7: 'CREDIT_DERIVATIVE', 8: 'CURRENCY_PAIR',
  11: 'CURRENCY_CASH', 12: 'CURRENCY_FOREIGN'
};
const SPECIAL_INVESTMENT_INSTRUMENT = {
  0: 'DIRECT_INVESTMENT', 1: 'ETF', 2: 'MUTUAL_FUND', 3: 'PENSION_FUNDS', 4: 'CFD', 5: 'FOREX',
  6: 'ISSUER_RISK_PRODUCT', 10: 'NON_INVESTABLE_INDICES'
};

function fail(message) {
  console.error(`export-securities: ${message}`);
  process.exit(1);
}

function parseArgs() {
  const args = {};
  for (const argument of process.argv.slice(2)) {
    const match = /^--([^=]+)(?:=(.*))?$/.exec(argument);
    if (!match) {
      fail(`Unrecognized argument: ${argument} (expected --key=value)`);
    }
    args[match[1]] = match[2] ?? '';
  }
  if ('help' in args) {
    console.log('Usage: node scripts/export-securities.mjs --user=<user> --password=<password> '
      + '--database=<database> --out=<securities.json> [--mysql-bin=<path>]');
    process.exit(0);
  }
  for (const required of ['user', 'password', 'database', 'out']) {
    if (!args[required]) {
      fail(`Missing required argument --${required}=...`);
    }
  }
  return args;
}

function findMysqlClient(explicit) {
  const usable = binary => {
    try {
      return spawnSync(binary, ['--version'], {stdio: 'ignore'}).status === 0;
    } catch {
      return false;
    }
  };
  const tried = [];
  for (const candidate of [explicit, process.env.GT_MYSQL_BIN].filter(Boolean)) {
    if (usable(candidate)) {
      return candidate;
    }
    tried.push(candidate);
  }
  for (const candidate of ['mariadb', 'mysql']) {
    if (usable(candidate)) {
      return candidate;
    }
    tried.push(`${candidate} (PATH)`);
  }
  const xampp = 'C:\\xampp\\mysql\\bin\\mysql.exe';
  if (IS_WIN && existsSync(xampp)) {
    return xampp;
  }
  tried.push(xampp);
  fail(`No MariaDB/MySQL client found. Tried: ${tried.join(', ')}. Set GT_MYSQL_BIN or --mysql-bin.`);
}

function queryRows(context) {
  const sql = `SELECT JSON_OBJECT(
      'name', s.name, 'isin', s.isin, 'tickerSymbol', s.ticker_symbol, 'currency', s.currency,
      'activeFromDate', DATE_FORMAT(s.active_from_date, '%Y-%m-%d'),
      'activeToDate', DATE_FORMAT(s.active_to_date, '%Y-%m-%d'),
      'distributionFrequencyValue', s.dist_frequency, 'denomination', s.denomination,
      'leverageFactor', s.leverage_factor, 'stockexchangeName', se.name,
      'categoryTypeValue', a.category_type, 'subCategoryDE', m.text,
      'specialInvestmentInstrumentValue', a.spec_invest_instrument,
      'stockexchangeLink', sc.stockexchange_link, 'productLink', s.product_link,
      'formulaPrices', s.formula_prices,
      'idConnectorHistory', sc.id_connector_history, 'urlHistoryExtend', sc.url_history_extend,
      'idConnectorIntra', sc.id_connector_intra, 'urlIntraExtend', sc.url_intra_extend,
      'idConnectorDividend', s.id_connector_dividend, 'urlDividendExtend', s.url_dividend_extend,
      'dividendCurrency', s.dividend_currency, 'idConnectorSplit', s.id_connector_split,
      'urlSplitExtend', s.url_split_extend, 'note', sc.note,
      'e2e', CASE WHEN se.country_code = 'ES' THEN 'e' ELSE 'i' END)
    FROM security s
    JOIN securitycurrency sc ON s.id_securitycurrency = sc.id_securitycurrency
    JOIN stockexchange se ON s.id_stockexchange = se.id_stockexchange
    JOIN assetclass a ON s.id_asset_class = a.id_asset_class
    JOIN multilinguestrings m ON a.sub_category_nls = m.id_string AND m.language = 'de'
    WHERE s.active_to_date > CURDATE()
      AND (sc.id_connector_history IS NULL OR sc.id_connector_history NOT IN
        (SELECT CONCAT('gt.datafeed.', id_provider) FROM connector_apikey))
      AND (sc.id_connector_intra IS NULL OR sc.id_connector_intra NOT IN
        (SELECT CONCAT('gt.datafeed.', id_provider) FROM connector_apikey))
      AND (sc.id_connector_history IS NOT NULL OR sc.id_connector_intra IS NOT NULL)
      AND se.country_code IN ('ES', 'AT', 'AU')
    ORDER BY CASE se.country_code WHEN 'ES' THEN 0 WHEN 'AT' THEN 1 ELSE 2 END, s.name`;
  const result = spawnSync(context.client,
    [`--user=${context.user}`, `--database=${context.database}`, '--default-character-set=utf8mb4', '--batch', '--raw',
      '--skip-column-names', `--execute=${sql}`],
    {env: {...process.env, MYSQL_PWD: context.password, MARIADB_PWD: context.password}, encoding: 'utf8'});
  if (result.error) {
    fail(`mysql invocation failed: ${result.error.message}`);
  }
  if (result.status !== 0) {
    fail(`mysql exited with ${result.status}:\n${result.stderr}`);
  }
  if (result.stdout.includes('\uFFFD')) {
    fail('mysql output contains U+FFFD replacement characters; verify the client connection encoding');
  }
  return result.stdout.split(/\r?\n/).filter(line => line.trim()).map((line, index) => {
    try {
      return JSON.parse(line);
    } catch (error) {
      fail(`Cannot parse row ${index + 1} as JSON (${error.message}): ${line.slice(0, 200)}`);
    }
  });
}

function decodeEnum(values, value, field) {
  if (!(value in values)) {
    fail(`Unknown ${field} value ${value}; update the exporter enum map`);
  }
  return values[value];
}

function dividendConnectorValue(row, value) {
  // Security.clearProperties() removes dividend connector settings when the security never distributes.
  // Production can still contain legacy values that predate that normalization, but such values cannot be recreated.
  return row.distributionFrequencyValue === 0 ? null : value;
}

function main() {
  const args = parseArgs();
  const context = {
    client: findMysqlClient(args['mysql-bin']),
    user: args.user,
    password: args.password,
    database: args.database
  };
  const output = queryRows(context).map(row => ({
    name: row.name,
    isin: row.isin,
    tickerSymbol: row.tickerSymbol,
    currency: row.currency,
    activeFromDate: row.activeFromDate,
    activeToDate: row.activeToDate,
    distributionFrequency: decodeEnum(DISTRIBUTION_FREQUENCY, row.distributionFrequencyValue,
      'DistributionFrequency'),
    denomination: row.denomination,
    leverageFactor: row.leverageFactor,
    stockexchangeName: row.stockexchangeName,
    categoryType: decodeEnum(ASSETCLASS_TYPE, row.categoryTypeValue, 'AssetclassType'),
    subCategoryDE: row.subCategoryDE,
    specialInvestmentInstrument: decodeEnum(SPECIAL_INVESTMENT_INSTRUMENT,
      row.specialInvestmentInstrumentValue, 'SpecialInvestmentInstruments'),
    stockexchangeLink: row.stockexchangeLink,
    productLink: row.productLink,
    formulaPrices: row.formulaPrices,
    idConnectorHistory: row.idConnectorHistory,
    urlHistoryExtend: row.urlHistoryExtend,
    idConnectorIntra: row.idConnectorIntra,
    urlIntraExtend: row.urlIntraExtend,
    idConnectorDividend: dividendConnectorValue(row, row.idConnectorDividend),
    urlDividendExtend: dividendConnectorValue(row, row.urlDividendExtend),
    dividendCurrency: row.dividendCurrency,
    idConnectorSplit: row.idConnectorSplit,
    urlSplitExtend: row.urlSplitExtend,
    note: row.note,
    e2e: row.e2e
  }));

  const outPath = path.resolve(args.out);
  mkdirSync(path.dirname(outPath), {recursive: true});
  writeFileSync(outPath, `${JSON.stringify(output, null, 2)}\n`, 'utf8');
  console.log(`Wrote ${output.length} securities to ${outPath}`);
}

main();
