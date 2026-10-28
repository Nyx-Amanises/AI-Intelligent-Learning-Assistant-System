import { defineStore } from 'pinia'
import { readStorage, readStorageJson, removeStorage, writeStorage } from '@/utils/storage'

export interface UserProfile {
  id?: number
  username?: string
  nickname?: string
  email?: string
  phone?: string
  avatarUrl?: string
  roleCode?: string
  lastLoginTime?: string
}

interface UserState {
  token: string
  profile: UserProfile | null
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: readStorage('token') || '',
    profile: readStorageJson<UserProfile | null>('profile', null)
  }),
  getters: {
    /** 是否为管理员；开发者向的页面与操作只对管理员开放。 */
    isAdmin: (state) => String(state.profile?.roleCode || '').toUpperCase() === 'ADMIN'
  },
  actions: {
    setLogin(token: string, profile: UserState['profile']) {
      this.token = token
      this.profile = profile
      writeStorage('token', token)
      writeStorage('profile', JSON.stringify(profile))
    },
    /** 修改密码后后端会重新签发 token，只替换 token、保留当前资料。 */
    setToken(token: string) {
      this.token = token
      writeStorage('token', token)
    },
    setProfile(profile: UserProfile | null) {
      this.profile = profile
      if (profile) {
        writeStorage('profile', JSON.stringify(profile))
      } else {
        removeStorage('profile')
      }
    },
    logout() {
      this.token = ''
      this.profile = null
      removeStorage('token')
      removeStorage('profile')
    }
  }
})
