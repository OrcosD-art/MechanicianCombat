# MechanicianCombat 源码协作说明

## 当前版本

- Mod 名称：MechanicianCombat
- Mod ID：`tkgun`
- 构建版本：`r349-2026-10-25`
- 当前 JAR：`release/MechanicianCombat-1.0.0.jar`
- 当前 JAR SHA1：`37972672201EDF288B35929BE9A21CD020414115`
- Minecraft：1.21.1
- Fabric Loader：按 0.18.2 测试
- Java：21

本包是源码协作快照，不是只读发布包。源码与资源版权归原作者所有；第三方
Mod JAR 仅作为本地编译依赖，除非其各自许可证允许，否则不要重新分发。

## 包内结构

```text
mod/
  src/main/java/                  Java 源码
  src/main/resources/             Mod 资源、Geo、动画、声音、贴图与数据
tools/
  build.ps1                       当前离线构建入口
  remap/                          Yarn、Intermediary 映射及 tiny-remapper
  *.ps1 / *.py / *.js             模型导出、资源生成与辅助脚本
fapi-named/                       Fabric API 的 named 编译依赖
deps/                             GeckoLib 等 Mod 的 named 编译依赖
build/
  mc-named.jar                    Yarn named Minecraft 编译目标
  mc-intermediary.jar             Intermediary Minecraft 中间文件
source_assets/                    作者提供的 bbmodel、贴图、声音及视觉源文件
docs/                             操作说明、更新日志、已知问题
release/                          当前构建 JAR
SOURCE_BUILD.ps1                  面向本包的构建包装器
```

## 编译

需要 JDK 21。将 `-Libs` 指向自己的 Minecraft 1.21.1 启动器 `libraries`
目录：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\SOURCE_BUILD.ps1 `
  -Libs "D:\path\to\.minecraft\libraries" `
  -JavaHome "C:\path\to\jdk-21"
```

默认输出：

```text
release\MechanicianCombat-1.0.0.jar
```

包内已经包含 named 依赖和映射，因此不需要联网。JDK 路径不是默认位置时，
用 `-JavaHome` 指定。不要用解压后的旧 class
继续开发；修改源码后必须重新运行构建脚本。安装新 JAR 前应完全退出游戏，
避免运行期间覆盖 JAR。

## 运行时前置

已在 1.21.1 测试端验证的版本：

- Fabric API `0.116.17+1.21.1`
- GeckoLib `4.8.2`
- Player Animation Lib `2.0.4+1.21.1`
- Accessories `1.1.0-beta.54+1.21.1`
- owo-lib `0.13.0-alpha.15+1.21`
- Cloth Config `15.0.140`
- Forge Config API Port `21.1.6`
- Shoulder Surfing Reloaded `4.21.0`
- Combat Roll `2.0.6+1.21.1`

后两项是 `fabric.mod.json` 声明的必要条件。启动器必须同时加载客户端和
服务端入口；单人游戏也需要完整前置。

## 主要代码入口

- `com.tk.gun.TkGunServer`：服务端状态机、射击、弹药和战斗背包逻辑。
- `com.tk.gun.client.TkGunClient`：客户端输入和状态同步。
- `com.tk.gun.client.rig.BowgunRigRenderer`：玩家 GeckoLib rig 渲染。
- `com.tk.gun.client.CombatBackpackGeoRenderer`：战斗背包、便携发射器与
  自动炮塔渲染。
- `com.tk.gun.client.CombatBackpackClientState`：背包目标、阶段和发射状态。
- `com.tk.gun.item.CombatBackpackAnimation`：背包动画阶段选择。

模型修改前请先备份 `mod/src/main/resources/assets/tkgun/geo` 和
`animations/entity`。`source_assets` 中存在用户正在编辑的 bbmodel，导出脚本
可能包含本机绝对路径，换机器后需要修改脚本参数。

## 自动炮塔当前实现

自动发射器攻击时，`auto_C3` 使用 `auto_aim.bbmodel` 的 C3 内四个 cube：

- 四个 cube 已直接合并到 `auto_C3`；
- 锁定时隐藏旧的 `auto_ejector` 子模型；
- 非锁定和 CD 状态仍使用旧自动炮塔动画模型；
- 锁定方向使用目标方向、`auto_front/auto_rear` 轴及四元数；
- C1/C2 保持原动画，不被锁定逻辑接管。

用于复现资源合并的脚本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\merge_auto_aim_lock_model.ps1
```

该脚本默认读取本包 `build/auto_aim.raw.geo.json`。如果重新编辑
`source_assets/.../自动炮台/auto_aim.bbmodel`，先使用
`tools/export_auto_launcher_model.ps1` 重新导出原始 Geo。

## 当前待验证项

- 自动炮塔在新 `auto_aim` C3 模型下的游戏内锁定方向和滚转稳定性。
- 不同玩家骨架、越肩视角和移动状态下的炮塔视觉位置。
- 自动锁定与目标切换时的平滑过滤表现。

源码包不包含游戏日志、崩溃报告、账号令牌、Blockbench 账号配置或浏览器
缓存。`source_assets` 中的作者资源不应在未确认授权的情况下公开再分发。

