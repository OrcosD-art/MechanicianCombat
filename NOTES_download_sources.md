# 本机下载源约定（2026-10-16 用户要求）

**这台机器没有 VPN → 一律优先国内源。**

| 要下的东西 | 国内源 | 备注 |
|---|---|---|
| Modrinth 的 mod（API + 文件） | `https://mod.mcimirror.top/modrinth/v2/...`（MCIM 镜像）/ 或 `https://mod.mcimirror.top` 对应路径 | 2026-10-16 实测 **api.modrinth.com 直连也能通**，6 个依赖 jar 都是直连下的（见 `tools\fetch_mods.ps1`）；不稳时换 mcimirror |
| Minecraft 本体 / 版本清单 / 库 | BMCLAPI：`https://bmclapi2.bangbang93.com` | 本次没用到（编译目标是用本机 `建筑端` 里现成的 1.21.1 客户端 jar 现场造的） |
| Fabric Maven（loader / yarn 等） | `https://bmclapi2.bangbang93.com/maven` | 本次没用到（映射与 tiny-remapper 都在交接包里） |
| GitHub raw（例如 Blockbench 的 `geckolib.js`） | `https://gh-proxy.com/https://raw.githubusercontent.com/...` 或 `https://raw.gitmirror.com/...` | 本次 `raw.githubusercontent.com` 直连成功（434KB，已存 `tools\geckolib_bb5.js`） |
| Blockbench 本体 | 本机已有：`C:\Users\lmc\AppData\Local\Programs\Blockbench_x64_5.1.6.exe`（已解包到 `bb516\`） | 不需要再下 |

> 已经在 `D:\Game\mc\建筑端\.minecraft\versions\1.21.1-Fabric 0.18.2\mods` 里的 8 个 jar 都已下完并校验过 sha1，
> 除非要换版本，否则不用再联网。
