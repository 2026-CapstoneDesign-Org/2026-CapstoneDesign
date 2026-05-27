package com.example.Capstone.client.reservation;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.example.Capstone.domain.ReservationStatus;

import lombok.RequiredArgsConstructor;

@Primary
@Component
@RequiredArgsConstructor
public class SafeReservationCallProvider implements ReservationCallProvider {

    private final ReservationProviderProperties providerProperties;
    private final MockReservationCallProvider mockReservationCallProvider;
    private final ReservationProviderActivationGuard activationGuard;
    private final OpenAiRealtimeClient openAiRealtimeClient;
    private final PhoneProviderClient phoneProviderClient;
    private final ClawOpsSidecarPhoneProviderClient clawOpsSidecarPhoneProviderClient;

    @Override
    public ReservationCallStartResult startCall(ReservationCallStartCommand command) {
        ReservationProviderMode providerMode = providerProperties.effectiveMode();
        if (providerMode == ReservationProviderMode.MOCK) {
            return mockReservationCallProvider.startCall(command);
        }
        if (providerMode == ReservationProviderMode.NOOP) {
            return noOpResult(command, "NOOP_PROVIDER_MODE");
        }

        ReservationProviderActivationResult activationResult = activationGuard.verify(providerMode, command);
        if (!activationResult.allowed()) {
            return noOpResult(command, activationResult.providerStatus());
        }

        if (providerMode == ReservationProviderMode.CLAWOPS) {
            PhoneProviderCallStartCommand phoneCommand = PhoneProviderCallStartCommand.from(
                    providerMode,
                    command,
                    null
            ).withToPhoneNumber(providerProperties.resolveProviderTargetPhoneNumber(
                    providerMode,
                    command.restaurantPhoneNumber()
            ));
            if (providerProperties.isClawOpsSidecarRuntime()) {
                return clawOpsSidecarPhoneProviderClient.startCall(phoneCommand)
                        .toReservationCallStartResult();
            }
            return phoneProviderClient.startCall(
                    phoneCommand
            ).toReservationCallStartResult();
        }

        OpenAiRealtimeSessionResult openAiSession = openAiRealtimeClient.prepareReservationSession(
                OpenAiRealtimeSessionCommand.from(command)
        );
        if (openAiSession == null || !openAiSession.enabled()) {
            return noOpResult(command, "NOOP_OPENAI_REALTIME_DISABLED");
        }

        return phoneProviderClient.startCall(
                PhoneProviderCallStartCommand.from(providerMode, command, openAiSession)
        ).toReservationCallStartResult();
    }

    private ReservationCallStartResult noOpResult(ReservationCallStartCommand command, String providerStatus) {
        return new ReservationCallStartResult(
                ReservationStatus.REQUESTED,
                "NOOP",
                "noop-" + command.reservationId(),
                providerStatus
        );
    }
}
