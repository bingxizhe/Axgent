#!/usr/bin/env python3
"""把 Qoder 会话明文 JSONL 转成 Markdown，用于交给其他 agent 续跑任务。

输出格式：每段对话（会话）一个 # 标题，每轮用 ## User / ## Assistant 标注，
轮次之间用 --- 分隔。

用法：
  python -X utf8 scripts/jsonl-to-md.py -i <文件或目录> [-o <文件或目录>]
                                        [--max-chars N] [--keep-noise]

-i 可以是单个 <会话ID>.jsonl，也可以是 conversation-history 目录（则导出全部会话）。
-o 省略时写入 runs/handoff/。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from datetime import datetime

# 会话记录里混入的框架噪声标签，续跑任务时不需要，默认剥掉
NOISE_TAGS = ("system-reminder", "current_open_file")
CACHE_ROOT = os.path.expanduser(r"~\.qoder-cn\cache\projects")


def unwrap_query(text: str) -> str:
    """拆掉 <user_query> 之类的外层标签，保留正文。"""
    m = re.search(r"<user_query>\s*(.*?)\s*</user_query>", text, re.S)
    if m:
        text = m.group(1)
    for tag in NOISE_TAGS:
        text = re.sub(rf"<{tag}>.*?</{tag}>\s*", "", text, flags=re.S)
        text = re.sub(rf"<{tag}>", "", text)
    return text.strip()


def render_block(block: dict, max_chars: int) -> list[str]:
    """把单个 content block 渲染成 Markdown 片段列表。"""
    kind = block.get("type", "text")
    if kind == "text":
        raw = block.get("text", "")
        out = []
        for para in re.split(r"\n{2,}", raw.strip()):
            if not para.strip():
                continue
            if max_chars and len(para) > max_chars:
                para = para[:max_chars] + f"\n…（截断，共 {len(para)} 字）"
            out.append(para)
        return out
    if kind in ("tool_use", "tool-call"):
        name = block.get("name", "?")
        arg = json.dumps(block.get("input", {}), ensure_ascii=False)
        if max_chars and len(arg) > max_chars:
            arg = arg[:max_chars] + "…"
        return [f"> **[工具调用 `{name}`]** `{arg}`"]
    if kind in ("tool_result", "tool-response"):
        body = block.get("content", block.get("output", ""))
        if not isinstance(body, str):
            body = json.dumps(body, ensure_ascii=False)
        if max_chars and len(body) > max_chars:
            body = body[:max_chars] + "…"
        return [f"> **[工具结果]** {body.strip()}"]
    if kind == "image":
        path = (block.get("source") or {}).get("path") or (
            block.get("asset") or {}).get("path") or block.get("assetId", "?")
        return [f"![图片](assets/{os.path.basename(str(path))})"]
    return [f"> **[未识别块 `{kind}`]**"]


def demote_headings(body: str, shift: int = 2) -> str:
    """正文自带的 # 标题降级，避免和 ## User / ## Assistant 撞层级；代码块内不动。"""
    out, fence = [], None
    for line in body.split("\n"):
        stripped = line.lstrip()
        marker = stripped[:3]
        if marker in ("```", "~~~"):
            fence = None if fence is None else marker
            out.append(line)
            continue
        if fence is None:
            m = re.match(r"^(#{1,6})(\s)", line)
            if m:
                line = "#" * min(6, len(m.group(1)) + shift) + m.group(2) + line[len(m.group(0)):]
        out.append(line)
    return "\n".join(out)


def load_turns(jsonl_path: str, keep_noise: bool, max_chars: int) -> list[dict]:
    """读 JSONL，合并连续同角色片段为一轮：assistant 的流式碎片会属于同一轮。"""
    turns: list[dict] = []
    with open(jsonl_path, encoding="utf-8") as fh:
        for lineno, line in enumerate(fh, 1):
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                print(f"  ! 第 {lineno} 行 JSON 解析失败，已跳过", file=sys.stderr)
                continue
            role = obj.get("role", "?")
            msg = obj.get("message", obj)
            content = msg.get("content")
            blocks = [{"type": "text", "text": content}] if isinstance(
                content, str) else (content or [])
            parts: list[str] = []
            for blk in blocks:
                if not keep_noise and role == "user" and blk.get("type") == "text":
                    blk = dict(blk, text=unwrap_query(blk.get("text", "")))
                    if not blk["text"].strip():
                        continue
                parts.extend(render_block(blk, max_chars))
            body = "\n\n".join(p for p in parts if p.strip())
            if not body.strip():
                continue
            if turns and turns[-1]["role"] == role:
                turns[-1]["body"] += "\n\n" + body
            else:
                turns.append({"role": role, "body": body})
    return turns


def session_title(jsonl_path: str, turns: list[dict]) -> str:
    sid = os.path.basename(os.path.dirname(jsonl_path)) or \
        os.path.splitext(os.path.basename(jsonl_path))[0]
    st = os.stat(jsonl_path)
    day = datetime.fromtimestamp(st.st_ctime).strftime("%Y-%m-%d")
    first = next((t["body"].splitlines()[0] for t in turns
                  if t["role"] == "user"), "")
    first = re.sub(r"\s+", " ", first)[:40]
    return f"# 会话 {sid}（{day}）{('：' + first) if first else ''}"


def to_markdown(jsonl_path: str, keep_noise: bool, max_chars: int) -> str:
    turns = load_turns(jsonl_path, keep_noise, max_chars)
    assets_dir = os.path.join(os.path.dirname(jsonl_path), "assets")
    assets = sorted(os.listdir(assets_dir)) if os.path.isdir(assets_dir) else []
    head = [
        session_title(jsonl_path, turns),
        "",
        f"- 源文件：`{jsonl_path}`",
        f"- 原始消息 {sum(1 for _ in open(jsonl_path, encoding='utf-8'))} 条"
        f" → 合并为 {len(turns)} 轮",
        f"- 导出时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
    ]
    if assets:
        head.append(f"- 附件：{', '.join(assets)}（在同名 assets/ 目录）")
    out = head + ["", "---", ""]
    for i, turn in enumerate(turns):
        out.append(f"## {turn['role'].capitalize()}")
        out.append("")
        out.append(demote_headings(turn["body"].strip()))
        if i != len(turns) - 1:
            out += ["", "---", ""]
    return "\n".join(out).rstrip() + "\n"


def resolve_inputs(src: str) -> list[str]:
    if os.path.isfile(src):
        return [src]
    if os.path.isdir(src):
        found = []
        for root, _dirs, files in os.walk(src):
            for name in files:
                if name.endswith(".jsonl"):
                    found.append(os.path.join(root, name))
        return sorted(found, key=os.path.getctime)
    # 未给路径时按当前目录自动匹配项目缓存
    slug = re.sub(r"[^A-Za-z0-9]+", "-", os.path.basename(os.getcwd())).lower()
    hit = [p for p in glob_projects() if slug in os.path.basename(p).lower()]
    if not hit:
        raise SystemExit(f"找不到 {src}，也没在 {CACHE_ROOT} 匹配到项目缓存目录")
    return sorted(hit, key=os.path.getctime)


def glob_projects() -> list[str]:
    out = []
    if not os.path.isdir(CACHE_ROOT):
        return out
    for proj in os.listdir(CACHE_ROOT):
        hist = os.path.join(CACHE_ROOT, proj, "conversation-history")
        if os.path.isdir(hist):
            for sess in os.listdir(hist):
                j = os.path.join(hist, sess, f"{sess}.jsonl")
                if os.path.isfile(j):
                    out.append(j)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("-i", "--input", default="auto",
                    help="会话 .jsonl 文件、conversation-history 目录，或 auto")
    ap.add_argument("-o", "--output", default=os.path.join("runs", "handoff"),
                    help="输出 .md 文件；输入为多会话时视为目录")
    ap.add_argument("--max-chars", type=int, default=0,
                    help="单段最大字符数，0 表示不截断（默认）")
    ap.add_argument("--keep-noise", action="store_true",
                    help="保留 system-reminder / current_open_file 等框架标签")
    args = ap.parse_args()

    inputs = glob_projects() if args.input == "auto" else resolve_inputs(args.input)
    if not inputs:
        raise SystemExit("没有找到任何会话 jsonl")

    multi = len(inputs) > 1
    if multi and not args.output.endswith(".md"):
        os.makedirs(args.output, exist_ok=True)
    elif not multi:
        parent = os.path.dirname(os.path.abspath(args.output))
        os.makedirs(parent, exist_ok=True)
        if os.path.isdir(args.output):
            args.output = os.path.join(
                args.output, os.path.basename(inputs[0]).replace(".jsonl", ".md"))

    chunks = []
    for js in inputs:
        md = to_markdown(js, args.keep_noise, args.max_chars)
        if multi:
            target = os.path.join(
                args.output, os.path.basename(js).replace(".jsonl", ".md"))
            with open(target, "w", encoding="utf-8", newline="\n") as fh:
                fh.write(md)
            print(f"OK {target}  {len(md):,} 字符")
        else:
            with open(args.output, "w", encoding="utf-8", newline="\n") as fh:
                fh.write(md)
            print(f"OK {args.output}  {len(md):,} 字符")
        chunks.append(md)
    if multi:
        combined = os.path.join(args.output, "_all.md")
        with open(combined, "w", encoding="utf-8", newline="\n") as fh:
            fh.write("\n\n\n".join(chunks))
        print(f"OK {combined}  （全部会话合并，每段一个 # 标题）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
