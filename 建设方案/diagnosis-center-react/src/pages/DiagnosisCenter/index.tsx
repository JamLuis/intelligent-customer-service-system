import DiagnosisLayout from '../../layouts/DiagnosisLayout'
import CaseSidebar from './CaseSidebar'
import ChatPanel from './ChatPanel'
import EvidenceChain from './EvidenceChain'
import ConclusionPanel from './ConclusionPanel'
import WorkOrderPanel from './WorkOrderPanel'

export default function DiagnosisCenterPage() {
  return (
    <DiagnosisLayout
      sidebar={<CaseSidebar />}
      chat={<ChatPanel />}
      evidence={<EvidenceChain />}
      bottom={
        <div className="diagnosis-bottom-grid">
          <ConclusionPanel />
          <WorkOrderPanel />
        </div>
      }
    />
  )
}
