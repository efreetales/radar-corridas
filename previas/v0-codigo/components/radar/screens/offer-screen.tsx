import { OfferCard } from '../offer-card'
import { RideBackdrop } from '../ride-backdrop'
import type { ScreenProps, Verdict } from '../config'

export function OfferScreen({ variant }: ScreenProps) {
  return (
    <RideBackdrop>
      <div className="absolute inset-x-3 top-3">
        <OfferCard verdict={variant as Verdict} />
      </div>
    </RideBackdrop>
  )
}
