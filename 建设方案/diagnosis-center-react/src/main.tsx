import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/theme.css'
import './styles/glass.css'
import DiagnosisCenterPage from './pages/DiagnosisCenter'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <DiagnosisCenterPage />
  </StrictMode>,
)

