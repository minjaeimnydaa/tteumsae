import { getGate1bOpsStatus } from "../../lib/database.js";
import { requiredEnv } from "../../lib/env.js";
import {
  json,
  methodNotAllowed,
  serverError,
  unauthorized
} from "../../lib/http.js";
import {
  kstUsageDate,
  mobilityBudgetPolicy,
  walkBudgetPolicy
} from "../../lib/provider-usage.js";

const defaultDependencies = {
  secret: () => requiredEnv("CRON_SECRET"),
  now: () => new Date(),
  getStatus: getGate1bOpsStatus
};

function usageWithCurrentWarnings(usage = []) {
  const mobilityWarning = mobilityBudgetPolicy().warningThreshold;
  const walkWarning = walkBudgetPolicy().warningThreshold;
  return usage.map((item) => ({
    ...item,
    warning:
      (item.provider === "KAKAO_MOBILITY" &&
        item.operation === "DIRECTIONS" &&
        item.reservedCount >= mobilityWarning) ||
      (item.provider === "KAKAO_LOCAL" &&
        item.operation === "WALK_DIRECTIONS" &&
        item.reservedCount >= walkWarning)
  }));
}

export function createOpsStatusHandler(dependencies = {}) {
  const deps = { ...defaultDependencies, ...dependencies };
  return {
    async fetch(request) {
      if (request.method !== "GET") return methodNotAllowed(["GET"]);
      try {
        const expected = `Bearer ${deps.secret()}`;
        if (request.headers.get("authorization") !== expected) return unauthorized();

        const generatedAt = deps.now();
        const usageDate = kstUsageDate(generatedAt);
        const status = await deps.getStatus({
          usageDate,
          sigunguCode: 1,
          curationTarget: 100
        });
        return json({
          status: "ok",
          generatedAt: generatedAt.toISOString(),
          usageDate,
          usage: usageWithCurrentWarnings(status?.usage),
          syncJobs: status?.syncJobs ?? [],
          dataQuality: status?.dataQuality ?? {}
        });
      } catch (error) {
        return serverError(error);
      }
    }
  };
}

export default createOpsStatusHandler();
