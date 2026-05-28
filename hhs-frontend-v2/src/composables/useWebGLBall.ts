import { ref, watch, onUnmounted, type Ref } from 'vue'
import * as THREE from 'three'

interface WebGLBallOptions {
  size?: number
  color?: string
  hoverColor?: string
  activeColor?: string
}

interface BallState {
  isHovering: boolean
  isActive: boolean
  isDragging: boolean
  mouseX: number
  mouseY: number
}

export function useWebGLBall(
  canvasRef: Ref<HTMLCanvasElement | undefined>,
  options: WebGLBallOptions = {}
) {
  const {
    size = 56,
    color = '#2563eb',
    hoverColor = '#4f8eff',
    activeColor = '#1d4ed8'
  } = options

  const isReady = ref(false)
  const isMobile = ref(false)

  let renderer: THREE.WebGLRenderer | null = null
  let scene: THREE.Scene | null = null
  let camera: THREE.PerspectiveCamera | null = null
  let sphere: THREE.Mesh | null = null
  let ambientLight: THREE.AmbientLight | null = null
  let directionalLight: THREE.DirectionalLight | null = null
  let pointLight: THREE.PointLight | null = null
  let animationFrameId: number | null = null

  const state: BallState = {
    isHovering: false,
    isActive: false,
    isDragging: false,
    mouseX: 0,
    mouseY: 0
  }

  let targetRotationX = 0
  let targetRotationY = 0
  let currentRotationX = 0
  let currentRotationY = 0
  let breathPhase = 0
  let hoverBaseX = 0
  let hoverBaseY = 0
  let wasHovering = false
  let dragStretchX = 1
  let dragStretchY = 1
  let targetStretchX = 1
  let targetStretchY = 1

  function checkMobile() {
    isMobile.value = window.matchMedia('(max-width: 768px)').matches
  }

  function initScene(canvas: HTMLCanvasElement) {
    checkMobile()
    if (isMobile.value) return

    const pixelRatio = Math.min(window.devicePixelRatio, 2)
    const canvasSize = size * pixelRatio

    renderer = new THREE.WebGLRenderer({
      canvas,
      alpha: true,
      antialias: true,
      powerPreference: 'high-performance'
    })
    renderer.setSize(canvasSize, canvasSize, false)
    renderer.setPixelRatio(pixelRatio)
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.toneMapping = THREE.ACESFilmicToneMapping
    renderer.toneMappingExposure = 1.2

    scene = new THREE.Scene()

    camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100)
    camera.position.z = 2.5

    const geometry = new THREE.SphereGeometry(1, 32, 32)
    const logoTexture = createLogoTexture()
    const material = new THREE.MeshPhysicalMaterial({
      map: logoTexture,
      metalness: 0.2,
      roughness: 0.3,
      clearcoat: 0.9,
      clearcoatRoughness: 0.1,
      reflectivity: 0.8,
      envMapIntensity: 0.8
    })

    sphere = new THREE.Mesh(geometry, material)
    scene.add(sphere)

    ambientLight = new THREE.AmbientLight(0xffffff, 0.7)
    scene.add(ambientLight)

    directionalLight = new THREE.DirectionalLight(0xffffff, 1.0)
    directionalLight.position.set(3, 3, 4)
    scene.add(directionalLight)

    pointLight = new THREE.PointLight(new THREE.Color(color), 1.2, 10)
    pointLight.position.set(-2, 2, 3)
    scene.add(pointLight)

    const envTexture = createEnvTexture()
    if (envTexture) {
      scene.environment = envTexture
    }

    isReady.value = true
    startAnimation()
  }

  function createLogoTexture(): THREE.CanvasTexture {
    const canvas = document.createElement('canvas')
    canvas.width = 512
    canvas.height = 512
    const ctx = canvas.getContext('2d')!

    ctx.fillStyle = '#1a3a6e'
    ctx.fillRect(0, 0, 512, 512)

    const gradient = ctx.createRadialGradient(256, 256, 0, 256, 256, 256)
    gradient.addColorStop(0, '#2a5aae')
    gradient.addColorStop(1, '#0f2a5c')
    ctx.fillStyle = gradient
    ctx.fillRect(0, 0, 512, 512)

    ctx.fillStyle = '#ffffff'
    ctx.font = 'bold 120px Arial, sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'

    ctx.save()
    ctx.translate(256, 256)
    ctx.fillText('HHS', 0, -20)

    ctx.font = '36px Arial, sans-serif'
    ctx.fillText('Health System', 0, 50)
    ctx.restore()

    ctx.strokeStyle = '#ffffff'
    ctx.lineWidth = 6
    ctx.beginPath()
    ctx.moveTo(256, 180)
    ctx.lineTo(256, 220)
    ctx.moveTo(240, 200)
    ctx.lineTo(272, 200)
    ctx.stroke()

    const texture = new THREE.CanvasTexture(canvas)
    texture.needsUpdate = true
    return texture
  }

  function createEnvTexture(): THREE.CubeTexture | null {
    if (!scene) return null

    const cubeRenderTarget = new THREE.WebGLCubeRenderTarget(128)
    const cubeCamera = new THREE.CubeCamera(0.1, 10, cubeRenderTarget)

    const gradientTop = new THREE.Color(0x1a1a2e)
    const gradientBottom = new THREE.Color(0x16213e)

    const envScene = new THREE.Scene()
    const envGeo = new THREE.SphereGeometry(50, 32, 32)
    const envMat = new THREE.ShaderMaterial({
      uniforms: {
        topColor: { value: gradientTop },
        bottomColor: { value: gradientBottom }
      },
      vertexShader: `
        varying vec3 vWorldPosition;
        void main() {
          vec4 worldPosition = modelMatrix * vec4(position, 1.0);
          vWorldPosition = worldPosition.xyz;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: `
        uniform vec3 topColor;
        uniform vec3 bottomColor;
        varying vec3 vWorldPosition;
        void main() {
          float h = normalize(vWorldPosition).y;
          gl_FragColor = vec4(mix(bottomColor, topColor, max(h, 0.0)), 1.0);
        }
      `,
      side: THREE.BackSide
    })
    envScene.add(new THREE.Mesh(envGeo, envMat))

    const light = new THREE.AmbientLight(0xffffff, 0.5)
    envScene.add(light)

    cubeCamera.update(renderer!, envScene)
    return cubeRenderTarget.texture
  }

  function startAnimation() {
    function animate() {
      animationFrameId = requestAnimationFrame(animate)
      update()
      render()
    }
    animate()
  }

  function update() {
    if (!sphere) return

    breathPhase += 0.02
    const breathIntensity = 0.8 + Math.sin(breathPhase) * 0.2

    if (pointLight) {
      pointLight.intensity = breathIntensity
    }

    if (state.isHovering) {
      if (!wasHovering) {
        hoverBaseX = currentRotationX
        hoverBaseY = currentRotationY
        wasHovering = true
      }
      targetRotationX = hoverBaseX + (-state.mouseY * 0.3)
      targetRotationY = hoverBaseY + (state.mouseX * 0.3)
    } else {
      wasHovering = false
      targetRotationY += 0.012
    }

    currentRotationX += (targetRotationX - currentRotationX) * 0.08
    currentRotationY += (targetRotationY - currentRotationY) * 0.08

    sphere.rotation.x = currentRotationX
    sphere.rotation.y = currentRotationY

    if (state.isDragging) {
      targetStretchX = 1.15
      targetStretchY = 0.9
    } else {
      targetStretchX = 1
      targetStretchY = 1
    }

    dragStretchX += (targetStretchX - dragStretchX) * 0.1
    dragStretchY += (targetStretchY - dragStretchY) * 0.1

    sphere.scale.set(dragStretchX, dragStretchY, 1)

    if (sphere.material instanceof THREE.MeshPhysicalMaterial) {
      const targetColor = state.isActive
        ? new THREE.Color(activeColor)
        : state.isHovering
          ? new THREE.Color(hoverColor)
          : new THREE.Color(color)

      sphere.material.color.lerp(targetColor, 0.05)

      const targetMetalness = state.isHovering ? 0.5 : 0.3
      sphere.material.metalness += (targetMetalness - sphere.material.metalness) * 0.05

      const targetClearcoat = state.isHovering ? 1.0 : 0.8
      sphere.material.clearcoat += (targetClearcoat - sphere.material.clearcoat) * 0.05
    }
  }

  function render() {
    if (!renderer || !scene || !camera) return
    renderer.render(scene, camera)
  }

  function updateHover(mouseX: number, mouseY: number) {
    state.isHovering = true
    state.mouseX = mouseX
    state.mouseY = mouseY
  }

  function clearHover() {
    state.isHovering = false
    state.mouseX = 0
    state.mouseY = 0
  }

  function setActive(active: boolean) {
    state.isActive = active
  }

  function setDragging(dragging: boolean) {
    state.isDragging = dragging
  }

  function dispose() {
    if (animationFrameId !== null) {
      cancelAnimationFrame(animationFrameId)
      animationFrameId = null
    }

    if (sphere) {
      sphere.geometry.dispose()
      if (sphere.material instanceof THREE.Material) {
        sphere.material.dispose()
      }
      sphere = null
    }

    if (renderer) {
      renderer.dispose()
      renderer = null
    }

    scene = null
    camera = null
    ambientLight = null
    directionalLight = null
    pointLight = null
    isReady.value = false
  }

  watch(canvasRef, (newCanvas) => {
    if (newCanvas) {
      initScene(newCanvas)
    } else {
      dispose()
    }
  })

  onUnmounted(() => {
    dispose()
  })

  if (typeof window !== 'undefined') {
    const mediaQuery = window.matchMedia('(max-width: 768px)')
    mediaQuery.addEventListener('change', checkMobile)
    onUnmounted(() => {
      mediaQuery.removeEventListener('change', checkMobile)
    })
  }

  return {
    isReady,
    isMobile,
    updateHover,
    clearHover,
    setActive,
    setDragging,
    dispose
  }
}
