import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

export type Theme = 'light' | 'dark' | 'system'

const THEME_KEY = 'hhs-theme'

/**
 * Get the system preferred theme
 */
const getSystemTheme = (): 'light' | 'dark' => {
  if (typeof window !== 'undefined' && window.matchMedia) {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  return 'light'
}

/**
 * Apply theme to document
 */
const applyTheme = (theme: 'light' | 'dark') => {
  if (typeof document !== 'undefined') {
    document.documentElement.setAttribute('data-theme', theme)

    // Update Element Plus theme
    const html = document.documentElement
    if (theme === 'dark') {
      html.classList.add('dark')
    } else {
      html.classList.remove('dark')
    }
  }
}

export const useThemeStore = defineStore('theme', () => {
  // Get initial theme from localStorage or default to 'system'
  const storedTheme = localStorage.getItem(THEME_KEY) as Theme | null
  const theme = ref<Theme>(storedTheme || 'light')

  // Computed effective theme (resolves 'system' to actual theme)
  const effectiveTheme = ref<'light' | 'dark'>(
    theme.value === 'system' ? getSystemTheme() : theme.value
  )

  /**
   * Set theme and persist to localStorage
   */
  const setTheme = (newTheme: Theme) => {
    theme.value = newTheme
    localStorage.setItem(THEME_KEY, newTheme)

    // Resolve effective theme
    effectiveTheme.value = newTheme === 'system' ? getSystemTheme() : newTheme
    applyTheme(effectiveTheme.value)
  }

  /**
   * Toggle between light and dark (skips system)
   */
  const toggleTheme = () => {
    const next = effectiveTheme.value === 'light' ? 'dark' : 'light'
    setTheme(next)
  }

  /**
   * Initialize theme on app start
   */
  const initTheme = () => {
    // Apply initial theme
    effectiveTheme.value = theme.value === 'system' ? getSystemTheme() : theme.value
    applyTheme(effectiveTheme.value)

    // Listen for system theme changes
    if (typeof window !== 'undefined' && window.matchMedia) {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')

      mediaQuery.addEventListener('change', e => {
        if (theme.value === 'system') {
          effectiveTheme.value = e.matches ? 'dark' : 'light'
          applyTheme(effectiveTheme.value)
        }
      })
    }
  }

  // Watch for theme changes
  watch(theme, newTheme => {
    effectiveTheme.value = newTheme === 'system' ? getSystemTheme() : newTheme
    applyTheme(effectiveTheme.value)
  })

  return {
    theme,
    effectiveTheme,
    setTheme,
    toggleTheme,
    initTheme
  }
})
