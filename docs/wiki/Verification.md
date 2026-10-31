# 构建与验证

Java 17、Gradle 8.14.6 wrapper、Loom 1.13.6，版本取自 Framework 的版本目录。Framework、Threatening 与 Damage 在 `settings.gradle` 中按
Framework 统一装配清单以源码组合，编译与开发运行使用其当前输出；客户端测试所需的 Human 客户端库按清单读已构建的 JAR。

```bash
./gradlew test build :fabric:runGametest
xvfb-run -a -s "-screen 0 1280x960x24" ./gradlew :fabric:runClientTest
python3 scripts/check_docs.py
git diff --check
```

`runClientTest` 复用 GameTest 生成的平坦世界（`build/gametest/world` 复制为单人存档 `dummy-hud-test`），
截图写到 `fabric/build/hud-screens/`，结论写到 `fabric/build/clienttest-results.txt`，首行不是 `success=true` 时任务失败。

## JUnit（`server/`）

- `HitReportsTest`：报告带出命中部位、类型、剂量、受伤组织与失能部位；组织命中归到所属部位；
  命中部位解析优先蔓延源，其次第一条扣除目标；结构外命中被拒绝；编码往返；长度、版本、未知字段与越界数值被拒绝；
  超长名称与极端剂量在构建时截断；扣除不足 0.005 的组织不进清单，校验也拒收这样的条目。
- `HitAmountsTest`：扣减与剂量不小于 0.1 保留一位小数，更小的保留两位；0.005 是列出门槛。

## GameTest（`fabric/src/gametest`）

| 用例 | 断言 |
| --- | --- |
| `itemPlacesDefaultTierDummyFacingThePlacer` | 物品右键放置恰好一个假人并消耗一份，面朝放置者，档位为游炁，身体是 Human 成人构型，类人大脑不接管 |
| `creativeGroupHoldsOnlyTheDummy` | 独立创造栏只有假人物品 |
| `swordHitOnLeftArmSendsOneMatchingReport` | 每个种子换一个新出生的假人，铁剑命中左上臂恰好产生一条报告：部位左上臂、类型切割、护甲前与护甲后剂量等于 Damage 观察到的输入，受伤清单里没有不足 0.005 的扣减 |
| `dummyBelowHumanDeathLineStaysAlive` | 连续重击使部位归零、总 HP 低于人体死亡线，20 tick 后假人仍存活、未移除、原版生命值为正 |
| `sneakingEmptyHandPicksTheDummyUp` | 潜行空手右键移除假人并得到一个物品 |
| `vanillaKillRemovesOnlyDummiesInsideTheRadius` | 原版 `kill @e[type=meridian_humanoid_dummy:dummy,distance=..2]` 移除半径内的两个，保留半径外的一个 |
| `mortalSpawnHasNoOpenedMeridianAndRealmCommandRebirths` | `/meridian humanoid spawn meridian_humanoid_dummy:dummy mortal` 生成凡人档且无已开经脉；`/meridian humanoid realm <uuid> true_qi` 在原位置换成真炁档 |
| `spawnCommandPlacesDefaultTierDummyFacingTheSource` | 命令源朝向 90° 时 `/meridian humanoid spawn meridian_humanoid_dummy:dummy` 生成的假人面朝命令源，档位为物种表默认的游炁，类人大脑不接管 |
| `realmCommandCompletesAndRequiresPermissionTwo` | `/meridian humanoid` 下补全到 `realm`，`spawn` 物种补全含假人；档位补全为 `mortal`、`wandering_qi`、`true_qi`；权限 1 看不到 `realm` 也不能换档；未知档位与非假人目标被拒绝；旧根 `/meridian_humanoid_dummy` 不存在 |

## Xvfb 客户端测试（`fabric/src/clientTest`）

单人世界中由服务端线程持铁剑击中视线前方的假人，报告经正式网络包到达 HUD。
任务启动前在运行目录写入 `options.txt`（`lang:zh_cn`），客户端以简体中文启动：

- 命中后 HUD 至少 3 行，截图 `hud-visible.png`，文本另存为 `hud-visible.txt`；
- 客户端语言是 `zh_cn`；HUD 文本没有拉丁字母、没有「0.0」「0.00」这样的零值扣减，并含「护甲前」「护甲后」「受伤：」；
- 3 秒时仍至少 3 行；4 秒加 250 毫秒后为 0 行，截图 `hud-hidden.png`；
- 两张截图右上角面板区域的平均亮度差不低于 10，证明 HUD 确实画在屏幕上。

## 最近一次结果（2026-10-30）

- `./gradlew test build :fabric:runGametest`：JUnit 15/15（`HitReportsTest` 9、`DummyResetPlanTest` 4、
  `HitAmountsTest` 2），GameTest 9/9。
- `xvfb-run ... :fabric:runClientTest`：`success=true`；简体中文客户端命中后 HUD 3 行，4.25 秒后为 0 行。
  三次运行的命中部位依次是左大腿、头部、左大腿；打在头部那次的 `hud-visible.txt` 为
  （脑和眼的扣减不足 0.1，按两位小数显示）：

  ```text
  头部 19.8/20.0（−0.2）
  切割 护甲前 0.2 → 护甲后 0.2
  受伤：头部皮肤 0.2、脑 0.01、左眼 0.01、右眼 0.01
  ```
- 依赖的是 Threatening 工作区中尚未提交的物种注册点与 Damage 工作区中尚未提交的观察事件；两仓提交前，
  本仓库只能对着它们当前的构建产物编译和测试。

## 2026-10-31：S2 指令整理

旧根 `/meridian_humanoid_dummy` 按 Framework 指令对照表删除：`spawn` 并入 Threatening 的 `/meridian humanoid spawn`（假人经放置钩子面朝命令源），
`clear` 改用原版 `/kill`，`reset` 删除，`realm` 挂到 `/meridian humanoid realm`，档位词表改为 `mortal`、`wandering_qi`、`true_qi`。
`DummyResetPlan`、档位名 `DummyRealm` 与 `DummyResetPlanTest`（4 项）随之删除，JUnit 15 → 11。
`swordHitOnLeftArmSendsOneMatchingReport` 原先每个种子前用 `reset` 回满同一个假人，现在每个种子换一个新出生的假人。
`resetCommandRestoresTheFullBody` 删除；替代写法 `/meridian body heal` 由 Debug 登记，Debug 落地后再补回满用例。

命令：先在 Framework、Human、Threatening 执行 `./gradlew assemble`（三仓均为最新），删除 `.gradle/loom-cache/remapped_mods` 后
`./gradlew test build :fabric:runGametest`；`ALSOFT_DRIVERS=null xvfb-run -a -s "-screen 0 1280x960x24" ./gradlew :fabric:runClientTest`；
`python3 scripts/check_docs.py`；`git diff --check`。

结果：JUnit 11/11（`HitReportsTest` 9、`HitAmountsTest` 2）；GameTest 9/9，加载 68 个模组；
clientTest `success=true`，加载 87 个模组，命中左小腿，HUD 3 行；文档检查通过（6 个页面）。
