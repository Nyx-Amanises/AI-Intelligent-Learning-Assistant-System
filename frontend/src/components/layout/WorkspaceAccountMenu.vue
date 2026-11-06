<template>
  <div class="account-menu">
    <el-dropdown trigger="click" placement="bottom-end" popper-class="account-menu-popper" @command="handleCommand">
      <button
        type="button"
        class="account-menu__trigger"
        :class="{ 'is-active': accountPageActive }"
        :aria-label="displayName + '，账户菜单'"
      >
        <img
          v-if="avatarSrc && !avatarLoadFailed"
          :src="avatarSrc"
          alt=""
          class="account-menu__avatar"
          @error="avatarLoadFailed = true"
        />
        <span v-else class="account-menu__avatar">{{ avatarText }}</span>
        <AppIcon class="account-menu__chevron" name="chevron-down" :size="15" />
      </button>
      <template #dropdown>
        <el-dropdown-menu class="account-menu__list">
          <el-dropdown-item disabled class="account-menu__header">
            <strong>{{ displayName }}</strong>
            <span>{{ userStore.profile?.username ? '@' + userStore.profile.username : '已登录' }}</span>
          </el-dropdown-item>
          <el-dropdown-item
            v-for="(item, index) in accountItems"
            :key="item.path"
            :command="item.path"
            :divided="index === 0"
            :class="{ 'is-current': route.path === item.path }"
          >
            <AppIcon :name="item.icon" :size="17" />{{ item.label }}
          </el-dropdown-item>
          <el-dropdown-item command="avatar" divided :disabled="avatarUploading">
            <AppIcon name="camera" :size="17" />{{ avatarUploading ? '正在上传头像…' : '更换头像' }}
          </el-dropdown-item>
          <el-dropdown-item command="logout"><AppIcon name="logout" :size="17" />退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    <input
      ref="avatarInputRef"
      type="file"
      accept="image/jpeg,image/png,image/webp,image/gif"
      class="visually-hidden"
      tabindex="-1"
      aria-hidden="true"
      @change="handleAvatarChange"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { uploadAvatarApi } from '@/api/modules/auth'
import AppIcon from '@/components/AppIcon.vue'
import { navigationSections } from '@/config/navigation'
import { useUserStore } from '@/stores/user'
import { resolveAvatarUrl } from '@/utils/avatar'
import { getErrorMessage } from '@/utils/errorMessage'

/** 顶栏右侧的头像菜单：个人信息、设置类页面、更换头像和退出登录。 */
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const avatarInputRef = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)
const avatarLoadFailed = ref(false)

const displayName = computed(() => userStore.profile?.nickname || userStore.profile?.username || '学习者')
const avatarText = computed(() => Array.from(displayName.value)[0]?.toUpperCase() || '学')
const avatarSrc = computed(() => resolveAvatarUrl(userStore.profile?.avatarUrl))
const accountItems = computed(() =>
  (navigationSections.find((section) => section.key === 'account')?.items || []).filter(
    (item) => !item.adminOnly || userStore.isAdmin
  )
)
const accountPageActive = computed(() => accountItems.value.some((item) => item.path === route.path))

watch(avatarSrc, () => {
  avatarLoadFailed.value = false
})

const handleCommand = (command: string) => {
  if (command.startsWith('/')) void router.push(command)
  if (command === 'avatar') avatarInputRef.value?.click()
  if (command === 'logout') {
    userStore.logout()
    void router.push('/login')
  }
}

const handleAvatarChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || avatarUploading.value) return
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    ElMessage.warning('请选择 JPG、PNG、WebP 或 GIF 图片')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像图片不能超过 5MB')
    return
  }
  avatarUploading.value = true
  const requestToken = userStore.token
  try {
    const response = await uploadAvatarApi(file)
    if (!requestToken || userStore.token !== requestToken) return
    userStore.setProfile(response.data.data)
    ElMessage.success('头像已更新')
  } catch (error) {
    if (requestToken && userStore.token === requestToken) {
      ElMessage.error(getErrorMessage(error, '头像上传失败'))
    }
  } finally {
    avatarUploading.value = false
  }
}
</script>
<style scoped>
.account-menu {
  display: flex;
}
.account-menu__trigger {
  display: flex;
  align-items: center;
  gap: 2px;
  height: 40px;
  padding: 0 6px 0 3px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  transition: background-color var(--duration-fast) ease;
}
.account-menu__trigger:hover,
.account-menu__trigger.is-active {
  background: var(--bg-secondary);
}
.account-menu__avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 14px;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--brand) 14%, transparent);
}
:global(.account-menu-popper .el-dropdown-menu) {
  min-width: 220px;
}
:global(.account-menu-popper .el-dropdown-menu__item) {
  gap: 10px;
}
:global(.account-menu-popper .el-dropdown-menu__item .app-icon) {
  color: var(--muted);
}
:global(.account-menu-popper .el-dropdown-menu__item.is-current) {
  color: var(--brand);
  font-weight: 600;
}
:global(.account-menu-popper .el-dropdown-menu__item.is-current .app-icon) {
  color: var(--brand);
}
:global(.account-menu-popper .account-menu__header.is-disabled) {
  display: grid;
  gap: 0;
  padding: 6px 12px 8px;
  color: var(--text);
  cursor: default;
  line-height: 1.5;
}
:global(.account-menu-popper .account-menu__header strong) {
  font-size: 15px;
  font-weight: 600;
}
:global(.account-menu-popper .account-menu__header span) {
  color: var(--muted);
  font-size: 12px;
}
@media (max-width: 760px) {
  .account-menu__trigger {
    padding-right: 3px;
  }
  .account-menu__chevron {
    display: none;
  }
}
</style>
