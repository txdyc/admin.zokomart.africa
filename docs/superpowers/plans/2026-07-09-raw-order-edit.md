# Raw Order Row Edit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Edit any row in the Raw Orders list via an Edit action that opens a pre-filled modal form and saves through `PUT /api/raw-orders/{id}`.

**Architecture:** Backend adds `RawOrderUpdateDTO` (jakarta validation mirroring import rules), a `update` method on `RawOrderService`, a PUT endpoint gated by new perm `raw-order:update` (V16 menu seed). Frontend extends the shared `SchemaForm` with a `date` component, then adds an 操作 column + edit modal to the existing hand-rolled Raw Orders table page.

**Tech Stack:** SpringBoot 3.5 + MyBatis-Plus + Sa-Token + Flyway (backend); Vue3 + Ant Design Vue + vitest (frontend).

**Spec:** `docs/superpowers/specs/2026-07-09-raw-order-edit-design.md`

**Branches:** Continue on the existing `feat/raw-order-import` branch in BOTH repos (this builds on the unmerged import feature). Backend tasks run in `D:\GHANA\claude\admin.zokomart.africa\backend`, frontend tasks in `D:\GHANA\claude\admin.zokomart.africa\frontend`.

**Environment:** backend builds need `export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11"`; MySQL+Redis must be running for `mvn test` (integration tests log in as superadmin). Error codes: validation → `400`, not found → `404`.

**Ground truth about the shipped code (differs from the import plan — follow the code, not the old plan):**
- `views/order/raw/index.vue` does NOT use BasicTable; it hand-rolls `load()`/`query`/`dataSource`/`pagination` and exposes `searchForm, query, onSearch, onReset, importOpen, openImport`.
- `RawOrderImportModal` takes `visible` prop with `update:visible`/`ok` emits.
- `RawOrderApiTest` has helpers `token()`, `csv(String)`, constant `HEADER`, and `@Autowired RawOrderMapper rawOrderMapper`.

---

### Task 1: Backend — V16 migration (raw-order:update perm)

**Files:**
- Create: `backend/src/main/resources/db/migration/V16__raw_order_update_perm.sql`

- [ ] **Step 1: Write the migration**

```sql
-- ===========================================================================
-- V16: 原始订单行编辑权限。按钮 2068 raw-order:update（挂在 1117 原始订单页下）。
--      superadmin 通配自动可见；其它角色后续在角色管理里授权。
-- ===========================================================================
INSERT INTO sys_menu (id, parent_id, name, type, perm_code, route_path, component, icon, sort, visible, status, create_time, deleted, version) VALUES
(2068, 1117, '编辑原始订单', 3, 'raw-order:update', NULL, NULL, NULL, 3, 1, 1, NOW(), 0, 0);
```

- [ ] **Step 2: Commit**

```bash
cd "D:\GHANA\claude\admin.zokomart.africa\backend"
git add src/main/resources/db/migration/V16__raw_order_update_perm.sql
git commit -m "feat(raworder): V16 raw-order:update permission seed

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

(Flyway applies it on the next context boot — Task 2's test run.)

---

### Task 2: Backend — failing integration tests for update

**Files:**
- Modify: `backend/src/test/java/africa/zokomart/admin/raworder/RawOrderApiTest.java` (append two test methods inside the class; reuse existing helpers)

- [ ] **Step 1: Append the tests**

Add before the closing brace of `RawOrderApiTest`. Also add this helper used by both tests:

```java
    /** 合法更新载荷；tel 用于查询定位与清理。 */
    private static String updateJson(String tel, String status) {
        return """
                {"orderDate":"2026-07-03","brand":"Nasco","price":150.50,
                 "customerName":"Ama Updated","city":"Kumasi","address":"new addr",
                 "telephone":"%s","productName":"TV 43","productCode":"UPD-NEW",
                 "quantity":2,"status":"%s","cod":0.00,"freight":12.00,"balance":150.50}
                """.formatted(tel, status);
    }

    @Test
    void update_row_then_get_reflects_changes() throws Exception {
        String t = token();
        String tel = "0666" + System.currentTimeMillis();
        String body = HEADER + "\n"
                + "2026-07-01,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,UPD-A,1,PAID,100.00,10.00,0.00\n";
        mvc.perform(multipart("/api/raw-orders/import").file(csv(body)).header("Authorization", t))
                .andExpect(jsonPath("$.data.success").value(1));

        MvcResult r = mvc.perform(get("/api/raw-orders").header("Authorization", t).param("keyword", tel))
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        long id = om.readTree(r.getResponse().getContentAsString()).at("/data/records/0/id").asLong();

        mvc.perform(put("/api/raw-orders/" + id).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(updateJson(tel, "RECIPIENT_REFUSED")))
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/raw-orders").header("Authorization", t).param("keyword", tel))
                .andExpect(jsonPath("$.data.records[0].orderDate").value("2026-07-03"))
                .andExpect(jsonPath("$.data.records[0].brand").value("Nasco"))
                .andExpect(jsonPath("$.data.records[0].productCode").value("UPD-NEW"))
                .andExpect(jsonPath("$.data.records[0].quantity").value(2))
                .andExpect(jsonPath("$.data.records[0].status").value("RECIPIENT_REFUSED"))
                .andExpect(jsonPath("$.data.records[0].balance").value(150.50));

        rawOrderMapper.delete(new LambdaQueryWrapper<RawOrder>().eq(RawOrder::getTelephone, tel));
    }

    @Test
    void update_rejects_bad_status_blank_field_and_unknown_id() throws Exception {
        String t = token();
        String tel = "0667" + System.currentTimeMillis();
        String body = HEADER + "\n"
                + "2026-07-01,Hisense,100.00,Kofi,Accra,addr," + tel + ",TV 32,UPD-B,1,PAID,100.00,10.00,0.00\n";
        mvc.perform(multipart("/api/raw-orders/import").file(csv(body)).header("Authorization", t))
                .andExpect(jsonPath("$.data.success").value(1));
        MvcResult r = mvc.perform(get("/api/raw-orders").header("Authorization", t).param("keyword", tel)).andReturn();
        long id = om.readTree(r.getResponse().getContentAsString()).at("/data/records/0/id").asLong();

        // 非法状态 → 400
        mvc.perform(put("/api/raw-orders/" + id).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(updateJson(tel, "SHIPPED")))
                .andExpect(jsonPath("$.code").value(400));

        // 空 customerName → 400（jakarta 校验）
        mvc.perform(put("/api/raw-orders/" + id).header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(tel, "PAID").replace("\"Ama Updated\"", "\"\"")))
                .andExpect(jsonPath("$.code").value(400));

        // 不存在 id → 404
        mvc.perform(put("/api/raw-orders/999999999999999").header("Authorization", t)
                        .contentType(MediaType.APPLICATION_JSON).content(updateJson(tel, "PAID")))
                .andExpect(jsonPath("$.code").value(404));

        // 失败的更新均未落库：状态仍是导入时的 PAID、姓名未变
        mvc.perform(get("/api/raw-orders").header("Authorization", t).param("keyword", tel))
                .andExpect(jsonPath("$.data.records[0].status").value("PAID"))
                .andExpect(jsonPath("$.data.records[0].customerName").value("Kofi"));

        rawOrderMapper.delete(new LambdaQueryWrapper<RawOrder>().eq(RawOrder::getTelephone, tel));
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd "D:\GHANA\claude\admin.zokomart.africa\backend" && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn test -Dtest=RawOrderApiTest`
Expected: the 4 existing tests PASS; the 2 new tests FAIL (PUT returns 404-shaped response / `$.code` missing — no endpoint yet).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/africa/zokomart/admin/raworder/RawOrderApiTest.java
git commit -m "test(raworder): failing tests for row update endpoint

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Backend — DTO, service update, PUT endpoint

**Files:**
- Create: `backend/src/main/java/africa/zokomart/admin/module/raworder/dto/RawOrderUpdateDTO.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/raworder/service/RawOrderService.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/raworder/service/impl/RawOrderServiceImpl.java`
- Modify: `backend/src/main/java/africa/zokomart/admin/module/raworder/controller/RawOrderController.java`

- [ ] **Step 1: RawOrderUpdateDTO.java**

```java
package africa.zokomart.admin.module.raworder.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 原始订单行编辑入参：14 个业务字段全量必填，规则与 CSV 导入一致。 */
@Data
public class RawOrderUpdateDTO {

    @NotNull
    private LocalDate orderDate;

    @NotBlank
    @Size(max = 128)
    private String brand;

    @NotNull
    @DecimalMin("0")
    private BigDecimal price;

    @NotBlank
    @Size(max = 128)
    private String customerName;

    @NotBlank
    @Size(max = 128)
    private String city;

    @NotBlank
    @Size(max = 512)
    private String address;

    @NotBlank
    @Size(max = 32)
    private String telephone;

    @NotBlank
    @Size(max = 255)
    private String productName;

    @NotBlank
    @Size(max = 64)
    private String productCode;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotBlank
    private String status;

    @NotNull
    @DecimalMin("0")
    private BigDecimal cod;

    @NotNull
    @DecimalMin("0")
    private BigDecimal freight;

    @NotNull
    @DecimalMin("0")
    private BigDecimal balance;
}
```

- [ ] **Step 2: Add `update` to RawOrderService interface**

Add the import and method:

```java
import africa.zokomart.admin.module.raworder.dto.RawOrderUpdateDTO;
```

```java
    void update(Long id, RawOrderUpdateDTO dto);
```

- [ ] **Step 3: Implement in RawOrderServiceImpl**

Add imports:

```java
import africa.zokomart.admin.common.exception.BusinessException;
import africa.zokomart.admin.common.result.ResultCode;
import africa.zokomart.admin.module.raworder.constant.RawOrderStatus;
import africa.zokomart.admin.module.raworder.dto.RawOrderUpdateDTO;
```

Add method (BeanUtils is already imported for `toVo`):

```java
    @Override
    public void update(Long id, RawOrderUpdateDTO dto) {
        RawOrder existing = rawOrderMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "原始订单不存在");
        }
        if (!RawOrderStatus.ALL.contains(dto.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "status 非法: " + dto.getStatus());
        }
        // DTO 与实体同名字段全量覆盖；id/审计/version 不在 DTO 上，保持原值（乐观锁沿用查出的 version）
        BeanUtils.copyProperties(dto, existing);
        rawOrderMapper.updateById(existing);
    }
```

- [ ] **Step 4: Add PUT endpoint to RawOrderController**

Add imports:

```java
import africa.zokomart.admin.module.raworder.dto.RawOrderUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
```

Add method:

```java
    @PutMapping("/api/raw-orders/{id}")
    @SaCheckPermission("raw-order:update")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RawOrderUpdateDTO dto) {
        rawOrderService.update(id, dto);
        return Result.ok();
    }
```

- [ ] **Step 5: Run the test class, then the full suite**

Run: `mvn test -Dtest=RawOrderApiTest`
Expected: 6/6 PASS.
Run: `mvn test`
Expected: BUILD SUCCESS, no failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/africa/zokomart/admin/module/raworder
git commit -m "feat(raworder): PUT /api/raw-orders/{id} row update

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Frontend — SchemaForm `date` component (TDD)

**Files:**
- Modify: `frontend/tests/unit/schema-form.spec.ts`
- Modify: `frontend/src/components/SchemaForm.vue`

- [ ] **Step 1: Add the failing spec**

Append inside the existing `describe('SchemaForm', ...)` block:

```ts
  it('date 组件渲染 a-date-picker 并以 YYYY-MM-DD 字符串读写', () => {
    const wrapper = mount(SchemaForm, {
      props: {
        schema: [{ field: 'd', label: '日期', component: 'date' } as FormField],
        initial: { d: '2026-07-09' },
      },
    });
    expect(wrapper.findComponent({ name: 'ADatePicker' }).exists()).toBe(true);
    expect((wrapper.vm as any).getValues()).toEqual({ d: '2026-07-09' });
  });
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd "D:\GHANA\claude\admin.zokomart.africa\frontend" && pnpm vitest run tests/unit/schema-form.spec.ts`
Expected: new test FAILS — either a TS type error on `'date'` or `ADatePicker` not found (component type doesn't exist yet).

- [ ] **Step 3: Implement**

In `SchemaForm.vue`, extend the `FormField` type union:

```ts
  component: 'input' | 'password' | 'textarea' | 'number' | 'select' | 'switch' | 'imageUpload' | 'date';
```

Add to the template, after the `a-select` branch and before `a-switch`:

```html
      <a-date-picker
        v-else-if="f.component === 'date'"
        v-model:value="model[f.field]"
        value-format="YYYY-MM-DD"
        class="w-full"
        v-bind="f.props"
      />
```

- [ ] **Step 4: Run to verify it passes**

Run: `pnpm vitest run tests/unit/schema-form.spec.ts`
Expected: 3/3 PASS.

- [ ] **Step 5: Commit**

```bash
cd "D:\GHANA\claude\admin.zokomart.africa\frontend"
git add src/components/SchemaForm.vue tests/unit/schema-form.spec.ts
git commit -m "feat(components): SchemaForm date field (a-date-picker, YYYY-MM-DD)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Frontend — type, API function, i18n keys

**Files:**
- Modify: `frontend/src/types/order.d.ts`
- Modify: `frontend/src/api/order/rawOrder.ts`
- Modify: `frontend/src/locales/lang/en-US.ts`
- Modify: `frontend/src/locales/lang/zh-CN.ts`

- [ ] **Step 1: Add `RawOrderUpdateDTO` to types/order.d.ts**

Append at the end of the file:

```ts
export interface RawOrderUpdateDTO {
  orderDate: string;
  brand: string;
  price: number;
  customerName: string;
  city: string;
  address: string;
  telephone: string;
  productName: string;
  productCode: string;
  quantity: number;
  status: RawOrderStatus;
  cod: number;
  freight: number;
  balance: number;
}
```

- [ ] **Step 2: Add `apiRawOrderUpdate` to api/order/rawOrder.ts**

Update the type import and append the function:

```ts
import type { Id, PageResult } from '@/types/api';
import type { RawOrderVO, RawOrderQuery, RawOrderImportResult, RawOrderUpdateDTO } from '@/types/order';
```

```ts
export const apiRawOrderUpdate = (id: Id, dto: RawOrderUpdateDTO) =>
  http.put<void>(`/raw-orders/${id}`, dto);
```

- [ ] **Step 3: i18n — add `editTitle` inside the `rawOrder:` section of both locale files**

`en-US.ts` (keep key position after `importTitle`):

```ts
    editTitle: 'Edit Raw Order',
```

`zh-CN.ts` (same position):

```ts
    editTitle: '编辑原始订单',
```

- [ ] **Step 4: Commit**

```bash
git add src/types/order.d.ts src/api/order/rawOrder.ts src/locales/lang/en-US.ts src/locales/lang/zh-CN.ts
git commit -m "feat(order): raw order update API, DTO type, edit i18n

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Frontend — edit modal on the Raw Orders page (TDD)

**Files:**
- Modify: `frontend/tests/unit/raw-order-page.spec.ts`
- Modify: `frontend/src/views/order/raw/index.vue`

- [ ] **Step 1: Add failing page specs**

In `raw-order-page.spec.ts`: extend the API mock and stubs, and append two tests.

Replace the mock block with:

```ts
const apiRawOrderPage = vi.fn(async (..._a: any[]) => ({ records: [], total: 0, current: 1, size: 10 }));
const apiRawOrderUpdate = vi.fn(async (..._a: any[]) => undefined);
vi.mock('@/api/order/rawOrder', () => ({
  apiRawOrderPage: (...a: any[]) => apiRawOrderPage(...a),
  apiRawOrderImport: vi.fn(),
  apiRawOrderUpdate: (...a: any[]) => apiRawOrderUpdate(...a),
}));
```

Add `'a-modal': true, SchemaForm: true, 'a-table': true,` to the `stubs` object (keep the existing entries).

Append inside the `describe` block:

```ts
  const row = {
    id: '9007199254740993', orderDate: '2026-07-05', brand: 'Hisense', price: 899,
    customerName: 'Kwame', city: 'Accra', address: '1 Test Ave', telephone: '0599',
    productName: 'Washer W1', productCode: 'UI-A', quantity: 1, status: 'PAID' as const,
    cod: 899, freight: 30, balance: 0, createTime: null,
  };

  it('openEdit 预填 14 个业务字段并打开弹窗（不含 id）', async () => {
    const w = mountPage();
    await flushPromises();
    w.vm.openEdit(row);
    expect(w.vm.editOpen).toBe(true);
    expect(w.vm.editInitial).toMatchObject({ brand: 'Hisense', status: 'PAID', quantity: 1, orderDate: '2026-07-05' });
    expect(w.vm.editInitial.id).toBeUndefined();
  });

  it('onEditSubmit 校验通过后按行 id 调更新接口、关弹窗并刷新列表', async () => {
    const w = mountPage();
    await flushPromises();
    w.vm.openEdit(row);
    w.vm.editFormRef = {
      validate: async () => true,
      getValues: () => ({ ...w.vm.editInitial, brand: 'Nasco' }),
    } as any;
    apiRawOrderPage.mockClear();
    await w.vm.onEditSubmit();
    expect(apiRawOrderUpdate).toHaveBeenCalledTimes(1);
    expect(apiRawOrderUpdate.mock.calls[0][0]).toBe('9007199254740993');
    expect(apiRawOrderUpdate.mock.calls[0][1]).toMatchObject({ brand: 'Nasco' });
    expect(w.vm.editOpen).toBe(false);
    expect(apiRawOrderPage).toHaveBeenCalledTimes(1);
  });
```

- [ ] **Step 2: Run to verify they fail**

Run: `pnpm vitest run tests/unit/raw-order-page.spec.ts`
Expected: existing 4 tests PASS, new 2 FAIL (`openEdit is not a function`).

- [ ] **Step 3: Implement in index.vue**

Script changes — add imports:

```ts
import { message } from 'ant-design-vue';
import SchemaForm, { type FormField } from '@/components/SchemaForm.vue';
import { apiRawOrderPage, apiRawOrderUpdate } from '@/api/order/rawOrder';
import type { RawOrderVO, RawOrderStatus, RawOrderUpdateDTO } from '@/types/order';
import type { Id } from '@/types/api';
```

Add the 操作 column at the end of the `columns` array:

```ts
  { title: t('common.operation'), key: 'operation', fixed: 'right', width: 80 },
```

Add edit state + logic after the `statusLabel` declaration:

```ts
const statusOptions = computed(() =>
  (Object.keys(statusColor) as RawOrderStatus[]).map((s) => ({ label: statusLabel(s), value: s })),
);

const editOpen = ref(false);
const editingId = ref<Id | null>(null);
const editInitial = ref<Record<string, any>>({});
const editFormRef = ref<InstanceType<typeof SchemaForm>>();
const editSubmitting = ref(false);

const requiredInput = () => [{ required: true, message: t('common.pleaseInput') }];
const requiredSelect = () => [{ required: true, message: t('common.pleaseSelect') }];

const editSchema = computed<FormField[]>(() => [
  { field: 'orderDate', label: t('rawOrder.date'), component: 'date', rules: requiredSelect() },
  { field: 'brand', label: t('rawOrder.brand'), component: 'input', rules: requiredInput() },
  { field: 'productCode', label: t('rawOrder.productCode'), component: 'input', rules: requiredInput() },
  { field: 'productName', label: t('rawOrder.productName'), component: 'input', rules: requiredInput() },
  { field: 'quantity', label: t('rawOrder.quantity'), component: 'number', props: { min: 1, precision: 0 }, rules: requiredInput() },
  { field: 'price', label: t('rawOrder.price'), component: 'number', props: { min: 0, precision: 2 }, rules: requiredInput() },
  { field: 'customerName', label: t('rawOrder.customerName'), component: 'input', rules: requiredInput() },
  { field: 'telephone', label: t('rawOrder.telephone'), component: 'input', rules: requiredInput() },
  { field: 'city', label: t('rawOrder.city'), component: 'input', rules: requiredInput() },
  { field: 'address', label: t('rawOrder.address'), component: 'input', rules: requiredInput() },
  { field: 'status', label: t('common.status'), component: 'select', options: statusOptions.value, rules: requiredSelect() },
  { field: 'cod', label: t('rawOrder.cod'), component: 'number', props: { min: 0, precision: 2 }, rules: requiredInput() },
  { field: 'freight', label: t('rawOrder.freight'), component: 'number', props: { min: 0, precision: 2 }, rules: requiredInput() },
  { field: 'balance', label: t('rawOrder.balance'), component: 'number', props: { min: 0, precision: 2 }, rules: requiredInput() },
]);

function openEdit(row: RawOrderVO) {
  editingId.value = row.id;
  editInitial.value = {
    orderDate: row.orderDate,
    brand: row.brand,
    price: row.price,
    customerName: row.customerName,
    city: row.city,
    address: row.address,
    telephone: row.telephone,
    productName: row.productName,
    productCode: row.productCode,
    quantity: row.quantity,
    status: row.status,
    cod: row.cod,
    freight: row.freight,
    balance: row.balance,
  };
  editOpen.value = true;
}

async function onEditSubmit() {
  if (!editFormRef.value || !(await editFormRef.value.validate())) return;
  if (editingId.value == null) return;
  editSubmitting.value = true;
  try {
    await apiRawOrderUpdate(editingId.value, editFormRef.value.getValues() as RawOrderUpdateDTO);
    message.success(t('common.saveSuccess'));
    editOpen.value = false;
    load();
  } finally {
    editSubmitting.value = false;
  }
}
```

Extend `defineExpose`:

```ts
defineExpose({
  searchForm, query, onSearch, onReset, importOpen, openImport,
  editOpen, editInitial, editFormRef, openEdit, onEditSubmit,
});
```

Template changes — inside the `#bodyCell` template add an operation branch (after the `status` branch):

```html
          <template v-else-if="column.key === 'operation'">
            <a v-perm="'raw-order:update'" data-test="raw-order-edit" @click="openEdit(record as RawOrderVO)">
              {{ t('common.edit') }}
            </a>
          </template>
```

Add `:scroll="{ x: 1500 }"` to the `<a-table>` so the fixed-right column works while scrolling.

After the `<RawOrderImportModal ... />` line, add the edit modal:

```html
    <a-modal
      :open="editOpen"
      :title="t('rawOrder.editTitle')"
      :width="640"
      :confirm-loading="editSubmitting"
      @ok="onEditSubmit"
      @cancel="editOpen = false"
    >
      <SchemaForm ref="editFormRef" :schema="editSchema" :initial="editInitial" />
    </a-modal>
```

Also add `data-test="raw-order-import"` to the existing import button (missing `data-test` noted in verification):

```html
          <a-button v-perm="'raw-order:import'" type="primary" data-test="raw-order-import" @click="openImport">
```

- [ ] **Step 4: Run the page spec, then the full suite + build**

Run: `pnpm vitest run tests/unit/raw-order-page.spec.ts`
Expected: 6/6 PASS.
Run: `pnpm vitest run && pnpm build`
Expected: all tests pass, build succeeds.

- [ ] **Step 5: Commit**

```bash
git add src/views/order/raw/index.vue tests/unit/raw-order-page.spec.ts
git commit -m "feat(order): edit raw order rows via modal on the list page

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: End-to-end smoke verification

**Files:** none

- [ ] **Step 1: Rebuild and restart the backend jar**

The backend runs as a jar and the running process locks `target/`; kill it first:

```bash
netstat -ano | grep ":8081" | grep LISTENING   # 找 PID
taskkill //PID <pid> //F
cd "D:\GHANA\claude\admin.zokomart.africa\backend"
export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11"
mvn clean package -DskipTests
"$JAVA_HOME/bin/java" -jar target/admin-1.0.0.jar > run.log 2>&1 &
```

Wait for `Started AdminApplication`, confirm `curl -s http://localhost:8081/api/ping` → pong, and that the startup log shows Flyway at version 16.

- [ ] **Step 2: Drive the UI**

Start the frontend (`.claude/launch.json` config `frontend-dev`, port 5199) and as superadmin:

1. 订单管理 → 原始订单: each row shows an 编辑 link in the fixed-right 操作 column.
2. Edit a row: modal opens pre-filled; change brand + status + quantity → 保存 → success toast, list reflects the change immediately.
3. Clear a required field → save blocked with inline validation message.
4. Set status via the select — only the 4 legal options are offered.

Expected: all four checks pass.

- [ ] **Step 3: Final full test runs**

```bash
cd "D:\GHANA\claude\admin.zokomart.africa\backend" && export JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" && mvn test
cd "D:\GHANA\claude\admin.zokomart.africa\frontend" && pnpm vitest run && pnpm build
```

Expected: everything green. Both repos stay on `feat/raw-order-import`; hand off to superpowers:finishing-a-development-branch for merge/PR of the combined import+edit feature.
