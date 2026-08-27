<template>
  <div ref="wrapRef" class="orders-chart" @mousemove="onMove" @mouseleave="hoverIndex = null">
    <svg :viewBox="`0 0 ${W} ${H}`" class="orders-chart-svg">
      <line
        v-for="(tick, i) in yTicks"
        :key="`grid-${i}`"
        :x1="pad.l"
        :x2="W - pad.r"
        :y1="yScale(tick)"
        :y2="yScale(tick)"
        class="orders-chart-grid"
      />
      <path v-if="points.length > 1" :d="areaPath" class="orders-chart-area" />
      <path v-if="points.length > 1" :d="linePath" class="orders-chart-line" />
      <circle v-for="(p, i) in markerPoints" :key="`mk-${i}`" :cx="p.x" :cy="p.y" r="3.5" class="orders-chart-marker" />

      <text v-for="(lbl, i) in xLabels" :key="`xl-${i}`" :x="lbl.x" :y="H - 6" text-anchor="middle" class="orders-chart-axis-label">
        {{ lbl.text }}
      </text>
      <text v-for="(tick, i) in yTicks" :key="`yl-${i}`" :x="pad.l - 6" :y="yScale(tick) + 3.5" text-anchor="end" class="orders-chart-axis-label">
        {{ tick }}
      </text>

      <g v-if="hoverIndex !== null">
        <line :x1="xScale(hoverIndex)" :x2="xScale(hoverIndex)" :y1="pad.t" :y2="H - pad.b" class="orders-chart-crosshair" />
        <circle :cx="xScale(hoverIndex)" :cy="yScale(points[hoverIndex].count)" r="4.5" class="orders-chart-marker-active" />
      </g>
    </svg>

    <div v-if="hoverIndex !== null" class="orders-chart-tooltip" :style="tooltipStyle">
      <div class="orders-chart-tooltip-label">{{ points[hoverIndex].label }}</div>
      <div class="orders-chart-tooltip-value">{{ points[hoverIndex].count }} pedido{{ points[hoverIndex].count === 1 ? '' : 's' }}</div>
    </div>

    <p v-if="points.length === 0" class="orders-chart-empty">Sem pedidos no período.</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { OrderPeriodPoint } from '@/api/salesOrders'

const props = defineProps<{ points: OrderPeriodPoint[] }>()

const wrapRef = ref<HTMLElement | null>(null)
const hoverIndex = ref<number | null>(null)

const H = 160
const pad = { t: 14, r: 14, b: 26, l: 30 }

// Kept in sync with the wrapper's actual rendered width (not just window
// resize -- the sidebar's collapse toggle changes this layout without
// firing one) so the viewBox always maps 1:1 to real pixels. Anything else
// (a fixed viewBox stretched via CSS, or preserveAspectRatio="none") scales
// non-uniformly against a fluid container and blurs the text/markers.
const containerWidth = ref(700)
let resizeObserver: ResizeObserver | undefined

onMounted(() => {
  const el = wrapRef.value
  if (!el) return
  containerWidth.value = el.clientWidth || containerWidth.value
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width
      if (width) containerWidth.value = width
    })
    resizeObserver.observe(el)
  }
})

onBeforeUnmount(() => resizeObserver?.disconnect())

const W = computed(() => Math.max(containerWidth.value, 200))

const maxCount = computed(() => Math.max(1, ...props.points.map((p) => p.count)))

const yTicks = computed(() => {
  const max = maxCount.value
  return [0, Math.round(max / 2), max].filter((v, i, arr) => arr.indexOf(v) === i)
})

function xScale(i: number) {
  const n = props.points.length
  if (n <= 1) return pad.l
  return pad.l + (i / (n - 1)) * (W.value - pad.l - pad.r)
}

function yScale(value: number) {
  const innerH = H - pad.t - pad.b
  return pad.t + innerH - (value / maxCount.value) * innerH
}

const linePath = computed(() =>
  props.points.map((p, i) => `${i === 0 ? 'M' : 'L'}${xScale(i).toFixed(1)} ${yScale(p.count).toFixed(1)}`).join(' '),
)

const areaPath = computed(() => {
  const n = props.points.length
  if (n === 0) return ''
  return `${linePath.value} L${xScale(n - 1).toFixed(1)} ${H - pad.b} L${xScale(0).toFixed(1)} ${H - pad.b}Z`
})

// A dot on every point crowds a 30-day series -- show at most ~7, always including the last.
const markerPoints = computed(() => {
  const n = props.points.length
  if (n === 0) return []
  const step = Math.max(1, Math.ceil(n / 7))
  return props.points
    .map((p, i) => ({ x: xScale(i), y: yScale(p.count), i }))
    .filter(({ i }) => i % step === 0 || i === n - 1)
})

const xLabels = computed(() => {
  const n = props.points.length
  if (n === 0) return []
  const step = Math.max(1, Math.ceil(n / 7))
  return props.points
    .map((p, i) => ({ text: p.label, x: xScale(i), i }))
    .filter(({ i }) => i % step === 0 || i === n - 1)
})

function onMove(event: MouseEvent) {
  const el = wrapRef.value
  if (!el || props.points.length === 0) return
  const rect = el.getBoundingClientRect()
  const relX = ((event.clientX - rect.left) / rect.width) * W.value
  let nearest = 0
  let best = Infinity
  for (let i = 0; i < props.points.length; i++) {
    const d = Math.abs(xScale(i) - relX)
    if (d < best) {
      best = d
      nearest = i
    }
  }
  hoverIndex.value = nearest
}

const tooltipStyle = computed(() => {
  if (hoverIndex.value === null) return {}
  const leftPct = (xScale(hoverIndex.value) / W.value) * 100
  const topPct = (yScale(props.points[hoverIndex.value].count) / H) * 100
  return { left: `${leftPct}%`, top: `${topPct}%` }
})
</script>

<style scoped>
.orders-chart {
  position: relative;
  width: 100%;
}

.orders-chart-svg {
  width: 100%;
  height: 160px;
  display: block;
}

.orders-chart-grid {
  stroke: var(--pm-border-light);
  stroke-width: 1;
  stroke-dasharray: 4 3;
}

.orders-chart-area {
  fill: var(--pm-accent);
  fill-opacity: 0.08;
}

.orders-chart-line {
  fill: none;
  stroke: var(--pm-accent);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.orders-chart-marker {
  fill: var(--pm-white);
  stroke: var(--pm-accent);
  stroke-width: 2;
}

.orders-chart-marker-active {
  fill: var(--pm-accent);
  stroke: var(--pm-white);
  stroke-width: 2;
}

.orders-chart-crosshair {
  stroke: var(--pm-text-muted);
  stroke-width: 1;
  stroke-dasharray: 3 3;
}

.orders-chart-axis-label {
  font-size: 9px;
  fill: var(--pm-text-muted);
  font-family: var(--pm-font);
}

.orders-chart-tooltip {
  position: absolute;
  transform: translate(-50%, -130%);
  background: var(--pm-text-dark);
  color: var(--pm-white);
  border-radius: 6px;
  padding: 6px 10px;
  font-family: var(--pm-font);
  font-size: 11px;
  white-space: nowrap;
  pointer-events: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.18);
}

.orders-chart-tooltip-label {
  opacity: 0.75;
  margin-bottom: 2px;
}

.orders-chart-tooltip-value {
  font-weight: 700;
}

.orders-chart-empty {
  text-align: center;
  color: var(--pm-text-mid);
  font-size: 13px;
  margin: 0;
}
</style>
