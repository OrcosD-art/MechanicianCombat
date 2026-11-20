# Mechanician Combat NeoForge 构建说明

## 环境

- Minecraft: 1.21.1
- NeoForge: 21.1.266
- FML: 4.0.45
- Java: 21

测试端：

```text
D:\Game\mc\测试端\.minecraft\versions\1.21.1-NeoForge
```

## 构建

在 PowerShell 中执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File "D:\codex\mod\neoforge-port\tools\build-neoforge.ps1"
```

输出文件：

```text
D:\codex\mod\neoforge-port\dist\MechanicianCombat-1.21.1-neoforge-1.0.0.jar
```

构建流程会自动执行 Java 编译、Yarn 到 Mojang 映射、Mixin/继承成员修正和资源打包。

## 启动

启动正式测试端：

```powershell
powershell.exe -ExecutionPolicy Bypass `
  -File "D:\codex\mod\neoforge-port\tools\run-neoforge-test.ps1"
```

在隔离目录中测试：

```powershell
powershell.exe -ExecutionPolicy Bypass `
  -File "D:\codex\mod\neoforge-port\tools\run-neoforge-test.ps1" `
  -GameDir "D:\codex\mod\neoforge-port\test-runtime"
```

启动脚本会从版本 JSON 自动读取 FML 版本。只有在调试其他版本时才手动传入
`-FmlVersion`。

## 注意事项

- `Combat Roll` 与 `Shoulder Surfing` 是可选集成。未安装时启动日志会出现对应 Mixin
  目标缺失警告，不影响模组启动。
- NeoForge 环境中的物品、方块、方块实体和实体类型需要在构造阶段创建占位 Holder。
  `RegistryBootstrapAccess` 只定向打开这些注册表，不能重新打开属性注册表，否则会破坏
  NeoForge 的属性 DeferredHolder。
- GeckoLib 4.9.3 的三元表达式不接受 `? -(...)` 写法；动画资源中使用 `? 0 - (...)`。
