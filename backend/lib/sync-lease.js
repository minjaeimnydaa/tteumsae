import { claimSyncJob, finishSyncJob } from "./database.js";

function persistedStatus(status) {
  return status === "partial" ? "partial" : "completed";
}

export async function runWithSyncLease({
  jobId,
  run,
  claim = claimSyncJob,
  finish = finishSyncJob,
  leaseSeconds = 90,
  now = () => new Date(),
  tokenFactory = () => crypto.randomUUID()
}) {
  const token = tokenFactory();
  const startedAt = now();
  const claimed = await claim({
    jobId,
    token,
    now: startedAt.toISOString(),
    leaseSeconds
  });
  if (!claimed) return { status: "skipped", reason: "already_running" };

  let completionAttempted = false;
  try {
    const result = await run();
    completionAttempted = true;
    const finished = await finish({
      jobId,
      token,
      status: persistedStatus(result.status),
      summary: result,
      finishedAt: now().toISOString()
    });
    if (!finished) throw new Error("Sync lease ownership was lost before finish");
    return result;
  } catch (error) {
    if (!completionAttempted) {
      try {
        await finish({
          jobId,
          token,
          status: "failed",
          summary: { errorCode: error?.code ?? "INTERNAL_ERROR" },
          finishedAt: now().toISOString()
        });
      } catch {
        // The original error wins; an orphaned lease expires after 90 seconds.
      }
    }
    throw error;
  }
}
