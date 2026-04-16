import { defineStore } from 'pinia'

interface UserState {
  token: string
  profile: {
    id?: number
    username?: string
    nickname?: string
  } | null
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
    logout() {
      this.token = ''
      this.profile = null
      localStorage.removeItem('token')
      localStorage.removeItem('profile')
    }
  }
})
