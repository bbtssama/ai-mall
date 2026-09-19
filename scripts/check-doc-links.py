# -*- coding: utf-8 -*-
"""全项目 Markdown 相对链接校验（ai-mall 仓库级）

用途：移动/改名文档后，一次性验证【整个仓库】的 .md/.html 相对链接是否还有断链。
      超链学习目录那份 `_链接校验.py` 只覆盖它自己那一层，本脚本覆盖全仓。

用法：<python> scripts/check-doc-links.py
退出码：0 = 全部通过；1 = 有断链

覆盖范围：从仓库根遍历，跳过 .git / node_modules / frontend / target / dist /
          _archive / __pycache__ / .workbuddy（归档与构建产物不参与校验）。
校验内容：`](相对路径.md)` 形式的目标文件是否存在（含 #锚点 剥离后的路径）。
"""
import io
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {".git", "node_modules", "frontend", "target", "dist",
             "_archive", "__pycache__", ".workbuddy", ".vscode"}
EXTS = {".md", ".html"}

# 只校验 ./ 或 ../ 开头的相对链接（裸文件名 / 绝对 URL / 纯锚点不在此列）
LINK_RE = re.compile(r"\]\(((?:\./|\.\./)[^)\s]*?)(#[^)\s]*)?\)")


def main():
    total = broken = 0
    problems = []

    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if os.path.splitext(fn)[1].lower() not in EXTS:
                continue
            path = os.path.join(dirpath, fn)
            try:
                text = io.open(path, encoding="utf-8", errors="ignore").read()
            except OSError:
                continue
            for m in LINK_RE.finditer(text):
                rel = m.group(1)
                total += 1
                target = os.path.normpath(
                    os.path.join(os.path.dirname(path), rel.replace("/", os.sep))
                )
                if not os.path.exists(target):
                    broken += 1
                    problems.append(
                        "%s\n      -> %s" % (os.path.relpath(path, ROOT), rel)
                    )

    print("=" * 72)
    print("仓库根：%s" % ROOT)
    print("扫描 %d 条相对链接 ｜ 失效 %d" % (total, broken))
    print("=" * 72)
    if problems:
        print("\n❌ 断链清单：")
        for p in problems:
            print("   " + p)
        return 1
    print("\n✅ 全部相对链接目标存在")
    return 0


if __name__ == "__main__":
    sys.exit(main())
