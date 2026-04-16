<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <section class="auth-hero">
        <h1>让学习资料真正流动起来</h1>
        <p>
          上传课堂资料，自动解析内容，AI 生成总结与练习建议，帮助你完成从整理知识点到复习巩固的完整闭环。
        </p>
        <div class="auth-points">
          <div class="auth-point">资料上传与结构化管理</div>
          <div class="auth-point">AI 自动总结与知识提炼</div>
          <div class="auth-point">面向简历展示的完整业务闭环</div>
        </div>
      </section>

      <section class="auth-form">
        <h2>{{ isRegister ? '创建账号' : '欢迎回来' }}</h2>
        <p style="color: var(--muted)">
          {{ isRegister ? '先注册一个演示账号，后面就可以直接联调接口。' : '使用后端注册好的账号登录系统。' }}
        </p>

        <el-form :model="form" label-position="top" @submit.prevent>
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-form-item v-if="isRegister" label="昵称">
            <el-input v-model="form.nickname" placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item v-if="isRegister" label="邮箱">
            <el-input v-model="form.email" placeholder="请输入邮箱" />
          </el-form-item>
          <el-button type="primary" style="width: 100%" @click="submit">
            {{ isRegister ? '注册并登录' : '登录系统' }}
          </el-button>
        </el-form>

        <el-button link style="margin-top: 12px; align-self: flex-start" @click="isRegister = !isRegister">
          {{ isRegister ? '已有账号，去登录' : '没有账号，先注册' }}
        </el-button>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getProfileApi, loginApi, registerApi } from '@/api/modules/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const isRegister = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: ''
})

const submit = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请先填写用户名和密码')
    return
  }

  if (isRegister.value) {
    if (!form.nickname) {
      ElMessage.warning('注册时请填写昵称')
      return
    }
    await registerApi({
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      email: form.email
    })
    ElMessage.success('注册成功，请继续登录')
    isRegister.value = false
    return
  }

  const loginRes = await loginApi({
    username: form.username,
    password: form.password
  })
  const loginData = loginRes.data.data
  userStore.setLogin(loginData.token, {
    id: loginData.userId,
    username: loginData.username,
    nickname: loginData.nickname
  })
  const profileRes = await getProfileApi()
  userStore.setLogin(loginData.token, profileRes.data.data)
  ElMessage.success('登录成功')
  router.push('/dashboard')
}
</script>
