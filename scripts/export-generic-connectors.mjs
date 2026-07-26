#!/usr/bin/env node
/**
 * Exports the generic connector definitions from the developer database into a nested JSON file
 * under testdata/generated. Invoked by backend/nv.bat alongside the CSV exports; can also be run
 * standalone:
 *
 *   node scripts/export-generic-connectors.mjs --user=grafioschtrader --password=... \
 *        --database=grafioschtrader --out=path/to/generic-connectors.json
 *
 * Output shape: an array of GenericConnectorDef objects whose property names match the
 * Jackson/REST serialization of the backend entities (grafioschtrader.entities.GenericConnectorDef
 * and children), so the file is consumable by the Playwright e2e suite (JSON.parse) and by future
 * JUnit tests (Jackson ObjectMapper with FAIL_ON_UNKNOWN_PROPERTIES=false, which skips the extra
 * per-connector "e2e" routing tag). Database PKs, audit columns, `activated` and
 * `everUsedSuccessfully` are omitted — the tests recreate connectors from scratch.
 *
 * Byte-coded enum columns are decoded to enum NAMES using lookup maps that mirror the Java enums
 * in grafioschtrader.types (RateLimitType, ResponseFormatType, ...). MariaDB 10.4 has no
 * JSON_ARRAYAGG, so each query emits one flat JSON_OBJECT per row (valid JSON per line with --raw)
 * and the nesting is assembled here.
 *
 * Environment overrides
 *   GT_MYSQL_BIN   Path to the mariadb/mysql CLI (default: `mariadb`/`mysql` on PATH, then
 *                  C:\xampp\mysql\bin\mysql.exe on Windows) — same convention as e2e-test.mjs.
 */

import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const IS_WIN = process.platform === 'win32';

// ---------------------------------------------------------------------------
// Enum decodings — MUST mirror backend/grafioschtrader-common/src/main/java/grafioschtrader/types/*.java
// ---------------------------------------------------------------------------
const RATE_LIMIT_TYPE = { 0: 'NONE', 1: 'TOKEN_BUCKET', 2: 'SEMAPHORE' };
const RESPONSE_FORMAT = { 1: 'JSON', 2: 'CSV', 3: 'HTML' };
const NUMBER_FORMAT = { 1: 'US', 2: 'GERMAN', 3: 'SWISS', 4: 'PLAIN' };
const DATE_FORMAT_TYPE = { 1: 'UNIX_SECONDS', 2: 'UNIX_MILLIS', 3: 'PATTERN', 4: 'ISO_DATE', 5: 'ISO_DATE_TIME' };
const JSON_DATA_STRUCTURE = { 1: 'ARRAY_OF_OBJECTS', 2: 'PARALLEL_ARRAYS', 3: 'SINGLE_OBJECT', 4: 'COLUMN_ROW_ARRAYS' };
const HTML_EXTRACT_MODE = { 1: 'REGEX_GROUPS', 2: 'SPLIT_POSITIONS', 3: 'MULTI_SELECTOR' };
const TICKER_BUILD_STRATEGY = { 1: 'URL_EXTEND', 2: 'CURRENCY_PAIR' };
// EndpointOption: enum value = bit position (EnumHelper.encodeEnumSet uses 1 << value)
const ENDPOINT_OPTIONS = [[0, 'SKIP_WEEKEND_DATA'], [1, 'REMOVE_DUPLICATE_DATES'], [2, 'INTRADAY_USE_LAST_BAR']];

function parseArgs() {
  const args = {};
  for (const a of process.argv.slice(2)) {
    const m = /^--([^=]+)=(.*)$/.exec(a);
    if (!m) {
      fail(`Unrecognized argument: ${a} (expected --key=value)`);
    }
    args[m[1]] = m[2];
  }
  for (const req of ['user', 'password', 'database', 'out']) {
    if (!args[req]) {
      fail(`Missing required argument --${req}=...`);
    }
  }
  return args;
}

function fail(msg) {
  console.error(`export-generic-connectors: ${msg}`);
  process.exit(1);
}

/** Same client resolution as scripts/e2e-test.mjs. */
function findMysqlClient(explicit) {
  const usable = bin => {
    try {
      return spawnSync(bin, ['--version'], { stdio: 'ignore' }).status === 0;
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

/**
 * Runs one SELECT that emits a single JSON_OBJECT per row and returns the parsed rows.
 * --raw keeps the JSON untouched (no \n/\t batch escaping); JSON_OBJECT itself escapes all
 * control characters, so every output line is one complete JSON document.
 */
function queryJsonRows(ctx, sql) {
  const result = spawnSync(ctx.client,
    ['--user=' + ctx.user, '--database=' + ctx.database, '--batch', '--raw', '--skip-column-names',
      '--execute=' + sql],
    { env: { ...process.env, MYSQL_PWD: ctx.password, MARIADB_PWD: ctx.password }, encoding: 'utf8' });
  if (result.error) {
    fail(`mysql invocation failed: ${result.error.message}`);
  }
  if (result.status !== 0) {
    fail(`mysql exited with ${result.status}:\n${result.stderr}`);
  }
  return result.stdout.split('\n').filter(line => line.trim() !== '').map((line, i) => {
    try {
      return JSON.parse(line);
    } catch (e) {
      fail(`Cannot parse row ${i + 1} of query as JSON (${e.message}): ${line.slice(0, 200)}`);
    }
  });
}

const bool = v => v === 1 || v === true;
const decodeEnum = (map, v, what) => {
  if (v === null || v === undefined) {
    return null;
  }
  if (!(v in map)) {
    fail(`Unknown ${what} value ${v} — update the lookup maps against grafioschtrader.types.*`);
  }
  return map[v];
};
const decodeEndpointOptions = mask =>
  mask == null ? [] : ENDPOINT_OPTIONS.filter(([bit]) => (mask & (1 << bit)) !== 0).map(([, name]) => name);

function main() {
  const args = parseArgs();
  const ctx = {
    client: findMysqlClient(args['mysql-bin']),
    user: args.user,
    password: args.password,
    database: args.database,
  };

  const defs = queryJsonRows(ctx, `SELECT JSON_OBJECT(
      'id', id_generic_connector, 'shortId', short_id, 'readableName', readable_name,
      'domainUrl', domain_url, 'needsApiKey', needs_api_key, 'rateLimitType', rate_limit_type,
      'rateLimitRequests', rate_limit_requests, 'rateLimitPeriodSec', rate_limit_period_sec,
      'rateLimitConcurrent', rate_limit_concurrent, 'intradayDelaySeconds', intraday_delay_seconds,
      'regexUrlPattern', regex_url_pattern, 'supportsSecurity', supports_security,
      'supportsCurrency', supports_currency, 'needHistoryGapFiller', need_history_gap_filler,
      'gbxDividerEnabled', gbx_divider_enabled, 'supportedCategories', supported_categories,
      'geoRestrictions', geo_restrictions, 'tokenConfigYaml', token_config_yaml,
      'descriptionNlsId', description_nls) FROM generic_connector_def`);

  const nlsRows = queryJsonRows(ctx, `SELECT JSON_OBJECT(
      'id', id_string, 'language', language, 'text', text) FROM multilinguestrings
      WHERE id_string IN (SELECT description_nls FROM generic_connector_def WHERE description_nls IS NOT NULL)`);

  const endpoints = queryJsonRows(ctx, `SELECT JSON_OBJECT(
      'id', id_endpoint, 'defId', id_generic_connector, 'feedSupport', feed_support,
      'instrumentType', instrument_type, 'urlTemplate', url_template, 'httpMethod', http_method,
      'responseFormat', response_format, 'numberFormat', number_format,
      'dateFormatType', date_format_type, 'dateFormatPattern', date_format_pattern,
      'jsonDataStructure', json_data_structure, 'jsonDataPath', json_data_path,
      'jsonColumnNamesPath', json_column_names_path, 'jsonStatusPath', json_status_path,
      'jsonStatusOkValue', json_status_ok_value, 'csvDelimiter', csv_delimiter,
      'csvSkipHeaderLines', csv_skip_header_lines, 'htmlCssSelector', html_css_selector,
      'htmlExtractMode', html_extract_mode, 'htmlTextCleanup', html_text_cleanup,
      'htmlExtractRegex', html_extract_regex, 'htmlSplitDelimiter', html_split_delimiter,
      'tickerBuildStrategy', ticker_build_strategy, 'currencyPairSeparator', currency_pair_separator,
      'currencyPairSuffix', currency_pair_suffix, 'tickerUppercase', ticker_uppercase,
      'maxDataPoints', max_data_points, 'paginationEnabled', pagination_enabled,
      'endpointOptions', endpoint_options) FROM generic_connector_endpoint`);

  const mappings = queryJsonRows(ctx, `SELECT JSON_OBJECT(
      'endpointId', id_endpoint, 'targetField', target_field, 'sourceExpression', source_expression,
      'csvColumnIndex', csv_column_index, 'dividerExpression', divider_expression,
      'required', is_required) FROM generic_connector_field_mapping`);

  const headers = queryJsonRows(ctx, `SELECT JSON_OBJECT(
      'defId', id_generic_connector, 'headerName', header_name, 'headerValue', header_value)
      FROM generic_connector_http_header`);

  const nlsById = new Map();
  for (const row of nlsRows) {
    if (!nlsById.has(row.id)) {
      nlsById.set(row.id, {});
    }
    nlsById.get(row.id)[row.language] = row.text;
  }

  const output = defs.map(d => ({
    e2e: 'e',
    shortId: d.shortId,
    readableName: d.readableName,
    descriptionNLS: d.descriptionNlsId == null ? null : { map: nlsById.get(d.descriptionNlsId) ?? {} },
    domainUrl: d.domainUrl,
    needsApiKey: bool(d.needsApiKey),
    rateLimitType: decodeEnum(RATE_LIMIT_TYPE, d.rateLimitType, 'RateLimitType'),
    rateLimitRequests: d.rateLimitRequests,
    rateLimitPeriodSec: d.rateLimitPeriodSec,
    rateLimitConcurrent: d.rateLimitConcurrent,
    intradayDelaySeconds: d.intradayDelaySeconds,
    regexUrlPattern: d.regexUrlPattern,
    supportsSecurity: bool(d.supportsSecurity),
    supportsCurrency: bool(d.supportsCurrency),
    needHistoryGapFiller: bool(d.needHistoryGapFiller),
    gbxDividerEnabled: bool(d.gbxDividerEnabled),
    supportedCategories: d.supportedCategories,
    geoRestrictions: d.geoRestrictions,
    tokenConfigYaml: d.tokenConfigYaml,
    httpHeaders: headers.filter(h => h.defId === d.id)
      .map(h => ({ headerName: h.headerName, headerValue: h.headerValue }))
      .sort((a, b) => a.headerName.localeCompare(b.headerName)),
    endpoints: endpoints.filter(e => e.defId === d.id).map(e => ({
      feedSupport: e.feedSupport,
      instrumentType: e.instrumentType,
      urlTemplate: e.urlTemplate,
      httpMethod: e.httpMethod,
      responseFormat: decodeEnum(RESPONSE_FORMAT, e.responseFormat, 'ResponseFormatType'),
      numberFormat: decodeEnum(NUMBER_FORMAT, e.numberFormat, 'NumberFormatType'),
      dateFormatType: decodeEnum(DATE_FORMAT_TYPE, e.dateFormatType, 'DateFormatType'),
      dateFormatPattern: e.dateFormatPattern,
      jsonDataStructure: decodeEnum(JSON_DATA_STRUCTURE, e.jsonDataStructure, 'JsonDataStructure'),
      jsonDataPath: e.jsonDataPath,
      jsonColumnNamesPath: e.jsonColumnNamesPath,
      jsonStatusPath: e.jsonStatusPath,
      jsonStatusOkValue: e.jsonStatusOkValue,
      csvDelimiter: e.csvDelimiter,
      csvSkipHeaderLines: e.csvSkipHeaderLines,
      htmlCssSelector: e.htmlCssSelector,
      htmlExtractMode: decodeEnum(HTML_EXTRACT_MODE, e.htmlExtractMode, 'HtmlExtractMode'),
      htmlTextCleanup: e.htmlTextCleanup,
      htmlExtractRegex: e.htmlExtractRegex,
      htmlSplitDelimiter: e.htmlSplitDelimiter,
      tickerBuildStrategy: decodeEnum(TICKER_BUILD_STRATEGY, e.tickerBuildStrategy, 'TickerBuildStrategy'),
      currencyPairSeparator: e.currencyPairSeparator,
      currencyPairSuffix: e.currencyPairSuffix,
      tickerUppercase: bool(e.tickerUppercase),
      maxDataPoints: e.maxDataPoints,
      paginationEnabled: bool(e.paginationEnabled),
      endpointOptions: decodeEndpointOptions(e.endpointOptions),
      fieldMappings: mappings.filter(m => m.endpointId === e.id).map(m => ({
        targetField: m.targetField,
        sourceExpression: m.sourceExpression,
        csvColumnIndex: m.csvColumnIndex,
        dividerExpression: m.dividerExpression,
        required: bool(m.required),
      })).sort((a, b) => a.targetField.localeCompare(b.targetField)),
    })).sort((a, b) => (a.feedSupport + a.instrumentType).localeCompare(b.feedSupport + b.instrumentType)),
  })).sort((a, b) => a.shortId.localeCompare(b.shortId));

  const outPath = path.resolve(args.out);
  mkdirSync(path.dirname(outPath), { recursive: true });
  writeFileSync(outPath, JSON.stringify(output, null, 2) + '\n', 'utf8');
  const epCount = output.reduce((n, c) => n + c.endpoints.length, 0);
  console.log(`Wrote ${output.length} generic connectors (${epCount} endpoints) to ${outPath}`);
}

main();
