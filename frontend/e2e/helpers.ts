import {Page} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

const CREDENTIALS_FILE = path.join(__dirname, '.auth', 'credentials.json');

export interface TestCredentials {
  email: string;
  password: string;
  tenantName: string;
  tenantCurrency: string;
}

/**
 * Reads the credentials saved by the auth setup.
 */
export function loadCredentials(): TestCredentials {
  const data = fs.readFileSync(CREDENTIALS_FILE, 'utf-8');
  return JSON.parse(data);
}

/**
 * Logs in with the saved credentials and waits for the main view to load.
 */
export async function login(page: Page): Promise<TestCredentials> {
  const creds = loadCredentials();
  await page.goto('/login');
  await page.locator('#email').waitFor({state: 'visible', timeout: 15_000});
  await page.locator('#email').fill(creds.email);
  await page.locator('#password').fill(creds.password);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/\/mainview/, {timeout: 15_000});
  await page.locator('p-tree').waitFor({state: 'visible', timeout: 15_000});
  return creds;
}
