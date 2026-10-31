# 训练假人

模组命名空间为 `meridian_humanoid_dummy`，实体 `meridian_humanoid_dummy:dummy`「训练假人」。
假人是两足灰色人形（无脸、无衣着），没有寻路、攻击、转头与击退，不会自然消失，可以被推动；放置时面朝放置者。

- [使用方法](Usage.md)：物品、放置与收回、命令、档位。
- [击打报告协议](Protocol.md)：S2C Protobuf 字段、范围校验与 HUD 规则。
- [整合接口](Integration.md)：Threatening 物种注册点与放置钩子、Damage 观察事件与 Framework 指令注册点。
- [构建与验证](Verification.md)：构建命令、GameTest 与 Xvfb 客户端测试覆盖。

## 依赖

Framework、Human、Vanilla-Humanoid-Threatening、Vanilla-Damage 与 Players-Animation-Mobs 都是必装依赖，
没有「未安装」时的替代路径。Framework 协议与 Threatening 纯规则由宿主 JAR 嵌套提供，本模组只嵌套自己的协议与报告规则。

## 身体与死亡

- 物种表 `meridian-humanoid-dummy-species.toml` 在初始化时登记到 Threatening：模板 `human:adult`，默认档位游炁，死亡规则 `never`，
  `brain = false`。
- 物品放置、`/meridian humanoid spawn` 与 `realm` 都经 Threatening 的公开出生入口生成身体；凡人档是游炁境界下一条经脉都未开。
- 所有伤害走 Damage → Framework 身体事务；`never` 规则让结算结果不再标记致死，部位可以停在 0 HP，原版生命值保持为正。
- 身体不自然回复。
- 放置不从区域真元池扣除，收回、清除与换档移除实体时按 Threatening 的规则把身体真元归还区域，与刷怪蛋一致。

## 源码分层

`shared/` 只放击打报告 Protobuf；`server/` 是与 Minecraft 无关的报告构建与有界校验；
`fabric/` 负责实体、物品、创造栏、命令、网络推送与客户端渲染、HUD。
本模组不含 AI。物种表的 `brain = false` 让 Threatening 的类人大脑不接管假人；以后给假人接大脑时改这一项。
