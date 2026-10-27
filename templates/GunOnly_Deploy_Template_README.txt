MechanicianCombat 枪件展开/收起模板

用途
- 仅保留玩家 rig 中的 gun 组及其全部子骨骼。
- 仅保留两段动画：Focus_ready（蹲射展开）与 Focus_over（起身收起）。
- 动画中属于 body、chest、head、手臂、腿、shells、smoke 的通道已剔除。
- gun 及其 barrel、fire、stock、case、magazine、aim、locator 等子组通道保持不变。

注意
- 原玩家动画同时使用 chest/body 旋转与位移。当前模板只保留枪件子树，
  因此可用于比较枪自身的展开结构，不会包含完整的玩家姿态复合结果。
- 如需完整玩家姿势，请使用 MechanicianCombat_Gun_Template_r397.bbmodel。

生成工具
rebuild/tools/create_gun_only_template.ps1
rebuild/tools/ExtractGunSubtreeTemplate.java

重新生成时传入合并版完整模板：
powershell -NoProfile -ExecutionPolicy Bypass -File
  rebuild/tools/create_gun_only_template.ps1
  -Source <MechanicianCombat_Gun_Template_r397.bbmodel>
  -Destination <MechanicianCombat_GunOnly_Deploy_Template.bbmodel>
