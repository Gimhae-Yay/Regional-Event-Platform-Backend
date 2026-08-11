package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentDiscrepancyRepository;

class PaymentDiscrepancyServiceTest {

    @Test
    void findAllByStatus_고정정렬조회만수행하고상태를변경하지않는다() {
        PaymentDiscrepancyRepository paymentDiscrepancyRepository = mock(
            PaymentDiscrepancyRepository.class
        );
        PaymentDiscrepancy discrepancy = mock(PaymentDiscrepancy.class);
        when(paymentDiscrepancyRepository
            .findAllByStatusOrderByDetectedAtAscPaymentDiscrepancyIdAsc("OPEN"))
            .thenReturn(List.of(discrepancy));
        PaymentDiscrepancyService service = new PaymentDiscrepancyService(paymentDiscrepancyRepository);

        List<PaymentDiscrepancy> actual = service.findAllByStatus("OPEN");

        assertThat(actual).containsExactly(discrepancy);
        verify(paymentDiscrepancyRepository)
            .findAllByStatusOrderByDetectedAtAscPaymentDiscrepancyIdAsc("OPEN");
        verifyNoMoreInteractions(paymentDiscrepancyRepository);
    }
}
