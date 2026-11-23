package com.uni.usermicroservice.communications;

import com.uni.usermicroservice.security.PasswordChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordChangedNotifier {

    private static final String EVENT_NAME = "usuario.contrasena-actualizada";

    private final CommunicationsClient communicationsClient;

    public PasswordChangedNotifier(CommunicationsClient communicationsClient) {
        this.communicationsClient = communicationsClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordChanged(PasswordChangedEvent event) {
        communicationsClient.notifyUser(EVENT_NAME, event.userId());
    }
}
