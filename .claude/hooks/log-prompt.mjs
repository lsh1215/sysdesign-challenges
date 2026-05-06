#!/usr/bin/env node

// UserPromptSubmit hook: append user prompt to a conversation log.
//
// Routing:
//   - If .omc/state/active-sysdesign-topic.txt has a non-empty topic slug,
//     append to <projectRoot>/<topic>/conversation-log/YYYY-MM-DD.log
//   - Else, append to .claude/logs/YYYY-MM-DD.log (catchall)
//
// One file per local day; multiple sessions within a day concatenate.

import { readFileSync, mkdirSync, appendFileSync } from "fs";
import { join } from "path";

function readStdin() {
  try {
    return JSON.parse(readFileSync("/dev/stdin", "utf8"));
  } catch {
    return {};
  }
}

function pad(n) {
  return String(n).padStart(2, "0");
}

function localDateAndTime(d = new Date()) {
  const date = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const time = `${date} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  return { date, time };
}

// If we're invoked from inside an OMC worktree
// (e.g., <main>/.claude/worktrees/<name>/...), redirect to the main root so
// that active-topic state and conversation logs live in one canonical place.
function getMainProjectDir(dir) {
  const m = dir.match(/^(.+)\/\.claude\/worktrees\/[^/]+/);
  return m ? m[1] : dir;
}

function readActiveTopic(projectDir) {
  try {
    const slug = readFileSync(
      join(projectDir, ".omc", "state", "active-sysdesign-topic.txt"),
      "utf8"
    ).trim();
    if (!slug || slug.includes("/") || slug.includes("\\") || slug.startsWith(".")) return null;
    return slug;
  } catch {
    return null;
  }
}

function resolveLogsDir(projectDir) {
  const topic = readActiveTopic(projectDir);
  if (topic) {
    return join(projectDir, topic, "conversation-log");
  }
  return join(projectDir, ".claude", "logs");
}

function main() {
  try {
    const input = readStdin();
    const prompt = (input.prompt || "").trim();
    const sessionId = (input.session_id || "unknown").slice(0, 8);
    const projectDir = getMainProjectDir(
      process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd()
    );

    if (!prompt) {
      process.stdout.write(JSON.stringify({ continue: true }));
      return;
    }

    const logsDir = resolveLogsDir(projectDir);
    mkdirSync(logsDir, { recursive: true });

    const { date, time } = localDateAndTime();
    const entry = `\n## ${time} [${sessionId}] USER\n\n${prompt}\n`;

    appendFileSync(join(logsDir, `${date}.log`), entry);
  } catch {
    // never block on logging failure
  }

  process.stdout.write(JSON.stringify({ continue: true }));
}

main();
