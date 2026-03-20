import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import * as parserVue from 'vue-eslint-parser'
import configPrettier from 'eslint-config-prettier'
import * as parserTypeScript from '@typescript-eslint/parser'
import pluginTypeScript from '@typescript-eslint/eslint-plugin'
import globals from 'globals'

export default [
  {
    // Ignore patterns
    ignores: [
      'node_modules/',
      'dist/',
      'coverage/',
      '*.local',
      '*.log*',
      '.vscode/',
      '.idea/',
      'public/',
      'auto-imports.d.ts',
      'components.d.ts'
    ]
  },

  // Base JavaScript recommended rules
  js.configs.recommended,

  // Vue recommended rules
  ...pluginVue.configs['flat/recommended'],

  // TypeScript and Vue files
  {
    files: ['**/*.vue', '**/*.ts', '**/*.tsx'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      parser: parserVue,
      parserOptions: {
        parser: parserTypeScript,
        ecmaVersion: 'latest',
        sourceType: 'module',
        jsxPragma: 'React',
        ecmaFeatures: {
          jsx: true
        }
      },
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.es2021,
        // Vite
        importMeta: 'readonly'
      }
    },
    plugins: {
      '@typescript-eslint': pluginTypeScript
    },
    rules: {
      // TypeScript rules
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/explicit-module-boundary-types': 'off',
      '@typescript-eslint/no-non-null-assertion': 'warn',

      // Vue rules
      'vue/multi-word-component-names': 'off',
      'vue/no-v-html': 'warn',
      'vue/require-default-prop': 'off',
      'vue/require-explicit-emits': 'warn',
      'vue/component-definition-name-casing': ['error', 'PascalCase'],
      'vue/component-name-in-template-casing': ['error', 'PascalCase'],

      // General rules
      'no-console': 'warn',
      'no-debugger': 'warn',
      'no-unused-vars': 'off', // Use TypeScript's version
      'prefer-const': 'warn',
      'no-var': 'error'
    }
  },

  // Test files with vitest globals
  {
    files: ['**/__tests__/**/*.ts', '**/*.spec.ts', '**/*.test.ts', '**/vitest.config.ts', '**/playwright.config.ts', 'e2e/**/*.ts'],
    languageOptions: {
      globals: {
        ...globals.node,
        // Vitest globals
        vi: 'readonly',
        describe: 'readonly',
        it: 'readonly',
        test: 'readonly',
        expect: 'readonly',
        beforeEach: 'readonly',
        afterEach: 'readonly',
        beforeAll: 'readonly',
        afterAll: 'readonly'
      }
    }
  },

  // Prettier config (must be last to override conflicting rules)
  configPrettier
]