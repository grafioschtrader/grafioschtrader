import { expect, Page, Route, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';

// Drives the consent page an MCP client opens for the OAuth sign-in. The validation and consent endpoints answer from
// here after a held delay, so the spec needs no registered client and creates no authorization.

const QUERY =
  'response_type=code&client_id=https%3A%2F%2Fclient.example.com%2Fmeta.json&redirect_uri=https%3A%2F%2Fclient.example.com%2Fcb' +
  '&state=e2e&code_challenge=abc&code_challenge_method=S256&scope=mcp.read';

const RX = {
  checking: /(The request of the application is being checked|Die Anfrage der Anwendung wird geprüft)/,
  question: /E2E Client \(client\.example\.com\)/,
  approve: /^(Allow|Erlauben|Zulassen)$/,
  deny: /^(Deny|Ablehnen|Verweigern)$/,
  notAvailable: /(has not enabled sign-in for applications|Anmeldung von Anwendungen)/
};

interface Held {
  arrived: Promise<void>;
  release: () => void;
}

async function hold(page: Page, url: string, answer: (route: Route) => Promise<void>): Promise<Held> {
  let release!: () => void;
  let arrive!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const arrived = new Promise<void>((resolve) => (arrive = resolve));
  // Every request to the URL waits for the release; a request the page abandoned meanwhile (the authorize page is
  // loaded once more after the login redirect) cannot be answered any more and is skipped.
  await page.route(url, async (route: Route): Promise<void> => {
    arrive();
    await gate;
    try {
      await answer(route);
    } catch (error) {
      if (!String(error).includes('already handled')) throw error;
    }
  });
  return { arrived, release };
}

async function openConsentPage(page: Page): Promise<void> {
  await loginAsFixtureUser(page, 'alledit');
  await page.goto(`/oauth/authorize?${QUERY}`);
}

test.describe('MCP OAuth consent notification', () => {
  test('the consent question appears once the delayed validation answers', async ({ page }) => {
    const validated = await hold(page, '**/api/oauth/authorize/validate', (route) =>
      route.fulfill({
        json: {
          authorizationRequestId: 'e2e-request',
          clientName: 'E2E Client',
          clientHost: 'client.example.com',
          scope: 'mcp.read'
        }
      })
    );
    await openConsentPage(page);
    await validated.arrived;
    await expect(page.getByText(RX.checking)).toBeVisible();
    validated.release();
    await expect(page.getByText(RX.question)).toBeVisible();
    const approve = page.getByRole('button', { name: RX.approve });
    const deny = page.getByRole('button', { name: RX.deny });
    await expect(approve).toBeEnabled();

    const refused = await hold(page, '**/api/oauth/consent', (route) => route.fulfill({ status: 400, json: {} }));
    await deny.click();
    await refused.arrived;
    await expect(approve).toBeDisabled();
    await expect(deny).toBeDisabled();
    refused.release();
    await expect(approve).toBeEnabled();
    await expect(deny).toBeEnabled();
  });

  test('a refused validation shows its reason after the delay', async ({ page }) => {
    const validated = await hold(page, '**/api/oauth/authorize/validate', (route) =>
      route.fulfill({ status: 404, json: {} })
    );
    await openConsentPage(page);
    await validated.arrived;
    await expect(page.getByText(RX.checking)).toBeVisible();
    validated.release();
    await expect(page.getByText(RX.notAvailable)).toBeVisible();
    await expect(page.getByText(RX.checking)).toHaveCount(0);
  });
});
