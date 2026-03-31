<template>
  <div class="profile-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>健康档案</span>
          <el-button v-if="!editing" type="primary" @click="editing = true">编辑</el-button>
          <div v-else>
            <el-button @click="cancelEdit">取消</el-button>
            <el-button type="primary" @click="handleSubmit">保存</el-button>
          </div>
        </div>
      </template>

      <el-form
        ref="formRef"
        v-loading="loading"
        :model="form"
        :rules="rules"
        label-width="120px"
        :disabled="!editing"
      >
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="form.gender">
                <el-radio value="male">男</el-radio>
                <el-radio value="female">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="出生日期" prop="birthDate">
              <el-date-picker
                v-model="form.birthDate"
                type="date"
                placeholder="选择日期"
                value-format="YYYY-MM-DD"
                :disabled-date="disabledDate"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="身高(cm)" prop="heightCm">
              <el-input-number v-model="form.heightCm" :min="50" :max="300" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="体重(kg)" prop="weightKg">
              <el-input-number
                v-model="form.weightKg"
                :min="20"
                :max="500"
                :precision="1"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="bmi" :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="BMI">
              <el-input :value="bmi" readonly>
                <template #append>
                  <el-tag :type="getBmiType(bmi)">{{ getBmiLabel(bmi) }}</el-tag>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="血型" prop="bloodType">
              <el-select v-model="form.bloodType" placeholder="请选择" style="width: 100%">
                <el-option label="A型" value="A" />
                <el-option label="B型" value="B" />
                <el-option label="AB型" value="AB" />
                <el-option label="O型" value="O" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="过敏史" prop="allergyHistory">
          <el-input
            v-model="form.allergyHistory"
            type="textarea"
            :rows="3"
            placeholder="请输入过敏史，如：青霉素过敏、花粉过敏等"
          />
        </el-form-item>

        <el-form-item label="家族病史" prop="familyHistory">
          <el-input
            v-model="form.familyHistory"
            type="textarea"
            :rows="3"
            placeholder="请输入家族病史，如：父亲有高血压、母亲有糖尿病等"
          />
        </el-form-item>

        <el-form-item label="生活习惯" prop="lifestyleHabits">
          <el-input
            v-model="form.lifestyleHabits"
            type="textarea"
            :rows="3"
            placeholder="请输入生活习惯，如：吸烟、饮酒、运动频率等"
          />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { preventionApi } from '@/api/prevention'
import type { HealthProfileRequest, HealthProfileVO } from '@/types/api'

const loading = ref(false)
const editing = ref(false)
const formRef = ref<FormInstance>()
const profileData = ref<HealthProfileVO | null>(null)

const form = reactive<HealthProfileRequest>({
  gender: '',
  birthDate: '',
  heightCm: undefined,
  weightKg: undefined,
  bloodType: '',
  allergyHistory: '',
  familyHistory: '',
  lifestyleHabits: ''
})

const rules: FormRules = {
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  birthDate: [{ required: true, message: '请选择出生日期', trigger: 'change' }]
}

const bmi = computed(() => {
  if (form.heightCm && form.weightKg) {
    const heightM = form.heightCm / 100
    return (form.weightKg / (heightM * heightM)).toFixed(1)
  }
  return null
})

const getBmiType = (bmi: string): string => {
  const value = parseFloat(bmi)
  if (value < 18.5) return 'warning'
  if (value < 24) return 'success'
  if (value < 28) return 'warning'
  return 'danger'
}

const getBmiLabel = (bmi: string): string => {
  const value = parseFloat(bmi)
  if (value < 18.5) return '偏瘦'
  if (value < 24) return '正常'
  if (value < 28) return '偏胖'
  return '肥胖'
}

const disabledDate = (time: Date) => {
  return time.getTime() >= Date.now()
}

const fetchProfile = async () => {
  loading.value = true
  try {
    const res = await preventionApi.getProfile()
    profileData.value = res.data
    Object.assign(form, {
      gender: res.data.gender || '',
      birthDate: res.data.birthDate || '',
      heightCm: res.data.heightCm,
      weightKg: res.data.weightKg,
      bloodType: res.data.bloodType || '',
      allergyHistory: res.data.allergyHistory || '',
      familyHistory: res.data.familyHistory || '',
      lifestyleHabits: res.data.lifestyleHabits || ''
    })
  } catch (error) {
    // 如果没有档案，允许创建
    editing.value = true
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    loading.value = true
    try {
      if (profileData.value) {
        await preventionApi.updateProfile(profileData.value.id, form)
        ElMessage.success('更新成功')
      } else {
        await preventionApi.createProfile(form)
        ElMessage.success('创建成功')
      }
      editing.value = false
      await fetchProfile()
    } catch (error) {
      ElMessage.error('操作失败')
    } finally {
      loading.value = false
    }
  })
}

const cancelEdit = () => {
  editing.value = false
  if (profileData.value) {
    Object.assign(form, {
      gender: profileData.value.gender || '',
      birthDate: profileData.value.birthDate || '',
      heightCm: profileData.value.heightCm,
      weightKg: profileData.value.weightKg,
      bloodType: profileData.value.bloodType || '',
      allergyHistory: profileData.value.allergyHistory || '',
      familyHistory: profileData.value.familyHistory || '',
      lifestyleHabits: profileData.value.lifestyleHabits || ''
    })
  }
}

onMounted(() => {
  fetchProfile()
})
</script>

<style scoped>
.profile-page {
  padding: 20px;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
