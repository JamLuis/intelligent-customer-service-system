import { StrictMode, type ReactNode } from 'react'
import { createRoot } from 'react-dom/client'
import '../style.css'

export function mountPage(node: ReactNode) {
  const rootElement = document.getElementById('app')

  if (!rootElement) {
    throw new Error('Root element #app was not found.')
  }

  createRoot(rootElement).render(<StrictMode>{node}</StrictMode>)
}