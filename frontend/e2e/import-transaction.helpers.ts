import {expect, Page, test} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

import {parseCsvRow} from './helpers';

const IMPORT_TRANSACTION_ROOT = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/import_transaction');

const SECURITY_ACCOUNT_FOLDER_RX = /^\s*(Securities\s+accounts|Depots)\s*$/i;
const DOCUMENT_FILE_RX = /^(\d{4})(\d{2})(\d{2})_([A-Za-z]+)\.pdf$/i;
const IMPORT_HEAD_FIXTURE = 'imp_trans_head.csv';
const TRANSACTION_CSV_RX = /^gt_transactions_.+\.csv$/i;

export type ImportedTransactionType = 'ACCUMULATE' | 'REDUCE' | 'DIVIDEND';

export interface ImportDocument {
  fileName: string;
  filePath: string;
  transactionDate: string;
  transactionType: ImportedTransactionType;
}

export interface ImportTransactionScenario {
  testType: string;
  loginNickname: string;
  securityAccountName: string;
  documents: ImportDocument[];
}

export interface ExpectedImportedTransaction {
  transactionDate: string;
  transactionType: ImportedTransactionType;
  isin: string;
}

export interface CsvImportTransactionScenario {
  testType: string;
  loginNickname: string;
  portfolioName: string;
  securityAccountName: string;
  importHeadName: string;
  note: string | null;
  useGtPlatform: boolean;
  e2e: 'd' | 'i' | 'e';
  csvFileName: string;
  csvFilePath: string;
  expectedTransactions: ExpectedImportedTransaction[];
}

interface ApiSecurityAccount {
  idSecuritycashAccount: number;
  name: string;
}

interface ApiPortfolio {
  name: string;
  currency: string;
  securityaccountList?: ApiSecurityAccount[];
}

interface ResolvedSecurityAccount {
  idSecurityAccount: number;
  portfolioName: string;
  portfolioCurrency: string;
}

interface ApiSecurity {
  idSecuritycurrency: number;
  isin: string;
}

interface ApiTransaction {
  idTransaction: number;
  connectedIdTransaction?: number | null;
  idSecurityaccount?: number | null;
  transactionTime: number | string;
  transactionType: string;
  security?: ApiSecurity | null;
}

interface ApiImportTransactionHead {
  idTransactionHead: number;
  name?: string;
  note?: string | null;
  useGtPlatform?: boolean;
}

interface ApiImportTransactionPos {
  idTransactionPos: number;
  transactionTime: number | string;
  transactionType: string;
  readyForTransaction: boolean;
  idTransaction?: number | null;
  idTransactionMaybe?: number | null;
  transactionError?: string | null;
  security?: ApiSecurity | null;
}

interface ApiCombinedImportTransactionPos {
  importTransactionPos: ApiImportTransactionPos;
}

interface DirectImportResult {
  idTransactionHead?: number | null;
  noOfImportedTransactions: number;
  noOfDifferentSecurities: number;
  failed: boolean;
}

/**
 * Discovers document-import scenarios below testdata/import_transaction/<test-type>. The next two directory levels
 * are the login nickname and the unique securities-account name. Every PDF name supplies the transaction date and
 * type used for startup cleanup and persisted-result verification.
 */
export function loadImportTransactionScenarios(testType: string): ImportTransactionScenario[] {
  const testTypeDirectory = path.join(IMPORT_TRANSACTION_ROOT, testType);
  if (!fs.existsSync(testTypeDirectory)) {
    throw new Error(`Import transaction fixture directory does not exist: ${testTypeDirectory}`);
  }

  const scenarios: ImportTransactionScenario[] = [];
  for (const nicknameEntry of sortedDirectoryEntries(testTypeDirectory)) {
    assertDirectory(nicknameEntry, testTypeDirectory, 'login nickname');
    const nicknameDirectory = path.join(testTypeDirectory, nicknameEntry.name);

    for (const accountEntry of sortedDirectoryEntries(nicknameDirectory)) {
      assertDirectory(accountEntry, nicknameDirectory, 'securities account');
      const accountDirectory = path.join(nicknameDirectory, accountEntry.name);
      // Fixtures for other import channels (for example a CSV replayed through the upload dialog) may live
      // beside the PDFs; this loader only owns the drop-zone scenarios.
      const documentEntries = sortedDirectoryEntries(accountDirectory)
        .filter(entry => entry.name.toLowerCase().endsWith('.pdf'));
      if (documentEntries.length === 0) {
        continue;
      }

      const documents = documentEntries.map(entry => parseImportDocument(accountDirectory, entry));
      scenarios.push({
        testType,
        loginNickname: nicknameEntry.name,
        securityAccountName: accountEntry.name,
        documents
      });
    }
  }

  if (scenarios.length === 0) {
    throw new Error(`No import transaction scenarios found below ${testTypeDirectory}`);
  }
  return scenarios;
}

/** Loads transaction-export CSV scenarios and their natural-key import-head fixtures. */
export function loadCsvImportTransactionScenarios(testType: string): CsvImportTransactionScenario[] {
  const testTypeDirectory = path.join(IMPORT_TRANSACTION_ROOT, testType);
  if (!fs.existsSync(testTypeDirectory)) {
    throw new Error(`Import transaction fixture directory does not exist: ${testTypeDirectory}`);
  }

  const scenarios: CsvImportTransactionScenario[] = [];
  for (const nicknameEntry of sortedDirectoryEntries(testTypeDirectory)) {
    assertDirectory(nicknameEntry, testTypeDirectory, 'login nickname');
    const nicknameDirectory = path.join(testTypeDirectory, nicknameEntry.name);

    for (const accountEntry of sortedDirectoryEntries(nicknameDirectory)) {
      assertDirectory(accountEntry, nicknameDirectory, 'securities account');
      const accountDirectory = path.join(nicknameDirectory, accountEntry.name);
      const headFixturePath = path.join(accountDirectory, IMPORT_HEAD_FIXTURE);
      if (!fs.existsSync(headFixturePath)) {
        continue;
      }

      const transactionCsvEntries = sortedDirectoryEntries(accountDirectory)
        .filter(entry => entry.isFile() && TRANSACTION_CSV_RX.test(entry.name));
      if (transactionCsvEntries.length !== 1) {
        throw new Error(`${accountDirectory} must contain exactly one gt_transactions_*.csv file`);
      }
      const csvFileName = transactionCsvEntries[0].name;
      const csvFilePath = path.join(accountDirectory, csvFileName);

      for (const [index, line] of nonEmptyLines(headFixturePath).entries()) {
        const columns = parseCsvRow(line);
        if (columns.length !== 7) {
          throw new Error(`${headFixturePath}:${index + 1}: expected 7 columns, got ${columns.length}`);
        }
        const e2e = columns[6] as CsvImportTransactionScenario['e2e'];
        if (!['d', 'i', 'e'].includes(e2e)) {
          throw new Error(`${headFixturePath}:${index + 1}: invalid e2e routing tag '${columns[6]}'`);
        }
        if (columns[0] !== nicknameEntry.name || columns[2] !== accountEntry.name) {
          throw new Error(`${headFixturePath}:${index + 1}: nickname/account must match its directory`);
        }
        if (e2e !== 'e') {
          continue;
        }
        scenarios.push({
          testType,
          loginNickname: columns[0],
          portfolioName: columns[1],
          securityAccountName: columns[2],
          importHeadName: columns[3],
          note: columns[4] || null,
          useGtPlatform: parseBoolean(columns[5], headFixturePath, index + 1),
          e2e,
          csvFileName,
          csvFilePath,
          expectedTransactions: parseTransactionExportCsv(csvFilePath)
        });
      }
    }
  }

  if (scenarios.length === 0) {
    throw new Error(`No CSV import transaction scenarios found below ${testTypeDirectory}`);
  }
  return scenarios;
}

/** Selects the uniquely named securities account through the main tree and returns its generated database ID. */
export async function openSecurityAccount(page: Page,
    scenario: {securityAccountName: string; portfolioName?: string}): Promise<number> {
  const account = await resolveSecurityAccount(page, scenario.securityAccountName, scenario.portfolioName);
  const portfolioText = `${account.portfolioName} / ${account.portfolioCurrency}`;
  const portfolioNode = page.locator('.p-tree-node-content').filter({hasText: exactText(portfolioText)}).first();
  await portfolioNode.waitFor({state: 'visible', timeout: 15_000});
  await portfolioNode.dblclick();

  const portfolioSubtree = page.locator('p-treenode')
    .filter({has: page.locator('.p-tree-node-content', {hasText: exactText(portfolioText)})})
    .last();
  const securityAccountFolder = portfolioSubtree.locator('.p-tree-node-content')
    .filter({hasText: SECURITY_ACCOUNT_FOLDER_RX}).first();
  await securityAccountFolder.waitFor({state: 'visible', timeout: 10_000});
  await securityAccountFolder.dblclick();

  const accountNode = portfolioSubtree.locator('.p-tree-node-content')
    .filter({hasText: exactText(scenario.securityAccountName)}).first();
  await accountNode.waitFor({state: 'visible', timeout: 10_000});
  await accountNode.click();
  await page.locator('ngx-file-drop .drop-zone-trans').waitFor({state: 'visible', timeout: 15_000});
  return account.idSecurityAccount;
}

/** Opens the transaction-import tab for the selected securities account. */
export async function openTransactionImport(page: Page): Promise<void> {
  await page.getByRole('tab', {name: /^Import$/i}).first().click();
  await page.locator('securityaccount-import-transaction-table').waitFor({state: 'visible', timeout: 15_000});
}

/**
 * Deletes only this CSV scenario's prior transactions/import head and recreates the head represented by the fixture.
 * Returns the generated head id; no exported database id is reused.
 */
export async function resetCsvImportTransactionScenario(page: Page,
    scenario: CsvImportTransactionScenario): Promise<number> {
  return await test.step('reset CSV transaction import scenario', async () => {
    const headers = await authHeaders(page);
    const account = await resolveSecurityAccount(page, scenario.securityAccountName, scenario.portfolioName);

    const matchingTransactions = (await getTenantTransactions(page, headers))
      .filter(transaction => belongsToCsvScenario(transaction, scenario, account.idSecurityAccount))
      .sort((left, right) => transactionTimestamp(right) - transactionTimestamp(left));
    for (const transaction of matchingTransactions) {
      const response = await page.request.delete(`/api/transaction/${transaction.idTransaction}`, {headers});
      expect(response.ok(), `deleting CSV-imported transaction ${transaction.idTransaction}: ${await response.text()}`)
        .toBeTruthy();
    }

    const headResponse = await page.request.get(
      `/api/importtransactionhead/securityaccount/${account.idSecurityAccount}`, {headers});
    expect(headResponse.ok(), `loading import heads for account ${account.idSecurityAccount}: `
      + await headResponse.text()).toBeTruthy();
    const heads = await headResponse.json() as ApiImportTransactionHead[];
    for (const head of heads.filter(candidate => candidate.name === scenario.importHeadName)) {
      const positions = await getImportPositions(page, head.idTransactionHead);
      if (positions.length > 0) {
        const deletePositionsResponse = await page.request.post('/api/importtransactionpos/deletes', {
          headers: {...headers, 'Content-Type': 'application/json'},
          data: positions.map(position => position.importTransactionPos.idTransactionPos)
        });
        expect(deletePositionsResponse.ok(), `deleting positions of prior CSV import head ${head.idTransactionHead}: `
          + await deletePositionsResponse.text()).toBeTruthy();
      }
      const response = await page.request.delete(`/api/importtransactionhead/${head.idTransactionHead}`, {headers});
      expect(response.ok(), `deleting prior CSV import head ${head.idTransactionHead}: ${await response.text()}`)
        .toBeTruthy();
    }

    const createResponse = await page.request.post('/api/importtransactionhead', {
      headers: {...headers, 'Content-Type': 'application/json'},
      data: {
        name: scenario.importHeadName,
        note: scenario.note,
        useGtPlatform: scenario.useGtPlatform,
        securityaccount: {idSecuritycashAccount: account.idSecurityAccount}
      }
    });
    expect(createResponse.ok(), `creating CSV import head: ${await createResponse.text()}`).toBeTruthy();
    const createdHead = await createResponse.json() as ApiImportTransactionHead;
    expect(createdHead.name).toBe(scenario.importHeadName);
    expect(createdHead.note ?? null).toBe(scenario.note);
    expect(createdHead.useGtPlatform).toBe(scenario.useGtPlatform);
    return createdHead.idTransactionHead;
  });
}

/** Selects the fixture import head and uploads its transaction-export CSV through the visible dialog. */
export async function uploadTransactionCsv(page: Page, scenario: CsvImportTransactionScenario,
    idTransactionHead: number): Promise<void> {
  await test.step(`upload ${scenario.csvFileName}`, async () => {
    const headSelect = page.locator('select#idTransactionHead');
    await headSelect.waitFor({state: 'visible', timeout: 10_000});
    await headSelect.selectOption(String(idTransactionHead));
    await expect(headSelect).toHaveValue(String(idTransactionHead));

    const container = transactionImportContainer(page);
    await container.click();
    await container.click({button: 'right'});
    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({state: 'visible', timeout: 10_000});
    await menu.getByRole('menuitem', {name: /^(Upload CSV file|Hochladen CSV-Datei)(\.\.\.)?$/i}).click();

    const dialog = page.locator('upload-file-dialog .p-dialog');
    await dialog.waitFor({state: 'visible', timeout: 10_000});
    const templateSelect = dialog.locator('select#idTransactionImportTemplate');
    await expect(templateSelect.locator('option')).not.toHaveCount(0, {timeout: 10_000});
    const germanTemplate = templateSelect.locator('option').filter({hasText: /deutsch/i}).first();
    await expect(germanTemplate, 'German Grafioschtrader transaction-export template').toHaveCount(1);
    await templateSelect.selectOption(await germanTemplate.getAttribute('value'));
    await dialog.locator('input#fileToUpload').setInputFiles(scenario.csvFilePath);

    const uploadResponsePromise = page.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname === `/api/importtransactionhead/${idTransactionHead}/uploadtransaction`,
    {timeout: 60_000});
    await dialog.locator('button[type="submit"]').click();
    const uploadResponse = await uploadResponsePromise;
    expect(uploadResponse.ok(), `uploading ${scenario.csvFileName}: ${await uploadResponse.text()}`).toBeTruthy();
    await dialog.waitFor({state: 'hidden', timeout: 15_000});
  });
}

/** Verifies all CSV rows were parsed once and are ready to become transactions. */
export async function expectCsvImportPositions(page: Page, scenario: CsvImportTransactionScenario,
    idTransactionHead: number, created: boolean): Promise<ApiCombinedImportTransactionPos[]> {
  const positions = await getImportPositions(page, idTransactionHead);
  expect(positions, 'CSV import position count').toHaveLength(scenario.expectedTransactions.length);
  expect(positionKeys(positions), 'parsed CSV transaction keys')
    .toEqual(expectedTransactionKeys(scenario.expectedTransactions));
  for (const combined of positions) {
    const position = combined.importTransactionPos;
    expect(position.readyForTransaction, `position ${position.idTransactionPos} ready for transaction`).toBe(true);
    expect(position.transactionError ?? null, `position ${position.idTransactionPos} transaction error`).toBeNull();
    if (created) {
      expect(typeof position.idTransaction, `position ${position.idTransactionPos} transaction id`).toBe('number');
    } else {
      expect(position.idTransaction ?? null, `position ${position.idTransactionPos} transaction id`).toBeNull();
      expect(position.idTransactionMaybe ?? null, `position ${position.idTransactionPos} possible duplicate`).toBeNull();
    }
  }
  return positions;
}

/** Selects every position in the UI and invokes the translated Create transactions context-menu command. */
export async function createAllImportedTransactions(page: Page, expectedCount: number): Promise<void> {
  await test.step('select all positions and create transactions', async () => {
    const table = page.locator('securityaccount-import-transaction-table configurable-table');
    await expect(table.locator('tbody tr')).toHaveCount(expectedCount, {timeout: 15_000});
    await table.locator('p-tableheadercheckbox').click();
    await expect(table.locator('tbody p-tablecheckbox input:checked')).toHaveCount(expectedCount);

    const container = transactionImportContainer(page);
    // The checkbox click reaches the parent's click handler before Optimus UI emits the new selection. Trigger a fresh
    // bubbled click after selection has settled so the parent rebuilds its menu with the current selectedEntities.
    await table.dispatchEvent('click');
    await container.click({button: 'right'});
    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({state: 'visible', timeout: 10_000});
    const createItem = menu.getByRole('menuitem', {name: /^(Create transactions|Erstelle Transaktionen)$/i});
    await expect(createItem).toBeEnabled();

    const createResponsePromise = page.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/importtransactionpos/createtransaction', {timeout: 60_000});
    await createItem.click();
    const createResponse = await createResponsePromise;
    expect(createResponse.ok(), `creating transactions from CSV positions: ${await createResponse.text()}`).toBeTruthy();
  });
}

/** Verifies the exact fixture transactions were persisted for the resolved securities account. */
export async function expectCsvImportedTransactions(page: Page, scenario: CsvImportTransactionScenario,
    idSecurityAccount: number): Promise<void> {
  const transactions = (await getTenantTransactions(page, await authHeaders(page)))
    .filter(transaction => belongsToCsvScenario(transaction, scenario, idSecurityAccount));
  expect(transactions, 'persisted CSV-imported transaction count').toHaveLength(scenario.expectedTransactions.length);
  expect(transactionKeys(transactions), 'persisted CSV transaction keys')
    .toEqual(expectedTransactionKeys(scenario.expectedTransactions));
  expect(transactions.filter(transaction => transaction.transactionType === 'ACCUMULATE'), 'persisted purchases')
    .toHaveLength(2);
  expect(transactions.filter(transaction => transaction.transactionType === 'DIVIDEND'), 'persisted dividends')
    .toHaveLength(16);
}

/** Removes leftovers owned by this scenario so a successful or partially failed run can be repeated. */
export async function cleanupImportTransactionScenario(page: Page, scenario: ImportTransactionScenario,
    idSecurityAccount: number): Promise<void> {
  await test.step('delete import leftovers', async () => {
    const headers = await authHeaders(page);
    await deleteGeneratedImportHeads(page, headers, idSecurityAccount);

    const matchingTransactions = (await getTenantTransactions(page, headers))
      .filter(transaction => belongsToScenario(transaction, scenario, idSecurityAccount))
      .sort((a, b) => Number(b.connectedIdTransaction != null) - Number(a.connectedIdTransaction != null)
        || transactionTimestamp(b) - transactionTimestamp(a));
    for (const transaction of matchingTransactions) {
      const response = await page.request.delete(`/api/transaction/${transaction.idTransaction}`, {headers});
      expect(response.ok(), `deleting imported transaction ${transaction.idTransaction}: ${await response.text()}`)
        .toBeTruthy();
    }

    const remaining = (await getTenantTransactions(page, headers))
      .filter(transaction => belongsToScenario(transaction, scenario, idSecurityAccount));
    expect(remaining, 'matching transactions after startup cleanup').toHaveLength(0);
  });
}

/** Enables Grafioschtrader templates and drops every scenario PDF together onto the visible account drop zone. */
export async function dropImportDocuments(page: Page, scenario: ImportTransactionScenario,
    idSecurityAccount: number): Promise<DirectImportResult> {
  const useGtPlatform = page.locator('input#useGtPlatformDrop');
  await useGtPlatform.waitFor({state: 'visible', timeout: 10_000});
  if (await useGtPlatform.isEnabled()) {
    await useGtPlatform.check();
  }
  await expect(useGtPlatform, 'Use Grafioschtrader import templates must be enabled').toBeChecked();

  const files = scenario.documents.map(document => ({
    name: document.fileName,
    mimeType: 'application/pdf',
    base64: fs.readFileSync(document.filePath).toString('base64')
  }));
  const uploadResponsePromise = page.waitForResponse(response => response.request().method() === 'POST'
    && new URL(response.url()).pathname
      === `/api/importtransactionhead/${idSecurityAccount}/uploadpdftransactions`, {timeout: 60_000});
  const dropZone = page.locator('ngx-file-drop .drop-zone-trans');
  await dropZone.evaluate((element, filePayloads) => {
    // ngx-file-drop consumes FileSystemEntry objects returned by webkitGetAsEntry(). Chromium's DataTransfer does
    // not create such entries for programmatically added files, so dispatch the browser events with the same entry
    // contract supplied by a real operating-system file drag.
    const items = filePayloads.map(payload => {
      const bytes = Uint8Array.from(atob(payload.base64), character => character.charCodeAt(0));
      const file = new File([bytes], payload.name, {type: payload.mimeType});
      const fileEntry = {
        name: file.name,
        isDirectory: false,
        isFile: true,
        file: (callback: (selectedFile: File) => void) => callback(file)
      };
      return {
        kind: 'file',
        type: file.type,
        getAsFile: () => file,
        webkitGetAsEntry: () => fileEntry
      };
    });
    const dataTransfer = {
      items,
      files: items.map(item => item.getAsFile()),
      dropEffect: 'none',
      effectAllowed: 'all'
    };
    for (const eventType of ['dragenter', 'dragover', 'drop']) {
      const event = new Event(eventType, {bubbles: true, cancelable: true});
      Object.defineProperty(event, 'dataTransfer', {value: dataTransfer});
      element.dispatchEvent(event);
    }
  }, files);

  const uploadResponse = await uploadResponsePromise;
  const responseBody = await uploadResponse.text();
  expect(uploadResponse.ok(), `uploading [${scenario.documents.map(document => document.fileName).join(', ')}]: `
    + responseBody).toBeTruthy();
  const result = JSON.parse(responseBody) as DirectImportResult;
  expect(result.failed, 'direct document import reported a failed position').toBe(false);
  expect(result.noOfImportedTransactions, 'number of transactions created from the dropped PDFs')
    .toBe(scenario.documents.length);
  expect(result.noOfDifferentSecurities, 'number of securities represented by the dropped PDFs').toBeGreaterThan(0);
  return result;
}

/** Verifies that every filename-derived transaction key was persisted exactly once. */
export async function expectImportedTransactions(page: Page, scenario: ImportTransactionScenario,
    idSecurityAccount: number): Promise<void> {
  await test.step('verify imported transactions', async () => {
    const transactions = (await getTenantTransactions(page, await authHeaders(page)))
      .filter(transaction => belongsToScenario(transaction, scenario, idSecurityAccount));
    expect(transactions, 'persisted transaction count for dropped PDFs').toHaveLength(scenario.documents.length);

    for (const document of scenario.documents) {
      const matches = transactions.filter(transaction => transaction.transactionType === document.transactionType
        && transactionDate(transaction) === document.transactionDate);
      expect(matches, `${document.fileName} persisted transaction`).toHaveLength(1);
    }
  });
}

function sortedDirectoryEntries(directory: string): fs.Dirent[] {
  return fs.readdirSync(directory, {withFileTypes: true})
    .sort((left, right) => left.name.localeCompare(right.name));
}

function assertDirectory(entry: fs.Dirent, parent: string, level: string): void {
  if (!entry.isDirectory()) {
    throw new Error(`Expected a ${level} directory, found ${path.join(parent, entry.name)}`);
  }
}

function parseImportDocument(accountDirectory: string, entry: fs.Dirent): ImportDocument {
  if (!entry.isFile()) {
    throw new Error(`Expected a PDF file, found directory ${path.join(accountDirectory, entry.name)}`);
  }
  const match = DOCUMENT_FILE_RX.exec(entry.name);
  if (!match) {
    throw new Error(`Import PDF '${entry.name}' must use the filename yyyyMMdd_<type>.pdf`);
  }
  const transactionDate = `${match[1]}-${match[2]}-${match[3]}`;
  assertValidDate(transactionDate, entry.name);
  return {
    fileName: entry.name,
    filePath: path.join(accountDirectory, entry.name),
    transactionDate,
    transactionType: mapTransactionType(match[4], entry.name)
  };
}

function mapTransactionType(typeName: string, fileName: string): ImportedTransactionType {
  switch (typeName.toLowerCase()) {
    case 'buy':
    case 'accumulate':
      return 'ACCUMULATE';
    case 'sell':
    case 'reduce':
      return 'REDUCE';
    case 'dividend':
      return 'DIVIDEND';
    default:
      throw new Error(`Unsupported import transaction type '${typeName}' in ${fileName}`);
  }
}

function assertValidDate(date: string, fileName: string): void {
  const parsed = new Date(`${date}T00:00:00.000Z`);
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) {
    throw new Error(`Invalid transaction date '${date}' in ${fileName}`);
  }
}

async function resolveSecurityAccount(page: Page, securityAccountName: string,
    portfolioName?: string): Promise<ResolvedSecurityAccount> {
  const headers = await authHeaders(page);
  const response = await page.request.get('/api/portfolio/tenant', {headers});
  expect(response.ok(), `loading portfolios: ${await response.text()}`).toBeTruthy();
  const portfolios = await response.json() as ApiPortfolio[];
  const matches = portfolios.filter(portfolio => portfolioName == null || portfolio.name === portfolioName)
    .flatMap(portfolio => (portfolio.securityaccountList ?? [])
    .filter(account => account.name === securityAccountName)
    .map(account => ({
      idSecurityAccount: account.idSecuritycashAccount,
      portfolioName: portfolio.name,
      portfolioCurrency: portfolio.currency
    })));
  expect(matches, `securities account '${securityAccountName}' must be unique for the fixture user`).toHaveLength(1);
  return matches[0];
}

async function getImportPositions(page: Page, idTransactionHead: number): Promise<ApiCombinedImportTransactionPos[]> {
  const response = await page.request.get(`/api/importtransactionpos/importtransactionhead/${idTransactionHead}`,
    {headers: await authHeaders(page)});
  expect(response.ok(), `loading positions for import head ${idTransactionHead}: ${await response.text()}`).toBeTruthy();
  return await response.json() as ApiCombinedImportTransactionPos[];
}

async function deleteGeneratedImportHeads(page: Page, headers: Record<string, string>,
    idSecurityAccount: number): Promise<void> {
  const response = await page.request.get(`/api/importtransactionhead/securityaccount/${idSecurityAccount}`, {headers});
  expect(response.ok(), `loading import heads for account ${idSecurityAccount}: ${await response.text()}`).toBeTruthy();
  const heads = await response.json() as ApiImportTransactionHead[];
  for (const head of heads.filter(candidate => candidate.note === 'Computer generated')) {
    const deleteResponse = await page.request.delete(`/api/importtransactionhead/${head.idTransactionHead}`, {headers});
    expect(deleteResponse.ok(), `deleting generated import head ${head.idTransactionHead}: `
      + await deleteResponse.text()).toBeTruthy();
  }
}

async function getTenantTransactions(page: Page, headers: Record<string, string>): Promise<ApiTransaction[]> {
  const response = await page.request.get('/api/transaction', {headers});
  expect(response.ok(), `loading tenant transactions: ${await response.text()}`).toBeTruthy();
  return await response.json() as ApiTransaction[];
}

function belongsToScenario(transaction: ApiTransaction, scenario: ImportTransactionScenario,
    idSecurityAccount: number): boolean {
  return transaction.idSecurityaccount === idSecurityAccount
    && scenario.documents.some(document => document.transactionType === transaction.transactionType
      && document.transactionDate === transactionDate(transaction));
}

function belongsToCsvScenario(transaction: ApiTransaction, scenario: CsvImportTransactionScenario,
    idSecurityAccount: number): boolean {
  return transaction.idSecurityaccount === idSecurityAccount
    && scenario.expectedTransactions.some(expected => expected.transactionType === transaction.transactionType
      && expected.transactionDate === transactionDate(transaction) && expected.isin === transaction.security?.isin);
}

function transactionDate(transaction: {transactionTime: number | string}): string {
  if (typeof transaction.transactionTime === 'number') {
    return new Date(transaction.transactionTime).toISOString().slice(0, 10);
  }
  return String(transaction.transactionTime).slice(0, 10);
}

function transactionTimestamp(transaction: ApiTransaction): number {
  return typeof transaction.transactionTime === 'number'
    ? transaction.transactionTime : new Date(transaction.transactionTime).getTime();
}

async function authHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return {'x-auth-token': token, Accept: 'application/json'};
}

function exactText(value: string): RegExp {
  return new RegExp(`^\\s*${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}

function transactionImportContainer(page: Page) {
  return page.locator('securityaccount-import-transaction-table').locator('xpath=ancestor::div[contains(@class,"data-container")]')
    .first();
}

function nonEmptyLines(filePath: string): string[] {
  return fs.readFileSync(filePath, 'utf8').split(/\r?\n/).filter(line => line.trim().length > 0);
}

function parseBoolean(value: string, filePath: string, lineNumber: number): boolean {
  if (value === 'true') {
    return true;
  }
  if (value === 'false') {
    return false;
  }
  throw new Error(`${filePath}:${lineNumber}: expected boolean, got '${value}'`);
}

function parseTransactionExportCsv(filePath: string): ExpectedImportedTransaction[] {
  const lines = nonEmptyLines(filePath);
  if (lines.length < 2) {
    throw new Error(`${filePath}: expected a header and at least one transaction row`);
  }
  const header = parseDelimitedRow(lines[0], ';');
  const requiredColumns = ['Datum', 'Transaktion', 'ISIN'];
  for (const column of requiredColumns) {
    if (!header.includes(column)) {
      throw new Error(`${filePath}: missing required column '${column}'`);
    }
  }
  const dateIndex = header.indexOf('Datum');
  const typeIndex = header.indexOf('Transaktion');
  const isinIndex = header.indexOf('ISIN');
  return lines.slice(1).map((line, index) => {
    const columns = parseDelimitedRow(line, ';');
    if (columns.length !== header.length) {
      throw new Error(`${filePath}:${index + 2}: expected ${header.length} columns, got ${columns.length}`);
    }
    const dateMatch = /^(\d{2})\.(\d{2})\.(\d{4})/.exec(columns[dateIndex]);
    if (!dateMatch) {
      throw new Error(`${filePath}:${index + 2}: invalid transaction date '${columns[dateIndex]}'`);
    }
    return {
      transactionDate: `${dateMatch[3]}-${dateMatch[2]}-${dateMatch[1]}`,
      transactionType: mapCsvTransactionType(columns[typeIndex], filePath, index + 2),
      isin: columns[isinIndex]
    };
  });
}

function parseDelimitedRow(line: string, delimiter: string): string[] {
  const columns: string[] = [];
  let value = '';
  let quoted = false;
  for (let index = 0; index < line.length; index++) {
    const character = line[index];
    if (character === '"') {
      if (quoted && line[index + 1] === '"') {
        value += '"';
        index++;
      } else {
        quoted = !quoted;
      }
    } else if (character === delimiter && !quoted) {
      columns.push(value);
      value = '';
    } else {
      value += character;
    }
  }
  columns.push(value);
  return columns;
}

function mapCsvTransactionType(value: string, filePath: string, lineNumber: number): ImportedTransactionType {
  switch (value) {
    case 'Kauf':
      return 'ACCUMULATE';
    case 'Verkauf':
      return 'REDUCE';
    case 'Dividende':
      return 'DIVIDEND';
    default:
      throw new Error(`${filePath}:${lineNumber}: unsupported transaction type '${value}'`);
  }
}

function expectedTransactionKeys(expected: ExpectedImportedTransaction[]): string[] {
  return expected.map(item => `${item.transactionDate}|${item.transactionType}|${item.isin}`).sort();
}

function positionKeys(positions: ApiCombinedImportTransactionPos[]): string[] {
  return positions.map(({importTransactionPos: position}) =>
    `${transactionDate(position)}|${position.transactionType}|${position.security?.isin ?? ''}`).sort();
}

function transactionKeys(transactions: ApiTransaction[]): string[] {
  return transactions.map(transaction =>
    `${transactionDate(transaction)}|${transaction.transactionType}|${transaction.security?.isin ?? ''}`).sort();
}
