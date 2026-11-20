import { loginAsFixtureUser } from './helpers';
import { taskMonitorScenarios } from './lib/task-monitor.scenarios';

taskMonitorScenarios(loginAsFixtureUser, true);
