import { ref, computed, watch, type Ref, type ComponentPublicInstance } from 'vue'

interface TiltOptions {
  maxTilt?: number
  scale?: number
  speed?: number
}

export function useTilt3D(
  targetRef: Ref<HTMLElement | ComponentPublicInstance | null>,
  options: TiltOptions = {}
) {
  const { maxTilt = 12, scale = 1.02, speed = 600 } = options
  const tiltX = ref(0)
  const tiltY = ref(0)
  const isHovering = ref(false)

  let animationFrame: number | null = null
  let attached = false

  const getEl = (): HTMLElement | null => {
    const val = targetRef.value
    if (!val) return null
    if (val instanceof HTMLElement) return val
    if ('$el' in val && val.$el instanceof HTMLElement) return val.$el
    return null
  }

  const handleMouseMove = (e: MouseEvent) => {
    if (animationFrame) cancelAnimationFrame(animationFrame)
    animationFrame = requestAnimationFrame(() => {
      const el = getEl()
      if (!el) return
      const rect = el.getBoundingClientRect()
      const centerX = rect.left + rect.width / 2
      const centerY = rect.top + rect.height / 2
      const mouseX = e.clientX - centerX
      const mouseY = e.clientY - centerY

      tiltY.value = (mouseX / (rect.width / 2)) * maxTilt
      tiltX.value = -(mouseY / (rect.height / 2)) * maxTilt
    })
  }

  const handleMouseEnter = () => {
    isHovering.value = true
  }

  const handleMouseLeave = () => {
    isHovering.value = false
    tiltX.value = 0
    tiltY.value = 0
  }

  const attach = () => {
    const el = getEl()
    if (!el || attached) return
    el.addEventListener('mousemove', handleMouseMove)
    el.addEventListener('mouseenter', handleMouseEnter)
    el.addEventListener('mouseleave', handleMouseLeave)
    attached = true
  }

  const detach = () => {
    const el = getEl()
    if (!el || !attached) return
    el.removeEventListener('mousemove', handleMouseMove)
    el.removeEventListener('mouseenter', handleMouseEnter)
    el.removeEventListener('mouseleave', handleMouseLeave)
    attached = false
  }

  watch(targetRef, (newVal, oldVal) => {
    if (oldVal) detach()
    if (newVal) {
      setTimeout(attach, 0)
    }
  }, { immediate: true })

  const style = computed(() => {
    if (!isHovering.value) {
      return {
        transform: 'perspective(800px) rotateX(0deg) rotateY(0deg) scale(1)',
        transition: `transform ${speed}ms cubic-bezier(0.34, 1.56, 0.64, 1)`
      }
    }
    return {
      transform: `perspective(800px) rotateX(${tiltX.value}deg) rotateY(${tiltY.value}deg) scale(${scale})`,
      transition: `transform ${speed * 0.1}ms cubic-bezier(0.34, 1.56, 0.64, 1)`
    }
  })

  return { style, isHovering }
}
