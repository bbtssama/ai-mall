<template>
  <div class="addr-page">
    <div class="page-head">
      <h2>收货地址</h2>
      <el-button type="primary" @click="openForm()">＋ 新增地址</el-button>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!list.length && !loading" description="还没有收货地址">
        <el-button type="primary" @click="openForm()">新增收货地址</el-button>
      </el-empty>

      <div v-else class="addr-list">
        <div v-for="a in list" :key="a.id" class="addr-item mall-card">
          <div class="addr-head">
            <span class="addr-receiver">{{ a.receiver }}</span>
            <span class="addr-phone">{{ a.phone }}</span>
            <el-tag v-if="a.isDefault" type="danger" size="small" effect="plain">默认</el-tag>
            <el-button v-else size="small" text type="primary" @click="setDefault(a)">设为默认</el-button>
          </div>
          <div class="addr-detail">{{ displayAddress(a) }}</div>
          <div class="addr-ops">
            <el-button size="small" text @click="openForm(a)">编辑</el-button>
            <el-button size="small" text type="danger" @click="removeAddr(a)">删除</el-button>
          </div>
        </div>
      </div>
    </div>

    <!--
      新增/编辑地址弹窗。
      移动端用 modal-class 把浮层标记出来，在样式里改造成底部抽屉（见文件末尾非 scoped 块）——
      Element 的弹窗默认挂到 body，scoped 选择器够不到它。
    -->
    <el-dialog
      v-model="formVisible"
      :title="form.id ? '编辑地址' : '新增地址'"
      width="480px"
      modal-class="addr-sheet-mask"
      @opened="bindKeyboardLift"
      @closed="unbindKeyboardLift">
      <el-form class="addr-form" label-width="70px" size="default">
        <el-form-item label="收货人"><el-input v-model="form.receiver" placeholder="收货人姓名" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" placeholder="手机号" /></el-form-item>
        <!-- 省/市/区县三级选择：手机上逐字敲省市名是灾难，且此前根本没有区县这一级 -->
        <el-form-item label="所在地区">
          <el-cascader
            v-model="region"
            :options="REGION_OPTIONS"
            :props="{ checkStrictly: true }"
            clearable
            filterable
            placeholder="选择省 / 市 / 区县" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="form.detail" placeholder="街道 / 小区 / 门牌号" />
        </el-form-item>
        <el-form-item class="default-item">
          <el-checkbox v-model="form.isDefault">设为默认收货地址</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addressApi } from '../api'

/**
 * 精简内置行政区划（省 → 市 → 区县）。
 * 不引 npm 包（全国完整区划数据 200KB+，为 6 个地址字段不值当）；
 * 级联选择器开了 checkStrictly，冷门地区只选到省/市也能提交。
 */
const REGION = {
  北京: { 北京市: ['东城区', '西城区', '朝阳区', '海淀区', '丰台区', '石景山区', '通州区', '昌平区', '大兴区', '顺义区'] },
  上海: { 上海市: ['黄浦区', '徐汇区', '长宁区', '静安区', '普陀区', '虹口区', '杨浦区', '浦东新区', '闵行区', '宝山区'] },
  天津: { 天津市: ['和平区', '河东区', '河西区', '南开区', '河北区', '红桥区', '东丽区', '西青区', '津南区', '滨海新区'] },
  重庆: { 重庆市: ['渝中区', '江北区', '南岸区', '九龙坡区', '沙坪坝区', '渝北区', '巴南区', '北碚区', '大渡口区'] },
  广东: {
    广州: ['天河区', '越秀区', '海珠区', '荔湾区', '白云区', '番禺区', '黄埔区', '增城区'],
    深圳: ['福田区', '南山区', '罗湖区', '宝安区', '龙岗区', '龙华区', '坪山区', '光明区'],
    东莞: ['南城街道', '东城街道', '莞城街道', '万江街道', '松山湖'],
    佛山: ['禅城区', '南海区', '顺德区', '三水区', '高明区'],
    珠海: ['香洲区', '金湾区', '斗门区'],
  },
  江苏: {
    南京: ['玄武区', '鼓楼区', '秦淮区', '建邺区', '江宁区', '雨花台区', '栖霞区', '浦口区'],
    苏州: ['姑苏区', '工业园区', '吴中区', '相城区', '吴江区', '昆山市', '常熟市'],
    无锡: ['梁溪区', '滨湖区', '新吴区', '惠山区', '锡山区'],
    常州: ['天宁区', '钟楼区', '新北区', '武进区'],
    南通: ['崇川区', '通州区', '海门区'],
  },
  浙江: {
    杭州: ['上城区', '拱墅区', '西湖区', '滨江区', '余杭区', '萧山区', '钱塘区', '临平区'],
    宁波: ['海曙区', '江北区', '鄞州区', '镇海区', '北仑区', '奉化区'],
    温州: ['鹿城区', '龙湾区', '瓯海区', '洞头区'],
    嘉兴: ['南湖区', '秀洲区', '嘉善县', '桐乡市'],
    金华: ['婺城区', '金东区', '义乌市', '东阳市'],
  },
  山东: {
    济南: ['历下区', '市中区', '天桥区', '槐荫区', '历城区', '章丘区'],
    青岛: ['市南区', '市北区', '崂山区', '李沧区', '黄岛区', '城阳区', '即墨区'],
    烟台: ['芝罘区', '福山区', '莱山区', '牟平区'],
    潍坊: ['潍城区', '奎文区', '坊子区', '寒亭区'],
  },
  四川: {
    成都: ['锦江区', '青羊区', '金牛区', '武侯区', '成华区', '高新区', '双流区', '龙泉驿区', '温江区', '新都区', '郫都区', '天府新区'],
    绵阳: ['涪城区', '游仙区', '安州区'],
    德阳: ['旌阳区', '广汉市'],
    宜宾: ['翠屏区', '叙州区'],
    南充: ['顺庆区', '高坪区', '嘉陵区'],
  },
  湖北: {
    武汉: ['江岸区', '江汉区', '硚口区', '汉阳区', '武昌区', '洪山区', '东西湖区', '江夏区'],
    宜昌: ['西陵区', '伍家岗区', '点军区', '猇亭区'],
    襄阳: ['襄城区', '樊城区', '襄州区'],
  },
  湖南: {
    长沙: ['芙蓉区', '天心区', '岳麓区', '开福区', '雨花区', '望城区'],
    株洲: ['天元区', '荷塘区', '芦淞区', '石峰区'],
    湘潭: ['雨湖区', '岳塘区'],
  },
  河南: {
    郑州: ['中原区', '二七区', '金水区', '惠济区', '管城回族区', '郑东新区', '高新区'],
    洛阳: ['洛龙区', '涧西区', '西工区', '老城区'],
    开封: ['鼓楼区', '龙亭区', '顺河回族区'],
  },
  河北: {
    石家庄: ['长安区', '桥西区', '新华区', '裕华区', '藁城区'],
    唐山: ['路南区', '路北区', '开平区', '丰润区'],
    保定: ['竞秀区', '莲池区', '徐水区'],
  },
  福建: {
    福州: ['鼓楼区', '台江区', '仓山区', '晋安区', '马尾区'],
    厦门: ['思明区', '湖里区', '集美区', '海沧区', '同安区', '翔安区'],
    泉州: ['鲤城区', '丰泽区', '洛江区', '晋江市'],
  },
  安徽: {
    合肥: ['庐阳区', '瑶海区', '蜀山区', '包河区', '经开区'],
    芜湖: ['镜湖区', '弋江区', '鸠江区'],
  },
  陕西: {
    西安: ['碑林区', '莲湖区', '雁塔区', '未央区', '新城区', '长安区', '高新区'],
    咸阳: ['秦都区', '渭城区'],
  },
  辽宁: {
    沈阳: ['和平区', '沈河区', '大东区', '皇姑区', '铁西区', '浑南区'],
    大连: ['中山区', '西岗区', '沙河口区', '甘井子区', '金州区'],
  },
  江西: {
    南昌: ['东湖区', '西湖区', '青云谱区', '青山湖区', '红谷滩区'],
    九江: ['浔阳区', '濂溪区'],
  },
  广西: {
    南宁: ['青秀区', '兴宁区', '西乡塘区', '江南区', '良庆区'],
    桂林: ['秀峰区', '象山区', '七星区', '叠彩区'],
  },
  云南: {
    昆明: ['五华区', '盘龙区', '官渡区', '西山区', '呈贡区'],
    大理: ['大理市', '祥云县'],
  },
  山西: {
    太原: ['小店区', '迎泽区', '杏花岭区', '万柏林区', '晋源区'],
    大同: ['平城区', '云冈区'],
  },
  贵州: {
    贵阳: ['南明区', '云岩区', '观山湖区', '花溪区', '乌当区'],
    遵义: ['红花岗区', '汇川区'],
  },
  黑龙江: {
    哈尔滨: ['道里区', '南岗区', '道外区', '香坊区', '松北区'],
    大庆: ['萨尔图区', '龙凤区'],
  },
  吉林: {
    长春: ['南关区', '朝阳区', '宽城区', '二道区', '绿园区'],
    吉林市: ['船营区', '昌邑区', '龙潭区'],
  },
  海南: {
    海口: ['龙华区', '美兰区', '秀英区', '琼山区'],
    三亚: ['吉阳区', '天涯区', '海棠区'],
  },
  甘肃: { 兰州: ['城关区', '七里河区', '安宁区', '西固区'] },
  内蒙古: { 呼和浩特: ['回民区', '新城区', '赛罕区', '玉泉区'] },
  新疆: { 乌鲁木齐: ['天山区', '沙依巴克区', '新市区', '水磨沟区'] },
  宁夏: { 银川: ['兴庆区', '金凤区', '西夏区'] },
  青海: { 西宁: ['城中区', '城东区', '城西区', '城北区'] },
  西藏: { 拉萨: ['城关区', '堆龙德庆区'] },
  香港: { 香港: ['中西区', '湾仔区', '东区', '油尖旺区', '沙田区'] },
  澳门: { 澳门: ['花地玛堂区', '大堂区', '风顺堂区'] },
  台湾: { 台北: ['中正区', '信义区', '大安区', '士林区'] }
}

const REGION_OPTIONS = Object.entries(REGION).map(([province, cities]) => ({
  value: province,
  label: province,
  children: Object.entries(cities).map(([city, districts]) => ({
    value: city,
    label: city,
    children: districts.map((d) => ({ value: d, label: d }))
  }))
}))

const list = ref([])
const loading = ref(false)
const formVisible = ref(false)
const saving = ref(false)
const form = ref({ id: null, receiver: '', phone: '', detail: '', isDefault: false })
/** 级联选择器的值：['四川','成都','双流区']，允许只选到省/市（checkStrictly） */
const region = ref([])

function displayAddress(a) {
  // 此前是 province + city + detail 直接拼接，出来是「四川成都双流」——三个地名糊在一起
  return [a.province, a.city, a.detail].filter(Boolean).join(' ')
}

async function load() {
  loading.value = true
  try { list.value = await addressApi.list() || [] } finally { loading.value = false }
}

/**
 * 老数据把区县写在了 detail 里（后端 fullAddress = 省+市+detail，没有独立区县字段）。
 * 能对上内置区县表就拆回去，让三级选择器正确回显；对不上就原样留在明细里，不丢信息。
 */
function splitDistrict(province, city, detail) {
  const districts = REGION[province]?.[city] || []
  const hit = districts.find((d) => detail.startsWith(d))
    || districts.find((d) => d.length > 2 && detail.startsWith(d.slice(0, -1)))
  return hit ? { district: hit, rest: detail.slice(hit.length) } : { district: '', rest: detail }
}

function openForm(a) {
  if (a) {
    const { district, rest } = splitDistrict(a.province, a.city, a.detail || '')
    form.value = {
      id: a.id, receiver: a.receiver || '', phone: a.phone || '',
      detail: rest, isDefault: !!a.isDefault
    }
    region.value = [a.province, a.city, district].filter(Boolean)
  } else {
    form.value = { id: null, receiver: '', phone: '', detail: '', isDefault: false }
    region.value = []
  }
  formVisible.value = true
}

async function save() {
  const [province = '', city = '', district = ''] = region.value
  // 区县回到 detail 里，保持后端 fullAddress = 省 + 市 + detail 的既有口径
  const detail = district + (form.value.detail || '').trim()

  if (!form.value.receiver.trim() || !form.value.phone.trim() || !detail) {
    ElMessage.warning('请填写收货人、电话和详细地址')
    return
  }

  const payload = {
    receiver: form.value.receiver.trim(),
    phone: form.value.phone.trim(),
    province,
    city,
    detail,
    isDefault: form.value.isDefault
  }

  saving.value = true
  try {
    if (form.value.id) {
      await addressApi.update(form.value.id, payload)
      ElMessage.success('地址已更新')
    } else {
      await addressApi.add(payload)
      ElMessage.success('地址已新增')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function removeAddr(a) {
  await ElMessageBox.confirm(`确定删除收货人「${a.receiver}」的地址吗？`, '确认删除', { type: 'warning' })
  await addressApi.remove(a.id)
  ElMessage.success('已删除')
  load()
}

async function setDefault(a) {
  await addressApi.setDefault(a.id)
  ElMessage.success('已设为默认')
  load()
}

/**
 * 抽屉避让软键盘：键盘弹起时 visualViewport 变矮，而 fixed 定位的浮层不会自己上移，
 * 底部「保存」会被盖住。这里把键盘高度补给浮层容器的 padding-bottom，整张抽屉上浮。
 */
let vvHandler = null

function bindKeyboardLift() {
  const vv = window.visualViewport
  if (!vv) return
  const apply = () => {
    const el = document.querySelector('.addr-sheet-mask .el-overlay-dialog')
    if (!el) return
    const kb = Math.max(0, window.innerHeight - vv.height - vv.offsetTop)
    el.style.paddingBottom = kb > 80 ? kb + 'px' : ''
  }
  vvHandler = apply
  vv.addEventListener('resize', apply)
  vv.addEventListener('scroll', apply)
}

function unbindKeyboardLift() {
  const vv = window.visualViewport
  if (vv && vvHandler) {
    vv.removeEventListener('resize', vvHandler)
    vv.removeEventListener('scroll', vvHandler)
  }
  vvHandler = null
  const el = document.querySelector('.addr-sheet-mask .el-overlay-dialog')
  if (el) el.style.paddingBottom = ''
}

onMounted(load)
</script>

<style scoped>
.addr-page { max-width: 720px; margin: 0 auto; }
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.page-head h2 { font-size: 20px; color: var(--clr-text); margin: 0; }
.addr-list { display: flex; flex-direction: column; gap: 12px; }
.addr-item { padding: 16px 20px; position: relative; }
.addr-head { display: flex; align-items: center; gap: 12px; margin-bottom: 6px; }
.addr-receiver { font-weight: 700; font-size: 16px; }
.addr-phone { color: var(--clr-text-2); font-size: 14px; }
.addr-detail { color: var(--clr-text-3); font-size: 14px; margin-bottom: 8px; }
.addr-ops { position: absolute; right: 16px; bottom: 10px; }
/* el-cascader 是多根组件，class 不会自动落到 .el-cascader 上，用 :deep 指定宽度 */
.addr-form :deep(.el-cascader) { width: 100%; }

/* =====================================================================
   移动端适配（≤ 768px）
   地址卡单列紧凑排版；编辑/删除由「右下角绝对定位」改为卡片底部一行，
   避免窄屏与地址文字重叠；弹窗表单 label 上移、控件全宽（纯 CSS，
   桌面端 label-width prop 保持不变）。
   ===================================================================== */
@media (max-width: 768px) {
  .addr-page { max-width: none; }
  .page-head { margin-bottom: 12px; gap: 8px; }
  .page-head h2 { font-size: 18px; }

  .addr-list { gap: 10px; }
  .addr-item { padding: 12px 14px; }

  .addr-head { flex-wrap: wrap; gap: 4px 8px; margin-bottom: 4px; }
  .addr-receiver { font-size: 15px; }
  .addr-phone { font-size: 13px; }
  .addr-detail { font-size: 13px; line-height: 1.5; margin-bottom: 0; }
  .addr-head .el-button { min-height: 44px; margin: 0; padding: 0 6px; }

  /* 操作区回到文档流：卡片底部一行。
     此前「编辑」和「删除」只隔 4px，两个都是 12px 小字，手指一滑就把地址删了。 */
  .addr-ops {
    position: static;
    display: flex; justify-content: flex-end; align-items: center; gap: 16px;
    margin-top: 8px; padding-top: 6px;
    border-top: 1px solid var(--clr-border-light);
  }
  .addr-ops .el-button {
    min-height: 44px; margin: 0; padding: 0 14px;
    font-size: 14px;
  }

  /* 弹窗表单：label 上移独占一行、控件全宽。
     EP 的 .el-form-item 默认 display:flex，必须显式改 block 才能让 label 换行。 */
  .addr-form :deep(.el-form-item) { display: block; margin-bottom: 14px; }
  .addr-form :deep(.el-form-item__label) {
    display: block; width: auto !important; height: auto; line-height: 1.4;
    text-align: left; padding: 0 0 6px; font-size: 13px;
  }
  .addr-form :deep(.el-form-item__content) { display: block; margin-left: 0 !important; }
  .addr-form :deep(.el-input__inner) { font-size: 16px; }
}
</style>

<!--
  非 scoped 块：Element Plus 的弹窗通过 Teleport 挂到 body，scoped 选择器够不到，
  所以这里用 modal-class 标记出来的 .addr-sheet-mask 作前缀限定作用域（不外溢到别的页面）。
  只做一件事：移动端把「居中卡片」改成「底部抽屉」。
-->
<style>
@media (max-width: 768px) {
  .el-overlay.addr-sheet-mask { display: flex; align-items: flex-end; }

  .addr-sheet-mask .el-overlay-dialog {
    display: flex; align-items: flex-end;
    padding: 0;
  }

  .addr-sheet-mask .el-dialog {
    width: 100% !important;
    max-width: 100%;
    max-height: 92vh;
    margin: 0 !important;
    padding: 4px 16px 0;
    border-radius: var(--r-lg) var(--r-lg) 0 0;
    display: flex; flex-direction: column;
  }
  .addr-sheet-mask .el-dialog__header { padding: 12px 0 6px; margin-right: 0; }
  .addr-sheet-mask .el-dialog__title { font-size: 16px; font-weight: 700; }
  .addr-sheet-mask .el-dialog__headerbtn { top: 6px; right: 0; width: 44px; height: 44px; }

  /* 内容区独立滚动：键盘弹起时字段仍可滚到，底部按钮不会被挤出可视区 */
  .addr-sheet-mask .el-dialog__body {
    flex: 1 1 auto; min-height: 0; overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    padding: 4px 0 12px;
  }

  /* 底部按钮此前是右上角卡片里右下角的两个 62px 小按钮（左边留白 200px），
     现在是左右对分的全宽按钮 + 安全区避让，拇指好按。 */
  .addr-sheet-mask .el-dialog__footer {
    flex: 0 0 auto;
    display: flex; gap: 12px;
    padding: 10px 0 calc(12px + var(--safe-b));
    border-top: 1px solid var(--clr-border-light);
  }
  .addr-sheet-mask .el-dialog__footer .el-button {
    flex: 1 1 0; margin: 0 !important;
    min-height: 48px; font-size: 16px;
  }
}
</style>
