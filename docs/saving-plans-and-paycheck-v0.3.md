# 沅满记账 · 攒钱计划 & 发薪分配 —— 设计方案 v0.3（追加于账户页 v0.2）

> 追加需求：「增加设置多个攒钱计划」「支持发薪分配」。
> 本文为账户 Tab 重构 v0.2 的增量设计，只描述新增部分；未提及的约定（token、弹层 chrome、下拉刷新、金额格式、零类型语义）一律沿用 v0.2。

---

## 0. 产品目标与设计论点

账户页 v0.2 回答「我现在有多少钱、钱在哪儿」。v0.3 追加回答两个新问题：

1. **我在为什么攒钱，攒到哪了** —— 多个用户自建「攒钱计划」（目标金额 + 进度），钱不用真的搬走；
2. **工资到手后钱怎么分** —— 一份用户自定的「发薪分配方案」，选一笔工资流水（或手填金额）一键执行：自动转账户、自动进计划、自动清负账户。

克制原则不变：**不新增资金主体**。计划不持有真钱，只是从某个账户「圈定」一笔专款（earmark）——账户余额、总资产、对账口径全部不变，避免引入第二套账本。计划只回答"圈了多少钱"。

### 0.1 基线决策（用户未答复询问，按以下默认实现，均可低成本回改）

| # | 决策点 | 基线 | 理由 |
|---|--------|------|------|
| D1 | 计划资金模型 | **虚拟专款圈定**：计划绑定一个「专款账户」，已圈金额 = Σ 该账户内圈定；总资产不变 | 与"账户才持有资金"的现有模型一致；对账/负债/明细零影响；随时可改可撤 |
| D2 | 发薪触发 | **账户页入口 → 选本月工资流水（或手填）→ 预览 → 一键执行** | 金额可溯、动作可见；不与记账页做事件联动（成本高且打断记账流） |
| D3 | 方案规则 | 支持 **固定额 / 比例 × 转账户 / 进计划** 四种；比例基数 = 本次到手金额 S；按规则列表顺序执行 | 覆盖"先还花呗、再转储蓄、按比例进计划"等常见组合 |
| D4 | 负账户处理 | 方案级开关「剩余自动清欠」**默认开**：规则执行完后若仍有剩余 → 按账户顺序还清负余额账户；再剩则留存来源账户；显式还款 = 添加一条向该账户的转账户规则即可 | 欠款自动优先但不越权 |
| D5 | 纪律边界 | ① 单账户全部专款之和 ≤ 账户余额（超圈会提示"余额低于专款，请补回"，此时禁再存）；② 删除账户若被计划占用 → 拦截并提示先处理计划 | 防止专款与真实资金脱节 |

---

## 1. 数据模型（新增，全部落 PreferencesRepository DataStore JSON）

```kotlin
// 攒钱计划
data class SavingPlanUiModel(
    val id: Long,
    val name: String,                    // 用户自定，如「旅行基金」
    val holderAccountId: Long,           // 专款账户（钱实际所在）
    val targetAmountCents: Long,         // 目标；0 = 不设上限（无进度条）
    val earmarkedCents: Long,            // 已圈专款
    val colorHex: Long,                  // 取自 12 色盘
    val sortOrder: Int,
    val createdAt: Long
)

// 发薪方案 = 规则列表 + 开关
enum class PaycheckRuleKind { TO_ACCOUNT_FIXED, TO_ACCOUNT_PCT, TO_PLAN_FIXED, TO_PLAN_PCT }

data class PaycheckRuleUiModel(
    val kind: PaycheckRuleKind,
    val targetId: Long,                  // 账户 id 或计划 id
    val amountCents: Long = 0,           // FIXED 用
    val percentBps: Int = 0              // PCT 用，万分比（30% = 3000）
)

data class PaycheckSchemeUiModel(
    val rules: List<PaycheckRuleUiModel>,
    val autoClearDebts: Boolean = true   // 剩余自动清欠
)

data class PaycheckLastRunUiModel(
    val at: Long?, val amountCents: Long?, val actionCount: Int?, val remainingCents: Long?
)

// 本月工资收入候选（UI 选择用；不持久化）
data class IncomeCandidateUiModel(
    val recordId: Long, val amountCents: Long,
    val note: String?, val method: String, val at: Long,
    val matchedAccountId: Long?, val matchedAccountName: String?   // 按支付方式匹配（复用 v0.2 同名启发式）
)
```

### 口径与不变量（引擎必须满足，文档 §5 同表）

- **记账口径不变**：分配只做账户间转账（in/out 语义与现有 transfer 一致）+ 计划 earmark 累加；不写 RecordRepository，不产生收支流水，不影响对账基线（基线只认账户期初与流水）。
- E1：规则顺序执行；每步实际执行额 = min(计划额, 当前可用 S 剩余)。FIXED 先按列表序，PCT 同样按列表序，但 **PCT 基数恒为 S**（不是剩余）。
- E2：进计划额另受专款上限约束：执行后该计划 earmark ≤ 其账户可圈上限（见 D5①）；触顶则该步取到上限值。
- E3：自转无效（目标账户 == 来源账户 → 跳过，预览标"跳过"）。
- E4：剩余自动清欠：按账户 sortOrder 依次把负余额账户转到 0；钱不够则先还 sortOrder 小的。
- E5：最后真正剩余 → 留存来源账户（不动，记入 lastRun.remainingCents）。
- E6：来源账户 = 所选工资流水命中的账户；手填时用户自选来源账户。S = 流水金额（手填输入）。
- E7：预览与执行共用同一纯函数引擎（同一输入必然同一输出），预览所见即执行所得。

### 状态扩展（AccountUiState 追加字段）

```kotlin
val plans: List<SavingPlanUiModel> = emptyList(),
val paycheckScheme: PaycheckSchemeUiModel = PaycheckSchemeUiModel(emptyList()),
val paycheckLastRun: PaycheckLastRunUiModel = PaycheckLastRunUiModel(null, null, null, null),
val incomeCandidates: List<IncomeCandidateUiModel> = emptyList(),   // 本月收入记录 + 匹配账户
// 派生（不持久化，UI 直接可读）：
val holderEarmarkTotal: Map<Long, Long>,       // 每账户全部计划已圈和
val availableToEarmark: Map<Long, Long>,       // 账户余额 - 该账户已圈和（下限 0）
```

UI 可据此直接判：再存上限、超额警示（某计划 earmark > 账户余额 → 该计划状态 "余额低于专款 ¥x，请补回" 琥珀警示）、删除账户占用拦截。

---

## 2. 页面与交互

### 2.1 账户 Tab 布局（v0.2 → v0.3 增量）

```
┌ 净资产快照 hero（不变）
├ [待核对提醒横幅]（不变，有逾期才出现）
├───────────────────────────── 可滚动区 ↓
├ 发薪分配入口卡（accounts 非空才显示）── 44dp，surfaceVariant 半透明 r14：
│   icon 薪资/闪电 + 主句「发薪分配」 副句「上次：9月5日 · ¥12,000 · 3 笔动作」│›
│   （从未执行过 → 副句「配置方案，工资到手一键分账」）
├ 攒钱计划区块
│   ├ 区块头：主色圆点「攒钱计划 · 3」    右「管理 ›」
│   └ 横向滚动卡行（92dp 高）：
│       [计划小卡 152dp：彩点+名称 / 进度条 4dp / 已圈 ¥x · 目标 ¥y（达标=✓ 主色）]
│       [+ 新建计划 幽灵小卡（虚线）]
│       （计划 earmark > 账户余额 → 卡内琥珀警示行「低于专款 ¥x」）
├ 账户分组列表…（不变，含组头/账户卡/新建账户幽灵卡）
```

- 点击计划小卡 → **跳二级页「攒钱计划」**（不弹层，管理在页面内）；
- 点击「发薪分配」入口卡 → 弹 发薪流程 sheet（§2.3）；
- 空计划态：区块头 + 一行宽幽灵卡「+ 新建攒钱计划」→ 二级页；
- 隐私模式：区块内所有金额 → ••••（入口卡副句金额也掩码）；账户列表与 hero 行为不变。

### 2.2 二级页「攒钱计划」（新 SecondaryScreen.SavingPlans，预测性返回与现有二级页一致）

```
┌ 顶栏：返回 + 「攒钱计划」 + 右侧「新建」文字钮
├ 总进度卡（r18 surface）：Σ已圈 / Σ目标（目标全 0 时只显示 Σ已圈），4dp 总进度条，
│   下方 13.5sp 鼓励句（如「已为 3 个心愿圈出 ¥18,000」；超额任一计划时显示琥珀警示句）
├ 发薪分配方案入口卡（r16）：主句「发薪分配方案」+ 副句规则数（如「5 条规则 · 剩余自动清欠」）›
├ 计划纵向卡列表：
│   [彩点 10dp + 名称 15sp Semi（含达标 ✓ 徽）
│    专款账户 · 已圈 ¥x / 目标 ¥y（或仅已圈）· 进度条
│    右：余额状态行（正常=onSurfaceVariant；超额=琥珀「余额低于专款 ¥x」）]
│   卡点击 → 计划操作面板（§2.4）
├ 无计划空态：空态组件 + 「新建计划」CTA
└ 悬浮语义一致：金额隐私掩码跟随全局
```

### 2.3 发薪分配流程（账户页入口 → 一步 sheet）

Sheet 内三态递进（同一 YuanmanModalBottomSheet 内切换，无跳页）：

1. **选金额**
   - 本月收入候选列表行（单选）：`图标 + 「工资 ¥12,000」note（副行：9月5日 · 招商银行储蓄卡 · 微信/网银）`；
     - 候选匹配到账户 → 来源即该账户；未匹配 → 行副行提示「未关联账户」，选中后进入第 1.5 步手动选来源（横向账户 chips 单选）；
   - 分隔「或手动填写本次到手金额 ¥」输入框（输入时自动取消候选选中）；
   - 下方实时小字：`来源：招商银行储蓄卡 · 可分配 ¥12,000`（手填且未选来源时提示先选账户）。
2. **预览**（选定金额 + 方案 ≥1 规则才可进入；无方案 → 引导文案 + 按钮「去设置方案」跳二级页并关 sheet）：
   - 规则行逐条列出**实际将执行**的动作与金额（引擎 E7）：
     `转 ¥5,000 → 招商银行储蓄卡` / `攒 ¥1,000 → 旅行基金` / `转 30% = ¥3,600 → 支付宝` /
     `自动清欠 → 花呗/信用卡 ¥2,000`（开关开且实际发生才显示）/ `留存 ¥800`（E5 才显示）；
   - 触顶/跳过/余额不足的动作行降透明度并标注原因（如「计划触顶 ¥0」）；
3. **确认**：底部主按钮「确认分配」→ 执行 → sheet 关 → toast：`已分配 ¥12,000（5 笔动作）`。
   - 执行 = 一次写入账户 JSON + 计划 JSON + 方案 lastRun，全在 VM 单协程内顺序完成。

- 进入即需 S>0 且来源可确定；方案从未配置（0 规则）→ 直接给「去设置方案」引导，不展示空预览。

### 2.4 计划操作面板（二级页内，卡片点击升起，风格对齐账户操作面板）

行序：**再存一笔**（secondary）/ **撤回专款**（secondary）/ 分隔线 / **编辑计划** / **删除计划**（error）。
- 再存 sheet：金额输入 + 实时可用上限展示（`可再存 ¥x（账户余额 - 其他计划专款）`）+ 确认；
- 撤回 sheet：金额输入（上限 = 该计划已圈）+ 确认；
- 编辑 sheet：名称 / 目标金额（0=不设限）/ 专款账户单选 chips（换账户时若原账户有其他计划超额检查）/ 12 色盘；
- 删除：ConfirmDeleteDialog（删除即释放专款，无级联副作用）。

### 2.5 发薪方案编辑（二级页「发薪分配方案入口卡」→ sheet）

- 顶部开关行：「剩余自动清欠」（默认开）+ 说明 12sp outline；
- 规则列表：每行 `icon（转=SwapHoriz 蓝 / 攒=Savings 绿） + 描述（转 ¥5,000 → 账户名 / 转 30% → 账户名 / 攒 ¥1,000 → 计划名 / 攒 20% → 计划名） + 删除（error，尾部 18dp）`；
- 空方案引导行「还没有规则：每月工资到手后按下面顺序执行」；
- 底部两行添加器：第一行 FilterChip 组：`固定金额 | 百分比`（互斥）；第二行 FilterChip 组按第一行态列出 **账户** 或 **计划**（横向滚动单选）；选中后弹内联输入（固定额 ¥ / 百分比 %）→ 点「添加」将规则 append 到列表尾部；
- 保存：即改即存（每次变更写 DataStore），关 sheet 无需确认按钮；删除规则即时生效。

---

## 3. 边界与守卫（清单）

| 场景 | 行为 |
|------|------|
| 删除被计划占用的账户（账户页删除确认后） | 拦截：toast「「X」是攒钱计划「Y」的专款账户，请先处理该计划」；不删除 |
| 再存超过可圈上限 | 按钮置灰 + 上限文字提示 |
| 专款账户被花到低于已圈（超额） | 计划小卡/纵向卡琥珀警示「余额低于专款 ¥x」；再存上限为 0；总进度卡显示警示句 |
| 来源账户余额 < 各规则应转总和 | 按规则顺序实际执行，触顶动作在预览中标原因，S 按流水金额仍如实展示 |
| 计划目标 0（不设限） | 不显示进度条与百分比；显示已圈金额即可 |
| 账户为空（无任何账户） | 账户页不显示发薪入口卡；计划区块显示「先创建账户」弱提示 ghost 禁用 |
| 隐私模式 | 全部计划/发薪金额掩码 |

## 4. 文件映射（全部在 ui/screens/account 包内，与 v0.2 同包共享契约）

| 文件 | 内容 | 归属 |
|------|------|------|
| SavingPlanModels.kt（新增·契约锚点） | 上文 §1 全部模型 + `savingsOf(holderId)`/`planIsOverdrawn` 等纯函数 | 主 agent |
| AccountViewModel.kt（扩展） | 新状态字段 combine；CRUD：savePlan/deletePlan/depositToPlan/withdrawFromPlan/savePaycheckScheme；发薪执行；纯引擎 `planPaycheck(accounts, plans, scheme, sourceId, S)` 返回 `List<PaycheckActionUi>`；收入候选匹配 | 主 agent |
| PlanOverviewComponents.kt（新增） | 账户页区块：PaycheckEntryCard / PlansSectionHeader / PlanMiniCard / PlansGhostMiniCard（§2.1） | agent A |
| PaycheckFlowSheet.kt（新增） | 发薪流程三步 sheet（§2.3），消费 VM 预览结果 | agent B |
| SavingPlansScreen.kt + PlanManageSheets.kt（新增） | 二级页（§2.2）+ 计划操作面板/表单 sheets（§2.4）+ 方案编辑 sheet（§2.5） | agent C |
| AccountScreen.kt（改） | 区块插入与回调接线、删除账户占用拦截 | 主 agent |
| YuanmanNavGraph.kt（改） | SecondaryScreen.SavingPlans 路由 + SavingPlanScreen 组合处（工厂复用 AccountViewModel？→ 独立 viewModel(factory) 实例） | 主 agent |

## 5. 验收清单（实现完成对照）

- [ ] 模型/JSON 落库：计划增删改、earmark 增减、方案规则与开关、lastRun 四次读写闭环（重启保留）
- [ ] 计划 CRUD 全路径 UI：账户页建入口 → 二级页管理 → 返回账户页区块即时刷新（同 DataStore 流驱动，无需手动刷新）
- [ ] 专款口径：Σ计划 earmark ≤ 账户余额 恒成立（含换账户、删计划、删账户拦截、负余额账户场景）
- [ ] 发薪：选流水/手填 → 预览金额 = 执行金额（E7 一致性，逐一核对含触顶/清欠/留存三态）
- [ ] 发薪后：来源与目标账户余额、计划 earmark、lastRun 文案、toast 全部一致；不产生收支流水
- [ ] 账户余额/对账/hero 数值与 v0.2 行为零回归（分配只走 in/out 语义）
- [ ] 视觉：所有新增金额块走 MoneyUtils 千分位 2 位；r16/r14/4dp 条、1dp 细描边 alpha 0.25–0.35、无实心大色块；隐私掩码全覆盖
- [ ] 全量 `:app:compileDebugKotlin` BUILD SUCCESSFUL
