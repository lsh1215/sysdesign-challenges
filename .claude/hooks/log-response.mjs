#!/usr/bin/env node

// Stop hook: append the latest assistant message from the transcript
// to a conversation log.
//
// Routing (mirrors log-prompt.mjs):
//   - If .omc/state/active-sysdesign-topic.txt has a non-empty topic slug,
//     append to <projectRoot>/<topic>/conversation-log/YYYY-MM-DD.log
//   - Else, append to .claude/logs/YYYY-MM-DD.log (catchall)

import { readFileSync, mkdirSync, appendFileSync, existsSync } from "fs";
import { join } from "path";

// If we're invoked from inside an OMC worktree, redirect to the main root.
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

// Walk the transcript JSONL backwards and pull the latest assistant turn's text.
// Transcript format varies slightly across versions; we accept either
// `{ role, content }` or `{ message: { role, content } }` and tolerate
// `content` being a string OR a list of `{ type, text }` blocks.
function getLatestAssistantText(transcriptPath) {
  if (!transcriptPath || !existsSync(transcriptPath)) return null;

  let content;
  try {
    content = readFileSync(transcriptPath, "utf8");
  } catch {
    return null;
  }

  const lines = content.split("\n").filter(Boolean);

  for (let i = lines.length - 1; i >= 0; i--) {
    let msg;
    try {
      msg = JSON.parse(lines[i]);
    } catch {
      continue;
    }

    const role = msg.role || msg.message?.role;
    if (role !== "assistant") continue;

    const body = msg.content ?? msg.message?.content;
    if (!body) continue;

    if (typeof body === "string") return body;
    if (Array.isArray(body)) {
      const text = body
        .filter((c) => c && c.type === "text" && typeof c.text === "string")
        .map((c) => c.text)
        .join("\n")
        .trim();
      if (text) return text;
    }
  }

  return null;
}

function main() {
  try {
    const input = readStdin();
    const transcriptPath = input.transcript_path;
    const sessionId = (input.session_id || "unknown").slice(0, 8);
    const projectDir = getMainProjectDir(
      process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd()
    );

    const text = getLatestAssistantText(transcriptPath);
    if (!text) {
      process.stdout.write(JSON.stringify({ continue: true }));
      return;
    }

    const logsDir = resolveLogsDir(projectDir);
    mkdirSync(logsDir, { recursive: true });

    const { date, time } = localDateAndTime();
    const entry = `\n## ${time} [${sessionId}] ASSISTANT\n\n${text}\n\n---\n`;

    appendFileSync(join(logsDir, `${date}.log`), entry);
  } catch {
    // never block on logging failure
  }

  process.stdout.write(JSON.stringify({ continue: true }));
}

main();
