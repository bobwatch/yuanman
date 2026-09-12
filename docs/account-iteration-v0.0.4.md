# 沅满记账 · 账户模块迭代 v0.0.4 —— 交互简化与对账记录化（2026-09 追加）

> 本文记录账户 Tab（v0.2/v0.3 基线）之后的**一轮产品反馈落地**：
> 目标不是加能力，而是**把已有能力讲人话**：入口更直白、层级更浅、
> 「对账」「攒钱计划」「发薪分配」全部从"功能正确"走向"看得懂、点得动"。
> 未提及的约定（token、弹层 chrome、金额格式、零类型语义、隐私掩码）一律沿用 v0.2/v0.3。

---

## 0. 需求清单 → 落地结论（与代码一一对应）

| # | 反馈 | 落地 |
|---|------|------|
| 1 | 小眼睛从「总资产」后改到「净资产」后 | hero 行2：眼睛内联净资产大数字右侧（同排 baseline）；整数位数 ≥8 自动降档防溢出 |
| 2 | 待核对 Tips 不吸顶、可滚动；关闭需二次确认"跳过本期" | 横幅移入 LazyColumn 首 item（随列表滚走）；X → AlertDialog 确认 → 每账户记录 `reconcileTipSkipUntil`（各自周期期末恢复，持久化） |
| 3 | 长按账户 → sheet 菜单；点击 → 账户页展示对账记录 | 账户行：点击 → **新增 AccountDetailScreen**（对账记录/周期/操作）；长按（combinedClickable）→ 原 AccountActionSheet |
| 4 | 计划详情页 + 攒钱/取出记录可删 + 改名称金额（自设计）；删管理入口，管理钮改新建计划 | **新增 PlanDetailScreen**（进度卡/再存·取出/记录删除/编辑计划/删除计划）；账户 Tab 区块头「管理 ›」→「＋ 新建计划」直接开新建表单；**删除 SavingPlansScreen 二级页**（方案编辑入口迁入发薪页） |
| 5 | 发薪分配交互看不懂 | PaycheckRunScreen 重排为**两步卡 + 单确认键**：① 这次到账多少钱（选工资入账/手填/选来源账户，实时"将用 X 分配 ¥Y"）② 钱怎么分（方案摘要+预览，方案本页即改即存）；未设方案给「去设置」直开编辑器，不再引导去别的页 |
| 6 | 计划小卡百分比展示布局 | 达成 % 从「已攒金额行尾内联」提升为**进度条同行行尾**的主级信息（primary bold）；金额行后续与目标合并为单行（详见 §4 增量） |
| 7 | 选专款账户不显示金额 | PlanFormSheet 账户 chips 只显示名称（原带余额）；余额只保留在转账等资金动账场景 |
| 8 | 「撤回专款」→「取出一笔」 | 全部 UI 文案/Toast 改名：取出金额 / 可取出 / 全部取出 / 已取出（VM 方法名保留 deposit/withdraw 语义） |
| 9 | ⋮ 里的发薪分配/账户核对 → 总资产行右侧 | hero 行3：总资产金额左 + 行尾两颗入口胶囊（Savings / AccountBalanceWallet）；待还负债独立成行（仅 ≠0），⋮ 菜单整体移除 |
| 10 | 核对页去列表按钮、整行点击对账；支持设周期；每期记录保存/删除 | 见 §1（数据模型）/§3（核对页） |

---

## 1. 数据模型（对账记录化 + 周期）

### 1.1 对账周期（用户确认的方案：全局默认 + 账户自定义覆盖）

- 周期 = **数量 × 单位**：周 / 月 / 季度(3月) / 半年(6月) / 年(12月)，默认**每月**。
- 全局默认存 PreferencesRepository 新键 `reconcile_cycle_data`（`{count, unit}` JSON）。
- 账户 JSON 新增可选 `reconcileCycleOverride`；**账户自定义优先级最高**（effective = override ?: global）。
- 自定义步进像进制一样**进位升位**：月族以总月数为最小粒度，+12 个月自动升为「每年」继续累加；换单位 chip 仅当可整除时等价换算（6 个月 ⇄ 每半年）。快捷档覆盖常用档位。
- 状态口径（`accountReconcileStatus` 周期化，纯函数）：
  - 月族按自然月差 d、周族按 7 天桶差 d；周期长度 K（周数或总月数）
  - d < K → FRESH（月/周×1 时文案 = 本月/本周已核对，其余 = 本周期已核对）
  - K ≤ d < 2K → NORMAL（上月/上周/上周期已核对）
  - d ≥ 2K → OVERDUE（`n 期未对账`，n = d/K，错过 ≥1 整期后提醒）
  - 从未 → NEVER。横幅/待核对集合 = OVERDUE ∪ NEVER。
- 向后兼容：旧 JSON 无记录只有 `lastReconciledAt` 时直接按该字段判定（展示层与种子账户零迁移）。

### 1.2 每期对账记录（审计痕迹，不参与余额计算）

账户 JSON 新增 `reconcileRecords[]`（asOfDate 降序）：`{id, asOfDate, actualBalanceCents, bookBalanceCents, diffCents, corrected}`；
每次对账追加一条并清「跳过提醒」；**最近核对时间/差额 = 最新一条记录派生**（无记录回退旧字段）。
删除记录（账户详情页，ConfirmDeleteDialog）→ 最近核对回退到剩余最新一条，无记录即回到「从未对账」；
**当时已并入期初的校正不撤销**（文案已说明）。

### 1.3 攒钱/取出事件流（计划详情页记录）

计划 JSON 新增 `events[]`（at 升序）：`{id, kind: DEPOSIT|WITHDRAW, amountCents, at, note?}`。
earmark 仍为权威值；每次 再存/取出/发薪自动攒入 追加一条事件（发薪攒入 note=「发薪分配」）；
删除事件按相反方向回滚 earmark（删攒入 → 减；删取出 → 加回，可能进入既有超额警示态）。

### 1.4 新增/修改文件

| 文件 | 动作 |
|---|---|
| AccountReconcileStatus.kt | 重写：周期模型/记录模型/JSON 序列化/周期化状态判定/periodEndEpoch |
| ReconcileCyclePickerSheet.kt | 新增：对账周期选择 sheet（快捷档 + 自定义进位） |
| AccountDetailScreen.kt | 新增：账户详情（记录+周期覆盖+操作），nav `SecondaryScreen.AccountDetail(accountId)` |
| PlanDetailScreen.kt | 新增：计划详情（记录+编辑+删除），nav `SecondaryScreen.PlanDetail(planId)` |
| SavingPlansScreen.kt | **删除**（管理页）；`SecondaryScreen.SavingPlans` 移除 |
| AccountHero.kt / AccountScreen.kt / AccountReconcileScreen.kt / PaycheckRunScreen.kt | 重构（对应需求 1/2/5/9/10） |
| PlanOverviewComponents.kt / PlanManageSheets.kt | 区块入口与小卡布局；文案（取出一笔）与 chips（去金额） |
| AccountViewModel.kt / SavingPlanModels.kt / PreferencesRepository.kt | 数据层扩展（§1） |
| AccountListComponents.kt / AccountActionPanels.kt / AccountReconcileDialog.kt | 状态来源统一为预计算 reconcileStatus；长按；文案去「月度」化 |

---

## 2. 关键交互设计决定（本轮"你来设计"的部分）

1. **计划编辑入口**：不做悬浮菜单——计划详情页顶部进度卡自解释，页面内
   「再存一笔 / 取出一笔」两个主按钮，管理卡两行（编辑计划 / 删除计划 error）；
   编辑复用 PlanFormSheet（名称 / 目标金额(0=不设上限) / 专款账户 / 主题色）。
2. **发薪分配**：两步卡 + 恒显确认按钮与"下一步提示"文案（先填金额→选账户→设方案 的引导链），
   方案编辑器从被删的攒钱计划页**迁入本页**，全程不跳页。
3. **横幅跳过语义**：跳过的是"本周期提醒"——按各账户生效周期期末静默，到期仍未对账则提醒恢复；
   对账成功自动清除跳过状态。
4. **账户行交互分层**：点击 = 进详情（低频浏览+记录管理）；长按 = 快捷操作面板（转账/对账/编辑/删除）。
   删除账户的"专款占用拦截"守卫在两条路径（面板/详情页）均有。

---

## 3. 待观察 / 已知取舍

- hero 行3 入口胶囊在**极大总资产**（≥ ¥9,999,999.99）时可能与胶囊贴边；金额行已做降档策略，极端值可后续加 万/亿 缩写。
- 账户自定义周期切换到更短周期会立刻把状态推向 OVERDUE——属预期（口径即判定），无历史迁移。
- 记录删除为软语义（JSON 内直接移除），不做撤销；与全局删除惯例一致（删除前均有二次确认）。
- 发薪预览文案改为白话（转/攒/还清欠款/剩余留在账户），引擎 note（触顶/跳过原因）仍保留在次要行。

*文档版本 v0.0.4 · 2026-09 · 基线 feature/v0.0.4*

---

## 4. v0.0.4.1 增量调整（2026-09-08，用户反馈第二轮）

| # | 反馈 | 落地 |
|---|------|------|
| 1 | 简化命名 | 「再存一笔 → 存一笔」「取出一笔 → 取一笔」：计划详情主按钮、存取 sheet CTA（存一笔/取一笔）、表单标签（存入金额/取出金额）、上限提示（还可存/可取出）、toast（已存/已取）、事件记录动作名与删除确认均同步；历史注释保留原名说明 |
| 2 | 去掉计划行尾「新建计划」卡片 | LazyRow 末尾 PlansGhostMiniCard 及其调用整体移除；新建入口收敛到区块头「＋ 新建计划」（空计划态仍有全宽幽灵卡） |
| 3 | 小卡更紧凑 + 金额合并一行 | 小卡 152×104 → 152×96；进度+% 与金额行整体贴底；「已攒 ¥x / 目标 ¥y」合并为单行收底（目标=0 → 「已攒 ¥x · 无上限」）；为容纳单行金额引入 `MoneyUtils.centsToCompactYuan`（整元不带小数，有角分才保留两位），仅小卡使用，其余金额仍两位小数 |
| 4 | 去除账户选择小圆点 | 专款账户 chips 选中态去掉彩色圆点，仅保留 ✓；chip 仍不显示余额 |

*文档版本 v0.0.4.1 · 2026-09-08 · 基线 feature/v0.0.4*

---

## 5. v0.0.4.2 增量调整（2026-09-08，第三轮反馈）

| # | 反馈 | 落地 |
|---|------|------|
| 1 | 不再需要账户页 mock 数据 | 移除 `AccountViewModel.getDefaultSeedAccounts()`（微信支付/支付宝/招行储蓄卡等演示账户）及其在空数据时的回退注入；账户数据为空（首启/清空）时直接展示空态「创建第一个账户」，引导真实创建 |

*文档版本 v0.0.4.2 · 2026-09-08 · 基线 feature/v0.0.4*

---

## 6. v0.0.4.3 增量（2026-09-08，产品确认）

**攒钱计划长按 → 快捷操作面板**（与账户行「点击详情 / 长按操作」同构）：

```
● 旅行基金                    ✓(达标)
招商银行储蓄卡 · 已圈 ¥8,160      ← 超额时琥珀「余额低于专款，请补回」
──────────────────────────
⊕ 存一笔        （primary）      → PlanDepositSheet
⊖ 取一笔        （常规）          → PlanWithdrawSheet
──────────────────────────
✎ 编辑计划                     → PlanFormSheet（名称/目标/专款账户/色盘）
──────────────────────────
🗑 删除计划        （error 赤红） → ConfirmDeleteDialog 二次确认
```

- 改动面：`PlanMiniCard` 由 clickable 改 combinedClickable（长按回调）；新增 `PlanQuickActionSheet`（PlanManageSheets.kt）；`AccountScreen` 增面板/存/取/编辑/删除状态与弹层接线；账户 Tab 计划表单升级为「新建/编辑」共用。
- 计划详情页与账户页各入口动作共用同一批 sheet/对话框，无重复实现。

*文档版本 v0.0.4.3 · 2026-09-08 · 基线 feature/v0.0.4*

---

## 7. v0.0.4.4 增量调整（2026-09-08，细节三连）

| # | 反馈 | 落地 |
|---|------|------|
| 1 | 去除「新建攒钱计划 · 先创建账户」入口 | 账户为空时页面不再渲染攒钱计划区块（头部 + 禁用宽幽灵卡）——无账户即无可建计划；`PlansWideGhostCard` 移除 `enabled=false` 弱提示分支，仅保留空计划态（有账户时）的「新建攒钱计划」宽幽灵卡 |
| 2 | 发薪分配/账户核对入口固定右侧 | hero 行3 重构：金额区整体占据弹性宽度（weight 1f），入口胶囊恒定锚定行右侧，不随总资产金额长短漂移；极端超长金额在金额区内省略 |
| 3 | 计划名与进度条间距过大 | 小卡改为自上而下紧凑排列（名称 → 3dp → 进度条+% → 3dp → 金额单行），弹性余量统一收在金额行之下，消除名称与进度条之间的空档 |

*文档版本 v0.0.4.4 · 2026-09-08 · 基线 feature/v0.0.4*

---

## 8. v0.0.4.5 产品形态变更：发薪分配 = 预置规则，工资到账自动分账（2026-09）

**需求**：发薪分配应当是「预分配规则」，薪资一记账到账就自动分配到各账户。
**产品决策（用户确认）**：触发口径 = 仅「工资」类收入（收入分类名含「工资」或「薪」字样，覆盖默认「工资」分类与用户自建的「月薪/薪资/薪酬」等）；保存即自动执行；原手动向导页改为「规则 + 开关 + 记录 + 手动补分」。

### 8.1 流程

```
用户提前配置：分配规则（转固定/转比例→账户、攒固定/攒比例→计划、剩余自动清欠）
          ↓
记账保存「工资」入账（记账页 / 首页快捷记账 均触发）
          ↓ 自动执行（开关 paycheck_auto_enabled 默认开）
转账户 / 进计划 earmark+事件 / 自动清欠 → 记录写分账历史
          ↓
记账页 toast：「已按发薪规则自动分账 N 笔动作」；账户余额与计划立即可见
          ↓ 异常分支
支付方式未匹配到账户 → 不执行并提示，可在「发薪分配 → 手动补分」处理
规则为空且未开清欠 → 静默（该笔工资仍标记为已处理，规则后配不追溯旧工资）
```

### 8.2 工程落点

| 层 | 内容 |
|---|---|
| 共享编解码 | 新增 `AccountDataCodec.kt`：账户 JSON 序列化/解析、支付方式匹配、`enrichAccountsForMonth`（当月富化+对账派生）、`isSalaryCategoryName`；AccountViewModel / HomeViewModel / AddEditRecordViewModel 与自动执行器共用，消除口径漂移 |
| 共享执行器 | 新增 `PaycheckAutoRun.kt`（`PaycheckExecutor`）：`onSalaryRecordSaved`（自动触发：开关→幂等→匹配来源→执行）、`runManual`（手动补分/旧 executePaycheck 委托同一路径）、`applyOnce` 落库（账户 in/out、计划事件「发薪分配」、lastRun+历史） |
| 持久化 | Preferences 新增三键：`paycheck_auto_enabled`（布尔，默认开）、`paycheck_auto_applied_ids`（幂等集合）、`paycheck_run_history_data`（最近 30 次 JSON）；`PaycheckLastRunUiModel` 增 recordId/source/auto 字段（旧 JSON 兼容） |
| 记账钩子 | `AddEditRecordViewModel.saveRecord` 单笔插入返回 id；工资类收入保存后调用执行器；连记模式把结果拼进气泡文案、单笔模式 toast 提示。`HomeViewModel.saveQuickEntry`（首页快捷记账）同款静默触发 |
| 页面 | `PaycheckRunScreen` 重做：① 自动分账开关卡 ② 分配规则卡（即改即存 → SchemeEditorSheet）③ 分账记录卡（自动/手动徽标、金额、留存）④ 手动补分 sheet（本月未自动分账的工资候选 + 手填金额 + 来源选择） |
| 幂等/回溯语义 | 同一笔工资只执行一次（applied ids）；编辑旧记录、删除工资不触发也不回滚（与转账一致）；规则变更不追溯此前已入账的工资 |

*文档版本 v0.0.4.5 · 2026-09-08 · 基线 feature/v0.0.4*
