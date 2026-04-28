import { defineStore } from 'pinia'

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
    token: localStorage.getItem('token') || '',
    profile: localStorage.getItem('profile')
      ? JSON.parse(localStorage.getItem('profile') as string)
      : null
  }),
  actions: {
    setLogin(token: string, profile: UserState['profile']) {
      this.token = token
      this.profile = profile
      localStorage.setItem('token', token)
      localStorage.setItem('profile', JSON.stringify(profile))
    },
    setProfile(profile: UserProfile | null) {
      this.profile = profile
      if (profile) {
        localStorage.setItem('profile', JSON.stringify(profile))
      } else {
        localStorage.removeItem('profile')
      }
    },
    logout() {
      this.token = ''
      this.profile = null
      localStorage.removeItem('token')
      localStorage.removeItem('profile')
    }
  }
})
