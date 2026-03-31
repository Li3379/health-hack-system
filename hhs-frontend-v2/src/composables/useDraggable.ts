import { ref, onMounted, onUnmounted, type Ref } from 'vue'

interface Position {
  x: number
  y: number
}

interface UseDraggableOptions {
  initialValue?: Position
  storageKey?: string
  boundaryPadding?: number
  dragThreshold?: number
  elementSize?: number
}

export function useDraggable(
  target: Ref<HTMLElement | undefined>,
  options: UseDraggableOptions = {}
) {
  const {
    initialValue = { x: window.innerWidth - 80, y: window.innerHeight - 140 },
    storageKey,
    boundaryPadding = 12,
    dragThreshold = 5,
    elementSize = 56
  } = options

  const savedPosition = storageKey
    ? loadPosition(storageKey, initialValue)
    : initialValue

  const position = ref<Position>({ ...savedPosition })
  const isDragging = ref(false)
  const hasMoved = ref(false)

  let startPos = { x: 0, y: 0 }
  let startPointer = { x: 0, y: 0 }
  let animationFrameId: number | null = null

  function loadPosition(key: string, fallback: Position): Position {
    try {
      const stored = localStorage.getItem(key)
      if (stored) {
        const parsed = JSON.parse(stored) as Position
        if (typeof parsed.x === 'number' && typeof parsed.y === 'number') {
          return parsed
        }
      }
    } catch {
      // ignore parse errors
    }
    return { ...fallback }
  }

  function savePosition() {
    if (!storageKey) return
    try {
      localStorage.setItem(storageKey, JSON.stringify(position.value))
    } catch {
      // ignore storage errors
    }
  }

  function clampPosition(pos: Position): Position {
    const maxX = window.innerWidth - elementSize - boundaryPadding
    const maxY = window.innerHeight - elementSize - boundaryPadding
    return {
      x: Math.max(boundaryPadding, Math.min(pos.x, maxX)),
      y: Math.max(boundaryPadding, Math.min(pos.y, maxY))
    }
  }

  function onPointerDown(e: PointerEvent) {
    if (!target.value) return
    e.preventDefault()

    startPos = { ...position.value }
    startPointer = { x: e.clientX, y: e.clientY }
    hasMoved.value = false

    target.value.setPointerCapture(e.pointerId)
    document.addEventListener('pointermove', onPointerMove)
    document.addEventListener('pointerup', onPointerUp)
  }

  function onPointerMove(e: PointerEvent) {
    const dx = e.clientX - startPointer.x
    const dy = e.clientY - startPointer.y

    if (!hasMoved.value && Math.abs(dx) < dragThreshold && Math.abs(dy) < dragThreshold) {
      return
    }

    hasMoved.value = true
    isDragging.value = true

    if (animationFrameId !== null) return

    animationFrameId = requestAnimationFrame(() => {
      const newPos = clampPosition({
        x: startPos.x + dx,
        y: startPos.y + dy
      })
      position.value = newPos
      animationFrameId = null
    })
  }

  function onPointerUp() {
    isDragging.value = false
    document.removeEventListener('pointermove', onPointerMove)
    document.removeEventListener('pointerup', onPointerUp)

    if (hasMoved.value) {
      savePosition()
    }

    if (animationFrameId !== null) {
      cancelAnimationFrame(animationFrameId)
      animationFrameId = null
    }
  }

  function resetPosition() {
    position.value = { ...initialValue }
    savePosition()
  }

  onMounted(() => {
    // Ensure initial position is within viewport bounds
    position.value = clampPosition(position.value)
  })

  onUnmounted(() => {
    document.removeEventListener('pointermove', onPointerMove)
    document.removeEventListener('pointerup', onPointerUp)
    if (animationFrameId !== null) {
      cancelAnimationFrame(animationFrameId)
    }
  })

  return {
    position,
    isDragging,
    hasMoved,
    onPointerDown,
    resetPosition
  }
}
