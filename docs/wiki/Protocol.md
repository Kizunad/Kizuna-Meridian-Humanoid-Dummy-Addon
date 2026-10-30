# 击打报告协议

通道 `meridian_humanoid_dummy:hit_report`，服务端 → 造成伤害的玩家，载荷为 `kizuna.meridian.dummy.v1.HitReport` 的 Protobuf 编码。
契约文件 `shared/src/main/proto/kizuna/meridian/dummy/v1/hit_report.proto`。

## 何时发送

服务端订阅 Vanilla-Damage 的 `registerDamageObserver`。目标是训练假人、攻击者是玩家且客户端声明能接收该通道时发送一条；
护甲与附效后剂量为 0 的命中 Damage 不提交身体事务，也就没有报告。数值全部取自 Damage 提交后的固定回执与前后快照：

- 命中部位：Damage 预选的命中目标；未预选时取 Framework 结算里的蔓延源，否则取第一条扣除目标。
- 部位 HP 与扣除：提交后快照与提交前快照之差。
- 受伤列表：组织、器官与蔓延部位的扣除，按扣除量降序；扣除不足 0.005 的不列出。

## 字段

| 字段 | 编号 | 类型 | 范围与含义 |
| --- | --- | --- | --- |
| `protocol_version` | 1 | int32 | 当前为 1，其他值拒收 |
| `part_name` | 2 | string | 命中部位的 Human 显示名，1..32 个字符 |
| `part_hp` | 3 | double | 命中后剩余 HP，0..`part_max_hp` |
| `part_max_hp` | 4 | double | 命中部位上限，正数 |
| `part_loss` | 5 | double | 本次部位实际扣除，0..`part_max_hp` |
| `damage_kind` | 6 | enum DamageKind | 与 Framework 伤害机制同名同序，不能为 UNSPECIFIED |
| `vanilla_dose` | 7 | double | Damage 日志「原版」剂量，HUD 标作「护甲前」：肌肉系数、附效与护甲之前 |
| `dose_after_armor` | 8 | double | Damage 日志「护甲后」剂量：进入身体结算的剂量 |
| `injuries` | 9 | repeated Injury | 0..32 条，按扣除量降序；超出只保留最多的 32 条 |
| `part_changes` | 10 | repeated PartChange | 0..32 条，只列状态变化的部位 |

`Injury`：`name`（1..32 字符）、`hp`（本次扣除，不小于 0.005）、`module`（PART / TISSUE / ORGAN）、`spillover`（来自命中部位溢出的蔓延）。

`PartChange`：`part_name`（1..32 字符）、`before`、`after`（USABLE / DISABLED，二者必须不同且不为 UNSPECIFIED）。

## 有界校验

两端都调用同一份 `HitReports.validate`：

- 载荷上限 16 KiB；客户端先按字节数拒收，再复制、解码。
- 所有数值必须是有限数，且不超过 1e6；字符串不能为空、不超过 32 个字符。
- 列表长度不超过 32；枚举不能为 UNSPECIFIED 或未知值。
- 解码与校验在客户端主线程完成；连接已经切换时丢弃，校验失败只记日志，不影响 HUD 中已有条目。
