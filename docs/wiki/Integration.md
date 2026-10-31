# 整合接口

## Threatening 物种注册

假人通过 Vanilla-Humanoid-Threatening 的公开数据驱动注册点接入，不复制宿主代码：

- `HumanoidSpeciesDefinitions.registerAll(String toml)`：解析物种表并登记，实体 ID、模板、默认档位、画像、死亡规则与大脑开关全部来自数据；
- `HumanoidSpecies.fromEntityId`：登记后假人实体与原版类人一样由 Threatening 认领为身体宿主；
- `HumanoidSpeciesBirths.birth(entity, tier)`：按档位出生身体，凡人档由生成器在游炁境界下不开辟任何经脉；
- `HumanoidSpawns.registerPlacement(species, placement)`：`/meridian humanoid spawn` 生成假人时调用本模组的放置入口，
  假人按命令源水平朝向转半圈、面朝命令源，与物品放置同一条出生路径；
- `HumanoidTier.of(BodySnapshot)`：从身体快照读回档位，测试用它核对出生档位。

注册格式见 Threatening Wiki 的「物种注册」页。死亡规则 `never` 让 Threatening 在 Human 伤害结算后清除致死标记，
所以 Framework 不会进入死亡流程。`brain = false` 使 `HumanoidSpecies.brainManaged()` 为假，
Threatening 的类人大脑不为假人决策、不装 Goal，也不改它的原版控制。

## Damage 观察事件

`VanillaDamageFabric.registerDamageObserver` 在身体事务提交后通知只读事实：目标、攻击者、原版伤害源、
剂量输入（类型、原版剂量、护甲后剂量、预选命中）与提交回执（前后快照、健康结果）。
观察方抛出的异常由 Damage 隔离，不改变结算。本模组用它构建击打报告。

## Framework 指令注册点

`MeridianCommands.register(Branch.HUMANOID, "realm", GAMEMASTER, …)`：换档命令挂在 `/meridian humanoid` 下，
权限 2 由注册点套在 `realm` 节点上；档位词表取 `HumanoidTier` 的小写名，与 `spawn` 相同。
指令写法只在 [Framework 指令页](https://github.com/Kizunad/Kizuna-Meridian-Mod-Framework/wiki/Commands#humanoid)记录。
