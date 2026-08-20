# [Bug] 启动后稳定崩溃退出：exit code 3221226505 (STATUS_INVALID_HANDLE)

## 环境
- Windows 11 (10.0.22631)
- Node v24.14.1
- dsh-synapse 0.3.0（通过 `link:` junction 装配进 DeepSeek Harness 的 web profile，作为 bundle 常驻加载）

## 现象
`pnpm dsh web` 启动 **100% 稳定复现**崩溃（连续 4 次全部如此），退出码 `3221226505` = `0xC0000008`（STATUS_INVALID_HANDLE）：

```
D:\deepseek-harness>pnpm dsh web
$ node --import tsx/esm apps/cli/src/bin.ts "web"
Processing request of type ListToolsRequest
dsh web: http://127.0.0.1:3080
[ELIFECYCLE] Command failed with exit code 3221226505.
```

关键特征：
- `dsh web: http://127.0.0.1:3080` 已打印 → **Web server 与整棵插件树已成功装载**（该行只在 Loader tree 全部 settle 后打印）
- 崩溃发生在**运行期**，紧接装载完成之后
- **单实例、无端口冲突**（已排除 EADDRINUSE：端口占用时是优雅 exit 1）
- 排除重复实例竞争：每次崩溃后进程退出、端口释放，下一次是全新单实例，仍然 100% 崩溃

## 高度怀疑：dsh-synapse 启动时全量投影 + 文件锁竞争

`index.js` 的 `apply()` 在启动时执行：

```js
if (autoProjection) {
  ctx.on('session/created', replaySession)
  ctx.on('session/event', enqueueProjection)
  for (const session of ctx.sessions.list()) replaySession(session)  // 启动即遍历所有会话投影
}
```

每个 `replaySession` → `projectSession` → `mutate()` → `save()`，而 `save()` 会：
1. `writeFile(workspaces.json.<pid>.tmp)` 写大文件（我的 workspaces.json 约 2.96MB）
2. `rename(tmp, workspaces.json)` 覆盖
3. `acquireLock()` / `releaseLock()` 操作 `.lock` 文件
4. `lockIsStale()` 里 `process.kill(pid, 0)` 探测锁持有者进程句柄

崩溃点最可能在这条路径上：**启动瞬间对 2.96MB 文件密集做 tmp+rename+锁+进程句柄探测**，在 Windows 上触发原生 `STATUS_INVALID_HANDLE`。

## 源码中发现的疑似缺陷
1. `acquireLock()` 拿锁失败**只 warn 不阻断** → 多实例/残留锁时仍继续写文件
2. `releaseLock()` **无条件 unlink** `.lock` → 会删掉其他实例持有的锁
3. `load()` 对损坏 JSON 直接 throw → `this.ready` 永久 rejected，整个 store 不可用；崩溃/中断还会残留 `workspaces.json.<pid>.tmp`
4. `lockIsStale()` 的 `process.kill(pid, 0)` 在 Windows 上与并发文件操作组合时是句柄异常高危点

## 复现步骤
1. Windows 下装配本插件进 DSH web profile
2. 确保 `synapse/workspaces.json` 存在（若干会话投影后的文件）
3. `pnpm dsh web` → 100% 复现崩溃（退出码 3221226505）

## 预期行为
- 启动后正常常驻运行，不崩溃
- 锁获取失败应 fail-fast（throw）而不是继续写；`releaseLock` 只释放自己持有的锁
- `load()` 遇到损坏数据应备份后重建，而不是永久不可用

## 临时规避
在 profile 的 `cordis.patch.yml` 中 `disabled: true` 禁用它后，实例稳定运行 30+ 分钟无崩溃。
