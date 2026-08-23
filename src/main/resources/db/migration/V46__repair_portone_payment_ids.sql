UPDATE payment
SET portone_payment_id = (
    SELECT pv.observed_order_id
    FROM payment_verification pv
    WHERE pv.payment_id = payment.payment_id
      AND pv.internal_decision = 'APPROVE'
    ORDER BY pv.payment_verification_id DESC
    LIMIT 1
)
WHERE payment.status = 'APPROVED'
  AND payment.portone_payment_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM payment_verification pv
      WHERE pv.payment_id = payment.payment_id
        AND pv.internal_decision = 'APPROVE'
  );

UPDATE payment
SET portone_payment_id = (
    SELECT pv.observed_order_id
    FROM payment_verification pv
    WHERE pv.payment_id = payment.payment_id
      AND pv.internal_decision IN ('APPROVE', 'DISCREPANT')
    ORDER BY pv.payment_verification_id DESC
    LIMIT 1
)
WHERE payment.status = 'DISCREPANT'
  AND payment.portone_payment_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM payment_verification pv
      WHERE pv.payment_id = payment.payment_id
        AND pv.internal_decision IN ('APPROVE', 'DISCREPANT')
  );
