import { onUnmounted, type Ref } from 'vue'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { DrawSVGPlugin } from 'gsap/DrawSVGPlugin'
import { MorphSVGPlugin } from 'gsap/MorphSVGPlugin'
import { SplitText } from 'gsap/SplitText'
import { Flip } from 'gsap/Flip'
import { Observer } from 'gsap/Observer'

gsap.registerPlugin(
  ScrollTrigger,
  DrawSVGPlugin,
  MorphSVGPlugin,
  SplitText,
  Flip,
  Observer,
)

// ─── Reduced Motion ───
const reducedMotion =
  typeof window !== 'undefined' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

// ─── Global defaults for better performance ───
gsap.defaults({ force3D: true })

// ─── Constants ───
const DUR = {
  instant: reducedMotion ? 0 : 0.15,
  fast: reducedMotion ? 0 : 0.3,
  mid: reducedMotion ? 0 : 0.45,
  slow: reducedMotion ? 0 : 0.6,
  dramatic: reducedMotion ? 0 : 1.0,
  epic: reducedMotion ? 0 : 1.6,
} as const

const EASE = {
  out: 'power2.out',
  inOut: 'power2.inOut',
  back: 'back.out(1.7)',
  elastic: 'elastic.out(1, 0.5)',
  spring: 'back.out(1.4)',
  expo: 'expo.out',
  power4: 'power4.out',
  circ: 'circ.out',
  none: 'none',
} as const

// ─── Responsive matchMedia instance ───
let mm: ReturnType<typeof gsap.matchMedia> | null = null

function getMatchMedia() {
  if (typeof window !== 'undefined' && !mm) {
    mm = gsap.matchMedia()
  }
  return mm
}

// ─── ScrollTrigger cleanup ───
const triggers: ScrollTrigger[] = []

// ═══════════════════════════════════════════════════════
// TEXT EFFECTS
// ═══════════════════════════════════════════════════════

/**
 * Character-by-character text reveal with stagger.
 * Dramatic entrance for headings and titles.
 */
export function textReveal(
  selector: string | string[],
  opts: {
    type?: 'chars' | 'words' | 'lines'
    stagger?: number
    duration?: number
    ease?: string
    y?: number
    rotationX?: number
    delay?: number
    scrollTrigger?: boolean
  } = {}
) {
  const {
    type = 'chars',
    stagger = 0.03,
    duration = DUR.mid,
    ease = EASE.back,
    y = 40,
    rotationX = -90,
    delay = 0,
    scrollTrigger = false,
  } = opts

  if (reducedMotion) return

  const targets = Array.isArray(selector) ? selector : [selector]
  const splits: InstanceType<typeof SplitText>[] = []
  const tweens: gsap.core.Tween[] = []

  targets.forEach((sel) => {
    const split = SplitText.create(sel, {
      type,
      mask: type === 'lines' ? 'lines' : undefined,
      aria: 'auto',
    })
    splits.push(split)

    const elements =
      type === 'chars' ? split.chars :
      type === 'words' ? split.words :
      split.lines

    const tween = gsap.fromTo(elements,
      { y, rotationX: type === 'chars' ? rotationX : 0, autoAlpha: 0 },
      {
        y: 0,
        rotationX: 0,
        autoAlpha: 1,
        duration,
        ease,
        stagger,
        delay,
        transformOrigin: '50% 50% - 50',
        clearProps: 'transform,autoAlpha,visibility,opacity',
        ...(scrollTrigger
          ? {
              scrollTrigger: {
                trigger: sel,
                start: 'top 85%',
                toggleActions: 'play none none none',
              },
            }
          : {}),
      }
    )

    if (tween.scrollTrigger) triggers.push(tween.scrollTrigger)
    tweens.push(tween)
  })

  return { splits, tweens }
}

/**
 * Text scramble/glitch effect — cycles through random characters before revealing.
 */
export function textScramble(
  el: string | HTMLElement,
  finalText: string,
  opts: {
    duration?: number
    chars?: string
    delay?: number
  } = {}
) {
  const {
    duration = DUR.slow,
    chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*',
    delay = 0,
  } = opts

  if (reducedMotion) {
    const target = typeof el === 'string' ? document.querySelector(el) : el
    if (target) target.textContent = finalText
    return
  }

  return gsap.to(el, {
    duration,
    delay,
    scrambleText: {
      text: finalText,
      chars,
      revealDelay: 0.3,
      speed: 0.3,
    },
  })
}

// ═══════════════════════════════════════════════════════
// SVG EFFECTS
// ═══════════════════════════════════════════════════════

/**
 * Animate SVG stroke drawing from nothing to full.
 */
export function drawSVG(
  selector: string | SVGPathElement | SVGPathElement[],
  opts: {
    duration?: number
    ease?: string
    delay?: number
    from?: string
    to?: string
    scrollTrigger?: boolean
  } = {}
) {
  const {
    duration = DUR.dramatic,
    ease = EASE.inOut,
    delay = 0,
    from = '0% 0%',
    to = '0% 100%',
    scrollTrigger = false,
  } = opts

  if (reducedMotion) return

  const tween = gsap.fromTo(
    selector,
    { drawSVG: from },
    {
      drawSVG: to,
      duration,
      ease,
      delay,
      ...(scrollTrigger
        ? {
            scrollTrigger: {
              trigger: selector as string,
              start: 'top 85%',
              toggleActions: 'play none none none',
            },
          }
        : {}),
    }
  )

  if (tween.scrollTrigger) triggers.push(tween.scrollTrigger)
  return tween
}

/**
 * Morph one SVG shape into another.
 */
export function morphSVG(
  fromEl: string | Element,
  toShape: string | SVGPathElement,
  opts: {
    duration?: number
    ease?: string
    delay?: number
  } = {}
) {
  const { duration = DUR.dramatic, ease = EASE.inOut, delay = 0 } = opts

  if (reducedMotion) return

  return gsap.to(fromEl, {
    morphSVG: toShape,
    duration,
    ease,
    delay,
  })
}

// ═══════════════════════════════════════════════════════
// SCROLL-DRIVEN EFFECTS
// ═══════════════════════════════════════════════════════

/**
 * Parallax layer — element moves at different speed than scroll.
 */
export function parallax(
  selector: string | HTMLElement,
  opts: {
    speed?: number
    direction?: 'y' | 'x'
    start?: string
    end?: string
  } = {}
) {
  const {
    speed = 0.3,
    direction = 'y',
    start = 'top bottom',
    end = 'bottom top',
  } = opts

  if (reducedMotion) return

  const distance = speed * 100
  const tween = gsap.to(selector, {
    [direction]: direction === 'y' ? -distance : distance,
    ease: EASE.none,
    scrollTrigger: {
      trigger: selector,
      start,
      end,
      scrub: 1,
    },
  })

  if (tween.scrollTrigger) triggers.push(tween.scrollTrigger)
  return tween
}

/**
 * Scale element on scroll — grows/shrinks as user scrolls past.
 */
export function scrollScale(
  selector: string | HTMLElement,
  opts: {
    from?: number
    to?: number
    start?: string
    end?: string
  } = {}
) {
  const {
    from = 0.8,
    to = 1,
    start = 'top bottom',
    end = 'top center',
  } = opts

  if (reducedMotion) return

  const tween = gsap.fromTo(
    selector,
    { scale: from, autoAlpha: 0 },
    {
      scale: to,
      autoAlpha: 1,
      ease: EASE.out,
      scrollTrigger: {
        trigger: selector,
        start,
        end,
        scrub: 1,
      },
    }
  )

  if (tween.scrollTrigger) triggers.push(tween.scrollTrigger)
  return tween
}

/**
 * Horizontal scroll section — pin and scroll content horizontally.
 */
export function horizontalScroll(
  containerSelector: string,
  contentSelector: string,
  opts: {
    scrollLength?: string
  } = {}
) {
  const { scrollLength = '+=2000' } = opts

  if (reducedMotion) return

  const container = document.querySelector(containerSelector)
  const content = document.querySelector(contentSelector)
  if (!container || !content) return

  const tween = gsap.to(content, {
    x: () => -(content.scrollWidth - window.innerWidth),
    ease: EASE.none,
    scrollTrigger: {
      trigger: container,
      pin: true,
      scrub: 1,
      end: scrollLength,
      invalidateOnRefresh: true,
    },
  })

  if (tween.scrollTrigger) triggers.push(tween.scrollTrigger)
  return tween
}

/**
 * Scroll-triggered progress bar (binds to page or section scroll).
 */
export function scrollProgressBar(
  barSelector: string,
  opts?: { container?: string }
) {
  if (reducedMotion) return

  const tween = gsap.to(barSelector, {
    scaleX: 1,
    ease: EASE.none,
    scrollTrigger: {
      trigger: opts?.container || document.body,
      start: 'top top',
      end: 'bottom bottom',
      scrub: 0.3,
    },
  })

  if (tween.scrollTrigger) triggers.push(tween.scrollTrigger)
  return tween
}

// ═══════════════════════════════════════════════════════
// INTERACTIVE EFFECTS
// ═══════════════════════════════════════════════════════

/**
 * Magnetic hover — element subtly follows cursor on hover.
 * Uses quickTo for better performance (reuses tween instead of creating new ones).
 */
export function magneticHover(
  el: string | HTMLElement | HTMLElement[],
  opts: {
    strength?: number
    duration?: number
    ease?: string
    radius?: number
  } = {}
) {
  const {
    strength = 0.35,
    duration = DUR.fast,
    ease = EASE.out,
    radius = 100,
  } = opts

  if (reducedMotion) return

  const elements: HTMLElement[] = typeof el === 'string'
    ? Array.from(document.querySelectorAll(el))
    : Array.isArray(el)
      ? el
      : [el]

  const cleanups: (() => void)[] = []

  elements.forEach((element) => {
    const xTo = gsap.quickTo(element, 'x', { duration, ease })
    const yTo = gsap.quickTo(element, 'y', { duration, ease })

    const handleMouseMove = (e: MouseEvent) => {
      const rect = element.getBoundingClientRect()
      const centerX = rect.left + rect.width / 2
      const centerY = rect.top + rect.height / 2
      const distX = e.clientX - centerX
      const distY = e.clientY - centerY
      const dist = Math.sqrt(distX * distX + distY * distY)

      if (dist < radius) {
        const power = (1 - dist / radius) * strength
        xTo(distX * power)
        yTo(distY * power)
      }
    }

    const handleMouseLeave = () => {
      gsap.to(element, {
        x: 0,
        y: 0,
        duration: DUR.mid,
        ease: EASE.elastic,
      })
    }

    element.addEventListener('mousemove', handleMouseMove)
    element.addEventListener('mouseleave', handleMouseLeave)

    cleanups.push(() => {
      element.removeEventListener('mousemove', handleMouseMove)
      element.removeEventListener('mouseleave', handleMouseLeave)
    })
  })

  return () => cleanups.forEach((fn) => fn())
}

/**
 * 3D tilt effect on hover — cards tilt toward cursor.
 * Uses quickTo for better performance (reuses tween instead of creating new ones).
 */
export function tilt3D(
  el: string | HTMLElement | HTMLElement[],
  opts: {
    maxTilt?: number
    perspective?: number
    duration?: number
    ease?: string
    scale?: number
  } = {}
) {
  const {
    maxTilt = 15,
    perspective = 1000,
    duration = DUR.fast,
    ease = EASE.out,
    scale = 1.02,
  } = opts

  if (reducedMotion) return

  const elements: HTMLElement[] = typeof el === 'string'
    ? Array.from(document.querySelectorAll(el))
    : Array.isArray(el)
      ? el
      : [el]

  const cleanups: (() => void)[] = []

  elements.forEach((element) => {
    ;(element.style as any).transformPerspective = `${perspective}px`

    const rotYTo = gsap.quickTo(element, 'rotationY', { duration, ease })
    const rotXTo = gsap.quickTo(element, 'rotationX', { duration, ease })
    const scaleTo = gsap.quickTo(element, 'scale', { duration, ease })

    const handleMouseMove = (e: MouseEvent) => {
      const rect = element.getBoundingClientRect()
      const x = (e.clientX - rect.left) / rect.width - 0.5
      const y = (e.clientY - rect.top) / rect.height - 0.5

      rotYTo(x * maxTilt)
      rotXTo(-y * maxTilt)
      scaleTo(scale)
    }

    const handleMouseLeave = () => {
      gsap.to(element, {
        rotationY: 0,
        rotationX: 0,
        scale: 1,
        duration: DUR.mid,
        ease: EASE.elastic,
      })
    }

    element.addEventListener('mousemove', handleMouseMove)
    element.addEventListener('mouseleave', handleMouseLeave)

    cleanups.push(() => {
      element.removeEventListener('mousemove', handleMouseMove)
      element.removeEventListener('mouseleave', handleMouseLeave)
    })
  })

  return () => cleanups.forEach((fn) => fn())
}

/**
 * Cursor glow — a glow follows the cursor within a container.
 */
export function cursorGlow(
  container: string | HTMLElement,
  glowSelector: string
) {
  if (reducedMotion) return

  const el = typeof container === 'string'
    ? document.querySelector(container)
    : container
  const glow = document.querySelector(glowSelector) as HTMLElement
  if (!el || !glow) return

  const handleMouseMove = (e: MouseEvent) => {
    const rect = el.getBoundingClientRect()
    gsap.to(glow, {
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
      duration: DUR.fast,
      ease: EASE.out,
    })
  }

  el.addEventListener('mousemove', handleMouseMove as (e: Event) => void)
  return () => el.removeEventListener('mousemove', handleMouseMove as (e: Event) => void)
}

// ═══════════════════════════════════════════════════════
// ENTRANCE EFFECTS
// ═══════════════════════════════════════════════════════

/**
 * Stagger-reveal a set of elements from below with fade-in.
 */
export function staggerReveal(
  selector: string | HTMLElement[],
  opts: {
    y?: number
    x?: number
    scale?: number
    rotation?: number
    stagger?: number | gsap.StaggerVars
    duration?: number
    ease?: string
    delay?: number
    scrollTrigger?: boolean
    containerRef?: HTMLElement | null
    from?: 'center' | 'edges' | 'start' | 'end' | 'random'
  } = {}
) {
  const {
    y = 24,
    x = 0,
    scale,
    rotation,
    stagger = 0.06,
    duration = DUR.mid,
    ease = EASE.out,
    delay = 0,
    scrollTrigger = false,
    containerRef = null,
    from,
  } = opts

  if (reducedMotion) return

  const targets = containerRef
    ? containerRef.querySelectorAll(selector as string)
    : selector

  const staggerConfig = from ? { amount: stagger as number, from } : stagger

  const tween = gsap.fromTo(targets,
    { y, x, ...(scale !== undefined ? { scale } : {}), ...(rotation !== undefined ? { rotation } : {}), autoAlpha: 0 },
    {
      y: 0,
      x: 0,
      scale: 1,
      rotation: 0,
      autoAlpha: 1,
      duration,
      ease,
      stagger: staggerConfig,
      delay,
      clearProps: 'transform,autoAlpha,visibility,opacity',
      ...(scrollTrigger
        ? {
            scrollTrigger: {
              trigger: containerRef ?? selector,
              start: 'top 85%',
              toggleActions: 'play none none none',
            },
          }
        : {}),
    }
  )

  if (tween.scrollTrigger) triggers.push(tween.scrollTrigger)
  return tween
}

/**
 * Scroll-reveal a single element or section.
 */
export function scrollReveal(
  selector: string | HTMLElement,
  opts: {
    y?: number
    x?: number
    scale?: number
    duration?: number
    ease?: string
    start?: string
  } = {}
) {
  const {
    y = 20,
    x = 0,
    scale = 1,
    duration = DUR.mid,
    ease = EASE.out,
    start = 'top 85%',
  } = opts

  if (reducedMotion) return

  const tween = gsap.fromTo(selector,
    { y, x, scale, autoAlpha: 0 },
    {
      y: 0,
      x: 0,
      scale: 1,
      autoAlpha: 1,
      duration,
      ease,
      clearProps: 'transform,autoAlpha,visibility,opacity',
      scrollTrigger: {
        trigger: selector,
        start,
        toggleActions: 'play none none none',
      },
    }
  )

  if (tween.scrollTrigger) triggers.push(tween.scrollTrigger)
  return tween
}

/**
 * Dramatic hero entrance — orchestrated timeline for page heroes.
 */
export function heroEntrance(
  opts: {
    titleSelector?: string
    subtitleSelector?: string
    contentSelector?: string
    ctaSelector?: string
    bgSelector?: string
  } = {}
) {
  if (reducedMotion) return

  const {
    titleSelector = '.hero-title',
    subtitleSelector = '.hero-subtitle',
    contentSelector = '.hero-content',
    ctaSelector = '.hero-cta',
    bgSelector = '.hero-bg',
  } = opts

  const tl = gsap.timeline({ defaults: { ease: EASE.expo } })

  // Background zoom-in
  if (document.querySelector(bgSelector)) {
    tl.from(bgSelector, { scale: 1.2, autoAlpha: 0, duration: DUR.epic }, 0)
  }

  // Title — 3D rotation reveal
  if (document.querySelector(titleSelector)) {
    tl.from(
      titleSelector,
      { y: 60, rotationX: -90, autoAlpha: 0, duration: DUR.dramatic, ease: EASE.back },
      0.2
    )
  }

  // Subtitle — slide up
  if (document.querySelector(subtitleSelector)) {
    tl.from(
      subtitleSelector,
      { y: 40, autoAlpha: 0, duration: DUR.slow },
      0.5
    )
  }

  // Content — fade in
  if (document.querySelector(contentSelector)) {
    tl.from(
      contentSelector,
      { y: 30, autoAlpha: 0, duration: DUR.slow },
      0.7
    )
  }

  // CTA — spring pop
  if (document.querySelector(ctaSelector)) {
    tl.from(
      ctaSelector,
      { scale: 0, autoAlpha: 0, duration: DUR.mid, ease: EASE.back },
      0.9
    )
  }

  return tl
}

/**
 * FLIP animation — animate between two layout states.
 */
export function flipTransition(
  stateEl: string | HTMLElement,
  opts: {
    duration?: number
    ease?: string
    stagger?: number
    absolute?: boolean
  } = {}
) {
  const {
    duration = DUR.mid,
    ease = EASE.inOut,
    stagger = 0,
    absolute = false,
  } = opts

  if (reducedMotion) return

  const state = Flip.getState(stateEl)
  return {
    state,
    from: (targets: string | HTMLElement) =>
      Flip.from(state, {
        duration,
        ease,
        stagger,
        absolute,
        targets,
      }),
  }
}

// ═══════════════════════════════════════════════════════
// ANIMATION HELPERS
// ═══════════════════════════════════════════════════════

/**
 * Animate a number counting up from 0 to `endValue`.
 */
export function countUp(
  el: HTMLElement | string,
  endValue: number,
  opts: {
    duration?: number
    ease?: string
    decimals?: number
    prefix?: string
    suffix?: string
    scrollTrigger?: boolean
  } = {}
) {
  const {
    duration = DUR.slow,
    ease = EASE.out,
    decimals = 0,
    prefix = '',
    suffix = '',
    scrollTrigger = false,
  } = opts

  if (reducedMotion) {
    const target = typeof el === 'string' ? document.querySelector(el) : el
    if (target) target.textContent = prefix + endValue.toFixed(decimals) + suffix
    return
  }

  const obj = { val: 0 }
  const tween = gsap.to(obj, {
    val: endValue,
    duration,
    ease,
    ...(scrollTrigger
      ? {
          scrollTrigger: {
            trigger: el,
            start: 'top 85%',
            toggleActions: 'play none none none',
          },
        }
      : {}),
    onUpdate() {
      const target = typeof el === 'string' ? document.querySelector(el) : el
      if (target) target.textContent = prefix + obj.val.toFixed(decimals) + suffix
    },
  })

  if (tween.scrollTrigger) triggers.push(tween.scrollTrigger)
  return tween
}

/**
 * Animate a progress bar from 0 to target percentage.
 */
export function progressAnimate(
  el: HTMLElement | string,
  targetPercent: number,
  opts: {
    duration?: number
    ease?: string
    delay?: number
  } = {}
) {
  const { duration = DUR.slow, ease = EASE.out, delay = 0 } = opts

  if (reducedMotion) {
    const target = typeof el === 'string' ? document.querySelector(el) : el
    if (target) (target as HTMLElement).style.width = targetPercent + '%'
    return
  }

  gsap.set(el, { width: '0%' })
  return gsap.to(el, {
    width: targetPercent + '%',
    duration,
    ease,
    delay,
  })
}

/**
 * Staggered entrance timeline for cards in a container.
 */
export function cardEntrance(
  container: HTMLElement,
  cardSelector: string,
  opts: {
    y?: number
    stagger?: number
    duration?: number
    delay?: number
  } = {}
) {
  const {
    y = 24,
    stagger = 0.06,
    duration = DUR.mid,
    delay = 0,
  } = opts

  if (reducedMotion) return

  const cards = container.querySelectorAll(cardSelector)
  if (!cards.length) return

  return gsap.fromTo(cards,
    { y, autoAlpha: 0 },
    {
      y: 0,
      autoAlpha: 1,
      duration,
      ease: EASE.out,
      stagger,
      delay,
      clearProps: 'transform,autoAlpha,visibility,opacity',
    }
  )
}

/**
 * Animate metric cards with number counting.
 */
export function metricCardsAnimate(
  container: HTMLElement,
  cardSelector: string,
  valueSelector: string,
  opts: { stagger?: number; decimals?: number } = {}
) {
  const { stagger = 0.08, decimals = 0 } = opts

  if (reducedMotion) return

  const cards = container.querySelectorAll(cardSelector)

  gsap.fromTo(cards,
    { y: 20, autoAlpha: 0 },
    {
      y: 0,
      autoAlpha: 1,
      duration: DUR.mid,
      ease: EASE.out,
      stagger,
      clearProps: 'transform,autoAlpha,visibility,opacity',
      onComplete() {
      cards.forEach((card) => {
        const valueEl = card.querySelector(valueSelector)
        if (!valueEl) return
        const text = valueEl.textContent?.trim() ?? '0'
        const num = parseFloat(text)
        if (isNaN(num)) return
        const obj = { val: 0 }
        gsap.to(obj, {
          val: num,
          duration: DUR.slow,
          ease: EASE.out,
          onUpdate() {
            valueEl.textContent = obj.val.toFixed(decimals)
          },
        })
      })
    },
  })
}

/**
 * Spring-pop entrance for buttons or small interactive elements.
 */
export function springPop(
  selector: string | HTMLElement[],
  opts: { stagger?: number; delay?: number; scale?: number } = {}
) {
  const { stagger = 0.04, delay = 0, scale = 0 } = opts

  if (reducedMotion) return

  return gsap.fromTo(selector,
    { scale, autoAlpha: 0 },
    {
      scale: 1,
      autoAlpha: 1,
      duration: DUR.mid,
      ease: EASE.back,
      stagger,
      delay,
      clearProps: 'scale,autoAlpha,visibility,opacity,transform',
    }
  )
}

/**
 * Pulse/glow effect for emphasis.
 */
export function pulseGlow(el: HTMLElement, opts: { scale?: number; duration?: number } = {}) {
  const { scale = 1.05, duration = 0.3 } = opts

  if (reducedMotion) return

  return gsap.to(el, {
    scale,
    duration,
    ease: EASE.inOut,
    yoyo: true,
    repeat: 1,
  })
}

/**
 * Glow pulse with box-shadow animation.
 */
export function glowPulse(
  el: string | HTMLElement,
  opts: {
    color?: string
    spread?: number
    duration?: number
  } = {}
) {
  const {
    color = 'rgba(94, 234, 212, 0.4)',
    spread = 20,
    duration = DUR.mid,
  } = opts

  if (reducedMotion) return

  return gsap.to(el, {
    boxShadow: `0 0 ${spread}px ${color}`,
    duration,
    ease: EASE.inOut,
    yoyo: true,
    repeat: -1,
  })
}

// ═══════════════════════════════════════════════════════
// CLICK & PRESS FEEDBACK
// ═══════════════════════════════════════════════════════

/**
 * Material-style ripple effect on click.
 * Spawns a ripple circle from the click point that expands and fades.
 */
export function rippleClick(
  el: string | HTMLElement | HTMLElement[],
  opts: { color?: string; duration?: number; opacity?: number } = {}
) {
  const { color = 'rgba(255,255,255,0.35)', duration = DUR.slow, opacity = 0 } = opts

  if (reducedMotion) return

  const elements: HTMLElement[] = typeof el === 'string'
    ? Array.from(document.querySelectorAll(el))
    : Array.isArray(el) ? el : [el]

  const cleanups: (() => void)[] = []

  elements.forEach((element) => {
    element.style.position = element.style.position || 'relative'

    const handleClick = (e: MouseEvent) => {
      const rect = element.getBoundingClientRect()
      const size = Math.max(rect.width, rect.height) * 2
      const ripple = document.createElement('span')
      ripple.style.cssText = `
        position:absolute;border-radius:50%;pointer-events:none;
        width:${size}px;height:${size}px;
        left:${e.clientX - rect.left - size / 2}px;
        top:${e.clientY - rect.top - size / 2}px;
        background:${color};z-index:10;
      `
      element.appendChild(ripple)

      gsap.fromTo(ripple,
        { scale: 0, opacity: 0.6 },
        { scale: 1, opacity, duration, ease: EASE.out, onComplete: () => ripple.remove() }
      )
    }

    element.addEventListener('click', handleClick)
    cleanups.push(() => element.removeEventListener('click', handleClick))
  })

  return () => cleanups.forEach((fn) => fn())
}

/**
 * Button press — scale down on mousedown, spring back on mouseup.
 * Gives tactile feedback like a physical button.
 */
export function buttonPress(
  el: string | HTMLElement | HTMLElement[],
  opts: { scale?: number; duration?: number } = {}
) {
  const { scale = 0.92, duration = DUR.instant } = opts

  if (reducedMotion) return

  const elements: HTMLElement[] = typeof el === 'string'
    ? Array.from(document.querySelectorAll(el))
    : Array.isArray(el) ? el : [el]

  const cleanups: (() => void)[] = []

  elements.forEach((element) => {
    const down = () => {
      gsap.to(element, { scale, duration, ease: 'power2.in' })
    }
    const up = () => {
      gsap.to(element, { scale: 1, duration: DUR.fast, ease: EASE.back })
    }
    const leave = () => {
      gsap.to(element, { scale: 1, duration: DUR.fast, ease: EASE.out })
    }

    element.addEventListener('mousedown', down)
    element.addEventListener('mouseup', up)
    element.addEventListener('mouseleave', leave)

    cleanups.push(() => {
      element.removeEventListener('mousedown', down)
      element.removeEventListener('mouseup', up)
      element.removeEventListener('mouseleave', leave)
    })
  })

  return () => cleanups.forEach((fn) => fn())
}

// ═══════════════════════════════════════════════════════
// HOVER EFFECTS
// ═══════════════════════════════════════════════════════

/**
 * Shine sweep — a light band glides across the element on hover.
 * Creates a premium glass/crystal feel.
 */
export function hoverShine(
  el: string | HTMLElement | HTMLElement[],
  opts: { duration?: number; angle?: number; color?: string } = {}
) {
  const { duration = DUR.slow, angle = 25, color = 'rgba(255,255,255,0.15)' } = opts

  if (reducedMotion) return

  const elements: HTMLElement[] = typeof el === 'string'
    ? Array.from(document.querySelectorAll(el))
    : Array.isArray(el) ? el : [el]

  const cleanups: (() => void)[] = []

  elements.forEach((element) => {
    element.style.position = element.style.position || 'relative'
    element.style.overflow = 'hidden'

    const shine = document.createElement('div')
    shine.style.cssText = `
      position:absolute;top:0;left:-100%;width:60%;height:100%;
      background:linear-gradient(${angle}deg,transparent,${color},transparent);
      pointer-events:none;z-index:5;transition:none;
    `
    element.appendChild(shine)

    const handleEnter = () => {
      gsap.fromTo(shine,
        { left: '-60%' },
        { left: '120%', duration, ease: EASE.expo }
      )
    }

    element.addEventListener('mouseenter', handleEnter)
    cleanups.push(() => {
      element.removeEventListener('mouseenter', handleEnter)
      shine.remove()
    })
  })

  return () => cleanups.forEach((fn) => fn())
}

/**
 * Hover lift — subtle raise + shadow increase on hover.
 * Gives a "card lifting off the surface" feel.
 */
export function hoverLift(
  el: string | HTMLElement | HTMLElement[],
  opts: { y?: number; shadow?: string; duration?: number; scale?: number } = {}
) {
  const {
    y = -6,
    shadow = '0 12px 28px rgba(0,0,0,0.15)',
    duration = DUR.fast,
    scale = 1.01,
  } = opts

  if (reducedMotion) return

  const elements: HTMLElement[] = typeof el === 'string'
    ? Array.from(document.querySelectorAll(el))
    : Array.isArray(el) ? el : [el]

  const cleanups: (() => void)[] = []

  elements.forEach((element) => {
    const origShadow = getComputedStyle(element).boxShadow

    const handleEnter = () => {
      gsap.to(element, { y, scale, boxShadow: shadow, duration, ease: EASE.out })
    }
    const handleLeave = () => {
      gsap.to(element, { y: 0, scale: 1, boxShadow: origShadow, duration, ease: EASE.out })
    }

    element.addEventListener('mouseenter', handleEnter)
    element.addEventListener('mouseleave', handleLeave)

    cleanups.push(() => {
      element.removeEventListener('mouseenter', handleEnter)
      element.removeEventListener('mouseleave', handleLeave)
    })
  })

  return () => cleanups.forEach((fn) => fn())
}

/**
 * Icon spin — spin an icon on hover or on click.
 */
export function iconSpin(
  el: string | HTMLElement | HTMLElement[],
  opts: { degrees?: number; duration?: number; ease?: string; trigger?: 'hover' | 'click' } = {}
) {
  const { degrees = 360, duration = DUR.mid, ease = EASE.back, trigger = 'hover' } = opts

  if (reducedMotion) return

  const elements: HTMLElement[] = typeof el === 'string'
    ? Array.from(document.querySelectorAll(el))
    : Array.isArray(el) ? el : [el]

  const cleanups: (() => void)[] = []

  elements.forEach((element) => {
    let currentRotation = 0

    const animate = () => {
      currentRotation += degrees
      gsap.to(element, { rotation: currentRotation, duration, ease })
    }

    if (trigger === 'hover') {
      element.addEventListener('mouseenter', animate)
      cleanups.push(() => element.removeEventListener('mouseenter', animate))
    } else {
      element.addEventListener('click', animate)
      cleanups.push(() => element.removeEventListener('click', animate))
    }
  })

  return () => cleanups.forEach((fn) => fn())
}

// ═══════════════════════════════════════════════════════
// VALUE & STATE TRANSITIONS
// ═══════════════════════════════════════════════════════

/**
 * Morph a displayed number from its current value to a new one.
 * Smooth ticker effect for dashboard stats.
 */
export function numberMorph(
  el: HTMLElement | string,
  newValue: number,
  opts: { duration?: number; decimals?: number; prefix?: string; suffix?: string } = {}
) {
  const { duration = DUR.mid, decimals = 0, prefix = '', suffix = '' } = opts

  if (reducedMotion) {
    const target = typeof el === 'string' ? document.querySelector(el) : el
    if (target) target.textContent = prefix + newValue.toFixed(decimals) + suffix
    return
  }

  const target = typeof el === 'string' ? document.querySelector(el) : el
  if (!target) return

  const currentText = target.textContent?.trim() ?? '0'
  const currentValue = parseFloat(currentText.replace(/[^\d.-]/g, '')) || 0
  const obj = { val: currentValue }

  return gsap.to(obj, {
    val: newValue,
    duration,
    ease: EASE.out,
    onUpdate() {
      target.textContent = prefix + obj.val.toFixed(decimals) + suffix
    },
  })
}

/**
 * Animated width/height transition for expanding/collapsing sections.
 * Uses FLIP under the hood for smooth layout animation.
 */
export function expandCollapse(
  _triggerEl: string | HTMLElement,
  contentEl: string | HTMLElement,
  opts: { duration?: number; ease?: string } = {}
) {
  const { duration = DUR.mid, ease = EASE.inOut } = opts

  if (reducedMotion) return

  const content = typeof contentEl === 'string'
    ? document.querySelector(contentEl) as HTMLElement
    : contentEl
  if (!content) return

  const isHidden = content.offsetHeight === 0 || getComputedStyle(content).display === 'none'

  if (isHidden) {
    // Expanding
    content.style.display = 'block'
    const height = content.scrollHeight
    gsap.fromTo(content,
      { height: 0, autoAlpha: 0 },
      { height, autoAlpha: 1, duration, ease, onComplete: () => { content.style.height = 'auto' } }
    )
  } else {
    // Collapsing
    gsap.to(content, {
      height: 0,
      autoAlpha: 0,
      duration,
      ease,
      onComplete: () => { content.style.display = 'none' },
    })
  }
}

// ═══════════════════════════════════════════════════════
// NOTIFICATION & TOAST ANIMATIONS
// ═══════════════════════════════════════════════════════

/**
 * Animate a toast/snackbar entering from the edge.
 */
export function toastEnter(
  el: string | HTMLElement,
  opts: { direction?: 'top' | 'bottom' | 'right'; distance?: number } = {}
) {
  const { direction = 'top', distance = 60 } = opts

  if (reducedMotion) return

  const from: Record<string, any> = { autoAlpha: 0, scale: 0.9 }
  if (direction === 'top') from.y = -distance
  else if (direction === 'bottom') from.y = distance
  else from.x = distance

  return gsap.fromTo(el,
    { ...from },
    {
      autoAlpha: 1,
      scale: 1,
      y: 0,
      x: 0,
      duration: DUR.mid,
      ease: EASE.back,
      clearProps: 'transform,autoAlpha,visibility,opacity',
    }
  )
}

/**
 * Animate a toast/snackbar exiting.
 */
export function toastExit(
  el: string | HTMLElement,
  opts: { direction?: 'top' | 'bottom' | 'right'; distance?: number } = {}
) {
  const { direction = 'right', distance = 100 } = opts

  if (reducedMotion) return

  const to: Record<string, any> = { autoAlpha: 0, scale: 0.9 }
  if (direction === 'top') to.y = -distance
  else if (direction === 'bottom') to.y = distance
  else to.x = distance

  return gsap.to(el, {
    ...to,
    duration: DUR.fast,
    ease: EASE.inOut,
  })
}

// ═══════════════════════════════════════════════════════
// CONTEXT & CLEANUP
// ═══════════════════════════════════════════════════════

export function useGsapContext(containerRef: Ref<HTMLElement | undefined>) {
  let ctx: gsap.Context | null = null

  const createContext = (fn: () => void) => {
    if (containerRef.value) {
      ctx = gsap.context(fn, containerRef.value)
    }
  }

  onUnmounted(() => {
    ctx?.revert()
  })

  return { createContext }
}

export function cleanupScrollTriggers() {
  triggers.forEach((t) => t.kill())
  triggers.length = 0
}

export function prefersReducedMotion(): boolean {
  return reducedMotion
}

/**
 * Get or create a shared matchMedia instance for responsive animations.
 * Call mm.revert() on unmount to clean up all registered animations.
 */
export function useMatchMedia() {
  return getMatchMedia()
}

/**
 * Animate SVG circle stroke-dashoffset with GSAP.
 * Replaces manual requestAnimationFrame or CSS transition approaches.
 */
export function animateSvgCircle(
  circle: SVGCircleElement,
  targetOffset: number,
  opts: {
    duration?: number
    ease?: string
    delay?: number
    onUpdate?: (progress: number) => void
  } = {}
) {
  const { duration = DUR.dramatic, ease = 'power3.out', delay = 0, onUpdate } = opts

  if (reducedMotion) {
    gsap.set(circle, { attr: { 'stroke-dashoffset': targetOffset } })
    onUpdate?.(1)
    return
  }

  return gsap.to(circle, {
    attr: { 'stroke-dashoffset': targetOffset },
    duration,
    ease,
    delay,
    onUpdate: onUpdate ? () => {
      const current = parseFloat(circle.getAttribute('stroke-dashoffset') || '0')
      onUpdate(current / targetOffset)
    } : undefined,
  })
}

export { gsap, ScrollTrigger, SplitText, DrawSVGPlugin, MorphSVGPlugin, Flip, Observer, DUR, EASE }
