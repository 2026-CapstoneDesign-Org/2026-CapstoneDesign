package com.example.Capstone.client.reservation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NoopPhoneProviderClient implements PhoneProviderClient {

    private final ClawOpsPhoneProviderClient clawOpsPhoneProviderClient;

    public NoopPhoneProviderClient() {
        this(null);
    }

    @Autowired
    public NoopPhoneProviderClient(ClawOpsPhoneProviderClient clawOpsPhoneProviderClient) {
        this.clawOpsPhoneProviderClient = clawOpsPhoneProviderClient;
    }

    @Override
    public PhoneProviderCallStartResult startCall(PhoneProviderCallStartCommand command) {
        if (command != null
                && command.providerMode() == ReservationProviderMode.CLAWOPS
                && clawOpsPhoneProviderClient != null) {
            return clawOpsPhoneProviderClient.startCall(command);
        }

        Long reservationId = command == null ? null : command.reservationId();
        return PhoneProviderCallStartResult.disabled(
                reservationId,
                "NOOP_PHONE_PROVIDER_DISABLED",
                "전화 provider client는 아직 no-op 모드입니다."
        );
    }
}
