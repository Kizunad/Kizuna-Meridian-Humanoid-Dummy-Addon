# 整合接口

## Threatening 物种注册

假人通过 Vanilla-Humanoid-Threatening 的公开数据驱动注册点接入，不复制宿主代码：

- `HumanoidSpeciesDefinitions.registerAll(String toml)`：解析物种表并登记，实体 ID、模板、默认档位、画像、死亡规则与大脑开关全部来自数据；
- `HumanoidSpecies.fromEntityId`：登记后假人实体与原版类人一样由 Threatening 认领为身体宿主；
- `HumanoidSpeciesBirths.birth(entity, tier)`：按档位出生身体，凡人档由生成器在游炁境界下不开辟任何经脉；
- `HumanoidTier.of(BodySnapshot)`：从身体快照读回档位，命令反馈与测试都用它。

注册格式见 Threatening Wiki 的「物种注册」页。死亡规则 `never` 让 Threatening 在 Human 伤害结算后清除致死标记，
所以 Framework 不会进入死亡流程。`brain = false` 使 `HumanoidSpecies.brainManaged()` 为假，
Threatening 的类人大脑不为假人决策、不装 Goal，也不改它的原版控制。

## Damage 观察事件

`VanillaDamageFabric.registerDamageObserver` 在身体事务提交后通知只读事实：目标、攻击者、原版伤害源、
剂量输入（类型、原版剂量、护甲后剂量、预选命中）与提交回执（前后快照、健康结果）。
观察方抛出的异常由 Damage 隔离，不改变结算。本模组用它构建击打报告。

## Framework 重置

`reset` 先用宿主检视请求取当前快照，由 `DummyResetPlan` 计算部位、组织与器官到上限的治疗剂量，
再逐条以带 `expectedRevision` 的 Framework 健康事务提交；每条提交前重新检视，修订号冲突时报错，不覆盖并发写入。
