import { ref, watch, onBeforeUnmount, type Ref } from 'vue'

export interface DropdownPosition {
  top?: string
  bottom?: string
  left: string
  width?: string
}

export interface UseDropdownPositionOptions {
  /** Size the dropdown to the trigger's own width (falling back to `minWidth` when narrower). */
  matchTriggerWidth?: boolean
  minWidth?: number
}

/**
 * Shared open/close + fixed-position logic for Teleport-to-body dropdowns
 * (row-action menus, filter panels, etc). Flips above the trigger when
 * there isn't enough room below, and closes on an outside click.
 *
 * Takes the trigger/menu template refs as params (rather than creating and
 * returning them) so the caller declares them locally via `ref()` -- the
 * pattern Vue's template-ref (`ref="x"`) resolution expects.
 */
export function useDropdownPosition(
  triggerRef: Ref<HTMLElement | null>,
  menuRef: Ref<HTMLElement | null>,
  estimatedHeight = 150,
  options: UseDropdownPositionOptions = {},
) {
  const open = ref(false)
  const position = ref<DropdownPosition>({ top: '0px', left: '0px' })

  function close() {
    open.value = false
  }

  function toggle() {
    if (open.value) {
      close()
      return
    }
    if (triggerRef.value) {
      const rect = triggerRef.value.getBoundingClientRect()
      const viewportHeight = document.documentElement.clientHeight
      const spaceBelow = viewportHeight - rect.bottom
      const width = options.matchTriggerWidth ? `${Math.max(rect.width, options.minWidth ?? 0)}px` : undefined
      position.value = {
        ...(spaceBelow > estimatedHeight
          ? { top: `${rect.bottom + 4}px` }
          : { bottom: `${viewportHeight - rect.top + 4}px` }),
        left: `${rect.left}px`,
        width,
      }
    }
    open.value = true
  }

  function handleClickOutside(event: MouseEvent) {
    const target = event.target as Node
    const inTrigger = !!triggerRef.value?.contains(target)
    const inMenu = !!menuRef.value?.contains(target)
    if (!inTrigger && !inMenu) {
      close()
    }
  }

  watch(open, (isOpen) => {
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside)
    } else {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  })

  onBeforeUnmount(() => {
    document.removeEventListener('mousedown', handleClickOutside)
  })

  return { open, position, toggle, close }
}
