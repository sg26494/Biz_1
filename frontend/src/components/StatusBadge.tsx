import { ORDER_STATUS_LABEL } from '@/lib/format';

const TONE: Record<string, string> = {
  PAID: 'bg-indigo-50 text-indigo-700', PACKED: 'bg-indigo-50 text-indigo-700', SHIPPED: 'bg-indigo-50 text-indigo-700',
  DELIVERED: 'bg-green-50 text-green-800', COMPLETED: 'bg-green-50 text-green-800',
  CANCELLED: 'bg-sand-100 text-sand-600', PAYMENT_FAILED: 'bg-red-50 text-red-800', RTO: 'bg-red-50 text-red-800',
  PENDING_PAYMENT: 'bg-clay-50 text-clay-700', RETURN_IN_PROGRESS: 'bg-clay-50 text-clay-700',
};

export function StatusBadge({ status }: { status: string }) {
  return <span className={`badge ${TONE[status] || 'bg-sand-100 text-sand-800'}`}>{ORDER_STATUS_LABEL[status] || status.replace(/_/g, ' ')}</span>;
}
