#!/usr/bin/env python3
"""生成插件 API 文档数据（ui/docs/ApiDocData.kt）。

插件开发者看到的「wa / bridge / audio 方法清单」不是手写的，而是从桥接层源码里
抽取 public 方法签名生成的，所以永远不会出现「文档里有、代码里没有」的接口。

用法（仓库根目录）：

    python3 tools/gen_apidoc.py

改过 ScriptWaBridge / ScriptPluginBridge / AudioTransformBridge 之后重跑一次即可。
"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPT_DIR = os.path.join(ROOT, 'app/src/main/java/h/Hchat/hooks/items/script')
AUDIO_FILE = os.path.join(ROOT, 'app/src/main/java/h/Hchat/media/AudioTransformBridge.kt')
OUT = os.path.join(ROOT, 'app/src/main/java/com/johnny/wxscript/ui/docs/ApiDocData.kt')

TARGETS = [
    ('waApis', os.path.join(SCRIPT_DIR, 'ScriptWaBridge.kt')),
    ('bridgeApis', os.path.join(SCRIPT_DIR, 'ScriptPluginBridge.kt')),
    ('audioApis', AUDIO_FILE),
]


def depth_before(lines, index):
    depth = 0
    for k in range(index):
        text = lines[k]
        if '//' in text:
            text = text.split('//')[0]
        depth += text.count('{') - text.count('}')
    return depth


def extract(path):
    """抽取类体第一层的 public 方法签名（跳过 private 与嵌套类里的方法）。"""
    if not os.path.exists(path):
        return []
    lines = open(path, encoding='utf-8').read().split('\n')
    out = []
    i = 0
    while i < len(lines):
        line = lines[i]
        match = re.match(
            r'\s*(?:@JvmStatic\s+)?(?:public\s+|internal\s+|private\s+)?fun\s+([A-Za-z0-9_]+)\s*\(',
            line,
        )
        if match and 'private ' not in line and depth_before(lines, i) == 1:
            buf = line.strip()
            j = i
            while buf.count('(') > buf.count(')') and j + 1 < len(lines):
                j += 1
                buf += ' ' + lines[j].strip()
            buf = re.sub(r'\s+', ' ', buf)
            buf = re.sub(r'\bfun\s+', '', buf, count=1)
            buf = re.sub(r'\s*=.*$', '', buf)
            buf = re.sub(r'\s*\{\s*$', '', buf)
            buf = buf.replace(', ', ',')
            out.append(buf.strip())
            i = j
        i += 1

    seen = set()
    uniq = []
    for item in out:
        if item in seen:
            continue
        seen.add(item)
        uniq.append(item)
    return uniq


def kt_str(value):
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'


def main():
    blocks = []
    counts = {}
    for name, path in TARGETS:
        items = extract(path)
        counts[name] = len(items)
        body = '\n'.join('        %s,' % kt_str(item) for item in items)
        blocks.append('    val %s: List<String> = listOf(\n%s\n    )' % (name, body))

    content = '''package com.johnny.wxscript.ui.docs

/**
 * 插件 API 签名清单。
 *
 * 由 tools/gen_apidoc.py 从桥接层源码自动生成，只收录真实存在的 public 方法，
 * 因此列表长度与模块版本一一对应；改桥接层后重新生成即可。
 */
object ApiDocData {

%s
}
''' % ('\n\n'.join(blocks))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8', newline='') as handle:
        handle.write(content)
    print(counts)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
