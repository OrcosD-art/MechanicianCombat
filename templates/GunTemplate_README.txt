MechanicianCombat 枪械接口与 bbmodel 模板

模板文件
- MechanicianCombat_Gun_Template_r397.bbmodel
- 模板基于当前破垒重炮完整玩家 rig，保留身体、枪件、弹链、弹壳和作者编排动画。
- 当前导出流程会在原始动画基础上派生弹壳独立动画、上下半身分层动画和 hold 尾帧；
  若要继续使用这些特性，请沿用 gen_geckolib_mech.py 的 derive_upper_variants、
  derive_shell_animations 和下半身派生步骤。

新增枪种
1. 用 GunDefinition.builder(id).magazines(...).timing(...).assets(...).build()
   新建定义；id 同时作为物品注册 ID。
2. 在模组初始化阶段调用 GunRegistry.register(definition, new Item.Settings().maxCount(1))。
3. 将定义中的 model 指向 geo/entity/<model>.geo.json，animation 指向
   animations/entity/<animation>.animation.json，gunTexture 填写完整 textures 路径。
4. 物品名称使用 nameTranslationKey，说明使用 descriptionTranslationKey。

最简注册示例
GunDefinition def = GunDefinition.builder(TkGun.id("new_gun"))
        .magazines(6, 10)
        .timing(30, 12, 5, 5)
        .baseDamage(9.6F)
        .build();
GunRegistry.register(def, new Item.Settings().maxCount(1));

仅替换现有枪的视觉资源
- 调用 GunRegistry.replace(newDef)，并保持 def.id() 不变。
- 已存在的 ItemStack 会在下一次渲染/逻辑读取时使用新定义。
- 不要用新物品替换旧 ID，否则旧存档中的弹仓和弹匣组件无法沿用。

bbmodel 约束
- 保留 body、chest、head、arm_left、arm_right、leg_left、leg_right。
- 保留 gun、barrel、fire、aim、locator、shells、change3。
- aim 与 locator 的连线用于实时枪口中心线；locator 必须位于实际枪口。
- 保留 magazine_1、magazine_2，供弹链状态和换弹渲染使用。
- 动画名称必须与当前状态机一致；新增枪可在 maxShotIndex 内复用
  Focus_shooting、Focus_shooting1..7 的命名规则。
- 导出 GeckoLib geo/动画后，项目仍需分别提供物品栏 JSON 模型和枪件 PNG 贴图。

模板内作者编排动画名
idle, walk_front, walk_back, walk_left, walk_right, run, roll, roll_to_idle,
sliding, Focus_ready, Focus_over, sliding_fast_ready, rolling_fast_ready,
reload, Focus_over_shooting, Overheat, stand_shooting,
Focus_shooting_extra, Focus_shooting1, Focus_shooting4,
Focus_shooting6, Focus_shooting7,
Focus_hold。

导出后必须存在的派生动画名
shell_eject_1..7, shell_hold_1..7,
reload_upper, roll_to_idle_upper, stand_shooting_hold,
idle_lower, run_lower, walk_front..right_lower, reload_lower。

专注射击合并规则
- 第 1..3 发共用 Focus_shooting_extra。
- 第 4..N-4 发共用 Focus_shooting1，第 N-3..N-2 发共用 Focus_shooting4。
- 两个阶段的弹链偏移由当前发数在运行时驱动，不重复保存逐发动画。
- 第 N-1 发使用 Focus_shooting6，最后一发使用 Focus_shooting7。

模板生成工具
rebuild/tools/create_gun_template.ps1
