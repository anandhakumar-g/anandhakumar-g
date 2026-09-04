-- ============================================================================
-- MVP-8 (B4): a resident can rate + comment on an offer (one row per user per
-- offer). App-scoped like the rest of the offer module — no RLS. Comment is
-- encrypted at rest, mirroring ticket.rating_comment_enc.
-- ============================================================================

CREATE TABLE offer_feedback (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    offer_id    uuid NOT NULL REFERENCES offer(id),
    user_id     uuid NOT NULL REFERENCES app_user(id),
    rating      integer NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment_enc text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (offer_id, user_id)
);
CREATE INDEX ix_offer_feedback_offer ON offer_feedback (offer_id);
