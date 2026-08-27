import { expect, test } from '@playwright/test';
import { authHeaders, login, PEER_B_FRONTEND } from './peer.helpers';

test('the restarted peer worker picks up a waiting task', async ({ browser }) => {
  const context = await browser.newContext({ baseURL: PEER_B_FRONTEND });
  const page = await context.newPage();
  try {
    await login(page, PEER_B_FRONTEND);
    const headers = await authHeaders(page);
    const existingResponse = await page.request.get('/api/taskdatachange?idTasks=15', { headers });
    for (const task of (await existingResponse.json()) as any[]) {
      await page.request.delete(`/api/taskdatachange/${task.idTaskDataChange}`, { headers });
    }
    const createdResponse = await page.request.post('/api/taskdatachange', {
      headers,
      data: {
        idTask: 'TOKEN_USER_REGISTRATION_PURGE',
        executionPriority: 'PRIO_VERY_HIGH',
        earliestStartTime: new Date().toISOString()
      }
    });
    expect(createdResponse.ok(), await createdResponse.text()).toBeTruthy();
    const created = await createdResponse.json();
    await expect
      .poll(
        async () => {
          const response = await page.request.get('/api/taskdatachange?idTasks=15', { headers });
          const tasks = (await response.json()) as any[];
          return tasks.find((task) => task.idTaskDataChange === created.idTaskDataChange)?.progressStateType;
        },
        { timeout: 45_000 }
      )
      .toMatch(/PROG_PROCESSED|2/);
    await page.request.delete(`/api/taskdatachange/${created.idTaskDataChange}`, { headers });
  } finally {
    await context.close();
  }
});
