# 仓库协作约定

- 本仓库只收 Minecraft Java 版 26.2 的类人训练假人：实体、物品、命令与击打 HUD。身体模板来自 Human，身体生成与宿主绑定归 Vanilla-Humanoid-Threatening，伤害结算归 Vanilla-Damage，权威身体状态归 Framework。
- Meridian 各附属按完整装配运行；不得添加缺少必需附属时的替代路径、降级或可选依赖判断。
- 不复制宿主或结算代码：物种经 Threatening 的公开数据驱动注册点接入，伤害只订阅 Damage 提交后的观察事件，重置走 Framework 健康事务。
- `shared/` 只放 Protobuf 契约；`server/` 保存与 Minecraft 无关的报告构建与有界校验；`fabric/` 负责实体、物品、命令、网络与客户端 HUD。
- 简体中文沟通、文档与函数内意图注释；公开标识符用英文。代码按读取、校验、计算、请求、结果展开，命名、换行和注释密度与相邻代码保持一致。
- 文档唯一编辑源为 `docs/wiki/`，README 仅作入口。交付执行 `test build`、Fabric GameTest、Xvfb 客户端测试、文档引用检查和 `git diff --check`。
- 未获授权不提交、推送或远端建仓。许可为 PolyForm Noncommercial 1.0.0，保留第三方许可。
